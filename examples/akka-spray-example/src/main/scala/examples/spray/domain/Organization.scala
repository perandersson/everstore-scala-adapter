package examples.spray.domain

case class Organization(orgNumber: OrganizationNumber,
                        financialYears: Seq[FinancialYear])

case class OrganizationCandidate(orgNumber: String)
