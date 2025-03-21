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
import za.co.absa.pramen.core.app.config.InfoDateConfig
import za.co.absa.pramen.core.app.config.InfoDateConfig.EXPECTED_DELAY_DAYS

import scala.collection.JavaConverters._

case class PipelineDef(
                        name: String,
                        environment: String,
                        operations: Seq[OperationDef]
                      )

object PipelineDef {
  val ENVIRONMENT_NAME = "pramen.environment.name"
  val PIPELINE_NAME_KEY = "pramen.pipeline.name"
  val OPERATIONS_KEY = "pramen.operations"

  def fromConfig(conf: Config, infoDateConfig: InfoDateConfig): PipelineDef = {
    val defaultDelayDays = conf.getInt(EXPECTED_DELAY_DAYS)
    val name = conf.getString(PIPELINE_NAME_KEY)
    val environment = conf.getString(ENVIRONMENT_NAME)
    val operations = conf.getConfigList(OPERATIONS_KEY)
      .asScala
      .zipWithIndex
      .flatMap{ case (c, i) => OperationDef.fromConfig(c, infoDateConfig, s"$OPERATIONS_KEY[$i]", defaultDelayDays) }
    PipelineDef(name, environment, operations)
  }
}
