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

package za.co.absa.pramen.core.pipeline

import com.typesafe.config.ConfigFactory
import org.apache.spark.sql.DataFrame
import org.scalatest.WordSpec
import za.co.absa.pramen.core.OperationDefFactory
import za.co.absa.pramen.core.base.SparkTestBase
import za.co.absa.pramen.core.expr.exceptions.SyntaxErrorException
import za.co.absa.pramen.core.fixtures.TextComparisonFixture
import za.co.absa.pramen.core.metastore.model.MetastoreDependency
import za.co.absa.pramen.core.mocks.MetaTableFactory
import za.co.absa.pramen.core.mocks.bookkeeper.SyncBookkeeperMock
import za.co.absa.pramen.core.mocks.job.JobBaseDummy
import za.co.absa.pramen.core.mocks.metastore.MetastoreSpy
import za.co.absa.pramen.core.pipeline.JobPreRunStatus.FailedDependencies

import java.time.LocalDate

class JobBaseSuite extends WordSpec with SparkTestBase with TextComparisonFixture {

  private val infoDate = LocalDate.of(2022, 1, 18)

  "getInfoDateRange()" should {
    "return correct range when both from and to are not provided" in {
      val job = getUseCase()

      val (from, to) = job.getInfoDateRange(infoDate, None, None)

      assert(from == infoDate)
      assert(to == infoDate)
    }

    "return correct range when from is provided but to is not" in {
      val job = getUseCase()

      val (from, to) = job.getInfoDateRange(infoDate, Some("@infoDate - 5"), None)

      assert(from == infoDate.minusDays(5))
      assert(to == infoDate)
    }

    "return correct range when to is provided but from is not" in {
      val job = getUseCase()

      val (from, to) = job.getInfoDateRange(infoDate, None, Some("@infoDate + 5"))

      assert(from == infoDate)
      assert(to == infoDate.plusDays(5))
    }

    "return correct range when both from and to are provided" in {
      val job = getUseCase()

      val (from, to) = job.getInfoDateRange(infoDate, Some("@infoDate - 5"), Some("@infoDate + 5"))

      assert(from == infoDate.minusDays(5))
      assert(to == infoDate.plusDays(5))
    }

    "throw an exception when the resulting date range is invalid" in {
      val job = getUseCase()

      val ex = intercept[IllegalArgumentException] {
        job.getInfoDateRange(infoDate, Some("@infoDate + 1"), Some("@infoDate - 1"))
      }

      assert(ex.getMessage.contains("Incorrect date range specified for test_output_table: from=2022-01-19 > to=2022-01-17"))
    }

    "throw an exception when an expression is invalid" in {
      val job = getUseCase()

      val ex = intercept[SyntaxErrorException] {
        job.getInfoDateRange(infoDate, Some("@unknownDate + 1"), None)
      }

      assert(ex.getMessage.contains("Unset variable 'unknownDate' used"))
    }
  }

  "preRunCheck()" should {
    "return failure on failed dependencies" in {
      val conf = ConfigFactory.empty()
      val dep = MetastoreDependency(Seq("table1"), "@infoDate", None, triggerUpdates = false, isOptional = false)
      val job = getUseCase(dependencies = Seq(dep), isTableAvailable = false)

      val actual = job.preRunCheck(infoDate, conf)

      assert(actual.dependencyWarnings.isEmpty)
      assert(actual.status.isInstanceOf[FailedDependencies])
    }

    "return warnings on failed optional dependencies" in {
      val conf = ConfigFactory.empty()
      val dep = MetastoreDependency(Seq("table1"), "@infoDate", None, triggerUpdates = false, isOptional = true)
      val job = getUseCase(dependencies = Seq(dep), isTableAvailable = false)

      val actual = job.preRunCheck(infoDate, conf)

      assert(actual.dependencyWarnings.nonEmpty)
      assert(actual.dependencyWarnings.head.table == "table1")
    }
  }

  def getUseCase(tableDf: DataFrame = null,
                 dependencies: Seq[MetastoreDependency] = Nil,
                 isTableAvailable: Boolean = true): JobBase = {
    val operation = OperationDefFactory.getDummyOperationDef(dependencies = dependencies,
      extraOptions = Map[String, String]("value" -> "7"))

    val bk = new SyncBookkeeperMock

    val metastore = new MetastoreSpy(tableDf = tableDf, isTableAvailable = isTableAvailable)

    val outputTable = MetaTableFactory.getDummyMetaTable(name = "test_output_table")

    new JobBaseDummy(operation, metastore, bk, outputTable)
  }
}
