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

import com.typesafe.config.Config

import scala.collection.JavaConverters._

case class TransformExpression(
                              column: String,
                              expression: String
                              )

object TransformExpression {
  def fromConfigSingleEntry(conf: Config, parentPath: String): TransformExpression = {
    if (!conf.hasPath("col")) {
      throw new IllegalArgumentException(s"'col' not set for the transformation in $parentPath' in the configuration.")
    }
    if (!conf.hasPath("expr")) {
      throw new IllegalArgumentException(s"'expr' not set for the transformation in $parentPath' in the configuration.")
    }

    val col = conf.getString("col")
    val expr = conf.getString("expr")

    TransformExpression(col, expr)
  }
  def fromConfig(conf: Config, arrayPath: String, parentPath: String): Seq[TransformExpression] = {
    if (conf.hasPath(arrayPath)) {
      val transformationConfigs = conf.getConfigList(arrayPath).asScala

      val transformations = transformationConfigs
        .zipWithIndex
        .map { case (transformationConfig, idx) => fromConfigSingleEntry(transformationConfig, s"$parentPath.$arrayPath[$idx]") }

      transformations
    } else {
      Seq.empty[TransformExpression]
    }
  }
}