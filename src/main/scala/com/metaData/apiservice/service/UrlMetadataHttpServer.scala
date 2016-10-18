package com.metaData.apiservice.service

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.metaData.apiservice.util.ConfigUtil._
import com.typesafe.scalalogging.LazyLogging
import org.fusesource.jansi.Ansi.Color._
import org.fusesource.jansi.Ansi._

import scala.util.{Failure, Success}


object UrlMetadataHttpServer extends App with LazyLogging {

  implicit val system = ActorSystem("proxyurl")
  implicit val executor = system.dispatcher
  implicit val materializer = ActorMaterializer()

  val http = Http()

  // use system's dispatcher as ExecutionContext

  val apiService = new UrlMetaDataService
  val binding = http.bindAndHandle(apiService.routes, hostPoint, port)

  //scalastyle:off
  binding.onComplete {
    case Success(binding) ⇒
      val localAddress = binding.localAddress
      println(ansi().fg(GREEN).a(
        """
           ___                              _   _         _
          | _ \  _ _   ___  __ __  _  _    | | | |  _ _  | |
          |  _/ | '_| / _ \ \ \ / | || |   | |_| | | '_| | |
          |_|   |_|   \___/ /_\_\  \_, |    \___/  |_|   |_|
          |__/
        """
      ).reset())
      //scalastyle:on

      logger.info(s"Server is listening on ${localAddress.getHostName}:${localAddress.getPort}")
    case Failure(e) ⇒
      logger.info(s"Binding failed with ${e.getMessage}")
      system.terminate()
  }
}
