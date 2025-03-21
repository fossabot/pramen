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

import org.apache.spark.sql.{DataFrame, SparkSession}
import org.slf4j.LoggerFactory
import za.co.absa.pramen.core.reader.JdbcUrlSelector
import za.co.absa.pramen.core.reader.model.JdbcConfig
import za.co.absa.pramen.core.utils.impl.ResultSetToRowIterator

import java.sql.{Connection, DriverManager, ResultSet}
import java.util.Properties
import scala.util.Try
import scala.util.control.NonFatal

/**
  * Utils that help fetching data from arbitrary JDBC queries (not just SELECT) using Spark.
  *
  * Pros:
  * - Allows to execute arbitrary queries (e.g. stored procedures).
  * - Results can be arbitrarily large.
  *
  * Cons:
  * - Only limited set of data types supported.
  * - Can be much slower than Spark's JDBC, especially for result sets that don't support scrollable cursors.
  * - Partitioned read (by specifying partitioning column and range) is not supported.
  * - It executes a given query at least 2 times. So, please, do not use it for queries that
  *   changes state of the database and is not idempotent (inserts, updates, deletes).
  */
object JdbcNativeUtils {
  private val log = LoggerFactory.getLogger(this.getClass)

  /** Returns a JDBC URL and connection by a config. */
  def getConnection(jdbcConfig: JdbcConfig, retries: Option[Int] = None): (String, Connection) = {
    val urlSelector = new JdbcUrlSelector(jdbcConfig)

    def getConnectionWithRetries(jdbcConfig: JdbcConfig, retriesLeft: Int): (String, Connection) = {
      val currentUrl = urlSelector.getUrl
      Class.forName(jdbcConfig.driver)
      try {
        val properties = new Properties()
        properties.put("driver", jdbcConfig.driver)
        properties.put("user", jdbcConfig.user)
        properties.put("password", jdbcConfig.password)
        jdbcConfig.database.foreach(db => properties.put("database", db))
        jdbcConfig.extraOptions.foreach{
          case (k, v) => properties.put(k, v)
        }

        (currentUrl, DriverManager.getConnection(currentUrl, properties))
      } catch {
        case NonFatal(ex) if retriesLeft > 0 =>
          val nextUrl = urlSelector.getNextUrl
          log.error(s"Error connecting to $currentUrl. Retries left = $retriesLeft. Retrying with $nextUrl...", ex)
          getConnectionWithRetries(jdbcConfig, retriesLeft - 1)
        case NonFatal(ex) if retriesLeft <= 0 => throw ex
      }
    }

    retries match {
      case Some(n) => getConnectionWithRetries(jdbcConfig, n)
      case None => getConnectionWithRetries(jdbcConfig, urlSelector.getNumberOfUrls - 1)
    }
  }

  /** Gets the number of records returned by a given query. */
  def getJdbcNativeRecordCount(jdbcConfig: JdbcConfig,
                               url: String,
                               query: String)
                              (implicit spark: SparkSession): Long = {
    val resultSet = getResultSet(jdbcConfig, url, query)
    getResultSetCount(resultSet)
  }

  /** Gets a dataframe given a JDBC query */
  def getJdbcNativeDataFrame(jdbcConfig: JdbcConfig,
                             url: String,
                             query: String)
                            (implicit spark: SparkSession): DataFrame = {

    // Executing the query
    val driverIterator = new ResultSetToRowIterator(getResultSet(jdbcConfig, url, query))
    val schema = driverIterator.getSchema
    driverIterator.close()

    val rdd = spark.sparkContext.parallelize(Seq(query)).flatMap(q => {
      new ResultSetToRowIterator(getResultSet(jdbcConfig, url, q))
    })

    spark.createDataFrame(rdd, schema)
  }

  private [core] def getResultSetCount(resultSet: ResultSet): Long = {
    val countOpt = Try {
      // The fast way of getting record count from a scrollable cursor
      resultSet.last()
      resultSet.getRow.toLong
    }.toOption
    val count = countOpt.getOrElse({
      // The slow way if the underlying RDBMS doesn't support scrollable cursors for such types of queries.
      var i = 0L
      while (resultSet.next()) {
        i += 1
      }
      i
    })

    resultSet.close()
    count
  }

  private def getResultSet(jdbcConfig: JdbcConfig,
                           url: String,
                           query: String): ResultSet = {
    Class.forName(jdbcConfig.driver)
    val connection = DriverManager.getConnection(url, jdbcConfig.user, jdbcConfig.password)
    val statement = try {
      connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY)
    } catch {
      case _: java.sql.SQLException =>
        // Fallback with more permissible result type.
        // JDBC sources should automatically downgrade result type, but Denodo driver doesn't do that.
        connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)
      case NonFatal(ex) =>
        throw ex
    }

    statement.executeQuery(query)
  }

}
