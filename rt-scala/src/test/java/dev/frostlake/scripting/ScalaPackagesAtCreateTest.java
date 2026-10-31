/*
 * Copyright 2026 MLorek
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

package dev.frostlake.scripting;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A Scala routine's PACKAGES are judged against the account's Scala list as it is created: an unknown package,
 * an unknown version and a name without a version are not supported; snowpark, snowpark_2.12 and snowpark_2.13
 * at latest and a listed version are taken (all live-verified).
 */
public class ScalaPackagesAtCreateTest extends MeasuredCellsTestSupport {

    @Test
    public void scalaPackages() {
        assertAnswer("REFUSED SQL compilation error: Package 'com.example:nosuch:1.0' is not supported.",
            "CREATE OR REPLACE FUNCTION pk_sc1() RETURNS INT LANGUAGE SCALA RUNTIME_VERSION = '2.12' PACKAGES = "
                + "('com.example:nosuch:1.0') HANDLER = 'H.f' AS 'object H { def f(): Int = 1 }'");
        assertAnswer("REFUSED SQL compilation error: Package 'com.snowflake:snowpark_2.12:9.9.9' is not supported.",
            "CREATE OR REPLACE FUNCTION pk_sc2() RETURNS INT LANGUAGE SCALA RUNTIME_VERSION = '2.12' PACKAGES = "
                + "('com.snowflake:snowpark_2.12:9.9.9') HANDLER = 'H.f' AS 'object H { def f(): Int = 1 }'");
        assertAnswer("Function PK_SC3 successfully created.",
            "CREATE OR REPLACE FUNCTION pk_sc3() RETURNS INT LANGUAGE SCALA RUNTIME_VERSION = '2.12' PACKAGES = "
                + "('com.snowflake:snowpark_2.12:latest') HANDLER = 'H.f' AS 'object H { def f(): Int = 1 }'");
        assertAnswer("Function PK_SC4 successfully created.",
            "CREATE OR REPLACE FUNCTION pk_sc4() RETURNS INT LANGUAGE SCALA RUNTIME_VERSION = '2.12' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.f' AS 'object H { def f(): Int = 1 }'");
        assertAnswer("1", "SELECT pk_sc4()");
    }

    @Test
    public void scalaPackageVersions() {
        assertAnswer("Function PK_SC5 successfully created.",
            "CREATE OR REPLACE FUNCTION pk_sc5() RETURNS INT LANGUAGE SCALA RUNTIME_VERSION = '2.12' PACKAGES = "
                + "('com.snowflake:snowpark_2.13:latest') HANDLER = 'H.f' AS 'object H { def f(): Int = 1 }'");
        assertAnswer("REFUSED SQL compilation error: Package 'com.snowflake:snowpark_2.12:1.14.0' is not supported.",
            "CREATE OR REPLACE FUNCTION pk_sc6() RETURNS INT LANGUAGE SCALA RUNTIME_VERSION = '2.12' PACKAGES = "
                + "('com.snowflake:snowpark_2.12:1.14.0') HANDLER = 'H.f' AS 'object H { def f(): Int = 1 }'");
        assertAnswer("Function PK_SC7 successfully created.",
            "CREATE OR REPLACE FUNCTION pk_sc7() RETURNS INT LANGUAGE SCALA RUNTIME_VERSION = '2.12' PACKAGES = "
                + "('com.snowflake:snowpark:1.14.0') HANDLER = 'H.f' AS 'object H { def f(): Int = 1 }'");
        assertAnswer("REFUSED SQL compilation error: Package 'com.snowflake:snowpark_2.12' is not supported.",
            "CREATE OR REPLACE FUNCTION pk_sc8() RETURNS INT LANGUAGE SCALA RUNTIME_VERSION = '2.12' PACKAGES = "
                + "('com.snowflake:snowpark_2.12') HANDLER = 'H.f' AS 'object H { def f(): Int = 1 }'");
    }
}
