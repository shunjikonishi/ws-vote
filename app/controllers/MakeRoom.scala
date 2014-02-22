package controllers

import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import models._
import play.api.libs.iteratee.Iteratee
import play.api.libs.iteratee.Concurrent
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import java.util.Date
import java.util.Calendar

object MakeRoom extends Controller {

  def top = Action { implicit request =>
    Ok(views.html.top(VoteRoom.appName))
  }
  
  def checkName = WebSocket.using[String] { request =>
  	val (out, channel) = Concurrent.broadcast[String]

  	val in = Iteratee.foreach[String] { key =>
  		val ok = VoteRoom.getSetting(key).filter(_.canView(new Date())).map(_ => "NG").getOrElse("OK")
  		channel.push(ok)
  	}
  	(in, out)
  }

  case class EditData(name: String, pass: String)
  val editForm = Form(mapping(
    "name" -> text,
    "pass" -> text
  )(EditData.apply)(EditData.unapply))

  def register = Action { implicit request =>
    def defaultVoteLimit = {
      val cal = Calendar.getInstance()
      cal.set(Calendar.MINUTE, 0)
      cal.set(Calendar.SECOND, 0)
      cal.set(Calendar.MILLISECOND, 0)
      cal.add(Calendar.HOUR_OF_DAY, 1)
      cal.getTime
    }
    def defaultViewLimit = {
      val cal = Calendar.getInstance()
      cal.set(Calendar.HOUR_OF_DAY, 0)
      cal.set(Calendar.MINUTE, 0)
      cal.set(Calendar.SECOND, 0)
      cal.set(Calendar.MILLISECOND, 0)
      cal.add(Calendar.DATE, 1)
      cal.getTime
    }
    val data = editForm.bindFromRequest.get
    VoteRoom.getSetting(data.name).foreach(VoteRoom.remove(_))
    val setting = VoteRoom.defaultSetting.copy(
      name=data.name,
      password=Some(data.pass),
      voteLimit=Some(defaultVoteLimit),
      viewLimit=Some(defaultViewLimit)
    )
    VoteRoom.save(setting)
    Redirect(routes.MakeRoom.edit(data.name)).flashing(
      "pass" -> data.pass.hashCode.toString
    )
  }

  def edit(name: String) = Action { implicit request =>
    VoteRoom.getSetting(name) match {
      case Some(setting) =>
        val pass = flash.get("pass").getOrElse("")
        setting.password.map(_.hashCode.toString).filter(_ == pass) match {
          case Some(x) =>
            Ok(views.html.edit(VoteRoom.appName, setting))
          case None =>
            Ok(views.html.lock(VoteRoom.appName, name))
        }
      case None =>
        NotFound
    }
  }

  def pass = Action { implicit request =>
    val data = editForm.bindFromRequest.get
    Redirect(routes.MakeRoom.edit(data.name)).flashing(
      "pass" -> data.pass.hashCode.toString
    )
  }

  def test = Action {
     VoteRoom.getSetting("default").foreach(VoteRoom.save(_))
    val demo = VoteRoom.getSetting("demo").foreach(VoteRoom.save(_))
    Ok("OK")
  }
}
