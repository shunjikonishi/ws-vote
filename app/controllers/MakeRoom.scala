package controllers

import play.api._
import play.api.mvc._
import models._
import play.api.libs.iteratee.Iteratee
import play.api.libs.iteratee.Concurrent
import play.api.libs.concurrent.Execution.Implicits.defaultContext

object MakeRoom extends Controller {

  def top = Action { implicit request =>
    Ok(views.html.top("Vote!"))
  }
  
  def checkName = WebSocket.using[String] { request =>
  	val (out, channel) = Concurrent.broadcast[String]

  	val in = Iteratee.foreach[String] { key =>
  		val ok = MyRedisService.withClient(_.get(key)).map(_ => "NG").getOrElse("OK")
  		channel.push(ok)
  	}
  	(in, out)
  }
}
