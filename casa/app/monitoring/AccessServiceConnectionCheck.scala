package monitoring

import app.ConfigProperties._
import gov.dwp.carers.CADSHealthCheck
import gov.dwp.carers.CADSHealthCheck.Result
import play.api.http.Status
import utils.HttpWrapper
import scala.language.{implicitConversions, postfixOps}

/**
 * Ping ClaimService to check connection
 */
class AccessServiceConnectionCheck extends CADSHealthCheck(s"${getStringProperty("application.name", throwError = false)}", getStringProperty("application.version", throwError = false).takeWhile(_ != '-')) {
  override def check(): Result = {
    val url = getStringProperty("accessControlServiceUrl") + "/ping"
    val timeout = getIntProperty("ac.timeout")
    val httpWrapper = new HttpWrapper
    val response = httpWrapper.get(url, timeout)
    response.getStatus match {
      case Status.OK =>
        Result.healthy
      case status@_ =>
        Result.unhealthy(s"Access Control Service ping failed: ${status} for $url with timeout $timeout.")
    }
  }
}
