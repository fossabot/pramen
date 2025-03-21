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

package za.co.absa.pramen.core.tests.runner

import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import org.scalatest.WordSpec
import za.co.absa.pramen.core.base.SparkTestBase
import za.co.absa.pramen.core.mocks.job.JobSpy
import za.co.absa.pramen.core.mocks.state.PipelineStateSpy
import za.co.absa.pramen.core.runner.AppRunner
import za.co.absa.pramen.core.state.PipelineState
import za.co.absa.pramen.core.utils.ResourceUtils
import za.co.absa.pramen.core.{AppContextFactory, RuntimeConfigFactory}

import scala.util.{Failure, Success}

class AppRunnerSuite extends WordSpec with SparkTestBase {
  "runPipeline()" should {
    "run the mock pipeline" in {
      val conf: Config = getTestConfig()

      val exitCode = AppRunner.runPipeline(conf)

      assert(exitCode == 0)
    }
  }

  "createPipelineState()" should {
    "be able to initialize a pipeline state" in {
      val conf: Config = getTestConfig()

      val state = AppRunner.createPipelineState(conf)

      assert(state != null)
    }
  }

  "filterJobs()" should {
    val conf: Config = getTestConfig()

    val state = AppRunner.createPipelineState(conf).get

    val jobs = Range(1, 10)
      .map(i => new JobSpy(jobName = s"Job $i", outputTableIn = s"table$i"))

    "do not change the input list of jobs if no run tables are specified" in {
      val runtimeConfig = RuntimeConfigFactory.getDummyRuntimeConfig(runTables = Seq.empty[String])

      val filteredJobs = AppRunner.filterJobs(state, jobs, runtimeConfig).get

      assert(filteredJobs.size == 9)
    }

    "filter out jobs that are not specified" in {
      val runtimeConfig = RuntimeConfigFactory.getDummyRuntimeConfig(runTables = Seq("table2", "table4"))


      val filteredJobs = AppRunner.filterJobs(state, jobs, runtimeConfig).get

      assert(filteredJobs.size == 2)
      assert(filteredJobs.map(_.outputTable.name).contains("table2"))
      assert(filteredJobs.map(_.outputTable.name).contains("table4"))
    }
  }

  "createAppContext()" should {
    "be able to initialize proper application context" in {
      implicit val conf: Config = getTestConfig()
      implicit val state: PipelineState = getMockPipelineState

      val appContext = AppRunner.createAppContext.get

      assert(appContext.bookkeeper != null)

      AppContextFactory.close()
    }

    "return a failure on error" in {
      implicit val conf: Config = ConfigFactory.empty()
      implicit val state: PipelineState = getMockPipelineState

      val appContextTry = AppRunner.createAppContext

      appContextTry match {
        case Success(_)  =>
          fail("Should have failed")
        case Failure(ex) =>
          assert(ex.getMessage.contains("An error occurred during initialization of the pipeline"))
          assert(ex.getCause.getMessage.contains("No configuration setting found for key 'pramen'"))
      }

      AppContextFactory.close()
    }
  }

  "handleFailure()" should {
    val state = getMockPipelineState

    "pass around success" in {
      val success = AppRunner.handleFailure(Success(1), state, "dummy stage")
      assert(success.isSuccess)
      assert(success.get == 1)
    }

    "add stage info to a failure" in {
      val failure = AppRunner.handleFailure(Failure(new Exception("Test failure")), state, "dummy stage")
      assert(failure.isFailure)
      assert(failure.failed.get.getMessage.contains("An error occurred during dummy stage"))
      assert(failure.failed.get.getCause.getMessage.contains("Test failure"))
    }
  }

  private def getTestConfig(extraConf: Config = ConfigFactory.empty()): Config = {
    val configStr = ResourceUtils.getResourceString("/test/config/pipeline_v2_empty.conf")

    val configBase = ConfigFactory.parseString(configStr)

    extraConf
      .withFallback(configBase)
      .withFallback(ConfigFactory.load())
      .withValue("pramen.stop.spark.session", ConfigValueFactory.fromAnyRef(false))
      .resolve()
  }

  private def getMockPipelineState: PipelineState = {
    new PipelineStateSpy
  }
}
