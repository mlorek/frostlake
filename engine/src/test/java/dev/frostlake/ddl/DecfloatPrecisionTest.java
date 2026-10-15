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
package dev.frostlake.ddl;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A DECFLOAT takes one precision, 38. Any other number written after the type - in a cast, a column, a
 * parameter or a declaration, a negative one among them - is refused while the statement compiles, by the number
 * as written: {@code Unsupported DECFLOAT precision: 5.} A list of two numbers and an empty one are syntax
 * errors, as they are for any type that takes one. Every answer is live-verified.
 */
public class DecfloatPrecisionTest extends MeasuredCellsTestSupport {

    @Test
    public void aDecfloatsPrecisionIsThirtyEight() {
        assertAnswer(
            "REFUSED SQL compilation error:  | Unsupported DECFLOAT precision: 0.",
            "SELECT NULL::DECFLOAT(0)");
        assertAnswer(
            "REFUSED SQL compilation error:  | Unsupported DECFLOAT precision: 1.",
            "SELECT NULL::DECFLOAT(1)");
        assertAnswer(
            "REFUSED SQL compilation error:  | Unsupported DECFLOAT precision: 5.",
            "SELECT NULL::DECFLOAT(5)");
        assertAnswer(
            "REFUSED SQL compilation error:  | Unsupported DECFLOAT precision: 16.",
            "SELECT NULL::DECFLOAT(16)");
        assertAnswer(
            "REFUSED SQL compilation error:  | Unsupported DECFLOAT precision: 34.",
            "SELECT NULL::DECFLOAT(34)");
        assertAnswer(
            "REFUSED SQL compilation error:  | Unsupported DECFLOAT precision: 36.",
            "SELECT NULL::DECFLOAT(36)");
        assertAnswer(
            "REFUSED SQL compilation error:  | Unsupported DECFLOAT precision: 37.",
            "SELECT NULL::DECFLOAT(37)");
        assertAnswer(
            "null",
            "SELECT NULL::DECFLOAT(38)");
        assertAnswer(
            "REFUSED SQL compilation error:  | Unsupported DECFLOAT precision: 39.",
            "SELECT NULL::DECFLOAT(39)");
        assertAnswer(
            "REFUSED SQL compilation error:  | Unsupported DECFLOAT precision: 100.",
            "SELECT NULL::DECFLOAT(100)");
        assertAnswer(
            "REFUSED SQL compilation error:  | Unsupported DECFLOAT precision: -1.",
            "SELECT NULL::DECFLOAT(-1)");
        assertAnswer(
            "null",
            "SELECT NULL::DECFLOAT");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected ')'.",
            "SELECT NULL::DECFLOAT()");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected ','. | syntax error "
                + "line 1 at position 26 unexpected ')'.",
            "SELECT NULL::DECFLOAT(38,0)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ','. | syntax error "
                + "line 1 at position 25 unexpected ')'.",
            "SELECT NULL::DECFLOAT(5,2)");
        assertAnswer(
            "REFUSED SQL compilation error:  | Unsupported DECFLOAT precision: 5.",
            "SELECT CAST(NULL AS DECFLOAT(5))");
        assertAnswer(
            "null",
            "SELECT CAST(NULL AS DECFLOAT(38))");
        assertAnswer(
            "REFUSED SQL compilation error:  | Unsupported DECFLOAT precision: 5.",
            "SELECT TRY_CAST(NULL AS DECFLOAT(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '('.",
            "SELECT CAST(NULL AS DECFLOAT())");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ','. | syntax error "
                + "line 1 at position 33 unexpected ')'.",
            "SELECT CAST(NULL AS DECFLOAT(38,0))");
        assertAnswer(
            "REFUSED SQL compilation error:  | Unsupported DECFLOAT precision: 5.",
            "CREATE OR REPLACE TABLE tp_df (a DECFLOAT(5))");
        assertAnswer(
            "Table TP_DF successfully created.",
            "CREATE OR REPLACE TABLE tp_df (a DECFLOAT(38))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected '('.",
            "CREATE OR REPLACE TABLE tp_df (a DECFLOAT())");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected ','. | syntax error "
                + "line 1 at position 45 unexpected '0'. | syntax error line 1 at position 47 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_df (a DECFLOAT(38,0))");
        assertAnswer(
            "Table TP_DF successfully created.",
            "CREATE OR REPLACE TABLE tp_df (a DECFLOAT)");
        answer(
            "SELECT SYSTEM$TYPEOF(1::DECFLOAT), SYSTEM$TYPEOF(1::DECFLOAT(38))");
        assertAnswer(
            "REFUSED SQL compilation error:  | Unsupported DECFLOAT precision: 5.",
            "CREATE OR REPLACE FUNCTION tp_f(x DECFLOAT(5)) RETURNS INT AS '1'");
        assertAnswer(
            "REFUSED SQL compilation error:  | Unsupported DECFLOAT precision: 5.",
            "DECLARE x DECFLOAT(5); BEGIN RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error:  | Unsupported DECFLOAT precision: 5.",
            "SELECT 1::DECFLOAT(5) + 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected ')'.",
            "SELECT NULL::DECFLOAT(5), NULL::NUMBER()");
        assertAnswer(
            "TP_DF successfully dropped.",
            "DROP TABLE IF EXISTS tp_df");
        assertAnswer(
            "null",
            "SELECT NULL::DECFLOAT(38)::VARCHAR");
        assertAnswer(
            "REFUSED SQL compilation error:  | Unsupported DECFLOAT precision: 37.",
            "SELECT 1.5::DECFLOAT(37)");
    }
}
