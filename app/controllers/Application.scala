package controllers

import play.api._
import play.api.mvc._
import models._
import java.net.URI

object Application extends Controller {

  val defaultSetting = RoomSetting(
    name="default",
    title="DevSumi2014",
    message="お好きな色を推してください",
    List(
      Button("red", "赤", "ff0000"),
      Button("yellow", "黄", "ffff00"),
      Button("pink", "ピンク", "ff69b4"),
      Button("green", "緑", "00ff7f"),
      Button("purple", "紫", "9400d3")
    )
  )
      
  def redirectToDefault = Action { implicit request =>
    Redirect(routes.Application.room("default"))
  }
  
  def index = Action { implicit request =>
    Ok(views.html.index("Vote!"))
  }
  
  def room(name: String) = Action { implicit request =>
    val setting = defaultSetting
    val counts = setting.buttons.map { b =>
      val cnt = MyRedisService.withClient(_.get(name + "#" + b.key))
      (b.key, cnt.getOrElse("0"))
    }.toMap
    Ok(views.html.room(setting, counts))
  }
  
  def ws(name: String) = WebSocket.async[String] { request =>
    VoteRoom.join(name)
  }
  
  def loadtest(name: String, threads: Int, count: Int) = Action { implicit request =>
    //val uri = new URI(routes.Application.ws(name).webSocketURL())
    val uri = new URI("ws://ws-vote.herokuapp.com/ws/default")
    val setting = defaultSetting
    
    new LoadTest(uri, setting).run(threads, count)
    Ok(s"loadtest: $name, $threads, $count, $uri");
  }
}