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

import org.junit.jupiter.api.Test;

/**
 * A context word written without its parentheses - CURRENT_DATE, CURRENT_TIME, CURRENT_TIMESTAMP, CURRENT_USER,
 * LOCALTIME, LOCALTIMESTAMP - is a name to a block's own expression, as CURRENT_ROLE and SYSDATE are, and no
 * variable has it: a RETURN, a condition, an assignment and a declaration's default refuse it as an invalid
 * identifier while the block compiles, and an untyped LET has no type to infer from it. With its parentheses
 * the word is the function, and a query the block runs reads the bare word as one. Every answer is
 * live-verified.
 */
public class BlockContextWordTest extends MeasuredCellsTestSupport {

    @Test
    public void aBareContextWordIsAName() {
        engine.execute(
            "CREATE OR REPLACE TABLE tw (d DATE, t TIMESTAMP_NTZ, s VARCHAR(10), n NUMBER(10,2), i INT)");
        engine.execute(
            "INSERT INTO tw VALUES ('2024-05-06', '2024-05-06 10:11:12', 'abc', 1.5, 7), (NULL, NULL, NULL, NULL, "
                + "NULL)");
        try {
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'CURRENT_DATE'",
                "BEGIN RETURN CURRENT_DATE; END;");
            assertAnswer(
                "true",
                "BEGIN RETURN CURRENT_DATE() IS NOT NULL; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier "
                    + "'CURRENT_TIMESTAMP'",
                "BEGIN RETURN CURRENT_TIMESTAMP IS NOT NULL; END;");
            assertAnswer(
                "true",
                "BEGIN RETURN CURRENT_TIMESTAMP() IS NOT NULL; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'CURRENT_TIME'",
                "BEGIN RETURN CURRENT_TIME IS NOT NULL; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'CURRENT_USER'",
                "BEGIN RETURN CURRENT_USER IS NOT NULL; END;");
            assertAnswer(
                "true",
                "BEGIN RETURN CURRENT_USER() IS NOT NULL; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'LOCALTIMESTAMP'",
                "BEGIN RETURN LOCALTIMESTAMP IS NOT NULL; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'LOCALTIME'",
                "BEGIN RETURN LOCALTIME IS NOT NULL; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'CURRENT_ROLE'",
                "BEGIN RETURN CURRENT_ROLE IS NOT NULL; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier "
                    + "'CURRENT_DATABASE'",
                "BEGIN RETURN CURRENT_DATABASE IS NOT NULL; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'CURRENT_SCHEMA'",
                "BEGIN RETURN CURRENT_SCHEMA IS NOT NULL; END;");
            assertAnswer(
                "true",
                "BEGIN RETURN SYSDATE() IS NOT NULL; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'SYSDATE'",
                "BEGIN RETURN SYSDATE IS NOT NULL; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 6 |  variable 'X' cannot have its type "
                    + "inferred from initializer",
                "BEGIN LET x := CURRENT_DATE; RETURN x IS NOT NULL; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 23 | invalid identifier 'CURRENT_DATE'",
                "DECLARE x DATE DEFAULT CURRENT_DATE; BEGIN RETURN x IS NOT NULL; END;");
            assertAnswer(
                "true",
                "DECLARE x DATE DEFAULT CURRENT_DATE(); BEGIN RETURN x IS NOT NULL; END;");
            assertAnswer(
                "true",
                "BEGIN RETURN (SELECT CURRENT_DATE) IS NOT NULL; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 10 | invalid identifier 'CURRENT_DATE'",
                "BEGIN IF (CURRENT_DATE > '2020-01-01') THEN RETURN 1; END IF; RETURN 2; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier "
                    + "'CURRENT_WAREHOUSE'",
                "BEGIN RETURN CURRENT_WAREHOUSE IS NOT NULL; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'CURRENT_SESSION'",
                "BEGIN RETURN CURRENT_SESSION IS NOT NULL; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 25 | invalid identifier 'CURRENT_DATE'",
                "BEGIN LET x := 1; WHILE (CURRENT_DATE IS NULL) DO x := 2; END WHILE; RETURN x; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'CURRENT_DATE'",
                "BEGIN RETURN current_date IS NOT NULL; END;");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }
}
