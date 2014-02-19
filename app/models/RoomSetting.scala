package models

import java.util.Date
import play.api.libs.json.Json

case class Button(key: String, text: String, color: String)
case class RoomSetting(
  name: String, 
  title: String, 
  message: String, 
  buttons: List[Button],
  viewLimit: Option[Date] = None,
  voteLimit: Option[Date] = None,
  roundNumber: Int = 1000
) {
  def buttonText(key: String) = {
    buttons.find(_.key == key).map(_.text)
  }
  
  def canView(d: Date) = viewLimit.map(d.getTime < _.getTime).getOrElse(true)
  def timeLimit = voteLimit.map { d =>
    val n = d.getTime - System.currentTimeMillis
    if (n <= 0) {
      0
    } else {
      n / 1000
    }
  }.getOrElse(-1L)

  def toJson = {
  	Json.toJson(this)(RoomSetting.settingFormat).toString
 	} 
}

object RoomSetting {

	implicit val buttonFormat = Json.format[Button]
  implicit val settingFormat = Json.format[RoomSetting]

  def fromJson(str: String) = Json.fromJson[RoomSetting](Json.parse(str)).get
}