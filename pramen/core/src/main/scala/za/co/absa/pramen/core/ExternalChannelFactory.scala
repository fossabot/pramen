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

package za.co.absa.pramen.core

import com.typesafe.config.Config
import org.apache.spark.sql.SparkSession
import za.co.absa.pramen.api.ExternalChannel
import za.co.absa.pramen.core.utils.ClassLoaderUtils

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.reflect.ClassTag
import scala.reflect.runtime.universe

/**
  * Base interface for all Pramen source and sink factories.
  */
trait ExternalChannelFactory[+A <: ExternalChannel] {
  def apply(conf: Config, parentPath: String, spark: SparkSession): A
}

object ExternalChannelFactory {
  val FACTORY_CLASS_KEY = "factory.class"
  val NAME_KEY = "name"

  def fromConfig[T <: ExternalChannel : ClassTag : universe.TypeTag](conf: Config,
                                                                     parentPath: String,
                                                                     channelType: String)
                                                                    (implicit spark: SparkSession): T = {
    if (!conf.hasPath(FACTORY_CLASS_KEY)) {
      throw new IllegalArgumentException(s"A class should be specified for the $channelType at '$parentPath'.")
    }
    val clazz = conf.getString(FACTORY_CLASS_KEY)
    val factory = ClassLoaderUtils.loadSingletonClassOfType[ExternalChannelFactory[T]](clazz)

    factory.apply(conf, parentPath, spark)
  }

  def fromConfigByName[T <: ExternalChannel : ClassTag : universe.TypeTag](conf: Config,
                                                                           overrideConf: Option[Config],
                                                                           arrayPath: String,
                                                                           name: String,
                                                                           channelType: String)
                                                                          (implicit spark: SparkSession): T = {
    validateConfig(conf, arrayPath, channelType)

    val srcConfig = conf.getConfigList(arrayPath).asScala
    val src1Config = srcConfig.zipWithIndex
      .find { case (cfg, _) => cfg.hasPath(NAME_KEY) && cfg.getString(NAME_KEY).equalsIgnoreCase(name) }

    src1Config match {
      case Some((cfg, idx)) =>
        val effectiveConf = overrideConf match {
          case Some(oc) => oc.withFallback(cfg)
          case None     => cfg
        }
        fromConfig(effectiveConf, s"$arrayPath[$idx]", channelType)
      case None             =>
        throw new IllegalArgumentException(s"Unknown name of a data $channelType: $name.")
    }
  }

  def validateConfig(conf: Config,
                     arrayPath: String,
                     channelType: String): Unit = {
    val channelConfigs = conf.getConfigList(arrayPath).asScala

    val emptyNameChannelsCnt = channelConfigs.filterNot(cfg => cfg.hasPath(NAME_KEY)).size
    val emptyFactoryClassesCnt = channelConfigs.filterNot(cfg => cfg.hasPath(FACTORY_CLASS_KEY)).size

    val names = channelConfigs
      .filter(cfg => cfg.hasPath(NAME_KEY))
      .map(cfg => cfg.getString(NAME_KEY))

    val duplicateNames = channelConfigs
      .filter(cfg => cfg.hasPath(NAME_KEY))
      .map(cfg => cfg.getString(NAME_KEY))
      .filter(name => names.count(n => name.equalsIgnoreCase(n)) > 1)

    val validationIssues = new ListBuffer[String]

    if (emptyNameChannelsCnt > 0) validationIssues += s"A name is not configured for $emptyNameChannelsCnt $channelType(s). Configure '$NAME_KEY' key."
    if (emptyFactoryClassesCnt > 0) validationIssues += s"Factory class is not configured for $emptyFactoryClassesCnt $channelType(s). Configure '$FACTORY_CLASS_KEY' key."
    if (duplicateNames.nonEmpty) validationIssues += s"Duplicate $channelType names: ${duplicateNames.mkString(", ")}"

    if (validationIssues.nonEmpty) {
      throw new IllegalArgumentException(s"Configuration error for a $channelType at '$arrayPath'. Issues:\n${validationIssues.mkString("\n")}")
    }
  }
}
