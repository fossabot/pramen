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

package za.co.absa.pramen.core.tests.utils

import org.scalatest.WordSpec
import za.co.absa.pramen.core.fixtures.TempDirFixture
import za.co.absa.pramen.core.utils.LocalFsUtils

import java.nio.file.{Files, Paths}

class LocalFsUtilsSuite extends WordSpec with TempDirFixture {
  "getListOfFiles()" should {
    "return the list of all files, but not dirs" in {
      withTempDirectory("local_fs_utils") { tempDir =>
        createBinFile(tempDir, "file1.txt", 100)
        createBinFile(tempDir, "file2.dat", 100)
        Files.createDirectory(Paths.get(tempDir, "dir"))

        val listOfFiles = LocalFsUtils.getListOfFiles(Paths.get(tempDir))

        assert(listOfFiles.size == 2)
        assert(listOfFiles.exists(_.getFileName.toString == "file1.txt"))
        assert(listOfFiles.exists(_.getFileName.toString == "file2.dat"))
      }
    }

    "return the list of files by mask" in {
      withTempDirectory("local_fs_utils") { tempDir =>
        createBinFile(tempDir, "file1.txt", 100)
        createBinFile(tempDir, "file2.dat", 100)

        val listOfFiles = LocalFsUtils.getListOfFiles(Paths.get(tempDir), "*.txt")

        assert(listOfFiles.exists(_.getFileName.toString == "file1.txt"))
        assert(!listOfFiles.exists(_.getFileName.toString == "file2.dat"))
      }
    }
  }

  "copyDirectory()" should {
    "copy all files from one dir to another" in {
      withTempDirectory("local_fs_utils") { tempDir =>
        val inputDir = Paths.get(tempDir, "input")
        val outputDir = Paths.get(tempDir, "output")

        Files.createDirectory(inputDir)
        Files.createDirectory(outputDir)

        createBinFile(inputDir.toString, "file1.txt", 100)
        createBinFile(inputDir.toString, "file2.dat", 100)

        LocalFsUtils.copyDirectory(inputDir, outputDir)


        val listOfIInputFiles = LocalFsUtils.getListOfFiles(inputDir)
        val listOfIOutputFiles = LocalFsUtils.getListOfFiles(outputDir)

        assert(listOfIInputFiles.size == 2)
        assert(listOfIOutputFiles.size == 2)
        assert(listOfIOutputFiles.exists(_.getFileName.toString == "file1.txt"))
        assert(listOfIOutputFiles.exists(_.getFileName.toString == "file2.dat"))
      }
    }
  }
}
