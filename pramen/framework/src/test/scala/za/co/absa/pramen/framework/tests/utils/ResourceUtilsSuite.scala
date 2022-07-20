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

package za.co.absa.pramen.framework.tests.utils

import org.scalatest.WordSpec
import za.co.absa.pramen.framework.utils.ResourceUtils

class ResourceUtilsSuite extends WordSpec {
  "getResourceString" should {
    "return the content of the resource" in {
      val str = ResourceUtils.getResourceString("/test/testResource.txt")

      assert(str == "Hello")
    }

    "thrown an exception if the resource is not found" in {
      intercept[NullPointerException] {
        ResourceUtils.getResourceString("test/NoSuchFile.txt")
      }
    }
  }

}
