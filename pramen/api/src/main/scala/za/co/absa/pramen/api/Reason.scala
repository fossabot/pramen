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

package za.co.absa.pramen.api

/** Reasons a Pramen job has failed. */
sealed trait Reason

object Reason {
  /** The transformation is ready to run */
  case object Ready extends Reason

  /** Data required to run the job is absent or not up to date. */
  case class NotReady(message: String) extends Reason

  /** It is too late to calculate data for the specified information date. */
  case class Skip(message: String) extends Reason
}
