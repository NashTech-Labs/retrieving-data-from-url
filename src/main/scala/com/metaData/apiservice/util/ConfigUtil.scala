package com.metaData.apiservice.util

import com.typesafe.config.ConfigFactory

object ConfigUtil {

  val config = ConfigFactory.load()

  val interface = config.getString("app.interface")
  val hostPoint = config.getString("app.host")
  val port = config.getInt("app.port")
  }
