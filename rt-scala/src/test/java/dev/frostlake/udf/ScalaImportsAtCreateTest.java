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
package dev.frostlake.udf;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A Scala routine's IMPORTS judged as it is created, as a Java routine's are (live-verified): a file a stage does not
 * hold is refused for a procedure, a table procedure and a function alike.
 */
public class ScalaImportsAtCreateTest extends MeasuredCellsTestSupport {

    /** A Scala procedure's, table procedure's and function's imports. */
    @Test
    public void importsOfScalaRoutines() {
        engine.execute("CREATE OR REPLACE TABLE ti801 (a INT)");
        engine.execute("CREATE OR REPLACE STAGE st801");
        assertCells(new String[][] {
            {"CREATE OR REPLACE PROCEDURE sp801() RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION = '2.12' PACKAGES = "
                + "('com.snowflake:snowpark:latest') IMPORTS = ('@st801/nosuch_p7.jar') HANDLER = 'H.run' AS "
                + "$$\nobject H { def run(session: com.snowflake.snowpark.Session): String = \"x\" }\n$$",
                "REFUSED Remote file 'nosuch_p7.jar' was not found. If you are running a copy command, please make "
                + "sure files are not deleted when they are being loaded or files are not being loaded into two "
                + "different tables concurrently with auto purge option."},
            {"CREATE OR REPLACE PROCEDURE stp801() RETURNS TABLE (a INT) LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') IMPORTS = ('@st801/nosuch_p7.jar') HANDLER = 'H.run' "
                + "AS $$\nobject H { def run(session: com.snowflake.snowpark.Session): "
                + "com.snowflake.snowpark.DataFrame = session.sql(\"SELECT 1 AS a\") }\n$$",
                "REFUSED Remote file 'nosuch_p7.jar' was not found. If you are running a copy command, please make "
                + "sure files are not deleted when they are being loaded or files are not being loaded into two "
                + "different tables concurrently with auto purge option."},
            {"CREATE OR REPLACE FUNCTION sf801(x INT) RETURNS INT LANGUAGE SCALA RUNTIME_VERSION = '2.12' IMPORTS = "
                + "('@st801/nosuch_p7.jar') HANDLER = 'H.f' AS $$\nobject H { def f(x: Int): Int = x }\n$$",
                "REFUSED Remote file 'nosuch_p7.jar' was not found. If you are running a copy command, please make "
                + "sure files are not deleted when they are being loaded or files are not being loaded into two "
                + "different tables concurrently with auto purge option."},
        });
    }
}
