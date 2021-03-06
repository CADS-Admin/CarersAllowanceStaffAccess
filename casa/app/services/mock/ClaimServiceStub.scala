package services.mock

import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTime, LocalDate, LocalTime}
import play.api.Logger
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsArray, JsBoolean, JsNumber, JsObject, _}
import services.{RenderServiceComponent, ClaimService}

import scala.util.Random



/**
 * I exist so that the app can be run up without the need for external services running
 * I can also be used as a base class for mocking
 */
class ClaimServiceStub extends ClaimService with RenderServiceComponent {

  override def getClaims(originTag: String, date: LocalDate): Option[JsArray] = {
    Logger.warn("Using stub claim service.")
    Some(Json.toJson(listOfClaimSummaries.filter {
      _.claimDateTime.toLocalDate == date
    }.filter {
      _.status != "completed"
    }).asInstanceOf[JsArray])
  }

  override def claimsFiltered(originTag: String, date: LocalDate, status: String): Option[JsArray] = {
    Logger.warn("Using stub claim service.")
    Some(Json.toJson(listOfClaimSummaries.filter {
      _.status == status
    }.filter {
      _.claimDateTime.toLocalDate == date
    }).asInstanceOf[JsArray])
  }

  override def getCircs(originTag: String, date: LocalDate): Option[JsArray] = {
    Logger.warn("Using stub claim service.")
    Some(Json.toJson(listOfCircsSummaries.filter {
      _.claimDateTime.toLocalDate == date
    }.filter {
      _.status != "completed"
    }).asInstanceOf[JsArray])
  }

  override def claimsFilteredBySurname(originTag: String, date: LocalDate, sortBy: String): Option[JsArray] = {
    Logger.warn("Using stub claim service.")

    val regex = if (sortBy == "atom") "[a-m].*".r else "[n-z].*".r

    val listSum = listOfClaimSummaries.filter {
      _.claimDateTime.toLocalDate == date
    }.filter {
      _.status != "completed"
    }

    Some(Json.toJson(listSum dropWhile (c => regex.findAllMatchIn(c.surname).isEmpty)).asInstanceOf[JsArray])
  }

  override def fullClaim(transactionId: String, originTag: String): Option[JsValue] = {
    Logger.warn("Using stub claim service.")
    Some(Json.parse("{}"))
  }

  override def updateClaim(transactionId: String, status: String): JsBoolean = {
    Logger.warn("Using stub claim service.")
    listOfClaimSummaries.filter {
      _.transactionId == transactionId
    }.headOption match {
      case Some(claimFound) if claimFound.status != status && claimFound.status != "completed" =>
        val updatedClaim = claimFound.copy(status = status)
        listOfClaimSummaries = updatedClaim +: listOfClaimSummaries.filterNot {
          _.transactionId == transactionId
        }
        JsBoolean(true)
      case _ => JsBoolean(false)
    }
  }

  override def claimNumbersFiltered(originTag: String, status: String*): JsObject = {
    Logger.warn("Using stub claim service.")
    var daysMap = Map.empty[LocalDate, Int]

    listOfClaimSummaries.foreach(cs => {
      val localDate = cs.claimDateTime.toLocalDate
      val currentCount = daysMap.get(localDate).getOrElse(0)
      if (!daysMap.exists(t => t._1 == localDate)) daysMap = daysMap + (localDate -> 0)
      if (status.exists(_ == cs.status)) {
        daysMap = daysMap + (localDate -> (currentCount + 1))
      }
    })

    def dateToString(date: LocalDate) = {
      DateTimeFormat.forPattern("ddMMyyyy").print(date)
    }

    JsObject(daysMap.map(t => dateToString(t._1) -> JsNumber(t._2)).toSeq)
  }


  override def countOfClaimsForTabs(originTag: String, date: LocalDate):JsObject = {
    Logger.warn("Using stub claim service.")
    Json.toJson(
      Map("counts"-> Json.toJson(Map("atom" -> Json.toJson(countOfClaimsForTabs(date,"atom")),
        "ntoz" -> Json.toJson(countOfClaimsForTabs(date,"ntoz")),
        "circs" -> Json.toJson(countOfCircsForTabs(date)))))
    ).as[JsObject]
  }

  private def countOfClaimsForTabs(date:LocalDate,sortBy:String):Long = {
    val regex = if(sortBy=="atom") "[a-m].*".r else "[n-z].*".r

    listOfClaimSummaries.filter{ _.claimDateTime.toLocalDate.compareTo(date) == 0}.filter{_.status != "completed"}
      .foldLeft(0l)((count,cs) => if(!regex.findAllMatchIn(cs.surname).equals(None)) count +1 else count)

  }

  private def countOfCircsForTabs(date:LocalDate):Long = {
    val circsSummaryList = listOfCircsSummaries.filter{_.claimDateTime.toLocalDate == date}.filter{_.status != "completed"}
    circsSummaryList.size
  }

  override def buildClaimHtml(transactionId: String, originTag: String): Option[String] = {
    Logger.warn("Using stub claim service.")
    if (listOfClaimSummaries.exists(_.transactionId == transactionId)) {
      this.updateClaim(transactionId, "viewed")

      Some(scala.io.Source.fromURL(getClass.getResource("/facadeHtml.txt")).mkString
        .replace("<title></title>", s"<title>Claim $transactionId</title>")
        .replace("</body>", "<script>window.onload = function(){window.opener.location.reload(false);};</script></body>"))

    } else None
  }

  implicit val claimSummary: Writes[ClaimSummary] = (
    (JsPath \ "transactionId").write[String] and
      (JsPath \ "claimType").write[String] and
      (JsPath \ "nino").write[String] and
      (JsPath \ "forename").write[String] and
      (JsPath \ "surname").write[String] and
      (JsPath \ "claimDateTime").write[DateTime] and
      (JsPath \ "status").write[String]
    )(unlift(ClaimSummary.unapply))

  def specialDaysRec(n: Int, today: LocalDate, localDates: Seq[LocalDate]): Seq[LocalDate] = {
    if (n == 0) localDates
    else {
      val day = today.minusDays(1)
      specialDaysRec(n - 1, day, day +: localDates)
    }
  }

  val daysToReport = specialDaysRec(8, new LocalDate plusDays 1, Seq())

  val availableStatuses = Seq("received", "viewed", "completed")

  val today = daysToReport(daysToReport.size - 1).toDateTime(new LocalTime(Random.nextInt(23), Random.nextInt(59)))

  val transIdPrefix = "1111"

  val mandatoryClaims = Seq(ClaimSummary(s"${transIdPrefix}070","claim", f"AB${Random.nextInt(999999)}%06dD", "aname", "asurname", today, "received"),
    ClaimSummary(s"${transIdPrefix}071","claim", f"AB${Random.nextInt(999999)}%06dD", "lname", "lsurname", today, "viewed"),
    ClaimSummary(s"${transIdPrefix}072","claim", f"AB${Random.nextInt(999999)}%06dD", "mname", "msurname", today, "completed"))

  // this blows up when the period covered includes the spring clock transition, as 1am-2am are not valid times on that day
  // so limit it to 2am +
  def dayToReport = daysToReport(Math.abs(Random.nextInt()) % daysToReport.size).toDateTime(new LocalTime(2 + Random.nextInt(21), Random.nextInt(59)))

  val alphaLetters = Seq("a","b","c","d","e","f","g","h","i","j","k","l","m","n","o","p","q","r","s","t","u","v","x","y","z","a","b","c","d","e","f","g","h","i","j","k","l","m","n","o","p","q","r","s","t","u","v","x","y","z","a","b","c","d","e","f","g","h","i","j","k","l","m","n","o","p","q","r","s","t","u","v","x","y","z")

  val randomList: List[ClaimSummary] =
    (for(i <- 1 to 70) yield {
      val statusToUse = availableStatuses(Math.abs(Random.nextInt()) % availableStatuses.size)
      ClaimSummary(f"99990$i%02d",if (Random.nextFloat() > 0.5f) "claim" else "circs", f"AB${Random.nextInt(999999)}%06dD", f"${alphaLetters(i)}name",  f"${alphaLetters(i)}surname", dayToReport, statusToUse)
    })(collection.breakOut)

  val listOfCircsSummaries: List[ClaimSummary] =
    (for(i <- 1 to 70) yield {
      val statusToUse = availableStatuses(Math.abs(Random.nextInt()) % availableStatuses.size)
      ClaimSummary(f"99990$i%02d", "circs", f"AB${Random.nextInt(999999)}%06dD", f"${alphaLetters(i)}name",  f"${alphaLetters(i)}surname", dayToReport, statusToUse)
    })(collection.breakOut)

  var listOfClaimSummaries = mandatoryClaims ++ randomList


  override def getOldClaims(originTag: String): Option[JsArray] = {
    Logger.warn("Using stub claim service.")
    val oldClaims = listOfClaimSummaries.filter(_.claimDateTime.isBefore(new DateTime().minus(20)))
    val dateFormat = DateTimeFormat.forPattern("ddMMyyyy")

    if (oldClaims.size > 0) {
      val header = Seq("claimType", "nino", "forename", "surname", "claimDateTime", "status")
      val values = oldClaims.map(c => Seq(c.claimType, c.nino, c.forename, c.surname, dateFormat.print(c.claimDateTime), c.status))

      Some(Json.toJson(header +: values).as[JsArray])
    } else {
      None
    }
  }

  override def purgeOldClaims(originTag: String): JsBoolean = {
    Logger.warn("Using stub claim service.")
    val newList = listOfClaimSummaries.filterNot(_.claimDateTime.isBefore(new DateTime().minus(20)))
    val result = newList.size != listOfClaimSummaries.size
    listOfClaimSummaries = newList

    JsBoolean(result)
  }
  case class ClaimSummary(transactionId: String, claimType: String, nino: String, forename: String, surname: String, claimDateTime: DateTime, status: String)
}


