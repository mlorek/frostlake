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
 * A Java procedure's answer shapes: a scalar procedure whose handler returns a DataFrame is refused as it is
 * created, and a text the handler returns for RETURNS ARRAY or RETURNS OBJECT is read as the value (all
 * live-verified).
 */
public class JavaProcedureAnswerShapesTest extends MeasuredCellsTestSupport {

    @Test
    public void answerShapes() {
        assertAnswer("REFUSED Expected stored procedure to return TABLE if and only if handler method returns "
                + "DATAFRAME in function JDF with handler H.run",
            "CREATE OR REPLACE PROCEDURE jdf() RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
                + "com.snowflake.snowpark_java.*;\nclass H {\n  public static DataFrame run(Session s) {\n    return "
                + "s.sql(\"SELECT 1 AS a\");\n  }\n}\n$$");
        assertAnswer("REFUSED SQL compilation error: | Unknown function JDF.", "CALL jdf()");
        assertAnswer("Function JARR successfully created.",
            "CREATE OR REPLACE PROCEDURE jarr() RETURNS ARRAY LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
                + "com.snowflake.snowpark_java.*;\nclass H {\n  public static String run(Session s) {\n    return "
                + "\"[1, 2]\";\n  }\n}\n$$");
        assertAnswer("[1,2]", "CALL jarr()");
        assertAnswer("Function JOBJ successfully created.",
            "CREATE OR REPLACE PROCEDURE jobj() RETURNS OBJECT LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
                + "com.snowflake.snowpark_java.*;\nclass H {\n  public static String run(Session s) {\n    return "
                + "\"{\\\"a\\\": 1}\";\n  }\n}\n$$");
        assertAnswer("{\"a\":1}", "CALL jobj()");
    }
}
