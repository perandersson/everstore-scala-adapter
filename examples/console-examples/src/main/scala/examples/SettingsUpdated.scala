package examples

trait SettingsEvent

case class SettingsUpdated(email: String, message: String, housework: Boolean, firstInvoiceNumber: Long) extends SettingsEvent
