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

import com.typesafe.config.Config
import org.slf4j.LoggerFactory
import za.co.absa.pramen.core.app.config.BookkeeperConfig.BOOKKEEPING_ENABLED
import za.co.absa.pramen.core.model.Constants.DATE_FORMAT_INTERNAL
import za.co.absa.pramen.core.runner.splitter.RunMode
import za.co.absa.pramen.core.utils.ConfigUtils
import za.co.absa.pramen.core.utils.DateUtils.convertStrToDate

import java.time.LocalDate

case class RuntimeConfig(
                          isDryRun: Boolean, // If true, the pipeline won't do any writes, just the list of jobs it would run
                          isRerun: Boolean, // If true, the pipeline will be forced rerun for the particular date
                          runTables: Seq[String], // Specifies which operations (identified by output table names) to run
                          isUndercover: Boolean, // If true, no bookkeeping will be done for the job
                          useLocks: Boolean, // If true, the pipeline will acquire locks before writing to a metastore table
                          checkOnlyLateData: Boolean,
                          checkOnlyNewData: Boolean,
                          warnIfNoData: Boolean,
                          nonZeroExitCodeIfNoData: Boolean,
                          emailIfNoChanges: Boolean,
                          runDate: LocalDate, // Specifies the date for which the pipeline should run (usually current date)
                          runDateTo: Option[LocalDate], // Specified the end date in case of historical rerun
                          isInverseOrder: Boolean,
                          parallelTasks: Int,
                          stopSparkSession: Boolean,
                          historicalRunMode: RunMode
                        )

object RuntimeConfig {
  private val log = LoggerFactory.getLogger(this.getClass)

  val INFORMATION_DATE_FORMAT_APP = "pramen.information.date.format"

  val DRY_RUN = "pramen.dry.run"
  val IS_RERUN = "pramen.runtime.is.rerun"
  val IS_INVERSE_ORDER = "pramen.runtime.inverse.order"
  val RUN_MODE = "pramen.runtime.run.mode"
  val RUN_TABLES = "pramen.runtime.run.tables"
  val UNDERCOVER = "pramen.undercover"
  val USE_LOCK = "pramen.use.lock"
  val CHECK_ONLY_LATE_DATA = "pramen.check.only.late.data"
  val CHECK_ONLY_NEW_DATA = "pramen.check.only.new.data"
  val WARN_NO_DATA = "pramen.warn.if.no.data"
  val NON_ZERO_EXIT_CODE_IF_NO_DATA = "pramen.non.zero.exit.code.if.no.data"
  val EMAIL_IF_NO_CHANGES = "pramen.email.if.no.changes"
  val CURRENT_DATE = "pramen.current.date"
  val LOAD_DATE_FROM = "pramen.load.date.from"
  val LOAD_DATE_TO = "pramen.load.date.to"
  val PARALLEL_TASKS = "pramen.parallel.tasks"
  val STOP_SPARK_SESSION = "pramen.stop.spark.session"

  def fromConfig(conf: Config): RuntimeConfig = {
    val infoDateFormat = conf.getString(INFORMATION_DATE_FORMAT_APP)

    def getDate(dateStr: String): LocalDate = {
      convertStrToDate(dateStr, DATE_FORMAT_INTERNAL, infoDateFormat)
    }

    val runMode = ConfigUtils.getOptionString(conf, RUN_MODE)
      .map(RunMode.fromString)
      .getOrElse(RunMode.CheckUpdates)

    val isDryRun = conf.getBoolean(DRY_RUN)
    val isUndercover = ConfigUtils.getOptionBoolean(conf, UNDERCOVER).getOrElse(false)

    if (isDryRun) {
      log.warn("DRY RUN MODE ON")
    }

    if (isUndercover) {
      log.warn("UNDERCOVER MODE ON - bookkeeping won't be updated")
    }

    val isRerun = ConfigUtils.getOptionBoolean(conf, IS_RERUN).getOrElse(false) || runMode == RunMode.ForceRun

    val currentDate = ConfigUtils.getOptionString(conf, CURRENT_DATE).map(getDate).getOrElse(LocalDate.now())
    val dateFromOpt = ConfigUtils.getOptionString(conf, LOAD_DATE_FROM).map(getDate)
    val dateToOpt = ConfigUtils.getOptionString(conf, LOAD_DATE_TO).map(getDate)

    val (dateFrom, dateTo) = (dateFromOpt, dateToOpt) match {
      case (Some(from), Some(to)) =>
        (from, Some(to))
      case (Some(from), None)     =>
        (from, None)
      case (None, Some(to))       =>
        (to, None)
      case (None, None)           =>
        (currentDate, None)
    }

    val bookkeepingEnabled = conf.getBoolean(BOOKKEEPING_ENABLED)

    val checkOnlyLateData = if (bookkeepingEnabled) {
      conf.getBoolean(CHECK_ONLY_LATE_DATA)
    } else {
      false
    }

    val checkOnlyNewData = if (bookkeepingEnabled) {
      conf.getBoolean(CHECK_ONLY_NEW_DATA)
    } else {
      log.info("Bookkeeping is DISABLED. Only new data will be checked.")
      true
    }

    RuntimeConfig(
      isDryRun = isDryRun,
      isRerun = isRerun,
      runTables = ConfigUtils.getOptListStrings(conf, RUN_TABLES),
      isUndercover = isUndercover,
      useLocks = conf.getBoolean(USE_LOCK),
      checkOnlyLateData = checkOnlyLateData,
      checkOnlyNewData = checkOnlyNewData,
      warnIfNoData = conf.getBoolean(WARN_NO_DATA),
      nonZeroExitCodeIfNoData = conf.getBoolean(NON_ZERO_EXIT_CODE_IF_NO_DATA),
      emailIfNoChanges = conf.getBoolean(EMAIL_IF_NO_CHANGES),
      runDate = dateFrom,
      runDateTo = dateTo,
      isInverseOrder = ConfigUtils.getOptionBoolean(conf, IS_INVERSE_ORDER).getOrElse(false),
      parallelTasks = conf.getInt(PARALLEL_TASKS),
      stopSparkSession = conf.getBoolean(STOP_SPARK_SESSION),
      runMode
    )
  }
}