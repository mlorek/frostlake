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
 * A table function called by a name of more than three parts is a syntax error at the dot that opens the fourth,
 * inside TABLE( … ) and after LATERAL, and live's recovery names the first token after the call that closes no
 * bracket on a second line — a join's alias there is followed by its ON on a third; a scalar call of such a name
 * is an unknown function (all live-verified).
 */
public class FourPartTableFunctionNameTest extends MeasuredCellsTestSupport {

    @Test
    public void aTableFunctionNamedInFourPartsIsRefusedAtTheFourthPartsDot() {
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '.'. | syntax "
                + "error line 1 at position 53 unexpected '<EOF>'.",
            "SELECT * FROM TABLE(a.b.c.flatten(PARSE_JSON('[1]')))");
        assertAnswer("1|null|[0]|0|1|[1]", "SELECT * FROM TABLE(b.c.flatten(PARSE_JSON('[1]')))");
    }

    @Test
    public void everyFormOfTheCallIsRefusedThere() {
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '.'. | syntax "
                + "error line 1 at position 54 unexpected 't'.",
            "SELECT * FROM TABLE(a.b.c.flatten(PARSE_JSON('[1]'))) t");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '.'. | syntax "
                + "error line 1 at position 54 unexpected 'WHERE'.",
            "SELECT * FROM TABLE(a.b.c.flatten(PARSE_JSON('[1]'))) WHERE 1 = 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '.'. | syntax "
                + "error line 1 at position 55 unexpected '<EOF>'.",
            "SELECT * FROM TABLE(a.b.c.d.flatten(PARSE_JSON('[1]')))");
        assertAnswer("REFUSED SQL compilation error: | Unknown user-defined function A.B.C.D.", "SELECT a.b.c.d(1)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '.'. | syntax "
                + "error line 1 at position 62 unexpected '<EOF>'.",
            "SELECT * FROM TABLE(a.b.c.flatten(input => PARSE_JSON('[1]')))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected '.'. | syntax "
                + "error line 1 at position 81 unexpected '<EOF>'.",
            "SELECT * FROM (SELECT PARSE_JSON('[1]') v) s, LATERAL a.b.c.flatten(input => s.v)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '.'. | syntax "
                + "error line 1 at position 52 unexpected '<EOF>'.",
            "SELECT * FROM TABLE(a..b.flatten(PARSE_JSON('[1]')))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 2 at position 16 unexpected '.'. | syntax "
                + "error line 2 at position 44 unexpected '<EOF>'.",
            "SELECT *\nFROM TABLE(a.b.c.flatten(PARSE_JSON('[1]')))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '.'. | syntax "
                + "error line 1 at position 36 unexpected '<EOF>'.",
            "SELECT * FROM TABLE(a.b.c.flatten())");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '.'. | syntax "
                + "error line 1 at position 53 unexpected ','.",
            "SELECT * FROM TABLE(a.b.c.flatten(PARSE_JSON('[1]'))), TABLE(FLATTEN(PARSE_JSON('[2]')))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected '.'. | syntax "
                + "error line 1 at position 69 unexpected '<EOF>'.",
            "SELECT * FROM TABLE(test_db.test_schema.x.flatten(PARSE_JSON('[1]')))");
        assertAnswer("REFUSED SQL compilation error: | Unknown user-defined function A.B.C.D.E.",
            "SELECT a.b.c.d.e(1)");
        assertAnswer("1|null|[0]|0|1|[1]", "SELECT * FROM TABLE(a.b.flatten(PARSE_JSON('[1]')))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '.'. | syntax "
                + "error line 1 at position 54 unexpected 'ORDER'.",
            "SELECT * FROM TABLE(a.b.c.flatten(PARSE_JSON('[1]'))) ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '.'. | syntax "
                + "error line 1 at position 43 unexpected '<EOF>'.",
            "SELECT * FROM TABLE(a.b.c.flatten(1, 2, 3))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '.'. | syntax "
                + "error line 1 at position 34 unexpected '<EOF>'.",
            "SELECT * FROM TABLE(a.b.c.flatten)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '.'. | syntax "
                + "error line 1 at position 28 unexpected '<EOF>'.",
            "SELECT * FROM TABLE(a.b.c.d)");
        assertAnswer("REFUSED SQL compilation error: | Unknown user-defined function A.B.C.D.",
            "SELECT a.b.c.d(1) FROM (SELECT 1 x)");
        assertAnswer("REFUSED SQL compilation error: | Unknown user-defined function A.B.C.D.",
            "SELECT 1 WHERE a.b.c.d(1) = 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '.'. | syntax "
                + "error line 1 at position 58 unexpected '<EOF>'.",
            "SELECT * FROM TABLE(a.b.c.flatten(PARSE_JSON('[1]'), 'x'))");
    }

    @Test
    public void theSecondLineFollowsTheCallsBrackets() {
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '.'. | syntax "
                + "error line 1 at position 54 unexpected 'AS'.",
            "SELECT * FROM TABLE(a.b.c.flatten(PARSE_JSON('[1]'))) AS t");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected '.'. | syntax "
                + "error line 1 at position 82 unexpected 'f'.",
            "SELECT * FROM (SELECT PARSE_JSON('[1]') v) s, LATERAL a.b.c.flatten(input => s.v) f");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '.'. | syntax "
                + "error line 1 at position 69 unexpected '<EOF>'.",
            "SELECT * FROM (SELECT * FROM TABLE(a.b.c.flatten(PARSE_JSON('[1]'))))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '('.",
            "SELECT * FROM a.b.c.flatten(PARSE_JSON('[1]'))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '('.",
            "SELECT * FROM a.b.flatten(PARSE_JSON('[1]'))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '.'. | syntax "
                + "error line 1 at position 54 unexpected 'JOIN'.",
            "SELECT * FROM TABLE(a.b.c.flatten(PARSE_JSON('[1]'))) JOIN TABLE(FLATTEN(PARSE_JSON('[2]'))) ON TRUE");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '.'. | syntax "
                + "error line 1 at position 54 unexpected '<EOF>'.",
            "SELECT * FROM TABLE(a.b.c.flatten(PARSE_JSON('[1]')) )");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 63 unexpected '.'. | syntax "
                + "error line 1 at position 86 unexpected 'f'. | syntax error line 1 at position 88 unexpected 'ON'.",
            "SELECT * FROM (SELECT PARSE_JSON('[1]') v) s JOIN LATERAL a.b.c.flatten(input => s.v) f ON TRUE");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '.'. | syntax "
                + "error line 1 at position 32 unexpected '<EOF>'.",
            "SELECT * FROM TABLE(a.b.c.d.e.f)");
        assertAnswer("REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SELECT * FROM a.b.c.d");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '.'. | syntax "
                + "error line 1 at position 62 unexpected '<EOF>'.",
            "SELECT * FROM TABLE(a.b.c.flatten(DISTINCT PARSE_JSON('[1]')))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected '.'. | syntax "
                + "error line 1 at position 80 unexpected '<EOF>'.",
            "SELECT * FROM (SELECT PARSE_JSON('[1]') v) s, LATERAL a..b.flatten(input => s.v)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '.'. | syntax "
                + "error line 1 at position 59 unexpected '<EOF>'.",
            "SELECT * FROM TABLE(\"a\".\"b\".\"c\".flatten(PARSE_JSON('[1]')))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '.'. | syntax "
                + "error line 1 at position 54 unexpected 't'.",
            "SELECT * FROM TABLE(a.b.c.flatten(PARSE_JSON('[1]'))) t WHERE 1 = 1");
    }
}
