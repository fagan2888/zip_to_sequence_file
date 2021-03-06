package uk.gov.ons.mdr.io.sequence

import java.nio.file.{Path, Paths}

import org.apache.hadoop.io.SequenceFile.Writer
import org.apache.hadoop.io.{BytesWritable, Text}
import org.slf4j.{Logger, LoggerFactory}

trait SequenceWriter extends java.io.Closeable {
  def write(fileData: FileData): Unit
}

/** Writes [[org.apache.hadoop.io.SequenceFile]] in batches.
  *
  * @param sequenceOutputDir directory to save resulting files to.
  * @param seqFileNamePrefix prefix to use for the sequence files.
  * @param byteThreshold Content to store before rolling the file.
  */
class RollingSequenceWriter(sequenceOutputDir: SequenceOutputDir,
                            seqFileNamePrefix: String,
                            byteThreshold: Long) extends SequenceWriter {

  this: WriterFactory =>

  def logger: Logger = LoggerFactory.getLogger(this.getClass)

  private[this] var currentFileIndex = 1
  private[this] var bytesWritten = 0L
  private[this] var writer = createWriter(currentPath())

  private[this] def currentPath(): Path = {
    val dir = sequenceOutputDir.path.toString
    val fileName = f"${seqFileNamePrefix}_$currentFileIndex%02d.seq"

    Paths.get(dir, fileName)
  }

  private[this] def roll(): Writer = {

    currentFileIndex += 1
    writer.close()
    bytesWritten = 0L

    val nextPath = currentPath()

    logger.info(s"Rolling file, new output $nextPath")
    createWriter(nextPath)
  }

  override def write(fileData: FileData): Unit = {
    this synchronized {

      if (bytesWritten > byteThreshold) {
        writer = roll()
      }

      writer.append(new Text(fileData.name), new BytesWritable(fileData.content))

      val nameByteLength = fileData.name.getBytes.length
      val contentByteLength = fileData.content.length

      bytesWritten += nameByteLength + contentByteLength
    }
  }

  override def close(): Unit = {
    writer.close()
  }
}
