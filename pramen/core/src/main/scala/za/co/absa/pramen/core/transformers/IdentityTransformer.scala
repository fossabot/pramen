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

package za.co.absa.pramen.core.transformers

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions._
import za.co.absa.pramen.api.{MetastoreReader, Reason, Transformer}

import java.time.LocalDate

class IdentityTransformer extends Transformer {
  override def validate(metastore: MetastoreReader, infoDate: LocalDate, options: Map[String, String]): Reason = {
    if (!options.contains("table")) {
      throw new IllegalArgumentException(s"Option 'table' is not defined")
    }

    val tableName = options("table")

    val df = metastore.getTable(tableName, Option(infoDate), Option(infoDate))

    if (df.count() > 0) {
      Reason.Ready
    } else {
      Reason.NotReady(s"No data for '$tableName' at $infoDate")
    }
  }

  override def run(metastore: MetastoreReader,
                   infoDate: LocalDate,
                   options: Map[String, String]): DataFrame = {
    val tableName = options("table")

    val df = metastore.getTable(tableName, Option(infoDate), Option(infoDate))

    df.withColumn("transform_id", rand())
  }
}
