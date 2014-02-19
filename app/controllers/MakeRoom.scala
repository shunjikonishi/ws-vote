package controllers

import play.api._
import play.api.mvc._
import models._
import play.api.libs.iteratee.Iteratee
import play.api.libs.iteratee.Concurrent
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import java.util.Date

object MakeRoom extends Controller {

  def top = Action { implicit request =>
    Ok(views.html.top("Vote!"))
  }
  
  def checkName = WebSocket.using[String] { request =>
  	val (out, channel) = Concurrent.broadcast[String]

  	val in = Iteratee.foreach[String] { key =>
  		val ok = VoteRoom.getSetting(key).filter(_.canView(new Date())).map(_ => "NG").getOrElse("OK")
  		channel.push(ok)
  	}
  	(in, out)
  }

  def test = Action {
     VoteRoom.getSetting("default").foreach(VoteRoom.save(_))
    val demo = VoteRoom.getSetting("demo").foreach(VoteRoom.save(_))
    Ok("OK")
  }
}
