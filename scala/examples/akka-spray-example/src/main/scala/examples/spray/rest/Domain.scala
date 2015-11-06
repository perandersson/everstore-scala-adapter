package examples.spray.rest

trait RestMessage

case class Error(message: String) extends RestMessage

case class Validation(message: String) extends RestMessage
