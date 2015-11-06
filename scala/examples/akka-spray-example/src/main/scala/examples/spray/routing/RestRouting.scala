package examples.spray.routing

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.pattern.ask
import akka.util.Timeout
import examples.spray.aggregate.AggregateManager
import examples.spray.aggregate.OrganizationAggregate
import examples.spray.aggregate.OrganizationAggregate.AddFinancialYear
import examples.spray.aggregate.OrganizationAggregate.CreateOrganization
import examples.spray.aggregate.OrganizationAggregate.GetOrganization
import examples.spray.domain._
import examples.spray.rest.AllMyBloodyFormats
import spray.http.MediaTypes._
import spray.routing.HttpService

import scala.concurrent.Future
import scala.concurrent.duration._

class RestRouting extends HttpService with Actor with ActorLogging with AggregateManager with AllMyBloodyFormats {
  implicit def actorRefFactory = context

  implicit val timeout = Timeout(5 seconds)
  implicit val executionContext = context.dispatcher

  def receive = runRoute(
    pathPrefix("api") {
      respondWithMediaType(`application/json`) {
        userRoutes
      }
    }
  )

  val userRoutes = {
    path("organizations") {
      post {
        entity(as[OrganizationCandidate]) { org =>
          val id = OrganizationNumber(org.orgNumber)
          val child = findOrganizationActor(id)
          val potentialOrg = (child ? CreateOrganization(id)).mapTo[Option[Organization]]
          complete(potentialOrg)
        }
      }
    } ~
      path("organizations" / Segment) { orgNumber =>
        get {
          val id = OrganizationNumber(orgNumber)
          val potentialOrg = getOrganization(id)
          complete(potentialOrg)
        }
      } ~
      path("financialyears" / Segment) { orgNumber =>
        get {
          val id = OrganizationNumber(orgNumber)
          val potentialOrg = getOrganization(id)
          val financialYears = potentialOrg.map({
            case Some(o) =>
              o.financialYears
            case None =>
              List.empty
          })

          complete(financialYears)
        } ~
          post {
            entity(as[FinancialYear]) { financialYear =>
              val id = OrganizationNumber(orgNumber)
              val child = findOrganizationActor(id)
              val potentialFinancialYear = (child ? AddFinancialYear(financialYear.accountingMethod)).mapTo[Option[FinancialYear]]
              complete(potentialFinancialYear)
            }
          }
      }
  }

  override def aggregateProps(id: String): Props = {
    val reg = """org\-(.*)""".r

    id match {
      case reg(orgId) =>
        OrganizationAggregate.props(OrganizationNumber(orgId))
    }
  }

  /**
   * Retrieves the organization
   *
   * @param id
   * @return
   */
  private def getOrganization(id: OrganizationNumber): Future[Option[Organization]] = {
    val child = findOrganizationActor(id)
    (child ? GetOrganization).mapTo[Option[Organization]]
  }

  private def findOrganizationActor(id: OrganizationNumber) = findOrCreate("org-" + id.value)
}
