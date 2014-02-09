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
import java.util.Date

import play.api.Play
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import flect.redis.RedisService

object MyRedisService extends RedisService(Play.configuration.getString("redis.uri").get)

case class Button(key: String, text: String, color: String)
case class RoomSetting(
  name: String, 
  title: String, 
  message: String, 
  buttons: List[Button],
  viewLimit: Option[Date] = None,
  voteLimit: Option[Date] = None
) {
  def buttonText(key: String) = {
    buttons.find(_.key == key).map(_.text)
  }
}

class VoteRoom(setting: RoomSetting, redis: RedisService) {
  
  Logger.info("Create ChatRoom: " + setting.name)
  val channel = redis.createPubSub(setting.name)
  
  private val member_key = setting.name + "-members"
  private def voteKey(key: String) = setting.name + "#" + key
  
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
  
  private def createIteratee(clientId: String): Iteratee[String, _] = {
    Iteratee.foreach[String] { msg =>
      msg match {
        case "###member###" =>
          val count = redis.withClient(_.get(member_key))
          count.foreach { n =>
            send("member", "now", n.toLong)
          }
        case "###dummy###" =>
          //Do nothing
        case _ =>
          val key = voteKey(msg)
          val count = redis.withClient(_.incr(key))
          count.foreach { n =>
            send("vote", msg, n)
            if ((n % 100) == 0) {
              setting.buttonText(msg).foreach { text =>
                channel.outChannel.push(Json.toJson(new Message(clientId,  text, n)).toString)
              }
            }
          }
      }
    }.map { _ =>
      val count = redis.withClient(_.decr(member_key))
      count.foreach(send("member", "quit", _))
      VoteRoom.quit(setting.name)
      Logger.info("Quit from " + setting.name)
    }
  }
    
  def join(clientId: String): (Iteratee[String,_], Enumerator[String]) = {
    Logger.info("Join to " + setting.name)
    connect
    
    val in = createIteratee(clientId)
    val count = redis.withClient(_.incr(member_key))
    count.foreach(send("member", "join", _))
    (in, channel.out)
  }
  
  def reset = {
    setting.buttons.foreach { b =>
      val key = voteKey(b.key)
      redis.withClient(_.del(key))
    }
  }
  
  def close = {
    val cnt = closer.count
    if (cnt != 0) {
      redis.withClient(_.decrby(member_key, cnt))
    }
    scheduler.cancel
    channel.close
  }
  
  val scheduler = Akka.system.scheduler.schedule(20 seconds, 20 seconds) {
    Logger.info("schedule");
    val count = redis.withClient(_.get(member_key))
    count.foreach { n =>
      val msg = new Message("member", "now", n.toLong)
      channel.outChannel.push(Json.toJson(msg).toString)
    }
  }
}

object VoteRoom {
  
  val defaultSetting = RoomSetting(
    name="default",
    title="DevSumi2014",
    message="お好きな色を推してください",
    buttons=List(
      Button("red", "赤", "ff0000"),
      Button("yellow", "黄", "ffff00"),
      Button("pink", "ピンク", "ff69b4"),
      Button("green", "緑", "00ff7f"),
      Button("purple", "紫", "9400d3")
    )
  )
      
  sealed class Msg
  case class Join(room: String, clientId: String)
  case class Quit(room: String)
  
  class MyActor extends Actor {
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
      case Quit(room) =>
        getRoom(room).disconnect
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
  

  
  def join(room: String, clientId: String): Future[(Iteratee[String,_], Enumerator[String])] = {
    (actor ? Join(room, clientId)).asInstanceOf[Future[(Iteratee[String,_], Enumerator[String])]]
  }
  
  def quit(room: String) = {
    actor ! Quit(room)
  }
  
  var settings = Map(defaultSetting.name -> defaultSetting)
  var rooms = Map.empty[String, VoteRoom]
  
  def getSetting(name: String): Option[RoomSetting] = {
    settings.get(name)
  }
  
  def getRoom(name: String): VoteRoom = {
    getSetting(name).map { setting =>
      val room = rooms.get(name).filter(_.active)
      room match {
        case Some(x) => x
        case None =>
          val ret = new VoteRoom(setting, MyRedisService)
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
  
  val actor = Akka.system.actorOf(Props(new MyActor()))
}

class Closer(body: => Any) {
  private var counter = 0
  private var active = true
  
  def count = synchronized { counter}
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
