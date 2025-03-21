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

package za.co.absa.pramen.core.tests.journal

import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, WordSpec}
import za.co.absa.pramen.core.base.SparkTestBase
import za.co.absa.pramen.core.fixtures.RelationalDbFixture
import za.co.absa.pramen.core.journal.{Journal, JournalJdbc}
import za.co.absa.pramen.core.rdb.PramenDb
import za.co.absa.pramen.core.reader.model.JdbcConfig

class JournalJdbcSuite extends WordSpec with SparkTestBase with BeforeAndAfter with BeforeAndAfterAll with RelationalDbFixture {
  import TestCases._

  val jdbcConfig: JdbcConfig = JdbcConfig(driver, Some(url), Nil, None, user, password, Map.empty[String, String])
  val pramenDb: PramenDb = PramenDb(jdbcConfig)

  before {
    pramenDb.rdb.executeDDL("DROP SCHEMA PUBLIC CASCADE;")
    pramenDb.setupDatabase()
  }

  override def afterAll(): Unit = {
    pramenDb.close()
    super.afterAll()
  }


  "Journal" should {
    "Initialize journal directory empty database" in {
      val journal = getJournal

      val tables = getTables

      assert(tables.exists(_.equalsIgnoreCase("journal")))
    }

    "addEntry()" should {
      "return Nil if there are no entries" in {
        val journal = getJournal

        assert(journal.getEntries(instant1, instant3).isEmpty)
      }

      "return entries if there are entries" in {
        val journal = getJournal

        journal.addEntry(task1)
        journal.addEntry(task2)
        journal.addEntry(task3)


        val entries = journal.getEntries(instant2, instant3).sortBy(_.informationDate.toString)

        assert(entries.nonEmpty)
        assert(entries == task2 :: task3 :: Nil)
      }
    }
  }

  private def getJournal: Journal = {
    new JournalJdbc(pramenDb.slickDb)
  }
}
