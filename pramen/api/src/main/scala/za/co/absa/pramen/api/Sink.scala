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

package za.co.absa.pramen.api

import org.apache.spark.sql.{DataFrame, SparkSession}

import java.time.LocalDate

/**
  * A sink is an entity where jobs can write to and it won't be under metastore control (e.g. Kafka).
  *
  * Special Sink jobs are used to write tables from metastore to a sink.
  */
trait Sink extends ExternalChannel with AutoCloseable {

  /** Sinks can optionally have a method to connect to it. */
  @throws[Exception]
  def connect(): Unit

  /** Sinks can optionally have a method to close it when saving is done or in case of an error. */
  @throws[Exception]
  override def close(): Unit

  /**
    * Sends the given data frame to the sink.
    *
    * @param df        the dataframe containing data to send to the sink (with all transformations and filters applied).
    * @param tableName the name of the table from which the data is coming from.
    * @param metastore the metastore reader to read other tables from.
    * @param infoDate  the information date to use when reading the table from the metastore.
    * @param options   arbitrary extra options to use for the table (e.g. topic name, etc.)
    * @return The number of records written if successful, throw an exception otherwise.
    */
  def send(df: DataFrame,
           tableName: String,
           metastore: MetastoreReader,
           infoDate: LocalDate,
           options: Map[String, String])
          (implicit spark: SparkSession): Long
}
