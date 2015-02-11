package com.uebercomputing.mailparser.enronfiles

import java.io
import java.io
import java.nio
import java.io.FileInputStream
import java.nio.file.Path
import java.nio.file.Files
import java.io.InputStream
import java.io.File

/**
 * Utility for handling file operations. This is only necessary because of the Enron files ending
 * with a dot. This makes the standard file manipulations not compatible with Windows without extra work.
 */
trait FileManager {
  def isRegularFile(path: Path): Boolean
  def inputStream(path: Path): InputStream
}
object FileManager {
  def apply() = if (isWindows) WindowsFileManager else DefaultFileManager
  private def isWindows() = System.getProperty("os.name").toLowerCase.contains("windows")
}

/**
 * Handles files that end with a dot on Windows.
 */
object WindowsFileManager extends FileManager {

  private val dotFilePrefix = "\\\\?\\"

  override def isRegularFile(path: Path) =
    if (isDotFile(path)) {
      val f = new File(toDotPath(path))
      f.exists && f.isFile
    } else
      DefaultFileManager.isRegularFile(path)

  override def inputStream(path: Path) =
    if (isDotFile(path))
      new FileInputStream(toDotPath(path))
    else
      DefaultFileManager.inputStream(path)

  private def isDotFile(path: Path) = path.toString.endsWith(".")

  private def toDotPath(path: Path) = dotFilePrefix + path.toAbsolutePath.toString
}

/**
 * Handles file operations for most systems and files.
 */
object DefaultFileManager extends FileManager {
  override def inputStream(path: Path) = Files.newInputStream(path)
  override def isRegularFile(path: Path) = Files.isRegularFile(path)
}