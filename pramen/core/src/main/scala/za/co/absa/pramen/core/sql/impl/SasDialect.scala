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

package za.co.absa.pramen.core.sql.impl

import org.apache.spark.sql.jdbc.JdbcDialect
import org.apache.spark.sql.types.{DataType, MetadataBuilder}

/**
  * This is required for Spark to be able to handle data that comes from Sas JDBC drivers
  */
object SasDialect extends JdbcDialect {
  override def getSchemaQuery(table: String): String = {
    if (table.startsWith("(") && table.endsWith(")")) {
      table.substring(1, table.length-1)
    } else {
      super.getSchemaQuery(table)
    }
  }

  override def canHandle(url: String): Boolean = url.startsWith("jdbc:sasiom")
  override def quoteIdentifier(colName: String): String = "\"" + colName + "\"n"
  override def getCatalystType(sqlType: Int, typeName: String, size: Int, md: MetadataBuilder): Option[DataType] =
    super.getCatalystType(sqlType, typeName, size, md)
}
