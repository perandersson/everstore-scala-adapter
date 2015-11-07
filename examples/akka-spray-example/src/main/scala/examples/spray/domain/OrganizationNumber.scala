package examples.spray.domain


case class OrganizationNumber(value: String) {
  require(value != null && !value.isEmpty, "The value cannot be null or empty")
  require(value.matches("""\d{6}-\d{4}"""), "The value must match the pattern DDDDDD-DDDD")
}
