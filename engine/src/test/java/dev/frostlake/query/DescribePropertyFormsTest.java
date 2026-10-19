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
 * The properties written after a DESCRIBE's object. A comma may stand between two of them and the last TYPE
 * wins; a value is a word, a number, a string - a $$ string too - a bind, a stage reference or a list in
 * parentheses, an empty one included, and every name but TYPE is refused as an invalid parameter once the
 * statement has been read whole: a name left without its value is the syntax error first. Every answer is
 * live-verified.
 */
public class DescribePropertyFormsTest extends MeasuredCellsTestSupport {

    @Test
    public void commasValuesAndTheSyntaxCheck() {
        engine.execute(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO FZ VALUES (1, TRUE), (2, FALSE)");
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        engine.execute(
            "CREATE OR REPLACE VIEW vv AS SELECT 1 AS c");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 x = (1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 x = ()");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 x = ('a', 'b')");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 x = ?");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 x = :b");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 x = $$x$$");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 x = 1, y = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 TYPE = COLUMNS, x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 (a) x = (1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 (a) x = @s");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 (a) x = @~");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 (a) x = @%t1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 (a) x = @s.t");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 (a) x = @s y = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 (a) x = @s/p");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 x = TRUE y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 x = NULL y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 x = (1) y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 TYPE = COLUMNS y");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 TYPE = COLUMNS x = 1");
        assertAnswer(
            "REFUSED Invalid value specified for property 'TYPE'",
            "DESCRIBE TABLE t1 TYPE = (COLUMNS)");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 TYPE = 'COLUMNS'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 TYPE = COLUMNS,");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected ','.",
            "DESCRIBE TABLE t1 , TYPE = COLUMNS");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected ','.",
            "DESCRIBE TABLE t1 TYPE = COLUMNS,, TYPE = STAGE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ','.",
            "DESCRIBE TABLE t1 TYPE = COLUMNS, , TYPE = STAGE");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 TYPE = COLUMNS;");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 TYPE = columns");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [$$ COLUMNS $$] for parameter 'TYPE'",
            "DESCRIBE TABLE t1 TYPE = $$ COLUMNS $$");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 TYPE = STAGE, x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 TYPE = STAGE x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'y'.",
            "DESCRIBE TABLE t1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 TYPE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 TYPE =");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 TYPE = COLUMNS TYPE");
        assertAnswer(
            "REFUSED Invalid value specified for property 'TYPE'",
            "DESCRIBE TABLE t1 TYPE = (COLUMNS, STAGE)");
        assertAnswer(
            "REFUSED Invalid value specified for property 'TYPE'",
            "DESCRIBE TABLE t1 TYPE = ()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 25 | Bind variable ? not set.",
            "DESCRIBE TABLE t1 TYPE = ?");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 25 | Bind variable b not set.",
            "DESCRIBE TABLE t1 TYPE = :b");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [@s] for parameter 'TYPE'",
            "DESCRIBE TABLE t1 TYPE = @s");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [1] for parameter 'TYPE'",
            "DESCRIBE TABLE t1 TYPE = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [NULL] for parameter 'TYPE'",
            "DESCRIBE TABLE t1 TYPE = NULL");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [TRUE] for parameter 'TYPE'",
            "DESCRIBE TABLE t1 TYPE = TRUE");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 TYPE = COLUMNS, TYPE = COLUMNS");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [x] for parameter 'TYPE'",
            "DESCRIBE TABLE t1 TYPE = x");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [-1] for parameter 'TYPE'",
            "DESCRIBE TABLE t1 TYPE = -1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 x = -1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 x = (-1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 x = (a, b)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 x = (a.b, c)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 x = a.b");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 x = $1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 x = 1.5");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ''b''.",
            "DESCRIBE TABLE t1 x = 'a' 'b'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('. | syntax error "
                + "line 1 at position 29 unexpected ')'.",
            "DESCRIBE TABLE t1 x = (1, (2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ')'.",
            "DESCRIBE TABLE t1 x = (1,)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 x = ($$a$$, ?)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 x = (:b)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 x = @s, y = @t");
        assertAnswer(
            "STAGE_LOCATION|URL|String||",
            "DESCRIBE VIEW vv TYPE = COLUMNS, TYPE = STAGE");
    }
}
