package com.uebercomputing.mailparser.enronfiles

import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import org.apache.logging.log4j.LogManager
import com.google.common.hash.Hashing
import com.google.common.base.Charsets

object MessageUtils {

  private val DateFormatter = DateTimeFormat.forPattern("EEE, dd MMM yyyy HH:mm:ss Z (z)").withZoneUTC()
  private val Logger = LogManager.getLogger(classOf[MailDirectoryProcessor])

  def parseDateAsUtcEpoch(dateStr: String): Long = {
    try {
      DateFormatter.parseMillis(dateStr)
    } catch {
      case NonFatal(e) => throw new ParseException(s"Bad date: $dateStr", e)
    }
  }

  def parseCommaSeparated(commaSeparated: String): java.util.List[String] = {
    commaSeparated.split(",").toList.asJava
  }

  def getMd5Hash(message: String): String = {
    val hashString = Hashing.md5().hashString(message, Charsets.UTF_8)
    val md5Hash = hashString.toString()
    md5Hash
  }
}
