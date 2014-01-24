package models

import akka.actor.ActorRef
import akka.actor.Actor
import akka.actor.Props
import scala.concurrent.duration.DurationInt
import scala.concurrent.Future

import play.api.Logger
import play.api.libs.json.Json
import play.api.libs.json.JsValue
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsError
import play.api.libs.iteratee.Iteratee
import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Done
import play.api.libs.iteratee.Input
import play.api.libs.iteratee.Concurrent
import play.api.libs.concurrent.Akka

import akka.util.Timeout
import akka.pattern.ask

import play.api.Play
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import flect.redis.RedisService

object MyRedisService extends RedisService(Play.configuration.getString("redis.uri").get)

case class Button(key: String, text: String, color: String)
case class RoomSetting(name: String, message: String, buttons: List[Button])

class VoteRoom(name: String, redis: RedisService) {
  
  Logger.info("Create ChatRoom: " + name)
  val channel = redis.createPubSub(name)
  
  private val member_key = name + "-members"
  private def voteKey(key: String) = name + "#" + key
  
  private val closer: Closer = new Closer(this.close)
  def active = !closer.closed
  
  def connect = closer.inc
  def disconnect = closer.desc
  
  case class Message(kind: String, key: String, count: Long)
  implicit val messageFormat = Json.format[Message]
  
  private def send(kind: String, key: String, count: Long) {
    val msg = new Message(kind, key, count)
    channel.send(Json.toJson(msg).toString)
  }
  
  private def createIteratee: Iteratee[String, _] = {
    Iteratee.foreach[String] { msg =>
      if (msg != "###dummy###") {
        val key = voteKey(msg)
        val count = redis.withClient(_.incr(key))
        count.foreach(send("vote", msg, _))
      }
    }.map { _ =>
      val count = redis.withClient(_.decr(member_key))
      count.foreach(send("member", "quit", _))
      VoteRoom.quit(name)
      Logger.info("Quit from " + name)
    }
  }
    
  def join: (Iteratee[String,_], Enumerator[String]) = {
    Logger.info("Join to " + name)
    connect
    
    val in = createIteratee
    val count = redis.withClient(_.incr(member_key))
    count.foreach(send("member", "join", _))
    (in, channel.out)
  }
  
  def close = {
    channel.close
  }
}

object VoteRoom {
  
  sealed class Msg
  case class Join(room: String)
  case class Quit(room: String)
  
  class MyActor extends Actor {
    def receive = {
      case Join(room) => 
        val ret = try {
          get(room).join
        } catch {
          case e: Exception =>
            e.printStackTrace
            error(e.getMessage)
        }
        sender ! ret
      case Quit(room) =>
        get(room).disconnect
        sender ! true
    }
    
    
    override def postStop() = {
      Logger.info("!!! postStop !!!")
      rooms.values.filter(_.active).foreach(_.close)
      rooms = Map.empty[String, VoteRoom]
      MyRedisService.close
      super.postStop()
    }
  }
  

  
  def join(room: String): Future[(Iteratee[String,_], Enumerator[String])] = {
    (actor ? Join(room)).asInstanceOf[Future[(Iteratee[String,_], Enumerator[String])]]
  }
  
  def quit(room: String) = {
    actor ! Quit(room)
  }
  
  var rooms = Map.empty[String, VoteRoom]
  
  private def get(name: String): VoteRoom = {
    val room = rooms.get(name).filter(_.active)
    room match {
      case Some(x) => x
      case None =>
        val ret = new VoteRoom(name, MyRedisService)
        rooms = rooms + (name -> ret)
        ret
    }
  }
  
  def error(msg: String): (Iteratee[String,_], Enumerator[String]) = {
    Logger.info("Can not connect room: " + msg)
    val in = Done[String,Unit]((),Input.EOF)
    val out =  Enumerator[String](JsObject(Seq("error" -> JsString(msg))).toString).andThen(Enumerator.enumInput(Input.EOF))
    (in, out)
  }
  
  implicit val timeout = Timeout(5 seconds)
  
  val actor = Akka.system.actorOf(Props(new MyActor()))
}

class Closer(body: => Any) {
  private var counter = 0
  private var active = true
  
  def closed = !active
  def inc = synchronized {
    if (active) counter += 1
  }
  def desc = synchronized {
    if (active) {
      if (counter == 0) {
        throw new IllegalStateException("Counter doesn't incremented.")
      }
      counter -= 1
      if (counter == 0) {
        active = false
        body
      }
    }
  }
}
