package examples.spray.domain

case class AccountingMethod(name: String)

object AccountingMethod {
  val AccrualBasisAccounting = AccountingMethod("AccrualBasisAccounting")
  val CashBasisAccounting = AccountingMethod("CashBasisAccounting")
}
