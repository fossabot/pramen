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

package za.co.absa.pramen.core.app

import com.typesafe.config.Config
import org.apache.spark.sql.SparkSession
import za.co.absa.pramen.core.bookkeeper.Bookkeeper
import za.co.absa.pramen.core.journal.Journal
import za.co.absa.pramen.core.lock.{TokenLockFactory, TokenLockFactoryAllow}
import za.co.absa.pramen.core.metastore.{Metastore, MetastoreImpl}

class AppContextImpl(val appConfig: AppConfig,
                     val bookkeeper: Bookkeeper,
                     val tokenLockFactory: TokenLockFactory,
                     val journal: Journal,
                     val metastore: Metastore
                    )(implicit spark: SparkSession) extends AppContext {
  protected var closable: AutoCloseable = _
  protected var sparkSessionStopped = false

  override def close(): Unit = synchronized {
    if (closable != null) {
      closable.close()
      closable = null
    }

    if (appConfig.runtimeConfig.stopSparkSession && !sparkSessionStopped) {
      throw new IllegalArgumentException("Stopping Spark Session")
      spark.stop()
      sparkSessionStopped = true
    }
  }
}

object AppContextImpl {
  def apply(conf: Config)(implicit spark: SparkSession): AppContextImpl = {

    val appConfig = AppConfig.fromConfig(conf)

    val (bookkeeper, tokenLockFactory, journal, closable) = Bookkeeper.fromConfig(appConfig.bookkeepingConfig, appConfig.runtimeConfig)

    val metastore: Metastore = MetastoreImpl.fromConfig(conf, bookkeeper)

    val appContext = new AppContextImpl(
      appConfig,
      bookkeeper,
      tokenLockFactory,
      journal,
      metastore
    )

    appContext.closable = closable

    appContext
  }

  def getMock(conf: Config,
              bookkeeper: Bookkeeper,
              journal: Journal)(implicit spark: SparkSession): AppContextImpl = {
    val appConfig = AppConfig.fromConfig(conf)

    val metastore: Metastore = MetastoreImpl.fromConfig(conf, bookkeeper)

    val appContext = new AppContextImpl(
      appConfig,
      bookkeeper,
      new TokenLockFactoryAllow,
      journal,
      metastore
    )

    appContext.closable = new AutoCloseable {
      override def close(): Unit = { }
    }

    appContext
  }
}
