package models

import akka.actor.ActorRef
import akka.actor.Actor
import akka.actor.Props
import akka.util.Timeout
import akka.pattern.ask
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import play.api.libs.concurrent.Akka
import play.api.Play.current
import play.api.Logger
import play.api.libs.iteratee.Iteratee
import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Done
import play.api.libs.iteratee.Input
import play.api.libs.json.JsValue
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import flect.redis.RedisService

class RoomManager(redis: RedisService) {
	
  private var rooms = Map.empty[String, VoteRoom]
      
  def getSetting(name: String): Option[RoomSetting] = {
    redis.hget("room", name).map { str =>
      RoomSetting.fromJson(str)
    }
  }

  def save(setting: RoomSetting) = {
    redis.hset("room", setting.name, setting.toJson)
  }
  
  def join(room: String, clientId: String): Future[(Iteratee[String,_], Enumerator[String])] = {
    (actor ? Join(room, clientId)).asInstanceOf[Future[(Iteratee[String,_], Enumerator[String])]]
  }
  
  def getRoom(name: String): VoteRoom = {
    getSetting(name).map { setting =>
      val room = rooms.get(name).filter(_.isActive)
      room match {
        case Some(x) => x
        case None =>
          val ret = new VoteRoom(setting, redis)
          rooms = rooms + (name -> ret)
          ret
      }
    }.getOrElse(throw new IllegalStateException("Room not found: " + name))
  }
  
  def error(msg: String): (Iteratee[String,_], Enumerator[String]) = {
    Logger.info("Can not connect room: " + msg)
    val in = Done[String,Unit]((),Input.EOF)
    val out =  Enumerator[String](JsObject(Seq("error" -> JsString(msg))).toString).andThen(Enumerator.enumInput(Input.EOF))
    (in, out)
  }
  
  implicit val timeout = Timeout(5 seconds)
  
  private val actor = Akka.system.actorOf(Props(new MyActor()))
  
  private sealed class Msg
  private case class Join(room: String, clientId: String)
  
  private class MyActor extends Actor {
    def receive = {
      case Join(room, clientId) => 
        val ret = try {
          getRoom(room).join(clientId)
        } catch {
          case e: Exception =>
            e.printStackTrace
            error(e.getMessage)
        }
        sender ! ret
    }
    
    
    override def postStop() = {
      Logger.info("!!! postStop !!!")
      rooms.values.filter(_.isActive).foreach(_.close)
      rooms = Map.empty[String, VoteRoom]
      redis.close
      super.postStop()
    }
  }
  
}