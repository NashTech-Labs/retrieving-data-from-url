package com.metaData.apiservice.handler

import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.metaData.apiservice.service.UrlMetaDataActor

import scala.concurrent.Future
import scala.concurrent.duration._

trait UrlMetaDataRestServiceHandler {

  implicit val system = ActorSystem("UrlMetaDataActor")

  implicit val dispatcher = system.dispatcher
  val urlMetaDataActor = system.actorOf(Props[UrlMetaDataActor], name = "UrlMetaDataActor")
  implicit val timeout = Timeout(30 seconds)

  def retrieveData(url: String): Future[String] = {
    val result = ask(urlMetaDataActor, url)
    result map {
      response =>
        response match {
          case data: String => data
        }
    }
  }
}
