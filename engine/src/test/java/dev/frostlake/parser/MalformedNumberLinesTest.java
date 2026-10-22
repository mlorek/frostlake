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
 * A number the lexer gives up on - an exponent that stops, a hexadecimal prefix, a second point no digit follows:
 * the character after it told by its code, or the end of the text, and the text read without the number and that
 * character (all live-verified).
 */
public class MalformedNumberLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void unfinishedNumbers() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT, b INT)");
        engine.execute("INSERT INTO t1 VALUES (1, 2)");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 9 near '<EOF>'. | syntax error "
                + "line 1 at position 9 unexpected '<EOF>'.",
            "SELECT 1e");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 9 near '32'. | syntax error "
                + "line 1 at position 10 unexpected 'FROM'.",
            "SELECT 1e FROM t1");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 13 near '<EOF>'. | syntax error "
                + "line 1 at position 13 unexpected '<EOF>'.",
            "SELECT 1 + 1e");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 9 near '32'.", "SELECT 1e + 1");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 9 near '44'.", "SELECT 1e, 2");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 9 near '32'. | syntax error "
                + "line 1 at position 10 unexpected 'AS'.",
            "SELECT 1e AS x");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 29 near '<EOF>'. | syntax error "
                + "line 1 at position 29 unexpected '<EOF>'.",
            "SELECT 1 FROM t1 WHERE a = 1e");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 29 near '32'. | syntax error "
                + "line 1 at position 30 unexpected 'AND'.",
            "SELECT 1 FROM t1 WHERE a = 1e AND b = 2");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 25 near '<EOF>'.",
            "SELECT 1 FROM t1 LIMIT 1e");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 13 near '41'. | syntax error "
                + "line 1 at position 14 unexpected '<EOF>'. | syntax error line 1 at position 10 unexpected '('.",
            "SELECT ABS(1e)");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 28 near '<EOF>'. | syntax error "
                + "line 1 at position 28 unexpected '<EOF>'.",
            "SELECT 1 FROM t1 ORDER BY 1e");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 10 near '<EOF>'. | syntax error "
                + "line 1 at position 10 unexpected '<EOF>'.",
            "SELECT 1.e");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 10 near '32'. | syntax error "
                + "line 1 at position 11 unexpected 'FROM'.",
            "SELECT 1.e FROM t1");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 14 near '<EOF>'. | syntax error "
                + "line 1 at position 14 unexpected '<EOF>'.",
            "SELECT 1 + 1.e");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 10 near '32'.", "SELECT 1.e + 1");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 10 near '44'.", "SELECT 1.e, 2");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 10 near '32'. | syntax error "
                + "line 1 at position 11 unexpected 'AS'.",
            "SELECT 1.e AS x");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 30 near '<EOF>'. | syntax error "
                + "line 1 at position 30 unexpected '<EOF>'.",
            "SELECT 1 FROM t1 WHERE a = 1.e");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 30 near '32'. | syntax error "
                + "line 1 at position 31 unexpected 'AND'.",
            "SELECT 1 FROM t1 WHERE a = 1.e AND b = 2");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 26 near '<EOF>'.",
            "SELECT 1 FROM t1 LIMIT 1.e");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 14 near '41'. | syntax error "
                + "line 1 at position 15 unexpected '<EOF>'. | syntax error line 1 at position 10 unexpected '('.",
            "SELECT ABS(1.e)");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 29 near '<EOF>'. | syntax error "
                + "line 1 at position 29 unexpected '<EOF>'.",
            "SELECT 1 FROM t1 ORDER BY 1.e");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 10 near '<EOF>'. | syntax error "
                + "line 1 at position 10 unexpected '<EOF>'.",
            "SELECT 1e+");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 10 near '32'. | syntax error "
                + "line 1 at position 11 unexpected 'FROM'.",
            "SELECT 1e+ FROM t1");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 14 near '<EOF>'. | syntax error "
                + "line 1 at position 14 unexpected '<EOF>'.",
            "SELECT 1 + 1e+");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 10 near '32'.", "SELECT 1e+ + 1");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 10 near '44'.", "SELECT 1e+, 2");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 10 near '32'. | syntax error "
                + "line 1 at position 11 unexpected 'AS'.",
            "SELECT 1e+ AS x");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 30 near '<EOF>'. | syntax error "
                + "line 1 at position 30 unexpected '<EOF>'.",
            "SELECT 1 FROM t1 WHERE a = 1e+");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 30 near '32'. | syntax error "
                + "line 1 at position 31 unexpected 'AND'.",
            "SELECT 1 FROM t1 WHERE a = 1e+ AND b = 2");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 26 near '<EOF>'.",
            "SELECT 1 FROM t1 LIMIT 1e+");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 14 near '41'. | syntax error "
                + "line 1 at position 15 unexpected '<EOF>'. | syntax error line 1 at position 10 unexpected '('.",
            "SELECT ABS(1e+)");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 29 near '<EOF>'. | syntax error "
                + "line 1 at position 29 unexpected '<EOF>'.",
            "SELECT 1 FROM t1 ORDER BY 1e+");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 11 near '<EOF>'. | syntax error "
                + "line 1 at position 11 unexpected '<EOF>'.",
            "SELECT 1.5.");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 11 near '32'. | syntax error "
                + "line 1 at position 12 unexpected 'FROM'.",
            "SELECT 1.5. FROM t1");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 15 near '<EOF>'. | syntax error "
                + "line 1 at position 15 unexpected '<EOF>'.",
            "SELECT 1 + 1.5.");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 11 near '32'.",
            "SELECT 1.5. + 1");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 11 near '44'.", "SELECT 1.5., 2");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 11 near '32'. | syntax error "
                + "line 1 at position 12 unexpected 'AS'.",
            "SELECT 1.5. AS x");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 31 near '<EOF>'. | syntax error "
                + "line 1 at position 31 unexpected '<EOF>'.",
            "SELECT 1 FROM t1 WHERE a = 1.5.");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 31 near '32'. | syntax error "
                + "line 1 at position 32 unexpected 'AND'.",
            "SELECT 1 FROM t1 WHERE a = 1.5. AND b = 2");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 27 near '<EOF>'.",
            "SELECT 1 FROM t1 LIMIT 1.5.");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 15 near '41'. | syntax error "
                + "line 1 at position 16 unexpected '<EOF>'. | syntax error line 1 at position 10 unexpected '('.",
            "SELECT ABS(1.5.)");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 30 near '<EOF>'. | syntax error "
                + "line 1 at position 30 unexpected '<EOF>'.",
            "SELECT 1 FROM t1 ORDER BY 1.5.");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 10 near '<EOF>'. | syntax error "
                + "line 1 at position 10 unexpected '<EOF>'.",
            "SELECT 1E-");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 10 near '32'. | syntax error "
                + "line 1 at position 11 unexpected 'FROM'.",
            "SELECT 1E- FROM t1");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 14 near '<EOF>'. | syntax error "
                + "line 1 at position 14 unexpected '<EOF>'.",
            "SELECT 1 + 1E-");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 10 near '32'.", "SELECT 1E- + 1");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 10 near '44'.", "SELECT 1E-, 2");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 10 near '32'. | syntax error "
                + "line 1 at position 11 unexpected 'AS'.",
            "SELECT 1E- AS x");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 30 near '<EOF>'. | syntax error "
                + "line 1 at position 30 unexpected '<EOF>'.",
            "SELECT 1 FROM t1 WHERE a = 1E-");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 30 near '32'. | syntax error "
                + "line 1 at position 31 unexpected 'AND'.",
            "SELECT 1 FROM t1 WHERE a = 1E- AND b = 2");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 26 near '<EOF>'.",
            "SELECT 1 FROM t1 LIMIT 1E-");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 14 near '41'. | syntax error "
                + "line 1 at position 15 unexpected '<EOF>'. | syntax error line 1 at position 10 unexpected '('.",
            "SELECT ABS(1E-)");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 29 near '<EOF>'. | syntax error "
                + "line 1 at position 29 unexpected '<EOF>'.",
            "SELECT 1 FROM t1 ORDER BY 1E-");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 11 near '<EOF>'. | syntax error "
                + "line 1 at position 11 unexpected '<EOF>'.",
            "SELECT 1.5e");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 11 near '32'. | syntax error "
                + "line 1 at position 12 unexpected 'FROM'.",
            "SELECT 1.5e FROM t1");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 15 near '<EOF>'. | syntax error "
                + "line 1 at position 15 unexpected '<EOF>'.",
            "SELECT 1 + 1.5e");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 11 near '32'.",
            "SELECT 1.5e + 1");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 11 near '44'.", "SELECT 1.5e, 2");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 11 near '32'. | syntax error "
                + "line 1 at position 12 unexpected 'AS'.",
            "SELECT 1.5e AS x");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 31 near '<EOF>'. | syntax error "
                + "line 1 at position 31 unexpected '<EOF>'.",
            "SELECT 1 FROM t1 WHERE a = 1.5e");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 31 near '32'. | syntax error "
                + "line 1 at position 32 unexpected 'AND'.",
            "SELECT 1 FROM t1 WHERE a = 1.5e AND b = 2");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 27 near '<EOF>'.",
            "SELECT 1 FROM t1 LIMIT 1.5e");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 15 near '41'. | syntax error "
                + "line 1 at position 16 unexpected '<EOF>'. | syntax error line 1 at position 10 unexpected '('.",
            "SELECT ABS(1.5e)");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 30 near '<EOF>'. | syntax error "
                + "line 1 at position 30 unexpected '<EOF>'.",
            "SELECT 1 FROM t1 ORDER BY 1.5e");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 10 near '<EOF>'. | syntax error "
                + "line 1 at position 10 unexpected '<EOF>'.",
            "SELECT .5e");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 10 near '32'. | syntax error "
                + "line 1 at position 11 unexpected 'FROM'.",
            "SELECT .5e FROM t1");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 14 near '<EOF>'. | syntax error "
                + "line 1 at position 14 unexpected '<EOF>'.",
            "SELECT 1 + .5e");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 10 near '32'.", "SELECT .5e + 1");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 10 near '44'.", "SELECT .5e, 2");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 10 near '32'. | syntax error "
                + "line 1 at position 11 unexpected 'AS'.",
            "SELECT .5e AS x");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 30 near '<EOF>'. | syntax error "
                + "line 1 at position 30 unexpected '<EOF>'.",
            "SELECT 1 FROM t1 WHERE a = .5e");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 30 near '32'. | syntax error "
                + "line 1 at position 31 unexpected 'AND'.",
            "SELECT 1 FROM t1 WHERE a = .5e AND b = 2");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 26 near '<EOF>'.",
            "SELECT 1 FROM t1 LIMIT .5e");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 14 near '41'. | syntax error "
                + "line 1 at position 15 unexpected '<EOF>'. | syntax error line 1 at position 10 unexpected '('.",
            "SELECT ABS(.5e)");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 29 near '<EOF>'. | syntax error "
                + "line 1 at position 29 unexpected '<EOF>'.",
            "SELECT 1 FROM t1 ORDER BY .5e");
        assertAnswer("100000", "SELECT 1e5e");
        assertAnswer("100000", "SELECT 1e5e FROM t1");
        assertAnswer("100001", "SELECT 1 + 1e5e");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '+'.",
            "SELECT 1e5e + 1");
        assertAnswer("100000|2", "SELECT 1e5e, 2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'AS'.",
            "SELECT 1e5e AS x");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'e'.",
            "SELECT 1 FROM t1 WHERE a = 1e5e");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'e'.",
            "SELECT 1 FROM t1 WHERE a = 1e5e AND b = 2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'e'.",
            "SELECT 1 FROM t1 LIMIT 1e5e");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'e'.",
            "SELECT ABS(1e5e)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'e'.",
            "SELECT 1 FROM t1 ORDER BY 1e5e");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 9 near '101'. | syntax error "
                + "line 1 at position 10 unexpected '<EOF>'.",
            "SELECT 1ee");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 9 near '101'. | syntax error "
                + "line 1 at position 11 unexpected 'FROM'.",
            "SELECT 1ee FROM t1");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 13 near '101'. | syntax error "
                + "line 1 at position 14 unexpected '<EOF>'.",
            "SELECT 1 + 1ee");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 9 near '101'.", "SELECT 1ee + 1");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 9 near '101'. | syntax error "
                + "line 1 at position 10 unexpected ','.",
            "SELECT 1ee, 2");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 9 near '101'. | syntax error "
                + "line 1 at position 11 unexpected 'AS'.",
            "SELECT 1ee AS x");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 29 near '101'. | syntax error "
                + "line 1 at position 30 unexpected '<EOF>'.",
            "SELECT 1 FROM t1 WHERE a = 1ee");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 29 near '101'. | syntax error "
                + "line 1 at position 31 unexpected 'AND'.",
            "SELECT 1 FROM t1 WHERE a = 1ee AND b = 2");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 25 near '101'.",
            "SELECT 1 FROM t1 LIMIT 1ee");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 13 near '101'.",
            "SELECT ABS(1ee)");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 28 near '101'. | syntax error "
                + "line 1 at position 29 unexpected '<EOF>'.",
            "SELECT 1 FROM t1 ORDER BY 1ee");
        assertAnswer("REFUSED SQL compilation error: | parse error line 2 at position 2 near '10'. | syntax error "
                + "line 3 at position 0 unexpected 'FROM'.",
            "SELECT\n1e\nFROM t1");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 9 near '32'.", "SELECT 1e x y");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 9 near '32'. | syntax error "
                + "line 1 at position 10 unexpected 'FROM'. | syntax error line 1 at position 20 unexpected 'y'.",
            "SELECT 1e FROM t1 x y");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 9 near '59'. | syntax error "
                + "line 1 at position 11 unexpected 'SELECT'.",
            "SELECT 1e; SELECT 2");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 28 near '<EOF>'. | syntax error "
                + "line 1 at position 28 unexpected '<EOF>'.",
            "SELECT 1 FROM t1 GROUP BY 1e");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 10 near '41'. | syntax error "
                + "line 1 at position 11 unexpected '<EOF>'.",
            "SELECT (1e)");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 13 near '44'.",
            "SELECT ABS(1e, 2)");
        assertAnswer("10", "SELECT 1e1e");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 9 near '41'. | syntax error "
                + "line 1 at position 10 unexpected '<EOF>'.",
            "SELECT 1e)");
    }

    @Test
    public void numbersBeforeClausesAndShapes() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT, b INT)");
        engine.execute("INSERT INTO t1 VALUES (1, 2)");
        assertAnswer("1", "SELECT 1 FROM t1 LIMIT");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 25 near '32'. | syntax error "
                + "line 1 at position 34 unexpected '<EOF>'.",
            "SELECT 1 FROM t1 LIMIT 1e OFFSET 1");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 25 near '<EOF>'.",
            "SELECT 1 FROM t1 LIMIT 0x");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 25 near '32'. | syntax error "
                + "line 1 at position 26 unexpected 'x'. | syntax error line 1 at position 26 unexpected 'x'.",
            "SELECT 1 FROM t1 LIMIT 1e x");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 25 near '59'.",
            "SELECT 1 FROM t1 LIMIT 1e;");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 25 near '32'. | syntax error "
                + "line 1 at position 32 unexpected '<EOF>'.",
            "SELECT 1 FROM t1 LIMIT 1e OFFSET");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 26 near '<EOF>'.",
            "SELECT 1 FROM t1 OFFSET 1e");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 34 near '<EOF>'. | syntax error "
                + "line 1 at position 34 unexpected '<EOF>'.",
            "SELECT 1 FROM t1 LIMIT 1 OFFSET 1e");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 13 near '32'. | syntax error "
                + "line 1 at position 16 unexpected 'FROM'.",
            "SELECT TOP 1e 1 FROM t1");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 25 near '<EOF>'.",
            "SELECT 1 FROM t1 FETCH 1e");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 25 near '44'.",
            "SELECT 1 FROM t1 LIMIT 1e,");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '<EOF>'.",
            "SELECT 1 FROM t1 LIMIT 1 OFFSET");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'x'. | syntax "
                + "error line 1 at position 23 unexpected 'x'.",
            "SELECT 1 FROM t1 LIMIT x");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '.2'.",
            "SELECT 1..2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '.'.",
            "SELECT .5. FROM t1");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 11 near '101'. | syntax error "
                + "line 1 at position 12 unexpected '<EOF>'.",
            "SELECT 1.5.e");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 11 near '120'. | syntax error "
                + "line 1 at position 12 unexpected '<EOF>'.",
            "SELECT 1.5.x");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 13 near '32'. | syntax error "
                + "line 1 at position 14 unexpected 'FROM'.",
            "SELECT 12.34. FROM t1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '.'.",
            "SELECT 1.. FROM t1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '1.5.6'.",
            "SELECT 1.5.6 FROM t1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '<EOF>'. | "
                + "syntax error line 1 at position 10 unexpected '('.",
            "SELECT ABS(");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 9 near '32'. | syntax error "
                + "line 1 at position 10 unexpected 'FROM'. | syntax error line 1 at position 26 unexpected 'b'.",
            "SELECT 1e FROM t1 WHERE a b");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 9 near '9'. | syntax error line "
                + "1 at position 10 unexpected 'FROM'.",
            "SELECT 1e	FROM t1");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 12 near '<EOF>'.",
            "SELECT 1, 1e");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 12 near '32'.",
            "SELECT 1, 1e FROM t1");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 10 near '32'. | syntax error "
                + "line 1 at position 11 unexpected 'FROM'.",
            "SELECT (1e FROM t1");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 31 near '41'. | syntax error "
                + "line 1 at position 32 unexpected '<EOF>'.",
            "SELECT 1 FROM t1 WHERE a IN (1e)");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 31 near '44'.",
            "SELECT 1 FROM t1 WHERE a IN (1e, 2)");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 15 near '59'. | syntax error "
                + "line 1 at position 20 unexpected '<EOF>'.",
            "BEGIN RETURN 1e; END");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 15 near '32'.",
            "BEGIN RETURN 1e x; END");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 9 near '32'.", "SELECT 1e x y");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 9 near '120'. | syntax error "
                + "line 1 at position 11 unexpected 'FROM'.",
            "SELECT 1ex FROM t1");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 10 near '120'. | syntax error "
                + "line 1 at position 12 unexpected 'FROM'.",
            "SELECT 1e+x FROM t1");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 9 near '32'. | syntax error "
                + "line 1 at position 10 unexpected 'FROM'.",
            "SELECT 0x FROM t1");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 9 near '<EOF>'. | syntax error "
                + "line 1 at position 9 unexpected '<EOF>'.",
            "SELECT 0x");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 13 near '<EOF>'. | syntax error "
                + "line 1 at position 13 unexpected '<EOF>'.",
            "SELECT 1 + 0x");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 9 near '103'. | syntax error "
                + "line 1 at position 11 unexpected 'FROM'.",
            "SELECT 0xg FROM t1");
    }
}
