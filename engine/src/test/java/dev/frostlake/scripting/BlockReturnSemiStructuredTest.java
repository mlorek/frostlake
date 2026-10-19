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
 * A block's RETURN of an ARRAY, an OBJECT or a VARIANT, from a variable, a constructor or a procedure: the
 * value is answered as the semi-structured value it is, a FLOAT inside it spelled as a FLOAT
 * (live-verified).
 */
public class BlockReturnSemiStructuredTest extends MeasuredCellsTestSupport {

    @Test
    public void arraysAndObjects() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        assertAnswer(
            "[1.500000000000000e+00]",
            "BEGIN LET x FLOAT := 1.5; RETURN ARRAY_CONSTRUCT(x); END;");
        assertAnswer(
            "[1.500000000000000e+00]",
            "BEGIN RETURN ARRAY_CONSTRUCT(1.5::FLOAT); END;");
        assertAnswer(
            "[1,2]",
            "BEGIN RETURN ARRAY_CONSTRUCT(1, 2); END;");
        assertAnswer(
            "[\"a\"]",
            "BEGIN RETURN ARRAY_CONSTRUCT('a'); END;");
        assertAnswer(
            "[1]",
            "BEGIN LET x INT := 1; RETURN ARRAY_CONSTRUCT(x); END;");
        assertAnswer(
            "[\"a\",\"a\"]",
            "BEGIN LET x VARCHAR := 'a'; RETURN ARRAY_CONSTRUCT(x, x); END;");
        assertAnswer(
            "{\"a\":1.500000000000000e+00}",
            "BEGIN RETURN OBJECT_CONSTRUCT('a', 1.5::FLOAT); END;");
        assertAnswer(
            "NaN",
            "BEGIN LET x FLOAT := 'NaN'::FLOAT; RETURN x; END;");
        assertAnswer(
            "[NaN]",
            "BEGIN LET x FLOAT := 'NaN'::FLOAT; RETURN ARRAY_CONSTRUCT(x)::VARCHAR; END;");
        assertAnswer(
            "[1,2]",
            "BEGIN LET a ARRAY := ARRAY_CONSTRUCT(1, 2); RETURN a; END;");
        assertAnswer(
            "[]",
            "BEGIN RETURN ARRAY_CONSTRUCT(); END;");
        assertAnswer(
            "[1.5]",
            "BEGIN RETURN PARSE_JSON('[1.5]'); END;");
        assertAnswer(
            "{\"a\":[1,2]}",
            "BEGIN RETURN PARSE_JSON('{\"a\": [1, 2]}'); END;");
        assertAnswer(
            "1.5",
            "BEGIN LET x FLOAT := 1.5; LET v VARIANT := x; RETURN v; END;");
        assertAnswer(
            "[2.000000000000000e+00]",
            "BEGIN LET x FLOAT := 2; RETURN ARRAY_CONSTRUCT(x); END;");
        assertAnswer(
            "[1.5]",
            "BEGIN LET x NUMBER(5,2) := 1.5; RETURN ARRAY_CONSTRUCT(x); END;");
        assertAnswer(
            "{\"a\":1.500000000000000e+00}",
            "BEGIN LET x FLOAT := 1.5; RETURN {'a': x}; END;");
    }

    @Test
    public void fromAProcedure() {
        try {
            answer(
                "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
            answer(
                "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
            assertAnswer(
                "Function PA successfully created.",
                "CREATE OR REPLACE PROCEDURE pa() RETURNS VARIANT LANGUAGE SQL AS $$BEGIN LET x FLOAT := 1.5; "
                    + "RETURN ARRAY_CONSTRUCT(x); END$$");
            assertAnswer(
                "[1.500000000000000e+00]",
                "CALL pa()");
        } finally {
            answer(
                "DROP PROCEDURE IF EXISTS pa()");
        }
    }
}
