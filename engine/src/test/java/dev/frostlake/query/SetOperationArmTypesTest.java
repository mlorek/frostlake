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
package dev.frostlake.query;

import static dev.frostlake.query.QueryAnswers.assertCells;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

/**
 * A set operation's arms meet as live folds them: a DATE beside a TIMESTAMP_LTZ becomes its midnight, a number or a
 * BOOLEAN before a VARIANT is a VARIANT, a text before a VARIANT keeps its own type, a BOOLEAN before a text reads
 * the text as a BOOLEAN while a text before a BOOLEAN stays a text, and a text or a date after a VARIANT, an ARRAY
 * or an OBJECT is refused (live-verified).
 */
public class SetOperationArmTypesTest extends BaseDatabaseTest {

    private static String inconsistent(final String expected, final String got) {
        return "SQL compilation error:|inconsistent data type for result columns for set operator input branches,"
            + " expected " + expected + ", got " + got;
    }

    @Test
    public void aDateBesideALocalTimestampBecomesItsMidnight() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
        try {
            assertCells(engine, new String[][] {
                {"SELECT TO_VARCHAR(x), SYSTEM$TYPEOF(x) FROM (SELECT '2024-01-01'::DATE AS x UNION ALL"
                    + " SELECT '2024-01-01 10:00:00'::TIMESTAMP_LTZ) ORDER BY 1",
                    "2024-01-01 00:00:00.000 Z, TIMESTAMP_LTZ(9)[SB16]"
                        + " | 2024-01-01 10:00:00.000 Z, TIMESTAMP_LTZ(9)[SB16]"},
                {"SELECT TO_VARCHAR(x) FROM (SELECT '2024-01-01 10:00:00'::TIMESTAMP_LTZ AS x UNION ALL"
                    + " SELECT '2024-01-01'::DATE) ORDER BY 1",
                    "2024-01-01 00:00:00.000 Z | 2024-01-01 10:00:00.000 Z"},
            });
        } finally {
            engine.execute("ALTER SESSION UNSET TIMEZONE");
        }
    }

    @Test
    public void aVariantArmDecidesAsLiveFoldsIt() {
        assertCells(engine, new String[][] {
            {"SELECT TYPEOF(x) FROM (SELECT 1::NUMBER(2,0) AS x UNION ALL SELECT 'x'::VARIANT) ORDER BY 1",
                "INTEGER | VARCHAR"},
            {"SELECT TYPEOF(x) FROM (SELECT 1::NUMBER(2,0) AS x UNION ALL SELECT '5'::VARIANT) ORDER BY 1",
                "INTEGER | VARCHAR"},
            {"SELECT TYPEOF(x) FROM (SELECT PARSE_JSON('1') AS x UNION ALL SELECT TRUE) ORDER BY 1",
                "BOOLEAN | INTEGER"},
            {"SELECT TYPEOF(x) FROM (SELECT '{\"a\":2}' AS x UNION ALL SELECT PARSE_JSON('{\"a\":1}')) ORDER BY 1",
                "SQL compilation error: error line 1 at position 7|Invalid argument types for function 'TYPEOF':"
                    + " (VARCHAR(7))"},
            {"SELECT TYPEOF(x), x:a FROM (SELECT PARSE_JSON('{\"a\":1}') AS x UNION ALL SELECT '{\"a\":2}') ORDER BY 1",
                inconsistent("VARCHAR(7)", "VARIANT")},
            {"SELECT TYPEOF(x) FROM (SELECT PARSE_JSON('1') AS x UNION ALL SELECT '2024-01-01'::DATE) ORDER BY 1",
                inconsistent("DATE", "VARIANT")},
            {"SELECT TYPEOF(x) FROM (SELECT ARRAY_CONSTRUCT(1) AS x UNION ALL SELECT 'a') ORDER BY 1",
                inconsistent("VARCHAR(1)", "ARRAY")},
        });
    }

    @Test
    public void aBooleanAndATextFoldByWhichLeads() {
        assertCells(engine, new String[][] {
            {"SELECT SYSTEM$TYPEOF(x) FROM (SELECT TRUE AS x UNION ALL SELECT 'false') ORDER BY 1",
                "BOOLEAN[SB1] | BOOLEAN[SB1]"},
            {"SELECT SYSTEM$TYPEOF(x) FROM (SELECT 'false' AS x UNION ALL SELECT TRUE) ORDER BY 1",
                "VARCHAR(5)[LOB] | VARCHAR(5)[LOB]"},
            {"SELECT x FROM (SELECT 'false' AS x UNION ALL SELECT TRUE) ORDER BY 1", "false | true"},
            {"SELECT x FROM (SELECT TRUE AS x UNION ALL SELECT 'maybe') ORDER BY 1",
                "Boolean value 'maybe' is not recognized"},
        });
    }
}
