package com.spok.apiservice.handler

import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.spok.model.Notification
import com.spok.model.Spok._
import com.spok.persistence.redis.RedisFactory
import com.spok.util.Constant._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpec }
import org.mockito.Mockito.when

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }

class ApiServiceHandlerSpec extends WordSpec with Matchers with MockitoSugar with BeforeAndAfterAll with ScalatestRouteTest with ApiServiceHandler {

  val mockedRedisFactory = mock[RedisFactory]
  override val redisFactory = mockedRedisFactory

  "ApiServiceHandler" should {

    "be able to test handle notification when user is follows" in {

      val userId = getUUID()
      val followerId = getUUID()

      when(mockedRedisFactory.fetchVisitiedUsers(followerId)) thenReturn (Future(Set("followerId1")))
      when(mockedRedisFactory.fetchVisitiedUsers(userId)) thenReturn (Future(Set("userId1")))
      val (visitorFollowerNotificationDetail, visitorNotificationDetail) = handleNotificationOnUnfollowOrFollow(followerId, userId, "Follows")
      val visitorFollowerNotificationDetailResult = Await.result(visitorFollowerNotificationDetail, 10 seconds)
      val visitorNotificationDetailResult = Await.result(visitorNotificationDetail, 10 seconds)

      assert(visitorFollowerNotificationDetailResult.emitterId === followerId)
      assert(visitorFollowerNotificationDetailResult.notificationType === "Follows")
      assert(visitorFollowerNotificationDetailResult.relatedTo === userId)

    }

    "be able to test handle notification when user is unfollows" in {

      val userId = getUUID()
      val followerId = getUUID()

      when(mockedRedisFactory.fetchVisitiedUsers(followerId)) thenReturn (Future(Set("followerId2")))
      when(mockedRedisFactory.fetchVisitiedUsers(userId)) thenReturn (Future(Set("userId2")))
      val (visitorFollowerNotificationDetail, visitorNotificationDetail) = handleNotificationOnUnfollowOrFollow(followerId, userId, "Unfollows")
      val visitorFollowerNotificationDetailResult = Await.result(visitorFollowerNotificationDetail, 10 seconds)
      val visitorNotificationDetailResult = Await.result(visitorNotificationDetail, 10 seconds)

      assert(visitorFollowerNotificationDetailResult.emitterId === followerId)
      assert(visitorFollowerNotificationDetailResult.notificationType === UNFOLLOWS)
      assert(visitorFollowerNotificationDetailResult.relatedTo === userId)

    }

    "be able to test handle notification when comment is removed" in {

      val userId = getUUID()
      val commentId = getUUID()
      val spokInstanceId = getUUID()
      val absoluteSpokId = getUUID()
      val commentInternalSpokResponse = CommentInternalSpokResponse(spokInstanceId, "1", "1", "4", "256545", Some(absoluteSpokId))
      val removeCommentResponse = RemoveCommentResponse(commentId, commentInternalSpokResponse)
      val txt = write(removeCommentResponse)
      when(mockedRedisFactory.fetchVisitiedUsers(absoluteSpokId)) thenReturn (Future(Set("userId2")))
      when(mockedRedisFactory.fetchSubscribers(absoluteSpokId)) thenReturn (Future(Set.empty[String]))
      val notificationDetail = handleCommentAndSpokOperations(txt, userId, absoluteSpokId, REMOVE_COMMENT)
      val visitorNotificationDetailResult = Await.result(notificationDetail, 10 seconds)

      assert(visitorNotificationDetailResult.head.emitterId === userId)
      assert(visitorNotificationDetailResult.head.notificationType === REMOVE_COMMENT)
      assert(visitorNotificationDetailResult.head.relatedTo === absoluteSpokId)

    }

    "be able to test handle notification when comment is added" in {

      val userId = getUUID()
      val commentId = getUUID()
      val spokInstanceId = getUUID()
      val absoluteSpokId = getUUID()
      val commentInternalSpokResponse = CommentInternalSpokResponse(spokInstanceId, "1", "1", "4", "256545", Some(absoluteSpokId))
      val commenterUserResponse = CommenterUserResponse(userId, "john", "male", "picture")
      val addCommentResponse = SpokCommentResponse(commentInternalSpokResponse, commenterUserResponse, Some(Nil), Some(commentId))
      val txt = write(addCommentResponse)
      when(mockedRedisFactory.fetchVisitiedUsers(absoluteSpokId)) thenReturn (Future(Set("userId2")))
      when(mockedRedisFactory.fetchSubscribers(absoluteSpokId)) thenReturn (Future(Set.empty[String]))
      val notificationDetail = handleCommentAndSpokOperations(txt, userId, absoluteSpokId, "Comment Added")
      val visitorNotificationDetailResult = Await.result(notificationDetail, 10 seconds)

      assert(visitorNotificationDetailResult.head.emitterId === userId)
      assert(visitorNotificationDetailResult.head.notificationType === COMMENT_ADDED)
      assert(visitorNotificationDetailResult.head.relatedTo === absoluteSpokId)
    }

    "be able to test handle notification when comment is updated" in {

      val userId = getUUID()
      val spokInstanceId = getUUID()
      val absoluteSpokId = getUUID()
      val updateCommentResponse = CommentUpdateResponse(spokInstanceId, "1", "1", "4", "256545")
      val txt = write(updateCommentResponse)
      when(mockedRedisFactory.fetchVisitiedUsers(absoluteSpokId)) thenReturn (Future(Set("userId2")))
      when(mockedRedisFactory.fetchSubscribers(absoluteSpokId)) thenReturn (Future(Set.empty[String]))
      val notificationDetail = handleCommentAndSpokOperations(txt, userId, absoluteSpokId, COMMENT_UPDATED)
      val visitorNotificationDetailResult = Await.result(notificationDetail, 10 seconds)

      assert(visitorNotificationDetailResult.head.emitterId === userId)
      assert(visitorNotificationDetailResult.head.notificationType === COMMENT_UPDATED)
      assert(visitorNotificationDetailResult.head.relatedTo === absoluteSpokId)
    }

    "be able to test handle notification when user profile is updated" in {

      val userId = getUUID()
      when(mockedRedisFactory.fetchVisitiedUsers(userId)) thenReturn (Future(Set("userId2")))
      val notificationDetail = handleUpdateUserProfileNotification(userId)
      val visitorNotificationDetailResult = Await.result(notificationDetail, 10 seconds)
      assert(visitorNotificationDetailResult.emitterId === userId)
      assert(visitorNotificationDetailResult.notificationType === USER_PROFILE_UPDATE_SUCCESS)
      assert(visitorNotificationDetailResult.relatedTo === userId)
      assert(visitorNotificationDetailResult.userIds === List("userId2"))
    }
  }

}
