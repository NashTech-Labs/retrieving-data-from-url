package com.metaData.apiservice.service

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import com.metaData.apiservice.handler.UrlMetaDataRestServiceHandler

import scala.util.parsing.json.JSON

class UrlMetaDataService(implicit fm: Materializer, system: ActorSystem) extends UrlMetaDataRestServiceHandler {

  final case class Url(url: String)

  // ==============================
  //     SAMPLE ROUTES
  // ==============================

  def sampleRoutes: Route = path("ping") {
    get {
      complete("pong")
    }
  }

  def urlproxyRoutes: Route =
    path("urlproxy") {
      post {
        entity(as[String]) { url =>
          val proxyUrl = extractUrl(url)
          onSuccess(retrieveData(proxyUrl))(complete(_))
        }

      }
    }

  private def extractUrl(url: String): String = {
    var urlString = JSON.parseFull(url)
    urlString match {
      case Some(proxy: Map[String, String]) => proxy.get("url").getOrElse("")
      case None => "Invalid Input"
    }

  }

  // ==============================
  //     ALL ROUTES
  // ==============================

  // combination of all routes
  def routes: Route = sampleRoutes ~ urlproxyRoutes

}

