package com.uebercomputing.mailparser.enronfiles

import java.io
import java.io
import java.nio
import java.io.FileInputStream
import java.nio.file.Path
import java.nio.file.Files
import java.io.InputStream
import java.io.File
import com.uebercomputing.io.PathUtils

/**
 * Utility for handling file operations. This is only necessary because of the Enron files ending
 * with a dot. This makes the standard file manipulations not compatible with Windows without extra work.
 */
trait FileManager {
  /**
   * Checks if a file exists and is a normal file.
   */
  def isRegularFile(path: Path): Boolean
  
  /**
   * Checks if the given path is a readable directory.
   */
  def isReadableDir(path: Path): Boolean
  
  /**
   * Gets an InputStream that can read from the given path.
   */
  def inputStream(path: Path): InputStream
  
  /**
   * Lists the paths under this directory path.
   */
  def listChildPaths(path: Path): List[Path]
}
object FileManager {
  def apply() = if (isWindows) new WindowsFileManager else new DefaultFileManager
  private def isWindows() = System.getProperty("os.name").toLowerCase.contains("windows")
}

/**
 * Handles file operations for most systems and files.
 */
class DefaultFileManager extends FileManager {
  override def inputStream(path: Path) = Files.newInputStream(path)
  override def isRegularFile(path: Path) = Files.isRegularFile(path)
  override def listChildPaths(path: Path) = PathUtils.listChildPaths(path)
  override def isReadableDir(path: Path) = Files.isDirectory(path) && Files.isReadable(path)
}

/**
 * Handles files that end with a dot on Windows.
 */
class WindowsFileManager extends DefaultFileManager {

  private val dotFilePrefix = "\\\\?\\"

  override def isRegularFile(path: Path) =
    if (isDotFile(path)) {
      val f = new File(toDotPath(path))
      f.exists && f.isFile
    } else
      super.isRegularFile(path)

  override def inputStream(path: Path) =
    if (isDotFile(path))
      new FileInputStream(toDotPath(path))
    else
      super.inputStream(path)

  private def isDotFile(path: Path) = path.toString.endsWith(".")

  private def toDotPath(path: Path) = dotFilePrefix + path.toAbsolutePath.toString
}
