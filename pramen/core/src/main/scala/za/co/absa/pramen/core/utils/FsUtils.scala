/*
 * Copyright 2022 ABSA Group Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package za.co.absa.pramen.core.utils

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, FileUtil, GlobFilter, Path}
import org.slf4j.{Logger, LoggerFactory}

import java.io.IOException
import java.nio.file.{Files, Paths}
import java.time._
import java.time.format.DateTimeFormatter
import scala.collection.mutable.ListBuffer
import scala.util.control.NonFatal
import scala.util.{Failure, Random, Success, Try}

class FsUtils(conf: Configuration, pathBase: String) {
  protected val log: Logger = LoggerFactory.getLogger(this.getClass)
  val fs: FileSystem = new Path(pathBase).getFileSystem(conf)

  /**
    * Returns directory size in megabytes
    */
  def getDirectorySizeMb(path: String): Long = {
    val megabyte = 1024L * 1024L
    val hadoopPath = new Path(path)

    fs.getContentSummary(hadoopPath).getLength / megabyte
  }

  /**
    * Returns directory size in bytes
    */
  def getDirectorySize(path: String): Long = {
    val hadoopPath = new Path(path)

    fs.getContentSummary(hadoopPath).getLength
  }

  def createDirectoryRecursive(path: Path): Unit = {
    val (prefix, rawPath) = splitUriPath(path)

    val tokens = rawPath.split("/").filter(_.nonEmpty)

    var currPath = prefix
    tokens.foreach({ dir =>
      currPath = currPath + "/" + dir
      val p = new Path(currPath)
      if (!fs.exists(p)) {
        log.info(s"Creating ${p.toUri.toString}...")
        fs.mkdirs(p)
      }
    })
  }

  def createDirectoryRecursiveButLast(path: Path): Unit = {
    val (prefix, rawPath) = splitUriPath(path)

    val tokens = rawPath.split("/").filter(_.nonEmpty).dropRight(1)

    var currPath = prefix
    tokens.foreach({ dir =>
      currPath = currPath + "/" + dir
      val p = new Path(currPath)
      if (!fs.exists(p)) {
        log.info(s"Creating ${p.toUri.toString}...")
        fs.mkdirs(p)
      }
    })
  }

  /**
    * Split path URI by separating scheme+server and path part
    * Example:
    * hdfs://server:8020/user/data/input -> (hdfs://server:8020, /user/data/input)
    * /user/data/input -> ("", /user/data/input)
    */
  def splitUriPath(path: Path): (String, String) = {
    val uri = path.toUri
    val scheme = uri.getScheme
    val authority = uri.getAuthority
    val prefix = if (scheme == null) "" else {
      if (authority == null) {
        scheme + "://"
      } else {
        scheme + "://" + authority
      }
    }
    val rawPath = uri.getRawPath
    (prefix, rawPath)
  }

  /**
    * Returns a sub-path of two paths
    * Examples:
    * hdfs://server:8020/user/data/input, hdfs://server:8020/user/data/test/inner1/example -> test/inner1/example
    * /a/b/c/d, /a/x/y/z -> x/y/z
    */
  def getSubPath(path1: Path, path2: Path): String = {
    if (path1 == path2) {
      return ""
    }

    val path1Fixed = splitUriPath(path1)._2
    val path2Fixed = splitUriPath(path2)._2

    val folders1 = path1Fixed.split('/')
    val folders2 = path2Fixed.split('/')

    val minSize = Math.min(folders1.length, folders2.length)

    var i = 0
    while (i < minSize && folders1(i) == folders2(i)) {
      i += 1
    }
    folders2.drop(i).mkString("/")
  }

  /**
    * Gets the list of files that match the given pattern (recursively).
    *
    * @param path     The base path to search
    * @param fileMask The file mask to apply
    * @return
    */
  def getFilesRecursive(path: Path, fileMask: String = "*", includeHiddenFiles: Boolean = false): Seq[Path] = {
    val filter = new GlobFilter(fileMask)
    val files = new ListBuffer[Path]

    def isHidden(fileName: String): Boolean = {
      fileName.startsWith("_") || fileName.startsWith(".")
    }

    def addToListRecursive(searchPath: Path): Unit = {
      val statuses = fs.globStatus(new Path(searchPath, "*"))
      statuses.foreach { status =>
        val p = status.getPath
        if (status.isDirectory) {
          if (includeHiddenFiles || !isHidden(p.getName)) {
            addToListRecursive(p)
          }
        } else {
          if (filter.accept(status.getPath)) {
            if (includeHiddenFiles || !isHidden(p.getName)) {
              files += status.getPath
            }
          }
        }
      }
    }

    if (isFile(path)) {
      Seq(path)
    } else {
      addToListRecursive(path)
      files
    }
  }

  /**
    * list files recursively returning the list of files and subdirs separately.
    *
    * For example, given,
    * path = /a/b
    * fileMask = *.csv
    *
    * the method might return
    * Seq(
    * Seq("c"), "/a/b/c/file1.csv"),
    * Seq("c", "d"), "/a/b/c/d/file2.csv"),
    * Seq("c", "d"), "/a/b/c/d/file3.csv"),
    * }
    *
    * @param path     a path to a file.
    * @param fileMask content to write.
    * @return The list of subdirs zipped with the list of files in the directory
    */
  def getFilesWithSubdirs(path: Path, fileMask: String = "*"): Seq[(Seq[String], Path)] = {
    val fullPathUri = path.toUri.toString
    val fullPathLength = fullPathUri.length

    val files = getFilesRecursive(path, fileMask)

    files.map { file =>
      val fileStr = file.toUri.toString
      val idx = fileStr.indexOf(fullPathUri)
      val relativePath = fileStr.substring(idx + fullPathLength + 1)

      val pathList = relativePath.split('/')
      if (pathList.length > 1) {
        (pathList.toSeq.dropRight(1), file)
      } else {
        (Seq.empty[String], file)
      }
    }
  }

  /**
    * Writes a string as a UTF-8 text file.
    *
    * @param filePath a path to a file.
    * @param content  content to write.
    */
  def writeFile(filePath: Path, content: String): Unit = {
    val out = fs.create(filePath)
    out.write(content.getBytes())
    out.close()
  }

  /**
    * Appends an UTF-8 string to a file.
    *
    * @param filePath a path to a file.
    * @param content  content to write.
    */
  def appendFile(filePath: Path, content: String): Unit = {
    try {
      val out = fs.append(filePath)
      log.info("Appending using the filesystem routine")
      out.write(content.getBytes())
      out.close()
    } catch {
      // It seems not all filesystems support append(). And Filesystem.append() just throws an exception in this case.
      // This is a workaround for this particular case, so the append can be done anyway.
      // At the same time, all other exceptions will be re-thrown. Since IOException is too broad, the code relies on
      // the particular message. It's a hack, sorry. HDFS supports append().
      case ex: Throwable if ex.getMessage != null && ex.getMessage.toLowerCase.contains("not supported") =>
        log.info("Appending using full overwrite")
        val originalContent = if (exists(filePath)) {
          readFile(filePath)
        } else ""
        safeWriteFile(filePath, originalContent + content)
      case ex: Throwable                                              => throw ex
    }
  }

  /**
    * Writes a string as a UTF-8 text file atomically so either full file is written or nothing is written.
    *
    * @param filePath a path to a file.
    * @param content  content to write.
    */
  def safeWriteFile(filePath: Path, content: String): Unit = {
    val tmpPath = new Path(s"${filePath.toUri}.tmp")
    writeFile(tmpPath, content)
    renamePath(tmpPath, filePath)
  }

  /**
    * Reads an entire file as a UTF-8 string.
    *
    * @param filePath a path to a file.
    * @return contents of the file as string.
    */
  def readFile(filePath: Path): String = {
    val in = fs.open(filePath)
    val content = Array.fill(in.available())(0.toByte)
    in.readFully(content)
    in.close()
    new String(content, "UTF-8")
  }

  /**
    * Checks if a path exists.
    *
    * @param path a path.
    * @return true if the path exists.
    */
  def exists(path: Path): Boolean = fs.exists(path)

  /**
    * Checks if a path points to a file.
    *
    * @param path a path.
    * @return true if the path is a file.
    */
  def isFile(path: Path): Boolean = fs.getFileStatus(path).isFile

  /**
    * Implements a file guard. A guard is a file on HDFS that contains its expiration time.
    * If the specified file is present and the expiration time is not reached a new
    * instance of the application won't be allowed to run.
    *
    * If the guard ticket is expired a new ticket is created and the application is allowed to run.
    *
    * If the guard file does not exist, the application created that file and puts the expiration time there,
    * effectively claiming the guard lock.
    *
    * @param filePath      a file name on HDFS to use as a guard lock.
    * @param expireSeconds The number of seconds before the lock is expired.
    * @return true if the file already exists
    */
  def isFileGuardOwned(filePath: Path, expireSeconds: Long): Boolean = {
    def createFileGuard(): Boolean = {
      try {
        val out = fs.create(filePath, false)
        val ticket = Instant.now.getEpochSecond + expireSeconds
        out.writeBytes(ticket.toString)
        out.close()
        log.info(s"Successfully acquired lock '$filePath'")
        true
      } catch {
        case _: IOException =>
          log.warn(s"Lock '$filePath' acquisition is blocked by another process.")
          false
        case NonFatal(ex) => throw ex
      }
    }

    def overwriteIfExpired(): Boolean = {
      val now = Instant.now.getEpochSecond
      val ticketExpires = Try(readFile(filePath).toLong).getOrElse(0L)
      if (now <= ticketExpires) {
        log.warn(s"Lock '$filePath' is acquired by another process. The ticket is not expired yet.")
        false
      } else {
        val newTicket = now + expireSeconds
        writeFile(filePath, newTicket.toString)
        log.warn(s"Successfully acquired the expired lock '$filePath'.")
        true
      }
    }

    if (!exists(filePath)) {
      createFileGuard()
    } else {
      if (!isFile(filePath)) {
        throw new IllegalArgumentException(s"Path $filePath is not a file.")
      } else {
        overwriteIfExpired()
      }
    }
  }

  /**
    * Updates a lock file with new expiration time.
    *
    * @param filePath      a file name on HDFS to use as a guard lock.
    * @param expireSeconds The number of seconds before the lock is expired.
    * @return true if the file already exists
    */
  def updateFileGuard(filePath: Path, expireSeconds: Long): Unit = {
    val now = Instant.now.getEpochSecond
    val newTicket = now + expireSeconds
    writeFile(filePath, newTicket.toString)
    log.info(s"Successfully updated lock '$filePath'")
  }

    /**
    * Copies a file.
    *
    * @param srcFile   source file.
    * @param dstFile   destination file.
    * @param overwrite overwrite the target file is exists.
    * @return true if the rename succeeded.
    */
  def copyFile(srcFile: Path, dstFile: Path, overwrite: Boolean = true): Unit = {
    FileUtil.copy(fs, srcFile, fs, dstFile, false, overwrite, conf)
  }

  def copyToLocal(srcFile: Path, targetFile: Path, overwrite: Boolean = false): Unit = {
    if (!overwrite && fs.exists(targetFile)) {
      throw new IllegalStateException(s"Target file $targetFile already exists.")
    }

    fs.copyToLocalFile(srcFile, targetFile)

    Try {
      val crcPath = new Path(targetFile.getParent, s".${targetFile.getName}.crc")
      Files.delete(Paths.get(crcPath.toString))
    }
  }

  def copyFromLocal(srcFile: Path, targetFile: Path): Unit = {
    fs.copyFromLocalFile(srcFile, targetFile)
  }

  /**
    * Renames/Moves a path.
    *
    * @param pathSrc   source path.
    * @param pathTrg   target path.
    * @param overwrite overwrite the target file is exists.
    * @return true if the rename succeeded.
    */
  def renamePath(pathSrc: Path, pathTrg: Path, overwrite: Boolean = true): Boolean = {
    log.debug(s"fs.exists($pathTrg) = ${fs.exists(pathTrg)}")
    if (overwrite && fs.exists(pathTrg)) {
      log.debug(s"Renaming is not succeeded. Deleting '$pathTrg'...")
      fs.delete(pathTrg, true)
      log.debug(s"Renaming $pathSrc to '$pathTrg'...")
      fs.rename(pathSrc, pathTrg)
    } else {
      log.debug(s"Renaming $pathSrc to '$pathTrg'...")
      fs.rename(pathSrc, pathTrg)
    }
  }

  /**
    * Archives files from the local file system to Hadoop.
    *
    * @param files      The list of files to archive.
    * @param archiveDir The output directory in Hadoop.
    */
  def archiveLocalFiles(files: Seq[java.nio.file.Path], archiveDir: Path): Unit = {
    val nowStr = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))

    files.foreach(filePath => {
      val fileName = filePath.getFileName.toString
      val newFileName = s"${fileName}_$nowStr"
      val src = new Path(filePath.toAbsolutePath.toString)
      val trg = new Path(archiveDir, newFileName)
      log.info(s"Moving $src to $trg...")
      Try {
        fs.moveFromLocalFile(src, trg)
      } match {
        case Success(_) => // Do nothing
        case Failure(ex) => log.error(s"Failed to move $src to $trg", ex)
      }
    })
  }

  /**
    * Deletes a file.
    *
    * @param filePath path to a file.
    */
  def deleteFile(filePath: Path): Unit = {
    fs.delete(filePath, false)
  }

  /**
    * Deletes a directory and all its contents recursively.
    *
    * @param path path to a file.
    */
  def deleteDirectoryRecursively(path: Path): Boolean = {
    if (fs.exists(path)) {
      log.info(s"Deleting recursively '$path'...")
      fs.delete(path, true)
    } else {
      false
    }
  }

  /**
    * Creates a new temp directory in the specified base path and returns the path to the new directory.
    *
    * @param baseTempPath path to a file.
    */
  def getTempPath(baseTempPath: Path): Path = {
    createDirectoryRecursive(baseTempPath)
    if (!exists(baseTempPath)) {
      throw new IllegalStateException(s"Unable to create $baseTempPath.")
    }

    var tmpPath = new Path(baseTempPath, getTimedToken)
    while (exists(tmpPath)) {
      tmpPath = new Path(baseTempPath, getTimedToken)
    }
    fs.mkdirs(tmpPath)
    tmpPath
  }

  /**
    * Invokes a function and provides it with a temporary path in Hadoop.
    *
    * @param baseTempPath path to a file.
    */
  def withTempDirectory(baseTempPath: Path)(f: Path => Unit): Unit = {
    val tmpPath = getTempPath(baseTempPath)
    try {
      f(tmpPath)
    } finally {
      try {
        deleteDirectoryRecursively(tmpPath)
      } catch {
        case NonFatal(ex) => log.error(s"Failed to delete $tmpPath", ex)
      }
    }
  }

   /**
    * Deletes all files older than `maxDays` days from `date`.
    * Deletes empty folders as well.
    *
    * @param folders The root path.
    */
  def deleteObsolete(folders: Seq[String], maxDays: Int, date: LocalDate, dryRun: Boolean, timezoneId: ZoneId): Unit = {
    val minTimeMs = DateUtils.fromDateToTimestampMs(date.minusDays(maxDays), timezoneId)

    def deleteObsoleteHelper(folder: Path): Boolean = {
      var isDirectoryEmpty = true
      var deletedSomething = false
      val statuses = fs.globStatus(new Path(folder, "*"))

      statuses.foreach(status => {
        val path = status.getPath

        if (status.isDirectory) {
          val isEmpty = deleteObsoleteHelper(path)
          if (isEmpty) {
            if (dryRun) {
              log.info(s"(dry-run) Deleting empty directory $path")
              deletedSomething = true
            } else {
              log.info(s"Deleting empty directory $path")
              fs.delete(path, true)
              deletedSomething = true
            }
          } else {
            isDirectoryEmpty = false
          }
        } else {
          val modTimeMs = status.getModificationTime
          if (modTimeMs < minTimeMs) {
            val time = LocalDateTime.ofInstant(Instant.ofEpochMilli(modTimeMs), timezoneId)
            if (dryRun) {
              log.info(s"(dry-run) Deleting a file created $time: $path")
              deletedSomething = true
            } else {
              log.info(s"Deleting a file created $time: $path")
              fs.delete(path, false)
              deletedSomething = true
            }
          } else {
            isDirectoryEmpty = false
          }
        }
      })
      isDirectoryEmpty && deletedSomething
    }

    folders.foreach(folder => deleteObsoleteHelper(new Path(folder)))
  }

  /**
    * Moves all files older than `maxDays` days from `date` to the trash dir.
    * moved files retain path structure.
    *
    * @param folders The root path.
    */
  def moveObsolete(folders: Seq[String], trashDir: String, maxDays: Int, date: LocalDate, dryRun: Boolean, timezoneId: ZoneId): Unit = {
    val trashPath = new Path(trashDir)
    val minTimeMs = DateUtils.fromDateToTimestampMs(date.minusDays(maxDays), timezoneId)

    def movetoTrash(file: Path, time: LocalDateTime): Unit = {
      val outputPath = new Path(trashDir, getSubPath(trashPath, file))
      val parent = outputPath.getParent

      if (!fs.exists(parent)) {
        createDirectoryRecursive(parent)
      }

      if (dryRun) {
        log.info(s"(dry-run) Moving a file created $time: $file to $outputPath")
      } else {
        log.info(s"Moving a file created $time: $file to $outputPath")
        fs.rename(file, outputPath)
      }
    }

    def moveObsoleteHelper(folder: Path): Boolean = {
      var isDirectoryEmpty = true
      var deletedSomething = false
      val statuses = fs.globStatus(new Path(folder, "*"))

      statuses.foreach(status => {
        val path = status.getPath

        if (status.isDirectory) {
          val isDeletionOfEmptyDirNeeded = moveObsoleteHelper(path)
          if (isDeletionOfEmptyDirNeeded) {
            if (dryRun) {
              log.info(s"(dry-run) Deleting empty directory $path")
              deletedSomething = true
            } else {
              log.info(s"Deleting empty directory $path")
              fs.delete(path, true)
              deletedSomething = true
            }
          } else {
            isDirectoryEmpty = false
          }
        } else {
          val modTimeMs = status.getModificationTime
          if (modTimeMs < minTimeMs) {
            val time = LocalDateTime.ofInstant(Instant.ofEpochMilli(modTimeMs), timezoneId)
            movetoTrash(path, time)
            deletedSomething = true
          } else {
            isDirectoryEmpty = false
          }
        }
      })
      isDirectoryEmpty && deletedSomething
    }

    folders.foreach(folder => moveObsoleteHelper(new Path(folder)))
  }

  protected def getTimedToken: String = {
    val now = Instant.now.getEpochSecond
    val r = Random.nextInt(100000)
    s"_${now}_$r"
  }

}
