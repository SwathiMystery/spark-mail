package com.uebercomputing.mailparser.enronfiles

import java.nio.file.Path

import scala.io.Source

import org.apache.logging.log4j.LogManager

import com.uebercomputing.io.Utf8Codec

import resource.managed

abstract class MailDirectoryProcessor(mailDirectory: Path, userNamesToProcess: List[String] = Nil) extends MessageProcessor {

  private val Logger = LogManager.getLogger(classOf[MailDirectoryProcessor])

  private val fileManager = FileManager()

  def processMailDirectory(): Int = {
    var mailMessagesProcessedCount = 0
    val userDirectories = fileManager.listChildPaths(mailDirectory)
    for (userDirectory <- userDirectories) {
      if (fileManager.isReadableDir(userDirectory)) {
        mailMessagesProcessedCount = processUserDirectory(userDirectory, mailMessagesProcessedCount)
      } else {
        val errMsg = s"Mail directory layout (${userDirectory} violates assumption: " +
          "Mail directory should contain only user directories."
        throw new ParseException(errMsg)
      }
    }
    mailMessagesProcessedCount
  }

  def processUserDirectory(userDirectory: Path, processedCountSoFar: Int): Int =
    {
      var processedCount = processedCountSoFar
      if (userNamesToProcess == Nil || isUserDirectoryToBeProcessed(userDirectory)) {
        val userName = userDirectory.getFileName().toString()
        val folders = fileManager.listChildPaths(userDirectory)
        for (folder <- folders) {
          if (fileManager.isReadableDir(folder)) {
            val parentFolderName = None
            processedCount = processFolder(userName, parentFolderName,
              folder, processedCount)
          }
        }
      }
      processedCount
    }

  def isUserDirectoryToBeProcessed(userDirectory: Path): Boolean = {
    val userName = userDirectory.getFileName().toString()
    userNamesToProcess.contains(userName)
  }

  def processFolder(userName: String, parentFolderName: Option[String],
    folder: Path, processCountSoFar: Int): Int = {
    val folderName = getFolderName(parentFolderName, folder)
    var processedCount = processCountSoFar
    val mailsOrSubdirs = fileManager.listChildPaths(folder)
    for (mailOrSubdir <- mailsOrSubdirs) {
      if (fileManager.isRegularFile(mailOrSubdir)) {
        processedCount = processFile(userName, folderName, mailOrSubdir, processedCount)
      } else {
        processedCount = processFolder(userName, Some(folderName), mailOrSubdir, processedCount)
      }
      if (processedCount % 10000 == 0) {
        println()
        println(s"Processed count now: $processedCount")
      } else if (processedCount % 100 == 0) {
        print(".")
      }
    }
    processedCount
  }

  def processFile(userName: String, folderName: String, mailFile: Path, processedCount: Int): Int = {
    val fileName = mailFile.getFileName().toString()
    for (mailIn <- managed(fileManager.inputStream(mailFile))) {
      try {
        val fileSystemMetadata = FileSystemMetadata(userName, folderName,
          fileName)
        val utf8Codec = Utf8Codec.codec
        process(fileSystemMetadata, Source.fromInputStream(mailIn)(utf8Codec))
      } catch {
        case e: Exception =>
          // scalastyle:off
          val msg = s"Unable to process ${mailDirectory.toAbsolutePath().toString()}/${userName}/${folderName}/${fileName} due to $e"
          Logger.warn(msg)
          // scalastyle:on
          throw new ParseException(msg)
      }
    }
    processedCount + 1
  }

  def getFolderName(parentFolderNameOpt: Option[String], folder: Path): String = {
    parentFolderNameOpt match {
      case Some(parentFolder) => s"${parentFolder}/${folder.getFileName().toString()}"
      case None => folder.getFileName().toString()
    }
  }
}
