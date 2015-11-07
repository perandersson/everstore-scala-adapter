package examples.spray.aggregate

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import everstore.adapter.akka.extension.PersistenceActor
import everstore.adapter.akka.extension.PersistenceActor.GetState
import everstore.adapter.akka.extension.PersistenceActor.UpdateState
import examples.spray.aggregate.OrganizationAggregate.GetOrganization
import examples.spray.aggregate.OrganizationAggregate._
import examples.spray.domain.AccountingMethod
import examples.spray.domain.FinancialYear
import examples.spray.domain.Organization
import examples.spray.domain.OrganizationNumber

import scala.concurrent.ExecutionContext.Implicits.global

class OrganizationAggregate(id: OrganizationNumber)
  extends Actor with PersistenceActor[Organization] with ActorLogging {

  override val journalName = s"/organizations/${id.value}"

  override def receiveCommand: Receive = {
    case GetOrganization =>
      self forward GetState
    case CreateOrganization(orgNumber) =>
      val senderRef = sender()
      val selfRef = self
      openTransaction()
        .foreach(implicit transaction => {
          persist(OrganizationCreated(orgNumber.value))
          commit() map {
            case commit if commit.success =>
              val newOrg = Organization(orgNumber, Seq.empty)
              selfRef ! UpdateState(newOrg, transaction.size)
              senderRef ! Option(newOrg)
            case commit if !commit.success =>
              log.error(s"Could not create organization: ${orgNumber}. Commit failed")
              senderRef ! None
          }
        })
    case AddFinancialYear(accountingMethod) =>
      val senderRef = sender()
      openTransaction()
        .foreach(implicit transaction => {
          persist(FinancialYearAdded(accountingMethod))
          commit() map {
            case commit if commit.success =>
              val financialYear = FinancialYear(accountingMethod)
              senderRef ! Option(financialYear)
            case commit if commit.success =>
              log.error(s"Could not add financial year: ${accountingMethod}. Commit failed")
              senderRef ! None
          }
        })
  }

  override def processEvent(state: State, event: AnyRef): State = event match {
    case OrganizationCreated(orgNumber) =>
      Organization(OrganizationNumber(orgNumber), Seq.empty)
    case FinancialYearAdded(accountingMethod) =>
      val financialYear = FinancialYear(accountingMethod)
      state.copy(financialYears = state.financialYears :+ financialYear)
  }
}

object OrganizationAggregate {

  def props(id: OrganizationNumber) = Props(new OrganizationAggregate(id))

  sealed trait Message

  case object GetOrganization extends Message

  case class CreateOrganization(orgNumber: OrganizationNumber) extends Message

  case class AddFinancialYear(accountingMethod: AccountingMethod) extends Message

  trait OrgEvent

  case class OrganizationCreated(orgNumber: String) extends OrgEvent

  trait FinancialYearEvent

  case class FinancialYearAdded(accountingMethod: AccountingMethod) extends FinancialYearEvent

}
