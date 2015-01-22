package com.uebercomputing.pst

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import org.apache.hadoop.conf.Configuration
import com.pff.PSTFile
import org.joda.time.DateTime
import com.uebercomputing.time.DateUtils

/**
 * Invoke command line from spark-mail:
 * java -classpath pst-utils/target/pst-utils-0.9.0-SNAPSHOT-shaded.jar com.uebercomputing.pst.Main --pstDir /opt/rpm1/jebbush --avroOutDir /opt/rpm1/jebbush/avro
 *
 * --pstDir /opt/rpm1/jebbush
 * --avroOutDir /opt/rpm1/jebbush/avro
 *
 * Create small set of test avro files:
 * --pstDir src/test/resources/psts/enron1.pst
 * --avroOutDir (don't specify - uses /tmp/avro)
 * --overwrite true
 */
object Main {

  val PstDirArg = "--pstDir"
  val AvroOutDirArg = "--avroOutDir"
  val HadoopConfFileArg = "--hadoopConfFile"
  val OverwriteArg = "--overwrite"

  case class Config(pstDir: String = ".",
                    avroOutDir: String = PstConstants.TempDir + "/avro",
                    hadoopConfFileOpt: Option[String] = None,
                    overwrite: Boolean = false)

  def main(args: Array[String]): Unit = {
    val startTime = new DateTime()
    val p = parser()
    // parser.parse returns Option[C]
    p.parse(args, Config()) map { config =>
      val pstFiles = getPstFiles(new File(config.pstDir))
      val hadoopConf = config.hadoopConfFileOpt match {
        case Some(confFilePath) => {
          val conf = new Configuration()
          conf.addResource(confFilePath)
          conf
        }
        case None => getLocalHadoopConf()
      }
      val rootPath = config.avroOutDir
      for (pstFileLoc <- pstFiles) {
        val pstStartTime = new DateTime()
        val pstAbsolutePath = pstFileLoc.getAbsolutePath
        val pstFile = new PSTFile(pstFileLoc)
        val datePartitionType = DatePartitionType.PartitionByDay
        val mailRecordByDateWriter = new MailRecordByDateWriter(hadoopConf, datePartitionType, rootPath, pstAbsolutePath)
        PstFileToAvroProcessor.processPstFile(mailRecordByDateWriter, pstFile, pstAbsolutePath)
        mailRecordByDateWriter.closeAllWriters()
        val pstEndTime = new DateTime()
        println(s"Time to process ${pstAbsolutePath} was ${DateUtils.getTimeDifferenceToSeconds(pstStartTime, pstEndTime)}")
        println(s"Total time since start was ${DateUtils.getTimeDifferenceToSeconds(startTime, pstEndTime)}")
      }
    }
    val endTime = new DateTime()
    println(s"Total runtime: ${DateUtils.getTimeDifferenceToSeconds(startTime, endTime)}")
  }

  def getLocalHadoopConf(): Configuration = {
    val conf = new Configuration
    conf.set("fs.defaultFS", "file:///")
    conf.set("mapreduce.framework.name", "local")
    conf
  }

  def getPstFiles(pstDir: File): List[File] = {
    val pstFilter = new PstFileFilter()
    pstDir.listFiles(pstFilter).toList
  }

  def parser(): scopt.OptionParser[Config] = {
    new scopt.OptionParser[Config]("scopt") {

      head("scopt", "3.x")

      opt[String]("pstDir") optional () action { (pstDirArg, config) =>
        config.copy(pstDir = pstDirArg)
      } validate { x =>
        val path = Paths.get(x)
        if (Files.exists(path) && Files.isReadable(path) && Files.isDirectory(path)) success
        else failure("Option --pstDir must be readable directory")
      } text ("pstDir is String with relative or absolute location of mail dir.")

      //      opt[String]("pstPaths") optional () action { (pstPathsArg, config) =>
      //        config.copy(pstPaths = pstPathsArg.split(",").toList)
      //      } text ("pstPaths is an optional argument as comma-separated list of pst files to process.")

      opt[String]("avroOutDir") optional () action { (avroOutDirArg, config) =>
        config.copy(avroOutDir = avroOutDirArg)
      } text ("avroOutDir is String with relative or absolute location of root directory for avro output files.")

      opt[String]("hadoopConfFile") optional () action { (hadoopConfFileArg, config) =>
        config.copy(hadoopConfFileOpt = Some(hadoopConfFileArg))
      } text ("hadoopConfFile is String with relative or absolute location of Hadoop configuration file to specify file system to use (contains fs.defaultFS). Default is file:///")

      opt[Boolean]("overwrite") optional () action { (overwriteArg, config) =>
        config.copy(overwrite = overwriteArg)
      } text ("explicit --overwrite true is needed to overwrite existing avro file.")

      checkConfig { config =>
        if (!config.overwrite) {
          if (new File(config.avroOutDir).exists()) {
            failure("avroOutDir must not exist! Use explicit --overwrite true option to overwrite existing directory.")
          } else {
            success
          }
        } else {
          success
        }
      }
    }
  }
}