package com.uebercomputing.pst

import scala.collection.mutable
import com.uebercomputing.mailrecord.MailRecordWriter
import com.uebercomputing.mailrecord.MailRecord
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.apache.log4j.Logger

object MailRecordByDateWriter {

  def getPstAvroFileName(pstFilePath: String): String = {
    val pstFileName = getSubstringAfterLastForwardSlash(pstFilePath)
    val pstAvroFileName = replaceExistingFileExtensionWithDotAvro(pstFileName)
    pstAvroFileName
  }

  def getSubstringAfterLastForwardSlash(pstFilePath: String): String = {
    if (pstFilePath.endsWith("/")) throw new RuntimeException(s"PST file path must not end with / ${pstFilePath}")
    val lastForwardSlash = pstFilePath.lastIndexOf("/")
    if (lastForwardSlash != -1) {
      pstFilePath.substring(lastForwardSlash + 1)
    } else {
      pstFilePath
    }
  }

  def replaceExistingFileExtensionWithDotAvro(pstFileName: String): String = {
    val lastDot = pstFileName.lastIndexOf(".")
    if (lastDot != -1) {
      pstFileName.take(lastDot) + ".avro"
    } else {
      pstFileName + ".avro"
    }
  }

}

class MailRecordByDateWriter(hadoopConf: Configuration, datePartitionType: DatePartitionType, rootPath: String, pstFilePath: String) {

  val LOGGER = Logger.getLogger(this.getClass)

  private val directoryToMailRecordWriterMap = mutable.Map[String, MailRecordWriter]()

  val fileSystem = FileSystem.get(hadoopConf)

  val pstAvroFileName = MailRecordByDateWriter.getPstAvroFileName(pstFilePath)

  def writeMailRecord(mailRecord: MailRecord): Boolean = {
    try {
      val utcOffsetInMillis = mailRecord.getDateUtcEpoch
      val datePartitions = DatePartitioner.getDatePartion(datePartitionType, utcOffsetInMillis)
      val datePartitionsKey = datePartitions.mkString("/")
      val recordWriter = getMailRecordWriter(datePartitionsKey)
      recordWriter.append(mailRecord)
      true
    } catch {
      case e: Exception => {
        LOGGER.error(s"Unable to write mail record ${mailRecord.getUuid} due to ${e}")
        false
      }
    }
  }

  /**
   * Must call to close all associated MailRecordWriters.
   */
  def closeAllWriters(): Unit = {
    for ((key, mailRecordWriter) <- directoryToMailRecordWriterMap) {
      mailRecordWriter.close()
    }
  }

  def getMailRecordWriter(datePartitionsKey: String): MailRecordWriter = {
    if (directoryToMailRecordWriterMap.contains(datePartitionsKey)) {
      val recordWriter = directoryToMailRecordWriterMap(datePartitionsKey)
      recordWriter
    } else {
      createAndCacheMailRecordWriter(datePartitionsKey)
    }
  }

  def createAndCacheMailRecordWriter(datePartitionsKey: String): MailRecordWriter = {
    val outputPathStr = getOutputPathString(datePartitionsKey)
    val outputPath = new Path(outputPathStr)
    if (fileSystem.exists(outputPath)) {
      throw new RuntimeException(s"Path already exists ${outputPath.toString()}")
    }
    val outputStream = fileSystem.create(outputPath)
    val recordWriter = new MailRecordWriter()
    recordWriter.open(outputStream)
    directoryToMailRecordWriterMap(datePartitionsKey) = recordWriter
    recordWriter
  }

  def getOutputPathString(datePartitionsKey: String): String = {
    val parts = List(rootPath, datePartitionsKey, pstAvroFileName)
    parts.mkString("/")
  }
}
