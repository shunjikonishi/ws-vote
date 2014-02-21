package models

import akka.actor.ActorRef
import akka.actor.Actor
import akka.actor.Props

import play.api.Logger
import play.api.libs.json.Json
import play.api.libs.iteratee.Iteratee
import play.api.libs.iteratee.Enumerator
import scala.concurrent.duration.DurationInt
//import play.api.libs.iteratee.Concurrent
import play.api.libs.concurrent.Akka

import java.util.Date

import play.api.Play
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import flect.redis.RedisService
import flect.redis.Room
import flect.redis.RoomHandler

object MyRedisService extends RedisService(Play.configuration.getString("redis.uri").get)

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
            if (setting.roundNumber > 0 && (n % setting.roundNumber) == 0) {
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

object VoteRoom extends RoomManager(MyRedisService) {
  val appName = "Vote!"
  
  val defaultSetting = RoomSetting(
    name="default",
    title="Vote!",
    message="お好きな色を推してください",
    buttons=List(
      Button("1", "赤", "ff0000"),
      Button("2", "黄", "ffff00"),
      Button("3", "ピンク", "ff69b4"),
      Button("4", "緑", "00ff7f"),
      Button("5", "紫", "9400d3")
    ),
    roundNumber = 1000
  )
  
}

