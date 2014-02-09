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
import flect.redis.Room
import flect.redis.RoomHandler

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
  
  def canView(d: Date) = viewLimit.map(d.getTime < _.getTime).getOrElse(true)
  def timeLimit = voteLimit.map(d => (d.getTime - System.currentTimeMillis) / 1000).getOrElse(0L)
}

class VoteRoom(setting: RoomSetting, redis: RedisService) extends Room(setting.name, redis) {
  
  Logger.info("Create ChatRoom: " + setting.name)
  
  private val member_key = setting.name + "-members"
  private def voteKey(key: String) = setting.name + "#" + key
  
  case class Message(kind: String, key: String, count: Long)
  implicit val messageFormat = Json.format[Message]
  
  private def createMessage(kind: String, key: String, count: Long) = {
    val msg = new Message(kind, key, count)
    Json.toJson(msg).toString
  }
  
  private def sendMessage(kind: String, key: String, count: Long) {
    channel.send(createMessage(kind, key, count))
  }
  
  def join(clientId: String): (Iteratee[String,_], Enumerator[String]) = {
    Logger.info("Join to " + setting.name)
    val count = redis.withClient(_.incr(member_key))
    count.foreach(sendMessage("member", "join", _))
    val h = RoomHandler().clientMsg { msg =>
      msg match {
        case "###member###" =>
          val count = redis.withClient(_.get(member_key))
          count.map(n => createMessage("member", "now", n.toLong))
        case "###dummy###" =>
          None
        case _ =>
          val key = voteKey(msg)
          val count = redis.withClient(_.incr(key))
          count.foreach { n =>
            if ((n % 1000) == 0) {
              channel.outChannel.push(createMessage(clientId,  msg, n))
            }
          }
          count.map(n => createMessage("vote", msg, n.toLong))
      }
      /*
    }.redisMsg { msg =>
      Logger.info("test: " + msg)
      Some(msg)
      */
    }.disconnect { () =>
      val count = redis.withClient(_.decr(member_key))
      count.foreach(sendMessage("member", "quit", _))
      Logger.info("Quit from " + setting.name)
    }
    join(h)
  }
  
  def reset = {
    setting.buttons.foreach { b =>
      val key = voteKey(b.key)
      redis.withClient(_.del(key))
    }
  }
  
  override def close = {
    val cnt = memberCount
    if (cnt != 0) {
      redis.withClient(_.decrby(member_key, cnt))
    }
    super.close
    scheduler.cancel
  }
  
  val scheduler = Akka.system.scheduler.schedule(20 seconds, 20 seconds) {
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
    ),
    voteLimit = Some(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2014-02-14 07:00:00"))
  )
  
  private var settings = Map(defaultSetting.name -> defaultSetting)
  private var rooms = Map.empty[String, VoteRoom]
      
  def getSetting(name: String): Option[RoomSetting] = settings.get(name)
  
  def join(room: String, clientId: String): Future[(Iteratee[String,_], Enumerator[String])] = {
    (actor ? Join(room, clientId)).asInstanceOf[Future[(Iteratee[String,_], Enumerator[String])]]
  }
  
  def getRoom(name: String): VoteRoom = {
    getSetting(name).map { setting =>
      val room = rooms.get(name).filter(_.isActive)
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
  
  private val actor = Akka.system.actorOf(Props(new MyActor()))
  
  private sealed class Msg
  private case class Join(room: String, clientId: String)
  
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
    }
    
    
    override def postStop() = {
      Logger.info("!!! postStop !!!")
      rooms.values.filter(_.isActive).foreach(_.close)
      rooms = Map.empty[String, VoteRoom]
      MyRedisService.close
      super.postStop()
    }
  }
  
}

