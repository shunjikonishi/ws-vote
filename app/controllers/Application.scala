package controllers

import play.api._
import play.api.mvc._
import models._
import java.net.URI

object Application extends Controller {

  def redirectToDefault = Action { implicit request =>
    Redirect(routes.Application.room("default"))
  }
  
  def index = Action { implicit request =>
    Ok(views.html.index("Vote!"))
  }
  
  def room(name: String) = Action { implicit request =>
    val setting = VoteRoom.getSetting(name)
    setting.map { s =>
      val counts = s.buttons.map { b =>
        val cnt = MyRedisService.withClient(_.get(name + "#" + b.key))
        (b.key, cnt.getOrElse("0"))
      }.toMap
      Ok(views.html.room(s, counts))
    }.getOrElse(NotFound(s"Room $name doesn't exist."))
  }
  
  def ws(name: String) = WebSocket.async[String] { request =>
    VoteRoom.join(name)
  }
  
  def loadtest(name: String, threads: Int, count: Int) = Action { implicit request =>
    val uri = new URI("ws://ws-vote.herokuapp.com/ws/default")
    val setting = VoteRoom.defaultSetting
    
    new LoadTest(uri, setting).run(threads, count)
    Ok(s"loadtest: $name, $threads, $count, $uri");
  }
}