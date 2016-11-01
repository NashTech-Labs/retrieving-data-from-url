package com.spok.apiservice.service

import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.metaData.apiservice.service.UrlMetaDataService
import org.scalatest.{Matchers, WordSpec}

class UrlMetaDataServiceSpec extends WordSpec with Matchers with ScalatestRouteTest {

  val urlMetaDataService = new UrlMetaDataService()
  val route = urlMetaDataService.routes

  "Api Service" should {

    "return pong in response" in {

      Get("/ping") ~> route ~> check {
        status shouldBe StatusCodes.OK
      }
    }

  }
}