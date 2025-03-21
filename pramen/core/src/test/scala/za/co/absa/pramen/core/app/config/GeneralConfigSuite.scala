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

package za.co.absa.pramen.core.app.config

import com.typesafe.config.ConfigFactory
import org.scalatest.WordSpec

import java.time.ZoneId

class GeneralConfigSuite extends WordSpec {
  "GeneralConfig" should {
    "deserialize the config properly" in {
      val configStr =
        s"""pramen {
           |  application.version = "1.0.0"
           |  environment.name = "DummyEnv"
           |  temporary.directory = "/dummy/dir"
           |  timezone = "Africa/Johannesburg"
           |}
           |""".stripMargin

      val config = ConfigFactory.parseString(configStr)

      val generalConfig = GeneralConfig.fromConfig(config)

      assert(generalConfig.applicationVersion == "1.0.0")
      assert(generalConfig.timezoneId == ZoneId.of("Africa/Johannesburg"))
      assert(generalConfig.environmentName == "DummyEnv")
      assert(generalConfig.temporaryDirectory == "/dummy/dir")
    }
  }
}
