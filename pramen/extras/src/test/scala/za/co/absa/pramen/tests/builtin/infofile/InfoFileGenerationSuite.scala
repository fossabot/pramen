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

package za.co.absa.pramen.tests.extras.infofile

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.hadoop.fs.Path
import org.scalatest.WordSpec
import za.co.absa.pramen.base.SparkTestBase
import za.co.absa.pramen.extras.infofile.InfoFileGeneration._
import za.co.absa.pramen.fixtures.{TempDirFixture, TextComparisonFixture}

import java.nio.file.{Files, Paths}
import java.time.{Instant, LocalDate, ZoneId}

class InfoFileGenerationSuite extends WordSpec with SparkTestBase with TextComparisonFixture with TempDirFixture {
  import spark.implicits._

  private val sourceStart = Instant.ofEpochSecond(1640684086L)
  private val rawStart = Instant.ofEpochSecond(1640684386L)
  private val rawFinish = Instant.ofEpochSecond(1640688586L)
  private val infoDate = LocalDate.of(2021, 12, 28)
  private val pramenVersion = "1.0.0"
  private val timezoneId = ZoneId.of("Africa/Johannesburg")

  "renderInfoFile" should {
    "render an Info file" in {
      val expectedInfoFile =
        """{
          |  "metadata" : {
          |    "sourceApplication" : "App1",
          |    "country" : "Africa",
          |    "historyType" : "Events",
          |    "dataFilename" : "JDBC",
          |    "sourceType" : "Source",
          |    "version" : 1,
          |    "informationDate" : "2021-12-28",
          |    "additionalInfo" : { }
          |  },
          |  "checkpoints" : [ {
          |    "name" : "Source",
          |    "software" : "pramen",
          |    "version" : "1.0.0",
          |    "processStartTime" : "28-12-2021 11:34:46 +0200",
          |    "processEndTime" : "28-12-2021 11:39:46 +0200",
          |    "workflowName" : "Source",
          |    "order" : 1,
          |    "controls" : [ {
          |      "controlName" : "recordCount",
          |      "controlType" : "count",
          |      "controlCol" : "*",
          |      "controlValue" : "100"
          |    } ]
          |  }, {
          |    "name" : "Raw",
          |    "software" : "pramen",
          |    "version" : "1.0.0",
          |    "processStartTime" : "28-12-2021 11:39:46 +0200",
          |    "processEndTime" : "28-12-2021 12:49:46 +0200",
          |    "workflowName" : "Source",
          |    "order" : 2,
          |    "controls" : [ {
          |      "controlName" : "recordCount",
          |      "controlType" : "count",
          |      "controlCol" : "*",
          |      "controlValue" : "200"
          |    } ]
          |  } ]
          |}""".stripMargin

      implicit val conf: Config = getConfig

      val infoFile = renderInfoFile(pramenVersion, timezoneId, 100, 200, infoDate, sourceStart, rawStart, rawFinish)

      compareText(infoFile, expectedInfoFile)
    }

    "fail if one of config keys are missing" when {
      val mandatory = Seq(SOURCE_APPLICATION_KEY, COUNTRY_KEY, HISTORY_TYPE_KEY, TIMESTAMP_FORMAT_KEY, DATE_FORMAT_KEY)

      mandatory.foreach(key => {
        key in {
          implicit val conf: Config = getConfig.withoutPath(key)

          val ex = intercept[IllegalArgumentException] {
            renderInfoFile(pramenVersion, timezoneId, 100, 200, infoDate, sourceStart, rawStart, rawFinish)
          }

          assert(ex.getMessage.contains(key))
        }
      })
    }
  }

  "generateInfoFile" should {
    "generate the info file" in {
      implicit val conf: Config = getConfig

      val rawDf = List(("A", 1), ("B", 2), ("C", 3)).toDF("a", "b")

      withTempDirectory("info_file_test") { tempDir =>
        generateInfoFile(pramenVersion, timezoneId, 100, rawDf, new Path(tempDir), infoDate, sourceStart, rawStart)

        assert(Files.exists(Paths.get(tempDir, "_INFO")))
      }
    }
  }

  def getConfig: Config = {
    ConfigFactory.parseString(
      s"""info.file {
         |  generate = true
         |  source.application = "App1"
         |  country = "Africa"
         |  history.type = "Events"
         |  timestamp.format = "dd-MM-yyyy HH:mm:ss Z"
         |  date.format = "yyyy-MM-dd"
         |}
         |""".stripMargin)
  }

}
