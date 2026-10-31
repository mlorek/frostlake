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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How deep Java procedures run inside one another: sixteen in full, and a seventeenth is ended before its code
 * runs, since the session it is handed issues a statement as it opens - a SQL body at that depth runs until it
 * issues one. So sixteen SQL procedures calling a Java one that issues nothing are refused, each SQL body the
 * refusal leaves wording it once more, and the history row of a refused call is FAILED_WITH_ERROR with 100315.
 * The frames an uncaught refusal is worded with differ between the account and this engine, so such a refusal is
 * asked for its sentence (all live-verified).
 */
public class JavaBodyCallDepthTest extends MeasuredCellsTestSupport {

    private static final String REFUSAL = "Stored procedure execution error: Stored procedure exceeds the maximum "
        + "allowed number of recursive calls.";

    private void assertEnded(final String sql) {
        final String answered = answer(sql);
        assertTrue(answered.startsWith("REFUSED ") && answered.contains(REFUSAL), answered);
    }

    @Test
    public void javaAndSqlBodiesAtTheSeventeenthLevel() {
        engine.execute("CREATE OR REPLACE PROCEDURE j_rec(n INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' "
            + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
            + "com.snowflake.snowpark_java.*;\nclass H {\n  public static long run(Session s, long n) {\n    if "
            + "(n <= 0) {\n      return 0;\n    }\n    return s.sql(\"CALL j_rec(\" + (n - 1) + "
            + "\")\").collect()[0].getLong(0) + 1;\n  }\n}\n$$");
        engine.execute("CREATE OR REPLACE PROCEDURE j_leaf() RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' "
            + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
            + "com.snowflake.snowpark_java.*;\nclass H {\n  public static long run(Session s) {\n    return 0;\n "
            + " }\n}\n$$");
        engine.execute("CREATE OR REPLACE PROCEDURE sql_to_j(n NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ DECLARE r "
            + "NUMBER; BEGIN IF (n <= 0) THEN CALL j_leaf() INTO :r; RETURN r; END IF; CALL sql_to_j(:n - 1) "
            + "INTO :r; RETURN r + 1; END; $$");
        assertAnswer("14", "CALL j_rec(14)");
        assertAnswer("15", "CALL j_rec(15)");
        answer("CALL j_rec(16)");
        answer("CALL j_rec(20)");
        assertAnswer("FAILED_WITH_ERROR|100315",
            "SELECT EXECUTION_STATUS, ERROR_CODE FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION()) WHERE "
                + "QUERY_TEXT = 'CALL j_rec(20)' ORDER BY START_TIME DESC LIMIT 1");
        assertAnswer("14", "CALL sql_to_j(14)");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 83 : Uncaught "
                + "exception of type 'STATEMENT_ERROR' on line 1 at position 83 : Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 83 : Uncaught exception of type 'STATEMENT_ERROR' on line "
                + "1 at position 83 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 83 : "
                + "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 83 : Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 83 : Uncaught exception of type 'STATEMENT_ERROR' on line "
                + "1 at position 83 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 83 : "
                + "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 83 : Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 83 : Uncaught exception of type 'STATEMENT_ERROR' on line "
                + "1 at position 83 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 83 : "
                + "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 83 : Uncaught exception of type "
                + "'STATEMENT_ERROR' on line 1 at position 83 : Uncaught exception of type 'STATEMENT_ERROR' on line "
                + "1 at position 42 : Stored procedure execution error: Stored procedure exceeds the maximum allowed "
                + "number of recursive calls.",
            "CALL sql_to_j(15)");
    }

    @Test
    public void theSeventeenthJavaBodyIsEndedAsItOpens() {
        engine.execute("CREATE OR REPLACE PROCEDURE j_rec(n INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' "
            + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
            + "com.snowflake.snowpark_java.*;\nclass H {\n  public static long run(Session s, long n) {\n    if "
            + "(n <= 0) {\n      return 0;\n    }\n    return s.sql(\"CALL j_rec(\" + (n - 1) + "
            + "\")\").collect()[0].getLong(0) + 1;\n  }\n}\n$$");
        assertAnswer("15", "CALL j_rec(15)");
        // The seventeenth procedure would answer 0 without a statement of its own.
        assertEnded("CALL j_rec(16)");
        assertEnded("CALL j_rec(20)");
    }
}
