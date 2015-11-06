package examples.spray.rest

import examples.spray.domain._
import spray.httpx.SprayJsonSupport
import spray.json.DefaultJsonProtocol

trait AllMyBloodyFormats extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val accountingMethodFormat = jsonFormat1(AccountingMethod.apply)
  implicit val orgIdFormat = jsonFormat1(OrganizationNumber)
  implicit val financialYearFormat = jsonFormat1(FinancialYear)
  implicit val organizationFormat = jsonFormat2(Organization)
  implicit val organizationCandidateFormat = jsonFormat1(OrganizationCandidate)
}
