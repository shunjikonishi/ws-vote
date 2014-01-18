package controllers

import play.api._
import play.api.mvc._
import flect.redis.RedisService

object Application extends Controller {

  def index = Action {
    Ok(views.html.index("WS Button"))
  }
  
  val myRedisService = RedisService("redis://@localhost:6379")
  
  def echo = WebSocket.using[String] { _ =>
    val channel = myRedisService.createPubSub("echo")
    (channel.in, channel.out)
  }
  
  def echoTest = Action { implicit request =>
    Ok(views.html.echoTest())
  }

}