package com.metaData.apiservice.service

import akka.actor.Actor
import net.liftweb.json.Serialization.write
import org.jsoup.Jsoup

import scala.collection.JavaConverters._

case class Data(title: String, description: String, contentType: String, images: Seq[String])

case class Result(status: String, data: Data)

case class Fail(status: String, error: String)

class UrlMetaDataActor extends Actor {
  implicit val formats = net.liftweb.json.DefaultFormats

  def receive = {
    case url: String => {
      try {
        val doc = Jsoup.connect(url).get()
        val title = doc.select("meta[property=og:title]").get(0).attr("content")
        val description = doc.select("meta[property=og:description]").get(0).attr("content")
        val contentType = doc.select("meta[property=og:type]").get(0).attr("content")
        lazy val extractImages: Seq[String] = {
          doc.select("meta[property=og:image]").iterator().asScala.toSeq.map(_.attr("abs:content"))
        }
        sender() ! write(Result("success", Data(title, description, contentType, extractImages)))

      } catch {
        case ex: Exception =>
          sender() ! write(Fail("Failure", ex.getMessage))
      }
    }
  }
}
