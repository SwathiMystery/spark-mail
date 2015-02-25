package com.uebercomputing.mailparser.enronfiles

import com.uebercomputing.test.UnitTest
import java.io.InputStream
import java.io.File
import java.nio.file.Files
import java.util.Map
import com.uebercomputing.mailparser.enronfiles.AvroMessageProcessor
import com.uebercomputing.mailparser.enronfiles.MessageParser
import com.uebercomputing.mailparser.enronfiles.MessageProcessor
import com.uebercomputing.mailrecord.MailRecord
import org.apache.commons.io.IOUtils
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FSDataOutputStream
import org.apache.hadoop.fs.FileStatus
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import resource.managed
import scala.io.Source
import org.scalatest.fixture.FunSuite
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import scala.util.Try

@RunWith(classOf[JUnitRunner])
class AvroMessageProcessorTest extends FunSuite {

  case class AvroFileTestInfo(val fileSystem: FileSystem, val avroFilePath: Path, val out: FSDataOutputStream)

  val TestFileUrl = "/enron/maildir/neal-s/all_documents/99.txt"

  private var tempFile: java.nio.file.Path = _

  type FixtureParam = AvroFileTestInfo

  override def withFixture(test: OneArgTest) = {
    tempFile = Files.createTempFile("test", ".avro")
    val conf = new Configuration()
    conf.set("default.fsName", "file:///")
    val avroFileUri = tempFile.toFile().getAbsolutePath
    println(avroFileUri)
    val fileSys = FileSystem.get(conf)
    val avroFilePath = new Path(avroFileUri)
    var out: FSDataOutputStream = null

    try {
      out = fileSys.create(avroFilePath)
      test(AvroFileTestInfo(fileSys, avroFilePath, out))
    } finally {
      IOUtils.closeQuietly(out)
      Try(Files.deleteIfExists(tempFile)).getOrElse(println(s"WARN: Could not delete $tempFile"))
    }
  }

  test("sunny day conversion from neal-s 99.") { testInfo =>
    val processor = new MessageProcessor with AvroMessageProcessor
    processor.open(testInfo.out)
    for (testFileIn <- managed(getClass().getResourceAsStream(TestFileUrl))) {
      val msgSrc = Source.fromInputStream(testFileIn)
      val fileSystemMetadata = getFileSystemMetadata(TestFileUrl)
      val mailRecord = processor.process(fileSystemMetadata, msgSrc)

      val mailFields = mailRecord.getMailFields()
      val actualFilename = mailFields.get(AvroMessageProcessor.FileName)
      assert(fileSystemMetadata.fileName === actualFilename.toString())

      val actualFolderName = mailFields.get(AvroMessageProcessor.FolderName)
      assert(fileSystemMetadata.folderName === actualFolderName.toString())

      val actualUserName = mailFields.get(AvroMessageProcessor.UserName)
      assert(fileSystemMetadata.userName === actualUserName.toString())

      assert("<19546475.1075853053633.JavaMail.evans@thyme>" === mailFields.get(MessageParser.MsgId).toString())
      assert("chris.sebesta@enron.com" === mailRecord.getFrom().toString())
      assert("RE: Alliant Energy - IES Utilities dispute re: Poi 2870 - Cherokee #1 TBS - July 99 thru April 2001"
        === mailRecord.getSubject().toString())

    }
    processor.close()

    val fileSys = testInfo.fileSystem
    assert(fileSys.exists(testInfo.avroFilePath))
    val fileStatus = fileSys.getFileStatus(testInfo.avroFilePath)
    val len = fileStatus.getLen()
    assert(len > 0)
  }

  def getFileSystemMetadata(path: String): FileSystemMetadata = {
    val pathParts = path.split("/")
    val userName = pathParts(2)
    val folderName = pathParts(3)
    val fileName = pathParts(4)
    FileSystemMetadata(userName, folderName,
      fileName)
  }
}
