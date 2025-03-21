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

import scala.reflect.runtime.universe._

/** Tools to help creating CSV files manually ann then reading them from Spark. */
object CsvUtils {

  /** Get CSV header string from a case class. */
  def getHeaders[T: TypeTag](separator: Char = ','): String = {
    typeOf[T]
      .members
      .filterNot(_.isMethod)
      .map(_.name.toString.trim)
      .toArray
      .reverse
      .mkString(s"$separator")
  }

  /** Get a CSV record string from an instance of a case class. */
  def getRecord[T](obj: T, separator: Char = ','): String = {
    obj.getClass.getDeclaredFields.map(field => {
      field.setAccessible(true)
      field.get(obj).toString.replace(separator, ' ')
    }).mkString("", s"$separator", "\n")
  }

}
