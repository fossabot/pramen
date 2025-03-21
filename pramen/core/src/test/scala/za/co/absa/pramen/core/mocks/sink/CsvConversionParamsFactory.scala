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

package za.co.absa.pramen.core.mocks.sink

import za.co.absa.pramen.core.sink.{ColumnNameTransform, CsvConversionParams}

object CsvConversionParamsFactory {
  def getDummyCsvConversionParams(csvOptions: Map[String, String] = Map.empty[String, String],
                                  fileNamePattern: String = "@tableName_@infoDate_@timestamp",
                                  tempHadoopPath: String = "/dummy/path",
                                  dateFormat: String = "yyyy-MM-dd",
                                  timestampFormat: String = "yyyy-MM-dd HH:mm:ss Z",
                                  fileNameTimestampPattern: String = "yyyyMMdd_HHmmss",
                                  columnNameTransform: ColumnNameTransform = ColumnNameTransform.NoChange): CsvConversionParams = {
    CsvConversionParams(csvOptions = csvOptions,
      fileNamePattern = fileNamePattern,
      tempHadoopPath = tempHadoopPath,
      dateFormat = dateFormat,
      timestampFormat = timestampFormat,
      fileNameTimestampPattern = fileNameTimestampPattern,
      columnNameTransform = columnNameTransform)
  }
}
