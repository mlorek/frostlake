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

package dev.frostlake.parser;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * The lines of a fault inside a structured type's parentheses: an ARRAY's or an OBJECT's first token that begins
 * no type named twice and then the ')' around the cast or a later cast's lines; a field without a type, a second
 * element, a missing MAP value or VECTOR dimension named once, with the structured type's ')' or the next one; a
 * list nested in one judged as its cast's; and a column's faulty list followed by its properties, read as a
 * property's name and value (all live-verified).
 */
public class StructuredTypeListLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void faultsByALaterRule() {
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '5'. | syntax "
                + "error line 1 at position 19 unexpected '5'.",
            "SELECT NULL::ARRAY(5 NOT NULL)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '5'. | syntax "
                + "error line 1 at position 26 unexpected '5'.",
            "SELECT CAST(NULL AS ARRAY(5 NOT NULL))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '5'. | syntax "
                + "error line 1 at position 30 unexpected '5'.",
            "SELECT TRY_CAST(NULL AS ARRAY(5 NOT NULL))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected ')'.",
            "SELECT NULL::OBJECT(a)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ')'.",
            "SELECT CAST(NULL AS OBJECT(a))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected ')'.",
            "SELECT TRY_CAST(NULL AS OBJECT(a))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_t (a OBJECT(a))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '5'.",
            "SELECT NULL::OBJECT(a 5)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ','. | syntax "
                + "error line 1 at position 28 unexpected ')'.",
            "SELECT NULL::ARRAY(NUMBER, 5)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected ','. | syntax "
                + "error line 1 at position 35 unexpected ')'.",
            "SELECT CAST(NULL AS ARRAY(NUMBER, 5))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected ','. | syntax "
                + "error line 1 at position 39 unexpected ')'.",
            "SELECT TRY_CAST(NULL AS ARRAY(NUMBER, 5))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected ','. | syntax "
                + "error line 1 at position 46 unexpected '5'. | syntax error line 1 at position 48 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_t (a ARRAY(NUMBER, 5))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ''x''. | syntax "
                + "error line 1 at position 19 unexpected ''x''.",
            "SELECT NULL::ARRAY('x')");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ''x''. | syntax "
                + "error line 1 at position 26 unexpected ''x''.",
            "SELECT CAST(NULL AS ARRAY('x'))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''x''. | syntax "
                + "error line 1 at position 30 unexpected ''x''.",
            "SELECT TRY_CAST(NULL AS ARRAY('x'))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'NUMBER'.",
            "SELECT NULL::ARRAY(NUMBER NUMBER)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected ')'.",
            "SELECT CAST(NULL AS MAP())");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ')'.",
            "SELECT TRY_CAST(NULL AS MAP())");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_t (a MAP())");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected ')'.",
            "SELECT NULL::MAP(VARCHAR)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected ')'. | syntax "
                + "error line 1 at position 44 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_t (a MAP(VARCHAR))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected ','. | syntax "
                + "error line 1 at position 35 unexpected ')'.",
            "SELECT NULL::MAP(VARCHAR, NUMBER, 5)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected ','. | syntax "
                + "error line 1 at position 42 unexpected ')'.",
            "SELECT CAST(NULL AS MAP(VARCHAR, NUMBER, 5))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected ','. | syntax "
                + "error line 1 at position 46 unexpected ')'.",
            "SELECT TRY_CAST(NULL AS MAP(VARCHAR, NUMBER, 5))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected ','. | syntax "
                + "error line 1 at position 53 unexpected '5'. | syntax error line 1 at position 55 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_t (a MAP(VARCHAR, NUMBER, 5))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '5'. | syntax "
                + "error line 1 at position 20 unexpected '5'. | syntax error line 1 at position 22 unexpected ')'.",
            "SELECT (NULL::ARRAY(5))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '5'. | syntax "
                + "error line 1 at position 28 unexpected '5'. | syntax error line 1 at position 33 unexpected ')'.",
            "SELECT COALESCE(NULL::ARRAY(5), 1)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '5'. | syntax "
                + "error line 1 at position 19 unexpected '5'. | syntax error line 1 at position 41 unexpected '5'. | "
                + "syntax error line 1 at position 41 unexpected '5'.",
            "SELECT NULL::ARRAY(5) AS a, NULL::OBJECT(5) AS b");
    }

    @Test
    public void nestedListsAndVectors() {
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected ','. | syntax "
                + "error line 1 at position 38 unexpected ')'.",
            "SELECT NULL::MAP(VARCHAR, NUMBER(5,2,3))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected ','. | syntax "
                + "error line 1 at position 37 unexpected ')'.",
            "SELECT NULL::MAP(VARCHAR, VARCHAR(5,2))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ','. | syntax "
                + "error line 1 at position 30 unexpected ')'.",
            "SELECT NULL::ARRAY(VARCHAR(5,2))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected '('. | syntax "
                + "error line 1 at position 42 unexpected ')'.",
            "SELECT CAST(NULL AS MAP(VARCHAR, NUMBER()))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '('. | syntax "
                + "error line 1 at position 35 unexpected ')'.",
            "SELECT CAST(NULL AS ARRAY(NUMBER()))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected '('. | syntax "
                + "error line 1 at position 54 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_t (a MAP(VARCHAR, NUMBER()))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected '('. | syntax "
                + "error line 1 at position 47 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_t (a ARRAY(NUMBER()))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ')'.",
            "SELECT CAST(NULL AS VECTOR())");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ')'.",
            "SELECT TRY_CAST(NULL AS VECTOR())");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_t (a VECTOR())");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ')'.",
            "SELECT NULL::VECTOR(INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected ')'. | syntax "
                + "error line 1 at position 43 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_t (a VECTOR(INT))");
    }

    @Test
    public void aColumnsListThenItsProperties() {
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected ','. | syntax "
                + "error line 1 at position 51 unexpected '2'. | syntax error line 1 at position 61 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_a (a OBJECT(a VARCHAR(5,2), b INT))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ','. | syntax "
                + "error line 1 at position 43 unexpected ')'. | syntax error line 1 at position 44 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_a (a VARCHAR(5,x))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ','. | syntax "
                + "error line 1 at position 42 unexpected '2'. | syntax error line 1 at position 53 unexpected ''c''. "
                + "| syntax error line 1 at position 56 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_a (a VARCHAR(5,2) COMMENT 'c')");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ','. | syntax "
                + "error line 1 at position 42 unexpected '2'. | syntax error line 1 at position 53 unexpected "
                + "''en''. | syntax error line 1 at position 57 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_a (a VARCHAR(5,2) COLLATE 'en')");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ','. | syntax "
                + "error line 1 at position 42 unexpected '2'. | syntax error line 1 at position 45 unexpected "
                + "'UNIQUE'.",
            "CREATE OR REPLACE TABLE tp_a (a VARCHAR(5,2) UNIQUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ','. | syntax "
                + "error line 1 at position 42 unexpected '2'. | syntax error line 1 at position 46 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_a (a VARCHAR(5,2) b)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ','. | syntax "
                + "error line 1 at position 32 unexpected '2'. | syntax error line 1 at position 41 unexpected ')'.",
            "SELECT NULL::OBJECT(a VARCHAR(5,2), b INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected ','. | syntax "
                + "error line 1 at position 39 unexpected '2'. | syntax error line 1 at position 42 unexpected ')'.",
            "SELECT CAST(NULL AS OBJECT(a VARCHAR(5,2)))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ','. | syntax "
                + "error line 1 at position 42 unexpected '2'. | syntax error line 1 at position 53 unexpected ''x''. "
                + "| syntax error line 1 at position 56 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_a (a VARCHAR(5,2) DEFAULT 'x')");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected ','. | syntax "
                + "error line 1 at position 49 unexpected '2'. | syntax error line 1 at position 60 unexpected 'KEY'. "
                + "| syntax error line 1 at position 63 unexpected ')'.",
            "CREATE OR REPLACE HYBRID TABLE tp_h (a VARCHAR(5,2) PRIMARY KEY)");
    }
}
