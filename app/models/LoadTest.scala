package models;

import play.Logger
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI

class LoadTest(uri: URI, setting: RoomSetting) {
  
  def run(threads: Int, count: Int) {
    val loopCount = threads / setting.buttons.size
    val list = for (i <- 1 to loopCount) yield {
      setting.buttons.map { b =>
        new LoadTestThread(uri, b.key, count)
      }
    }
    list.toList.flatten.foreach(_.start)
  }
  
}

class LoadTestThread(uri: URI, msg: String, count: Int) extends Thread {
  
  override def run: Unit = {
    val client = new LoadTestWebSocket(uri)
    try {
      client.connectBlocking
      for (i <- 1 to count) {
        client.send(msg)
        Thread.sleep(60)
      }
    } finally {
      client.close
    }
  }
}

class LoadTestWebSocket(uri: URI) extends WebSocketClient(uri) {
  
  def onOpen(sh: ServerHandshake): Unit = {
    Logger.info(s"onOpen: $uri")
  }
  
  def onMessage(message: String): Unit = {
    Logger.info(s"onMessage: $message")
  }
  
  def onClose(code: Int, reason: String, remote: Boolean): Unit = {
    Logger.info(s"onClose: $code, $reason, $remote")
  }
  
  def onError(ex: Exception): Unit = {
    Logger.error(s"onError: ex", ex)
  }

}
