package controllers

import play.api._
import play.api.mvc._
import models._
import java.net.URI
import java.util.UUID
import java.util.Date

object Application extends Controller {

  def redirectToDefault = Action { implicit request =>
    Redirect(routes.Application.room("default"))
  }
  
  def index = Action { implicit request =>
    Ok(views.html.index("Vote!"))
  }
  
  def top = Action { implicit request =>
    Ok(views.html.top("Vote!"))
  }
  
  def room(name: String) = Action { implicit request =>
    val d = new Date()
    VoteRoom.getSetting(name).filter(_.canView(d)).map { setting =>
      val counts = setting.buttons.map { b =>
        val cnt = MyRedisService.withClient(_.get(name + "#" + b.key))
        (b.key, cnt.getOrElse("0"))
      }.toMap
      val clientId = request.cookies.get("clientId").map(_.value).getOrElse(UUID.randomUUID().toString)
      Ok(views.html.room(setting, counts, clientId)).withCookies(
        Cookie(name="clientId", value=clientId, maxAge=Some(3600 * 24 * 30))
      )
    }.getOrElse(NotFound("Room not found"))
  }
  
  def reset(name: String) = Action { implicit request =>
    VoteRoom.getSetting(name).map { setting =>
      VoteRoom.getRoom(name).reset
      Redirect(routes.Application.room(name))
    }.getOrElse(NotFound("Room not found"))
  }
  
  def ws(name: String) = WebSocket.async[String] { request =>
    val clientId = request.cookies.get("clientId").map(_.value).getOrElse(UUID.randomUUID().toString)
    VoteRoom.join(name, clientId)
  }
  
  def loadtest(name: String, threads: Int, count: Int) = Action { implicit request =>
    val uri = new URI("ws://ws-vote.herokuapp.com/ws/default")
    val setting = VoteRoom.defaultSetting
    
    new LoadTest(uri, setting).run(threads, count)
    Ok(s"loadtest: $name, $threads, $count, $uri");
  }
}