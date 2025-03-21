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
import org.scalatest.WordSpec
import za.co.absa.pramen.api.Query.Table
import za.co.absa.pramen.core.pipeline.OperationType.{Ingestion, Transformation}

class OperationTypeSuite extends WordSpec {
  "OperationType.fromConfig()" should {
    "be able to serialize an ingestion operation" in {
      val conf = ConfigFactory.parseString(
        s"""type = "ingestion"
           |source = "jdbc1"
           |
           |tables = [
           |  {
           |    input.db.table = table1_db
           |    output.metastore.table = table1_sync
           |  }
           |]
           |""".stripMargin
      )

      val opType = OperationType.fromConfig(conf, "path").asInstanceOf[Ingestion]

      assert(opType.sourceName == "jdbc1")
      assert(opType.sourceTables.size == 1)
      assert(opType.sourceTables.head.metaTableName == "table1_sync")
      assert(opType.sourceTables.head.query.asInstanceOf[Table].dbTable == "table1_db")
    }

    "be able to serialize a transformation operation" in {
      val conf = ConfigFactory.parseString(
        s"""type = "transformation"
           |class = "myclass"
           |output.table = "dummy_table"
           |""".stripMargin
      )

      val opType = OperationType.fromConfig(conf, "path").asInstanceOf[Transformation]

      assert(opType.clazz == "myclass")
    }

    "be able to serialize an sink operation" in {
      val conf = ConfigFactory.parseString(
        s"""type = "sink"
           |sink = "kafka1"
           |
           |tables = [
           |  {
           |    input.metastore.table = table1_sync
           |    output.topic = table1_topic
           |  }
           |]
           |""".stripMargin
      )

      val opType = OperationType.fromConfig(conf, "path").asInstanceOf[OperationType.Sink]

      assert(opType.sinkName == "kafka1")
      assert(opType.sinkTables.size == 1)
      assert(opType.sinkTables.head.metaTableName == "table1_sync")
      assert(opType.sinkTables.head.options("topic") == "table1_topic")
    }
  }

}
