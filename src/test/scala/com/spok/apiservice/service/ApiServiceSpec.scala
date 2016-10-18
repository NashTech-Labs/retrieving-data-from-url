package com.spok.apiservice.service

import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.ws.{ Message, TextMessage, _ }
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.scaladsl.{ Flow, Keep }
import akka.stream.testkit.scaladsl.{ TestSink, TestSource }
import akka.stream.{ FlowShape, Graph }
import com.spok.util.ConfigUtil._
import com.spok.util.Constant._
import org.mockito.Mockito.when
import org.scalatest.mock.MockitoSugar
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpec }
import com.spok.model.NotificationDetail
import com.spok.model.Spok._
import com.spok.persistence.redis.RedisFactory
import com.spok.util.JsonHelper

import scala.concurrent.Future

class ApiServiceSpec extends WordSpec with Matchers with MockitoSugar with BeforeAndAfterAll with ScalatestRouteTest with JsonHelper {

  val apiService = new ApiService() {
    override val http = mock[HttpExt]
    override val redisFactory = mock[RedisFactory]
  }
  val route = apiService.routes

  "Api Service" should {

    "return pong in response" in {

      when(apiService.http.singleRequest(HttpRequest(uri = HTTP + interface + ":" + spokPort + DELIMITER + PING)))
        .thenReturn(Future(HttpResponse(StatusCodes.OK)))
      Get("/ping") ~> route ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    "return the bad request when server is not up" in {
      Get("/greeter") ~> route ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    "return ok if the server is up test2" in {
      Get("/greeter") ~> Upgrade(List(UpgradeProtocol("websocket"))) ~> emulateHttpCore ~> route ~> check {
        status shouldEqual StatusCodes.SwitchingProtocols
      }
    }

    "return ok if the spok route is hit" in {

      val apiService = new ApiService() {
        override val http = mock[HttpExt]

        override def spokResponseHandler(url: String)(userInfo: (String, String)): Flow[Message, Message, Any] = sendUserInfoInUrl(url)(userInfo)
      }
      val route = apiService.routes
      Get("/spoks") ~> Upgrade(List(UpgradeProtocol("websocket"))) ~> emulateHttpCore ~> route ~> check {
        status shouldEqual StatusCodes.SwitchingProtocols
      }
    }

    "return Invalid JSON as response from Spok server, if INVALID_JSON is sent" in {
      def receiveNotificationViaWebSocket(url: String)(userInfo: (String, String)): Flow[Message, Message, Any] = {
        Flow[Message]
          .collect {
            case TextMessage.Strict(msg) ⇒ INVALID_JSON
          }
          .map {
            case msg: String ⇒ TextMessage.Strict(INVALID_JSON)
          }
      }

      val apiService = new ApiService() {
        override val http = mock[HttpExt]

        override def sendUserInfoInUrl(url: String)(userInfo: (String, String)) = receiveNotificationViaWebSocket(url)(userInfo)
      }

      val result = (apiService.spokResponseHandler("url")("userId", "userNumber"))
      val (pub, sub) = TestSource.probe[Message]
        .via(result)
        .toMat(TestSink.probe[Message])(Keep.both)
        .run()
      sub.request(1)
      pub.sendNext(TextMessage.Strict(INVALID_JSON))
      sub.expectNext(TextMessage.Strict(INVALID_JSON))
    }

    "return valid JSON as response from Spok server, if token is sent from server and mention user id list is empty" in {

      val response = """{"spokId":"randomId","mentionUserId":[]}"""

      def receiveNotificationViaWebSocket(url: String)(userInfo: (String, String)): Flow[Message, Message, Any] = {

        Flow[Message]
          .collect {
            case TextMessage.Strict(msg) ⇒ response
          }
          .map {
            case msg: String ⇒ TextMessage.Strict(response)
          }
      }

      val apiService = new ApiService() {
        override val http = mock[HttpExt]

        override def sendUserInfoInUrl(url: String)(userInfo: (String, String)) = receiveNotificationViaWebSocket(url)(userInfo)

        val userInfo = ("userId", "userNumber")
      }

      val result = (apiService.spokResponseHandler("url")("userId", "userNumber"))
      val (pub, sub) = TestSource.probe[Message]
        .via(result)
        .toMat(TestSink.probe[Message])(Keep.both)
        .run()
      sub.request(1)
      pub.sendNext(TextMessage.Strict(response))
      sub.expectNext(TextMessage.Strict(response))
    }

    "return valid JSON as response from Spok server, if token is sent from server and mention user id list is not empty" in {

      val response = """{"spokId":"randomId","mentionUserId":["userId1234"]}"""

      def receiveNotificationViaWebSocket(url: String)(userInfo: (String, String)): Flow[Message, Message, Any] = {
        val (userId, _) = userInfo
        Flow[Message]
          .collect {
            case TextMessage.Strict(msg) ⇒ response
          }
          .map {
            case msg: String ⇒ TextMessage.Strict(response)
          }
      }

      val apiService = new ApiService() {
        override val http = mock[HttpExt]

        override def sendUserInfoInUrl(url: String)(userInfo: (String, String)) = receiveNotificationViaWebSocket(url)(userInfo)
      }

      val result = (apiService.spokResponseHandler("url")("userId", "userNumber"))
      val (pub, sub) = TestSource.probe[Message]
        .via(result)
        .toMat(TestSink.probe[Message])(Keep.both)
        .run()
      sub.request(1)
      pub.sendNext(TextMessage.Strict(response))
      sub.expectNext(TextMessage.Strict(response))
    }

    "return ok if the account create route is hit" in {
      Get("/register") ~> Upgrade(List(UpgradeProtocol("websocket"))) ~> emulateHttpCore ~> route ~> check {
        status shouldEqual StatusCodes.SwitchingProtocols
      }
    }

    "return ok if the account OTP Verification route is hit" in {
      Get("/register/code") ~> Upgrade(List(UpgradeProtocol("websocket"))) ~> emulateHttpCore ~> route ~> check {
        status shouldEqual StatusCodes.SwitchingProtocols
      }
    }

    "return ok if the register account details route is hit" in {
      val apiService = new ApiService() {
        override val http = mock[HttpExt]

        override def responseHandler = userAccountDetailsWebSocketFlow

        override def getInfoFromToken(token: String): (String, String) = ("1234", "1234")
      }

      val route = apiService.routes
      Get("/register/details") ~> Upgrade(List(UpgradeProtocol("websocket"))) ~> emulateHttpCore ~> route ~> check {
        status shouldEqual StatusCodes.SwitchingProtocols
      }
    }

    "return Invalid JSON as response, if INVALID_JSON is sent from server" in {
      def receiveNotificationViaWebSocket: Flow[Message, Message, Any] = {
        Flow[Message]
          .collect {
            case TextMessage.Strict(msg) ⇒ INVALID_JSON
          }
          .map {
            case msg: String ⇒ TextMessage.Strict(INVALID_JSON)
          }
      }

      val apiService = new ApiService() {
        override val http = mock[HttpExt]

        override def userAccountDetailsWebSocketFlow = receiveNotificationViaWebSocket

        override def getInfoFromToken(token: String): (String, String) = ("1234", "1234")
      }

      val result = apiService.responseHandler
      val (pub, sub) = TestSource.probe[Message]
        .via(result)
        .toMat(TestSink.probe[Message])(Keep.both)
        .run()
      sub.request(1)
      pub.sendNext(TextMessage.Strict(INVALID_JSON))
      sub.expectNext(TextMessage.Strict(INVALID_JSON))
    }

    "return valid JSON as response, if token is sent from server and registered contact id list is empty" in {

      val response = """{"token":"token123","userContactsIds":[]}"""

      def receiveNotificationViaWebSocket: Flow[Message, Message, Any] = {
        Flow[Message]
          .collect {
            case TextMessage.Strict(msg) ⇒ response
          }
          .map {
            case msg: String ⇒ TextMessage.Strict(response)
          }
      }

      val apiService = new ApiService() {
        override val http = mock[HttpExt]

        override def userAccountDetailsWebSocketFlow = receiveNotificationViaWebSocket

        override def getInfoFromToken(token: String): (String, String) = ("1234", "1234")
      }

      val result = apiService.responseHandler
      val (pub, sub) = TestSource.probe[Message]
        .via(result)
        .toMat(TestSink.probe[Message])(Keep.both)
        .run()
      sub.request(1)
      pub.sendNext(TextMessage.Strict(response))
      sub.expectNext(TextMessage.Strict(response))
    }

    "return error message response, if user send invalid inputs" in {

      val response = "invalid name"

      def receiveNotificationViaWebSocket: Flow[Message, Message, Any] = {
        Flow[Message]
          .collect {
            case TextMessage.Strict(msg) ⇒ response
          }
          .map {
            case msg: String ⇒ TextMessage.Strict(response)
          }
      }

      val apiService = new ApiService() {
        override val http = mock[HttpExt]

        override def userAccountDetailsWebSocketFlow = receiveNotificationViaWebSocket

        override def getInfoFromToken(token: String): (String, String) = ("1234", "1234")
      }

      val result = apiService.responseHandler
      val (pub, sub) = TestSource.probe[Message]
        .via(result)
        .toMat(TestSink.probe[Message])(Keep.both)
        .run()
      sub.request(1)
      pub.sendNext(TextMessage.Strict(response))
      sub.expectNext(TextMessage.Strict(response))
    }

    "return valid JSON as response, if token is sent from server and registered contact id list is non-empty" in {

      val response = """{"token":"token123","userContactsIds":["123"]}"""

      def receiveNotificationViaWebSocket: Flow[Message, Message, Any] = {
        Flow[Message]
          .collect {
            case TextMessage.Strict(msg) ⇒ response
          }
          .map {
            case msg: String ⇒ TextMessage.Strict(response)
          }
      }

      val apiService = new ApiService() {
        override val http = mock[HttpExt]

        override def userAccountDetailsWebSocketFlow = receiveNotificationViaWebSocket

        override def getInfoFromToken(token: String): (String, String) = ("1234", "1234")
      }

      val result = apiService.responseHandler
      val (pub, sub) = TestSource.probe[Message]
        .via(result)
        .toMat(TestSink.probe[Message])(Keep.both)
        .run()
      sub.request(1)
      pub.sendNext(TextMessage.Strict(response))
      sub.expectNext(TextMessage.Strict(response))
    }

    "return ok if the follow/unfollow path is hit" in {
      val apiService = new ApiService() {
        override val http = mock[HttpExt]

        override def followUnfollowResponseHandler(url: String, userId: String)(userInfo: (String, String)): Flow[Message, Message, Any] = sendUserInfoInUrl(url)(userInfo)
      }
      val route = apiService.routes
      Get("/profile/12345") ~> Upgrade(List(UpgradeProtocol("websocket"))) ~> emulateHttpCore ~> route ~> check {
        status shouldEqual StatusCodes.SwitchingProtocols
      }
    }

    "send notification when a user is followed" in {

      val response = "User Followed"
      val url = "url"
      val userId = java.util.UUID.randomUUID().toString
      val followerId = java.util.UUID.randomUUID().toString
      def notificationId = java.util.UUID.randomUUID().toString
      val notificationForFollower = NotificationDetail(List("followerId1"), notificationId, "Follows", userId, followerId)
      val notificationForUser = NotificationDetail(List("userId2"), notificationId, "Follows", userId, followerId)
      val mockedApiServiceHandler = mock[ApiServiceHandler]
      when(mockedApiServiceHandler.handleNotificationOnUnfollowOrFollow(followerId, userId, "Follows")) thenReturn ((Future(notificationForFollower), Future(notificationForUser)))

      def receiveNotificationViaWebSocket: Flow[Message, Message, Any] = {
        Flow[Message]
          .collect {
            case TextMessage.Strict(msg) ⇒ response
          }
          .map {
            case msg: String ⇒ TextMessage.Strict(response)
          }
      }

      val apiService = new ApiService() {
        override val http = mock[HttpExt]

        override def sendUserInfoInUrl(url: String)(userInfo: (String, String)) = receiveNotificationViaWebSocket
      }

      val result = apiService.followUnfollowResponseHandler(url, userId)(followerId, "99723456789")
      val (pub, sub) = TestSource.probe[Message]
        .via(result)
        .toMat(TestSink.probe[Message])(Keep.both)
        .run()
      sub.request(1)
      pub.sendNext(TextMessage.Strict(response))
      sub.expectNext(TextMessage.Strict(response))
    }

    "send notification when a user is unfollowed" in {

      val response = "User Unfollowed"
      val url = "url"
      val userId = java.util.UUID.randomUUID().toString
      val followerId = java.util.UUID.randomUUID().toString
      def notificationId = java.util.UUID.randomUUID().toString
      val notificationForFollower = NotificationDetail(List("followerId1"), notificationId, "Unfollows", userId, followerId)
      val notificationForUser = NotificationDetail(List("userId2"), notificationId, "Unfollows", userId, followerId)
      val mockedApiServiceHandler = mock[ApiServiceHandler]
      when(mockedApiServiceHandler.handleNotificationOnUnfollowOrFollow(followerId, userId, "Unfollows")) thenReturn ((Future(notificationForFollower), Future(notificationForUser)))

      def receiveNotificationViaWebSocket: Flow[Message, Message, Any] = {
        Flow[Message]
          .collect {
            case TextMessage.Strict(msg) ⇒ response
          }
          .map {
            case msg: String ⇒ TextMessage.Strict(response)
          }
      }

      val apiService = new ApiService() {
        override val http = mock[HttpExt]

        override def sendUserInfoInUrl(url: String)(userInfo: (String, String)) = receiveNotificationViaWebSocket
      }

      val result = apiService.followUnfollowResponseHandler(url, userId)(followerId, "99723456789")
      val (pub, sub) = TestSource.probe[Message]
        .via(result)
        .toMat(TestSink.probe[Message])(Keep.both)
        .run()
      sub.request(1)
      pub.sendNext(TextMessage.Strict(response))
      sub.expectNext(TextMessage.Strict(response))
    }

    "send notification when a user become friend" in {

      val response = "User Followed_Friend"
      val url = "url"
      val userId = java.util.UUID.randomUUID().toString
      val followerId = java.util.UUID.randomUUID().toString
      def notificationId = java.util.UUID.randomUUID().toString
      val notificationForFollower = NotificationDetail(List("followerId1"), notificationId, "Follows", userId, followerId)
      val notificationForUser = NotificationDetail(List("userId2"), notificationId, "Follows", userId, followerId)
      val mockedApiServiceHandler = mock[ApiServiceHandler]
      when(mockedApiServiceHandler.handleNotificationOnUnfollowOrFollow(followerId, userId, "Follows")) thenReturn ((Future(notificationForFollower), Future(notificationForUser)))

      def receiveNotificationViaWebSocket: Flow[Message, Message, Any] = {
        Flow[Message]
          .collect {
            case TextMessage.Strict(msg) ⇒ response
          }
          .map {
            case msg: String ⇒ TextMessage.Strict(response)
          }
      }

      val apiService = new ApiService() {
        override val http = mock[HttpExt]

        override def sendUserInfoInUrl(url: String)(userInfo: (String, String)) = receiveNotificationViaWebSocket
      }

      val result = apiService.followUnfollowResponseHandler(url, userId)(followerId, "99723456789")
      val result1 = apiService.followUnfollowResponseHandler(url, followerId)(userId, "99723456767")
      val (pub, sub) = TestSource.probe[Message]
        .via(result1)
        .toMat(TestSink.probe[Message])(Keep.both)
        .run()
      sub.request(1)
      pub.sendNext(TextMessage.Strict(response))
      sub.expectNext(TextMessage.Strict(response))
    }

    "return ok if the respok path is hit" in {
      val apiService = new ApiService() {
        override val http = mock[HttpExt]

        override def respokResponseHandler(url: String)(userInfo: (String, String)): Flow[Message, Message, Any] = sendUserInfoInUrl(url)(userInfo)
      }
      val route = apiService.routes
      Get("/spok/12345/respok") ~> Upgrade(List(UpgradeProtocol("websocket"))) ~> emulateHttpCore ~> route ~> check {
        status shouldEqual StatusCodes.SwitchingProtocols
      }
    }

    "return ok if the unspok path is hit" in {

      val apiService = new ApiService() {
        override val http = mock[HttpExt]

        override def unspokResponseHandler(url: String)(userInfo: (String, String)): Flow[Message, Message, Any] = sendUserInfoInUrl(url)(userInfo)
      }
      val route = apiService.routes
      Get("/spok/12345/unspok") ~> Upgrade(List(UpgradeProtocol("websocket"))) ~> emulateHttpCore ~> route ~> check {
        status shouldEqual StatusCodes.SwitchingProtocols
      }
    }

    "send notification when a spok is unspoked" in {

      val url = "url"
      val userId = java.util.UUID.randomUUID().toString
      val absoluteSpokId = java.util.UUID.randomUUID().toString
      def notificationId = java.util.UUID.randomUUID().toString
      val spokInstanceId = java.util.UUID.randomUUID().toString
      val spokInstance = SpokInstance(absoluteSpokId, spokInstanceId, UNSPOKED, PUBLIC,
        Some("spok text"), Some("0"))
      val unspokResponse = RespokResponse(spokInstance, spokInstanceId, RespokStatistics(4, 5, 3, 2455.57), List())
      val txt = write(unspokResponse)
      val notificationForUser = NotificationDetail(List("userId2"), notificationId, UNSPOKED, absoluteSpokId, userId)
      val mockedApiServiceHandler = mock[ApiServiceHandler]
      when(mockedApiServiceHandler.handleCommentAndSpokOperations(txt, userId, absoluteSpokId, UNSPOKED)) thenReturn (Future(List(notificationForUser)))

      def receiveNotificationViaWebSocket: Flow[Message, Message, Any] = {
        Flow[Message]
          .collect {
            case TextMessage.Strict(msg) ⇒ msg
          }
          .map {
            case msg: String ⇒ TextMessage.Strict(txt)
          }
      }

      val apiService = new ApiService() {
        override val http = mock[HttpExt]

        override def sendUserInfoInUrl(url: String)(userInfo: (String, String)) = receiveNotificationViaWebSocket
      }

      val result = apiService.unspokResponseHandler(url)(userId, "99723456789")
      val (pub, sub) = TestSource.probe[Message]
        .via(result)
        .toMat(TestSink.probe[Message])(Keep.both)
        .run()
      sub.request(1)
      pub.sendNext(TextMessage.Strict(url))
      sub.expectNext(TextMessage.Strict(txt))
    }

    "return ok if the create group route is hit" in {

      Get("/groups") ~> Upgrade(List(UpgradeProtocol("websocket"))) ~> emulateHttpCore ~> route ~> check {
        status shouldEqual StatusCodes.SwitchingProtocols
      }
    }

    "return ok if the route to add user in group is hit" in {

      Get("/group/1234") ~> Upgrade(List(UpgradeProtocol("websocket"))) ~> emulateHttpCoreForGroup ~> route ~> check {
        status shouldEqual StatusCodes.SwitchingProtocols
      }
    }

    "return ok if the route to remove user in group is hit" in {

      Get("/group/1234") ~> Upgrade(List(UpgradeProtocol("websocket"))) ~> emulateHttpCoreForGroupREMOVE_USER ~> route ~> check {
        status shouldEqual StatusCodes.SwitchingProtocols
      }
    }

    "return ok if the route to remove group is hit" in {

      Get("/group/1234") ~> Upgrade(List(UpgradeProtocol("websocket"))) ~> emulateHttpCoreForGroupREMOVE_GROUP ~> route ~> check {
        status shouldEqual StatusCodes.SwitchingProtocols
      }
    }

    "return ok if the route to update in group is hit" in {

      Get("/group/1234") ~> Upgrade(List(UpgradeProtocol("websocket"))) ~> emulateHttpCoreForGroupUPDATE_GROUP ~> route ~> check {
        status shouldEqual StatusCodes.SwitchingProtocols
      }
    }

    "return Unauthorized if the Spok Instance Stats path is hit with invalid Token" in {

      val rawHeader = RawHeader("Authorization", "Bearer eyJhbGciOi9IUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiI1YWQyNWFiOC1lNDRmLTQ1OTAtOGU4Mi04YmYwYzk3NDk5MWUiLCJwaG9uZV9udW1iZXIiOiIrMzM2NjA3NjAzNzYiLCJyb2xlIjoidXNlciIsImlhdCI6MTQ2ODU3MjE2MDIxNn0._lsHenZoIzKoZHtKGPoNl8nasuY0FksXkNJvUBJIRYo")
      Get("/spok/1234/stats").withHeaders(rawHeader) ~> route ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    "return ok if the Spok Instance Stats path is hit " in {

      val req: HttpRequest = HttpRequest(uri = HTTP + interface + ":" + spokPort + DELIMITER + SPOK + DELIMITER + "1234" + DELIMITER + STATS +
        "?userId=" + "5ad25ab8-e44f-4590-8e82-8bf0c974991e" + "&phone_number=" + "+33660760376")
      when(apiService.http.singleRequest(req)) thenReturn (Future(HttpResponse(StatusCodes.OK)))
      val rawHeader = RawHeader("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiI1YWQyNWFiOC1lNDRmLTQ1OTAtOGU4Mi04YmYwYzk3NDk5MWUiLCJwaG9uZV9udW1iZXIiOiIrMzM2NjA3NjAzNzYiLCJyb2xlIjoidXNlciIsImlhdCI6MTQ2ODU3MjE2MDIxNn0._lsHenZoIzKoZHtKGPoNl8nasuY0FksXkNJvUBJIRYo")
      Get("/spok/1234/stats").withHeaders(rawHeader) ~> route ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    "return Unauthorized if the Absolute Spok Stats path is hit with invalid Token" in {

      val rawHeader = RawHeader("Authorization", "Bearer eyJhbGciOi9IUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiI1YWQyNWFiOC1lNDRmLTQ1OTAtOGU4Mi04YmYwYzk3NDk5MWUiLCJwaG9uZV9udW1iZXIiOiIrMzM2NjA3NjAzNzYiLCJyb2xlIjoidXNlciIsImlhdCI6MTQ2ODU3MjE2MDIxNn0._lsHenZoIzKoZHtKGPoNl8nasuY0FksXkNJvUBJIRYo")
      Get("/spok/1234/abstats").withHeaders(rawHeader) ~> route ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    "return ok if the Absolute Spok Stats path is hit " in {
      val req: HttpRequest = HttpRequest(uri = HTTP + interface + ":" + spokPort + DELIMITER + SPOK + DELIMITER + "1234" + DELIMITER + ABSTATS +
        "?userId=" + "5ad25ab8-e44f-4590-8e82-8bf0c974991e" + "&phone_number=" + "+33660760376")
      when(apiService.http.singleRequest(req)) thenReturn (Future(HttpResponse(StatusCodes.OK)))
      val rawHeader = RawHeader("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiI1YWQyNWFiOC1lNDRmLTQ1OTAtOGU4Mi04YmYwYzk3NDk5MWUiLCJwaG9uZV9udW1iZXIiOiIrMzM2NjA3NjAzNzYiLCJyb2xlIjoidXNlciIsImlhdCI6MTQ2ODU3MjE2MDIxNn0._lsHenZoIzKoZHtKGPoNl8nasuY0FksXkNJvUBJIRYo")
      Get("/spok/1234/abstats").withHeaders(rawHeader) ~> route ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    "return ok if the Spok Stack path is hit " in {
      val req: HttpRequest = HttpRequest(uri = HTTP + interface + ":" + spokPort + DELIMITER + SPOKS + DELIMITER + "1234" +
        "?userId=" + "5ad25ab8-e44f-4590-8e82-8bf0c974991e" + "&phone_number=" + "+33660760376")
      when(apiService.http.singleRequest(req)) thenReturn (Future(HttpResponse(StatusCodes.OK)))
      val rawHeader = RawHeader("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiI1YWQyNWFiOC1lNDRmLTQ1OTAtOGU4Mi04YmYwYzk3NDk5MWUiLCJwaG9uZV9udW1iZXIiOiIrMzM2NjA3NjAzNzYiLCJyb2xlIjoidXNlciIsImlhdCI6MTQ2ODU3MjE2MDIxNn0._lsHenZoIzKoZHtKGPoNl8nasuY0FksXkNJvUBJIRYo")
      Get("/spoks/1234").withHeaders(rawHeader) ~> route ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    "return Unauthorized if the Spok Stack path is hit with invalid Token" in {

      val rawHeader = RawHeader("Authorization", "Bearer eyJhbGciOi9IUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiI1YWQyNWFiOC1lNDRmLTQ1OTAtOGU4Mi04YmYwYzk3NDk5MWUiLCJwaG9uZV9udW1iZXIiOiIrMzM2NjA3NjAzNzYiLCJyb2xlIjoidXNlciIsImlhdCI6MTQ2ODU3MjE2MDIxNn0._lsHenZoIzKoZHtKGPoNl8nasuY0FksXkNJvUBJIRYo")
      Get("/spoks/1234").withHeaders(rawHeader) ~> route ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    "return ok if get scoped users of a spok path is hit " in {
      val req: HttpRequest = HttpRequest(uri = HTTP + interface + ":" + spokPort + DELIMITER + SPOK + DELIMITER + "spokId" + DELIMITER + SCOPED + DELIMITER + "pos" +
        "?userId=" + "5ad25ab8-e44f-4590-8e82-8bf0c974991e" + "&phone_number=" + "+33660760376")
      when(apiService.http.singleRequest(req)) thenReturn (Future(HttpResponse(StatusCodes.OK)))
      val rawHeader = RawHeader("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiI1YWQyNWFiOC1lNDRmLTQ1OTAtOGU4Mi04YmYwYzk3NDk5MWUiLCJwaG9uZV9udW1iZXIiOiIrMzM2NjA3NjAzNzYiLCJyb2xlIjoidXNlciIsImlhdCI6MTQ2ODU3MjE2MDIxNn0._lsHenZoIzKoZHtKGPoNl8nasuY0FksXkNJvUBJIRYo")
      Get("/spok/spokId/scoped/pos").withHeaders(rawHeader) ~> route ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    "return Unauthorized if get scoped users of a spok path is hit with invalid Token" in {

      val rawHeader = RawHeader("Authorization", "Bearer eyJhbGciOi9IUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiI1YWQyNWFiOC1lNDRmLTQ1OTAtOGU4Mi04YmYwYzk3NDk5MWUiLCJwaG9uZV9udW1iZXIiOiIrMzM2NjA3NjAzNzYiLCJyb2xlIjoidXNlciIsImlhdCI6MTQ2ODU3MjE2MDIxNn0._lsHenZoIzKoZHtKGPoNl8nasuY0FksXkNJvUBJIRYo")
      Get("/spok/spokId/scoped/pos").withHeaders(rawHeader) ~> route ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    "return ok if get respokers of a spok path is hit " in {
      val req: HttpRequest = HttpRequest(uri = HTTP + interface + ":" + spokPort + DELIMITER + SPOK + DELIMITER + "spokId" + DELIMITER + RESPOKERS + DELIMITER + "pos" +
        "?userId=" + "5ad25ab8-e44f-4590-8e82-8bf0c974991e" + "&phone_number=" + "+33660760376")
      when(apiService.http.singleRequest(req)) thenReturn (Future(HttpResponse(StatusCodes.OK)))
      val rawHeader = RawHeader("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiI1YWQyNWFiOC1lNDRmLTQ1OTAtOGU4Mi04YmYwYzk3NDk5MWUiLCJwaG9uZV9udW1iZXIiOiIrMzM2NjA3NjAzNzYiLCJyb2xlIjoidXNlciIsImlhdCI6MTQ2ODU3MjE2MDIxNn0._lsHenZoIzKoZHtKGPoNl8nasuY0FksXkNJvUBJIRYo")
      Get("/spok/spokId/respokers/pos").withHeaders(rawHeader) ~> route ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    "return Unauthorized if get respokers of a spok path is hit with invalid Token" in {

      val rawHeader = RawHeader("Authorization", "Bearer eyJhbGciOi9IUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiI1YWQyNWFiOC1lNDRmLTQ1OTAtOGU4Mi04YmYwYzk3NDk5MWUiLCJwaG9uZV9udW1iZXIiOiIrMzM2NjA3NjAzNzYiLCJyb2xlIjoidXNlciIsImlhdCI6MTQ2ODU3MjE2MDIxNn0._lsHenZoIzKoZHtKGPoNl8nasuY0FksXkNJvUBJIRYo")
      Get("/spok/spokId/respokers/pos").withHeaders(rawHeader) ~> route ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    "return ok if view short spok of a spok path is hit " in {
      val req: HttpRequest = HttpRequest(uri = HTTP + interface + ":" + spokPort + DELIMITER + STACK + DELIMITER + "spokId" +
        "?userId=" + "5ad25ab8-e44f-4590-8e82-8bf0c974991e" + "&phone_number=" + "+33660760376")
      when(apiService.http.singleRequest(req)) thenReturn (Future(HttpResponse(StatusCodes.OK)))
      val rawHeader = RawHeader("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiI1YWQyNWFiOC1lNDRmLTQ1OTAtOGU4Mi04YmYwYzk3NDk5MWUiLCJwaG9uZV9udW1iZXIiOiIrMzM2NjA3NjAzNzYiLCJyb2xlIjoidXNlciIsImlhdCI6MTQ2ODU3MjE2MDIxNn0._lsHenZoIzKoZHtKGPoNl8nasuY0FksXkNJvUBJIRYo")
      Get("/stack/spokId").withHeaders(rawHeader) ~> route ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    "return Unauthorized if view short spok of a spok path is hit with invalid Token" in {

      val rawHeader = RawHeader("Authorization", "Bearer eyJhbGciOi9IUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiI1YWQyNWFiOC1lNDRmLTQ1OTAtOGU4Mi04YmYwYzk3NDk5MWUiLCJwaG9uZV9udW1iZXIiOiIrMzM2NjA3NjAzNzYiLCJyb2xlIjoidXNlciIsImlhdCI6MTQ2ODU3MjE2MDIxNn0._lsHenZoIzKoZHtKGPoNl8nasuY0FksXkNJvUBJIRYo")
      Get("/stack/spokId").withHeaders(rawHeader) ~> route ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    "return ok if view full spok of a spok path is hit " in {
      val req: HttpRequest = HttpRequest(uri = HTTP + interface + ":" + spokPort + DELIMITER + SPOK + DELIMITER + "spokId" + DELIMITER + FULL +
        "?userId=" + "5ad25ab8-e44f-4590-8e82-8bf0c974991e" + "&phone_number=" + "+33660760376")
      when(apiService.http.singleRequest(req)) thenReturn (Future(HttpResponse(StatusCodes.OK)))
      val rawHeader = RawHeader("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiI1YWQyNWFiOC1lNDRmLTQ1OTAtOGU4Mi04YmYwYzk3NDk5MWUiLCJwaG9uZV9udW1iZXIiOiIrMzM2NjA3NjAzNzYiLCJyb2xlIjoidXNlciIsImlhdCI6MTQ2ODU3MjE2MDIxNn0._lsHenZoIzKoZHtKGPoNl8nasuY0FksXkNJvUBJIRYo")
      Get("/spok/spokId/full").withHeaders(rawHeader) ~> route ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    "return Unauthorized if view full spok of a spok path is hit with invalid Token" in {

      val rawHeader = RawHeader("Authorization", "Bearer eyJhbGciOi9IUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiI1YWQyNWFiOC1lNDRmLTQ1OTAtOGU4Mi04YmYwYzk3NDk5MWUiLCJwaG9uZV9udW1iZXIiOiIrMzM2NjA3NjAzNzYiLCJyb2xlIjoidXNlciIsImlhdCI6MTQ2ODU3MjE2MDIxNn0._lsHenZoIzKoZHtKGPoNl8nasuY0FksXkNJvUBJIRYo")
      Get("/spok/spokId/full").withHeaders(rawHeader) ~> route ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    "return ok if url to view full user profile is hit " in {
      val req: HttpRequest = HttpRequest(uri = HTTP + interface + ":" + accountPort + DELIMITER + USER_ROLE + DELIMITER + "userId" +
        "?userId=" + "5ad25ab8-e44f-4590-8e82-8bf0c974991e" + "&phone_number=" + "+33660760376")
      when(apiService.http.singleRequest(req)) thenReturn (Future(HttpResponse(StatusCodes.OK)))
      val rawHeader = RawHeader("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiI1YWQyNWFiOC1lNDRmLTQ1OTAtOGU4Mi04YmYwYzk3NDk5MWUiLCJwaG9uZV9udW1iZXIiOiIrMzM2NjA3NjAzNzYiLCJyb2xlIjoidXNlciIsImlhdCI6MTQ2ODU3MjE2MDIxNn0._lsHenZoIzKoZHtKGPoNl8nasuY0FksXkNJvUBJIRYo")
      Get("/user/userId").withHeaders(rawHeader) ~> route ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    "return Unauthorized if url to view full user profile is hit with invalid Token" in {

      val rawHeader = RawHeader("Authorization", "Bearer eyJhbGciOi9IUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiI1YWQyNWFiOC1lNDRmLTQ1OTAtOGU4Mi04YmYwYzk3NDk5MWUiLCJwaG9uZV9udW1iZXIiOiIrMzM2NjA3NjAzNzYiLCJyb2xlIjoidXNlciIsImlhdCI6MTQ2ODU3MjE2MDIxNn0._lsHenZoIzKoZHtKGPoNl8nasuY0FksXkNJvUBJIRYo")
      Get("/user/userId").withHeaders(rawHeader) ~> route ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    "return ok if url to view minimal user profile is hit " in {
      val req: HttpRequest = HttpRequest(uri = HTTP + interface + ":" + accountPort + DELIMITER + USER_ROLE + DELIMITER + "userId" + DELIMITER + "minimal" +
        "?userId=" + "5ad25ab8-e44f-4590-8e82-8bf0c974991e" + "&phone_number=" + "+33660760376")
      when(apiService.http.singleRequest(req)) thenReturn (Future(HttpResponse(StatusCodes.OK)))
      val rawHeader = RawHeader("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiI1YWQyNWFiOC1lNDRmLTQ1OTAtOGU4Mi04YmYwYzk3NDk5MWUiLCJwaG9uZV9udW1iZXIiOiIrMzM2NjA3NjAzNzYiLCJyb2xlIjoidXNlciIsImlhdCI6MTQ2ODU3MjE2MDIxNn0._lsHenZoIzKoZHtKGPoNl8nasuY0FksXkNJvUBJIRYo")
      Get("/user/userId/minimal").withHeaders(rawHeader) ~> route ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    "return Unauthorized if url to view minimal user profile is hit with invalid Token" in {

      val rawHeader = RawHeader("Authorization", "Bearer eyJhbGciOi9IUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiI1YWQyNWFiOC1lNDRmLTQ1OTAtOGU4Mi04YmYwYzk3NDk5MWUiLCJwaG9uZV9udW1iZXIiOiIrMzM2NjA3NjAzNzYiLCJyb2xlIjoidXNlciIsImlhdCI6MTQ2ODU3MjE2MDIxNn0._lsHenZoIzKoZHtKGPoNl8nasuY0FksXkNJvUBJIRYo")
      Get("/user/userId/minimal").withHeaders(rawHeader) ~> route ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    "return ok if url to remove user from cache is hit " in {
      val req: HttpRequest = HttpRequest(uri = HTTP + interface + ":" + accountPort + DELIMITER + "cache" + DELIMITER + "userId" +
        "?userId=" + "5ad25ab8-e44f-4590-8e82-8bf0c974991e" + "&phone_number=" + "+33660760376")
      when(apiService.http.singleRequest(req)) thenReturn (Future(HttpResponse(StatusCodes.OK)))
      val rawHeader = RawHeader("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiI1YWQyNWFiOC1lNDRmLTQ1OTAtOGU4Mi04YmYwYzk3NDk5MWUiLCJwaG9uZV9udW1iZXIiOiIrMzM2NjA3NjAzNzYiLCJyb2xlIjoidXNlciIsImlhdCI6MTQ2ODU3MjE2MDIxNn0._lsHenZoIzKoZHtKGPoNl8nasuY0FksXkNJvUBJIRYo")
      Get("/cache/userId").withHeaders(rawHeader) ~> route ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    "return Unauthorized if url to remove user from cache is hit with invalid Token" in {

      val rawHeader = RawHeader("Authorization", "Bearer eyJhbGciOi9IUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiI1YWQyNWFiOC1lNDRmLTQ1OTAtOGU4Mi04YmYwYzk3NDk5MWUiLCJwaG9uZV9udW1iZXIiOiIrMzM2NjA3NjAzNzYiLCJyb2xlIjoidXNlciIsImlhdCI6MTQ2ODU3MjE2MDIxNn0._lsHenZoIzKoZHtKGPoNl8nasuY0FksXkNJvUBJIRYo")
      Get("/cache/userId").withHeaders(rawHeader) ~> route ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    "return ok if the add comment path is hit" in {

      val apiService = new ApiService() {
        override val http = mock[HttpExt]

        override def addCommentResponseHandler(url: String)(userInfo: (String, String)): Flow[Message, Message, Any] = sendUserInfoInUrl(url)(userInfo)
      }
      val route = apiService.routes
      Get("/spok/12345/comment") ~> Upgrade(List(UpgradeProtocol("websocket"))) ~> emulateHttpCore ~> route ~> check {
        status shouldEqual StatusCodes.SwitchingProtocols
      }
    }

    "return ok if url to list of followers of an user is hit " in {
      val req: HttpRequest = HttpRequest(uri = HTTP + interface + ":" + accountPort + DELIMITER + PROFILE + DELIMITER + "userId" + DELIMITER + FOLLOWERS + DELIMITER + "pos" +
        "?userId=" + "5ad25ab8-e44f-4590-8e82-8bf0c974991e" + "&phone_number=" + "+33660760376")
      when(apiService.http.singleRequest(req)) thenReturn (Future(HttpResponse(StatusCodes.OK)))
      val rawHeader = RawHeader("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiI1YWQyNWFiOC1lNDRmLTQ1OTAtOGU4Mi04YmYwYzk3NDk5MWUiLCJwaG9uZV9udW1iZXIiOiIrMzM2NjA3NjAzNzYiLCJyb2xlIjoidXNlciIsImlhdCI6MTQ2ODU3MjE2MDIxNn0._lsHenZoIzKoZHtKGPoNl8nasuY0FksXkNJvUBJIRYo")
      Get("/profile/userId/followers/pos").withHeaders(rawHeader) ~> route ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    "return Unauthorized if url to list of followers of an user is hit with invalid Token" in {

      val rawHeader = RawHeader("Authorization", "Bearer eyJhbGciOi9IUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiI1YWQyNWFiOC1lNDRmLTQ1OTAtOGU4Mi04YmYwYzk3NDk5MWUiLCJwaG9uZV9udW1iZXIiOiIrMzM2NjA3NjAzNzYiLCJyb2xlIjoidXNlciIsImlhdCI6MTQ2ODU3MjE2MDIxNn0._lsHenZoIzKoZHtKGPoNl8nasuY0FksXkNJvUBJIRYo")
      Get("/profile/userId/followers/pos").withHeaders(rawHeader) ~> route ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    "return ok if url to list of followings of an user is hit " in {
      val req: HttpRequest = HttpRequest(uri = HTTP + interface + ":" + accountPort + DELIMITER + PROFILE + DELIMITER + "userId" + DELIMITER + FOLLOWINGS + DELIMITER + "pos" +
        "?userId=" + "5ad25ab8-e44f-4590-8e82-8bf0c974991e" + "&phone_number=" + "+33660760376")
      when(apiService.http.singleRequest(req)) thenReturn (Future(HttpResponse(StatusCodes.OK)))
      val rawHeader = RawHeader("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiI1YWQyNWFiOC1lNDRmLTQ1OTAtOGU4Mi04YmYwYzk3NDk5MWUiLCJwaG9uZV9udW1iZXIiOiIrMzM2NjA3NjAzNzYiLCJyb2xlIjoidXNlciIsImlhdCI6MTQ2ODU3MjE2MDIxNn0._lsHenZoIzKoZHtKGPoNl8nasuY0FksXkNJvUBJIRYo")
      Get("/profile/userId/followings/pos").withHeaders(rawHeader) ~> route ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    "return Unauthorized if url to list of followings of an user is hit with invalid Token" in {

      val rawHeader = RawHeader("Authorization", "Bearer eyJhbGciOi9IUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiI1YWQyNWFiOC1lNDRmLTQ1OTAtOGU4Mi04YmYwYzk3NDk5MWUiLCJwaG9uZV9udW1iZXIiOiIrMzM2NjA3NjAzNzYiLCJyb2xlIjoidXNlciIsImlhdCI6MTQ2ODU3MjE2MDIxNn0._lsHenZoIzKoZHtKGPoNl8nasuY0FksXkNJvUBJIRYo")
      Get("/profile/userId/followings/pos").withHeaders(rawHeader) ~> route ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    "send notification when a comment is added" in {

      val url = "url"
      val userId = java.util.UUID.randomUUID().toString
      val absoluteSpokId = java.util.UUID.randomUUID().toString
      def notificationId = java.util.UUID.randomUUID().toString
      val commentId = java.util.UUID.randomUUID().toString
      val spokInstanceId = java.util.UUID.randomUUID().toString
      val commentInternalSpokResponse = CommentInternalSpokResponse(spokInstanceId, "1", "1", "4", "256545", Some(absoluteSpokId))
      val commenterUserResponse = CommenterUserResponse(userId, "john", "male", "picture")
      val addCommentResponse = SpokCommentResponse(commentInternalSpokResponse, commenterUserResponse, Some(Nil), Some(commentId))
      val txt = write(addCommentResponse.copy(mentionUserId = None, commentId = None))
      val notificationForUser = NotificationDetail(List("userId2"), notificationId, COMMENT_ADDED, absoluteSpokId, userId)
      val mockedApiServiceHandler = mock[ApiServiceHandler]
      when(mockedApiServiceHandler.handleCommentAndSpokOperations(txt, userId, absoluteSpokId, "Comment Added")) thenReturn (Future(List(notificationForUser)))

      def receiveNotificationViaWebSocket: Flow[Message, Message, Any] = {
        Flow[Message]
          .collect {
            case TextMessage.Strict(msg) ⇒ msg
          }
          .map {
            case msg: String ⇒ TextMessage.Strict(txt)
          }
      }

      val apiService = new ApiService() {
        override val http = mock[HttpExt]

        override def sendUserInfoInUrl(url: String)(userInfo: (String, String)) = receiveNotificationViaWebSocket
      }

      val result = apiService.addCommentResponseHandler(url)(userId, "99723456789")
      val (pub, sub) = TestSource.probe[Message]
        .via(result)
        .toMat(TestSink.probe[Message])(Keep.both)
        .run()
      sub.request(1)
      pub.sendNext(TextMessage.Strict(url))
      sub.expectNext(TextMessage.Strict(txt))
    }

    "not send notification when a comment is not added and an error is received" in {

      val url = "url"
      val userId = java.util.UUID.randomUUID().toString

      def receiveNotificationViaWebSocket: Flow[Message, Message, Any] = {
        Flow[Message]
          .collect {
            case TextMessage.Strict(msg) ⇒ msg
          }
          .map {
            case msg: String ⇒ JSONERROR
          }
      }

      val apiService = new ApiService() {
        override val http = mock[HttpExt]

        override def sendUserInfoInUrl(url: String)(userInfo: (String, String)) = receiveNotificationViaWebSocket
      }

      val result = apiService.addCommentResponseHandler(url)(userId, "99723456789")
      val (pub, sub) = TestSource.probe[Message]
        .via(result)
        .toMat(TestSink.probe[Message])(Keep.both)
        .run()
      sub.request(1)
      pub.sendNext(TextMessage.Strict(url))
      sub.expectNext(JSONERROR)
    }

    "return ok if the update comment path is hit" in {

      val apiService = new ApiService() {
        override val http = mock[HttpExt]

        override def updateCommentResponseHandler(url: String)(userInfo: (String, String)): Flow[Message, Message, Any] = sendUserInfoInUrl(url)(userInfo)
      }
      val route = apiService.routes
      Get("/comment/12345") ~> Upgrade(List(UpgradeProtocol("websocket"))) ~> emulateHttpCoreForCommentUpdate ~> route ~> check {
        status shouldEqual StatusCodes.SwitchingProtocols
      }
    }

    "send notification when a comment is updated" in {

      val url = "url"
      val userId = java.util.UUID.randomUUID().toString
      val absoluteSpokId = java.util.UUID.randomUUID().toString
      val commentId = java.util.UUID.randomUUID().toString
      def notificationId = java.util.UUID.randomUUID().toString
      val spokInstanceId = java.util.UUID.randomUUID().toString
      val updateCommentResponse = CommentUpdateResponse(spokInstanceId, "1", "1", "4", "256545", Some(absoluteSpokId), Some(List("userId4")), Some(commentId))
      val txt = write(updateCommentResponse.copy(absoluteSpokId = None, mentionUserId = None, commentId = None))
      val notificationForUser = NotificationDetail(List("userId2"), notificationId, COMMENT_UPDATED, absoluteSpokId, userId)
      val mockedApiServiceHandler = mock[ApiServiceHandler]
      when(mockedApiServiceHandler.handleCommentAndSpokOperations(txt, userId, absoluteSpokId, COMMENT_UPDATED)) thenReturn
        (Future(List(notificationForUser)))

      def receiveNotificationViaWebSocket: Flow[Message, Message, Any] = {
        Flow[Message]
          .collect {
            case TextMessage.Strict(msg) ⇒ msg
          }
          .map {
            case msg: String ⇒ TextMessage.Strict(write(updateCommentResponse))
          }
      }

      val apiService = new ApiService() {
        override val http = mock[HttpExt]

        override def sendUserInfoInUrl(url: String)(userInfo: (String, String)) = receiveNotificationViaWebSocket
      }

      val result = apiService.updateCommentResponseHandler(url)(userId, "99723456789")
      val (pub, sub) = TestSource.probe[Message]
        .via(result)
        .toMat(TestSink.probe[Message])(Keep.both)
        .run()
      sub.request(1)
      pub.sendNext(TextMessage.Strict(url))
      sub.expectNext(TextMessage.Strict(txt))
    }

    "not send notification when a comment is not updated and an error is received" in {

      val url = "url"
      val userId = java.util.UUID.randomUUID().toString

      def receiveNotificationViaWebSocket: Flow[Message, Message, Any] = {
        Flow[Message]
          .collect {
            case TextMessage.Strict(msg) ⇒ msg
          }
          .map {
            case msg: String ⇒ JSONERROR
          }
      }

      val apiService = new ApiService() {
        override val http = mock[HttpExt]

        override def sendUserInfoInUrl(url: String)(userInfo: (String, String)) = receiveNotificationViaWebSocket
      }

      val result = apiService.updateCommentResponseHandler(url)(userId, "99723456789")
      val (pub, sub) = TestSource.probe[Message]
        .via(result)
        .toMat(TestSink.probe[Message])(Keep.both)
        .run()
      sub.request(1)
      pub.sendNext(TextMessage.Strict(url))
      sub.expectNext(JSONERROR)
    }

    "return ok if the remove comment path is hit" in {

      val apiService = new ApiService() {
        override val http = mock[HttpExt]

        override def removeCommentResponseHandler(url: String)(userInfo: (String, String)): Flow[Message, Message, Any] = sendUserInfoInUrl(url)(userInfo)
      }
      val route = apiService.routes
      Get("/comment/12345") ~> Upgrade(List(UpgradeProtocol("websocket"))) ~> emulateHttpCoreForCommentRemove ~> route ~> check {
        status shouldEqual StatusCodes.SwitchingProtocols
      }
    }

    "send notification when a comment is removed" in {

      val url = "url"
      val userId = java.util.UUID.randomUUID().toString
      val absoluteSpokId = java.util.UUID.randomUUID().toString
      def notificationId = java.util.UUID.randomUUID().toString
      val commentId = java.util.UUID.randomUUID().toString
      val spokInstanceId = java.util.UUID.randomUUID().toString
      val commentInternalSpokResponse = CommentInternalSpokResponse(spokInstanceId, "1", "1", "4", "256545", Some(absoluteSpokId))
      val removeCommentResponse = RemoveCommentResponse(commentId, commentInternalSpokResponse)
      val txt = write(removeCommentResponse)
      val notificationForUser = NotificationDetail(List("userId2"), notificationId, "Unfollows", absoluteSpokId, userId)
      val mockedApiServiceHandler = mock[ApiServiceHandler]
      when(mockedApiServiceHandler.handleCommentAndSpokOperations(txt, userId, absoluteSpokId, REMOVE_COMMENT)) thenReturn (Future(List(notificationForUser)))
      val notificationResponse = s"""This comment is removed by: {\"emitter\":{\"id\": \"$userId\",\"nickName\": \"cyril hamedechi\",\"gender\": \"male\",\"picture\": \"updatepicture\"}}"""

      def receiveNotificationViaWebSocket: Flow[Message, Message, Any] = {
        Flow[Message]
          .collect {
            case TextMessage.Strict(msg) ⇒ msg
          }
          .map {
            case msg: String ⇒ TextMessage.Strict(txt)
          }
      }

      val apiService = new ApiService() {
        override val http = mock[HttpExt]

        override def sendUserInfoInUrl(url: String)(userInfo: (String, String)) = receiveNotificationViaWebSocket
      }

      val result = apiService.removeCommentResponseHandler(url)(userId, "99723456789")
      val (pub, sub) = TestSource.probe[Message]
        .via(result)
        .toMat(TestSink.probe[Message])(Keep.both)
        .run()
      sub.request(1)
      pub.sendNext(TextMessage.Strict(url))
      sub.expectNext(TextMessage.Strict(txt))
    }

    "not send notification when a comment is not removed and an error is received" in {

      val url = "url"
      val userId = java.util.UUID.randomUUID().toString

      def receiveNotificationViaWebSocket: Flow[Message, Message, Any] = {
        Flow[Message]
          .collect {
            case TextMessage.Strict(msg) ⇒ msg
          }
          .map {
            case msg: String ⇒ JSONERROR
          }
      }

      val apiService = new ApiService() {
        override val http = mock[HttpExt]

        override def sendUserInfoInUrl(url: String)(userInfo: (String, String)) = receiveNotificationViaWebSocket
      }

      val result = apiService.removeCommentResponseHandler(url)(userId, "99723456789")
      val (pub, sub) = TestSource.probe[Message]
        .via(result)
        .toMat(TestSink.probe[Message])(Keep.both)
        .run()
      sub.request(1)
      pub.sendNext(TextMessage.Strict(url))
      sub.expectNext(JSONERROR)
    }

    "return ok if get comments of a spok path is hit " in {
      val req: HttpRequest = HttpRequest(uri = HTTP + interface + ":" + spokPort + DELIMITER + SPOK + DELIMITER + "spokId" + DELIMITER + COMMENTS + DELIMITER + "pos" +
        "?userId=" + "5ad25ab8-e44f-4590-8e82-8bf0c974991e" + "&phone_number=" + "+33660760376")
      when(apiService.http.singleRequest(req)) thenReturn (Future(HttpResponse(StatusCodes.OK)))
      val rawHeader = RawHeader("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiI1YWQyNWFiOC1lNDRmLTQ1OTAtOGU4Mi04YmYwYzk3NDk5MWUiLCJwaG9uZV9udW1iZXIiOiIrMzM2NjA3NjAzNzYiLCJyb2xlIjoidXNlciIsImlhdCI6MTQ2ODU3MjE2MDIxNn0._lsHenZoIzKoZHtKGPoNl8nasuY0FksXkNJvUBJIRYo")
      Get("/spok/spokId/comments/pos").withHeaders(rawHeader) ~> route ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    "return Unauthorized if get comments of a spok path is hit with invalid Token" in {

      val rawHeader = RawHeader("Authorization", "Bearer eyJhbGciOi9IUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiI1YWQyNWFiOC1lNDRmLTQ1OTAtOGU4Mi04YmYwYzk3NDk5MWUiLCJwaG9uZV9udW1iZXIiOiIrMzM2NjA3NjAzNzYiLCJyb2xlIjoidXNlciIsImlhdCI6MTQ2ODU3MjE2MDIxNn0._lsHenZoIzKoZHtKGPoNl8nasuY0FksXkNJvUBJIRYo")
      Get("/spok/spokId/comments/pos").withHeaders(rawHeader) ~> route ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    "return ok if the update user profile path is hit" in {

      val apiService = new ApiService() {
        override val http = mock[HttpExt]

        override def updateProfileHandler(url: String)(userInfo: (String, String)): Flow[Message, Message, Any] = sendUserInfoInUrl(url)(userInfo)
      }
      val route = apiService.routes
      Get("/my/profile") ~> Upgrade(List(UpgradeProtocol("websocket"))) ~> emulateHttpCore ~> route ~> check {
        status shouldEqual StatusCodes.SwitchingProtocols
      }
    }

    "send notification when a user profile is updated" in {

      val url = "url"
      val userId = java.util.UUID.randomUUID().toString
      def notificationId = java.util.UUID.randomUUID().toString
      val notificationForUser = NotificationDetail(List("userId2"), notificationId, USER_PROFILE_UPDATE_SUCCESS, userId, userId)
      val mockedApiServiceHandler = mock[ApiServiceHandler]
      when(mockedApiServiceHandler.handleUpdateUserProfileNotification(userId)) thenReturn (Future(notificationForUser))

      def receiveNotificationViaWebSocket: Flow[Message, Message, Any] = {
        Flow[Message]
          .collect {
            case TextMessage.Strict(msg) ⇒ msg
          }
          .map {
            case msg: String ⇒ TextMessage.Strict(USER_PROFILE_UPDATE_SUCCESS)
          }
      }

      val apiService = new ApiService() {
        override val http = mock[HttpExt]

        override def sendUserInfoInUrl(url: String)(userInfo: (String, String)) = receiveNotificationViaWebSocket
      }

      val result = apiService.updateProfileHandler(url)(userId, "99723456789")
      val (pub, sub) = TestSource.probe[Message]
        .via(result)
        .toMat(TestSink.probe[Message])(Keep.both)
        .run()
      sub.request(1)
      pub.sendNext(TextMessage.Strict(url))
      sub.expectNext(TextMessage.Strict(USER_PROFILE_UPDATE_SUCCESS))
    }

    "return ok when the remove notification route is hit" in {

      Get("/my/notifications/12345") ~> Upgrade(List(UpgradeProtocol("websocket"))) ~> emulateHttpCore ~> route ~> check {
        status shouldEqual StatusCodes.SwitchingProtocols
      }
    }

    "return ok if the spokSubscribeUnsubscribe path is hit" in {
      Get("/spok/12345/subscribe") ~> Upgrade(List(UpgradeProtocol("websocket"))) ~> emulateHttpCore ~> route ~> check {
        status shouldEqual StatusCodes.SwitchingProtocols
      }
    }
    "return ok if url to view spoker's wall is hit " in {
      val req: HttpRequest = HttpRequest(uri = HTTP + interface + ":" + spokPort + DELIMITER + USER_ROLE + DELIMITER + "userId" + DELIMITER + WALL + DELIMITER + "pos" +
        "?userId=" + "5ad25ab8-e44f-4590-8e82-8bf0c974991e" + "&phone_number=" + "+33660760376")
      when(apiService.http.singleRequest(req)) thenReturn (Future(HttpResponse(StatusCodes.OK)))
      val rawHeader = RawHeader("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiI1YWQyNWFiOC1lNDRmLTQ1OTAtOGU4Mi04YmYwYzk3NDk5MWUiLCJwaG9uZV9udW1iZXIiOiIrMzM2NjA3NjAzNzYiLCJyb2xlIjoidXNlciIsImlhdCI6MTQ2ODU3MjE2MDIxNn0._lsHenZoIzKoZHtKGPoNl8nasuY0FksXkNJvUBJIRYo")
      Get("/user/userId/wall/pos").withHeaders(rawHeader) ~> route ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    "return ok if url to view wall's notifications is hit " in {
      val req: HttpRequest = HttpRequest(uri = HTTP + interface + ":" + notificationPort + DELIMITER + "my" + DELIMITER + "allnotifications" + DELIMITER + "pos" +
        "?userId=" + "5ad25ab8-e44f-4590-8e82-8bf0c974991e" + "&phone_number=" + "+33660760376")
      when(apiService.http.singleRequest(req)) thenReturn (Future(HttpResponse(StatusCodes.OK)))
      val rawHeader = RawHeader("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiI1YWQyNWFiOC1lNDRmLTQ1OTAtOGU4Mi04YmYwYzk3NDk5MWUiLCJwaG9uZV9udW1iZXIiOiIrMzM2NjA3NjAzNzYiLCJyb2xlIjoidXNlciIsImlhdCI6MTQ2ODU3MjE2MDIxNn0._lsHenZoIzKoZHtKGPoNl8nasuY0FksXkNJvUBJIRYo")
      Get("/my/allnotifications/pos").withHeaders(rawHeader) ~> route ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    "return ok when receive notification route is hit" in {

      Get("/") ~> Upgrade(List(UpgradeProtocol("websocket"))) ~> emulateHttpCore ~> route ~> check {
        status shouldEqual StatusCodes.SwitchingProtocols
      }
    }
    "send notification when a spok is respoked" in {

      val url = "url"

      val userId = java.util.UUID.randomUUID().toString
      val absoluteSpokId = java.util.UUID.randomUUID().toString
      def notificationId = java.util.UUID.randomUUID().toString
      val spokInstanceId = java.util.UUID.randomUUID().toString
      val visitiedUserId = java.util.UUID.randomUUID().toString
      val subscriberUserId = java.util.UUID.randomUUID().toString
      val spokInstance = SpokInstance(absoluteSpokId, spokInstanceId, RESPOKED, PUBLIC,
        Some("spok text"), Some("0"))
      val respokResponse = RespokResponse(spokInstance, spokInstanceId, RespokStatistics(4, 5, 3, 2455.57), List("userId3"))
      val txt = write(respokResponse)
      val notificationForUser = NotificationDetail(List("userId2"), notificationId, RESPOKED, absoluteSpokId, userId)
      val mockedApiServiceHandler = mock[ApiServiceHandler]
      def receiveNotificationViaWebSocket: Flow[Message, Message, Any] = {
        Flow[Message]
          .collect {
            case TextMessage.Strict(msg) ⇒ msg
          }
          .map {
            case msg: String ⇒ TextMessage.Strict(txt)
          }
      }

      val apiService = new ApiService() {
        override val http = mock[HttpExt]
        override val redisFactory = mock[RedisFactory]
        override def sendUserInfoInUrl(url: String)(userInfo: (String, String)) = receiveNotificationViaWebSocket
      }
      when(apiService.redisFactory.fetchVisitiedUsers(absoluteSpokId)) thenReturn (Future(Set(visitiedUserId)))
      when(apiService.redisFactory.fetchSubscribers(absoluteSpokId)) thenReturn (Future(Set(visitiedUserId)))
      val result = apiService.respokResponseHandler(url)(userId, "99723456789")
      val (pub, sub) = TestSource.probe[Message]
        .via(result)
        .toMat(TestSink.probe[Message])(Keep.both)
        .run()
      sub.request(1)
      pub.sendNext(TextMessage.Strict(url))
      sub.expectNext(TextMessage.Strict(txt))
    }

    "return ok if the url Proxy route is hit" in {

      Get("/urlproxy") ~> Upgrade(List(UpgradeProtocol("websocket"))) ~> emulateHttpCore ~> route ~> check {
        status shouldEqual StatusCodes.SwitchingProtocols
      }
    }
  }

  private def emulateHttpCore(req: HttpRequest): HttpRequest =
    req.header[Upgrade] match {
      case Some(upgrade) if upgrade.hasWebSocket => req.copy(headers = req.headers :+ handleWebSocketMessgae("access_token_"))
      case _ => req
    }

  private def emulateHttpCoreForGroup(req: HttpRequest): HttpRequest =
    req.header[Upgrade] match {
      case Some(upgrade) if upgrade.hasWebSocket => req.copy(headers = req.headers :+ handleWebSocketMessgae(ADD_USER))
      case _ => req
    }

  private def emulateHttpCoreForGroupREMOVE_USER(req: HttpRequest): HttpRequest =
    req.header[Upgrade] match {
      case Some(upgrade) if upgrade.hasWebSocket => req.copy(headers = req.headers :+ handleWebSocketMessgae(REMOVE_USER))
      case _ => req
    }

  private def emulateHttpCoreForGroupREMOVE_GROUP(req: HttpRequest): HttpRequest =
    req.header[Upgrade] match {
      case Some(upgrade) if upgrade.hasWebSocket => req.copy(headers = req.headers :+ handleWebSocketMessgae(REMOVE_PROTOCOL))
      case _ => req
    }

  private def emulateHttpCoreForGroupUPDATE_GROUP(req: HttpRequest): HttpRequest =
    req.header[Upgrade] match {
      case Some(upgrade) if upgrade.hasWebSocket => req.copy(headers = req.headers :+ handleWebSocketMessgae(UPDATE_PROTOCOL))
      case _ => req
    }

  private def emulateHttpCoreForCommentUpdate(req: HttpRequest): HttpRequest =
    req.header[Upgrade] match {
      case Some(upgrade) if upgrade.hasWebSocket => req.copy(headers = req.headers :+ handleWebSocketMessgae(UPDATE_PROTOCOL))
      case _ => req
    }

  private def emulateHttpCoreForCommentRemove(req: HttpRequest): HttpRequest =
    req.header[Upgrade] match {
      case Some(upgrade) if upgrade.hasWebSocket => req.copy(headers = req.headers :+ handleWebSocketMessgae(REMOVE_PROTOCOL))
      case _ => req
    }

  private def handleWebSocketMessgae(protocol: String) = {

    new CustomHeader() with UpgradeToWebSocket {
      override def requestedProtocols = List(protocol + "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiI1YWQyNWFiOC1lNDRmLTQ1OTAtOGU4Mi04YmYwYzk3NDk5MWUiLCJwaG9uZV9udW1iZXIiOiIrMzM2NjA3NjAzNzYiLCJyb2xlIjoidXNlciIsImlhdCI6MTQ2ODU3MjE2MDIxNn0._lsHenZoIzKoZHtKGPoNl8nasuY0FksXkNJvUBJIRYo")

      override def name = "dummy"

      override def value = "dummy"

      override def renderInRequests = true

      override def renderInResponses = true

      override def handleMessages(handlerFlow: Graph[FlowShape[Message, Message], Any], subprotocol: Option[String]): HttpResponse =
        HttpResponse(StatusCodes.SwitchingProtocols)
    }

  }
}