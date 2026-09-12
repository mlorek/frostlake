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

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A parenthesised scalar subquery is an argument like any other, even when it is a call's whole argument:
 * {@code ABS((SELECT -1))} is 1 and {@code TO_VARCHAR((SELECT 1.5))} is '1.5'. POSITION's IN form takes one
 * as its haystack, {@code POSITION('b' IN (SELECT 'abc'))} being 2. Every cell is live-verified.
 */
public class ScalarSubqueryArgumentTest extends BaseDatabaseTest {

    private String answer(final String sql) {
        try {
            return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private void assertAnswers(final String[][] cells) {
        final List<String> wrong = new ArrayList<String>();
        for (final String[] cell : cells) {
            final String got = answer(cell[0]);
            if (!cell[1].equals(got)) {
                wrong.add(cell[0] + "\n    expected: " + cell[1] + "\n    got:      " + got);
            }
        }
        assertTrue(wrong.isEmpty(), String.join("\n", wrong));
    }

    /** A call whose one argument is a parenthesised subquery. */
    @Test
    public void aSubqueryIsAWholeArgument() {
        assertAnswers(new String[][] {
            {"SELECT ABS((SELECT -1))", "1"},
            {"SELECT ABS(((SELECT -1)))", "1"},
            {"SELECT ABS((WITH q AS (SELECT -1 AS a) SELECT a FROM q))", "1"},
            {"SELECT UPPER((SELECT 'a'))", "A"},
            {"SELECT LENGTH((SELECT 'abc'))", "3"},
            {"SELECT TRIM((SELECT ' a '))", "a"},
            {"SELECT TO_VARCHAR((SELECT 1.5))", "1.5"},
            {"SELECT TO_CHAR((SELECT 1.5))", "1.5"},
            {"SELECT TO_VARCHAR((SELECT SUM(x) FROM (SELECT -0.0::FLOAT AS x)))", "-0"},
            {"SELECT TO_DATE((SELECT '2020-01-02'))", "2020-01-02"},
            {"SELECT TO_VARCHAR(TO_TIMESTAMP((SELECT '2020-01-02 03:04:05')))", "2020-01-02 03:04:05.000"},
            {"SELECT TO_VARCHAR(TO_TIMESTAMP_NTZ((SELECT '2020-01-02 03:04:05')))", "2020-01-02 03:04:05.000"},
            {"SELECT TO_BOOLEAN((SELECT 'true'))", "true"},
            {"SELECT TO_TIME((SELECT '03:04:05'))", "03:04:05"},
            {"SELECT TRY_TO_NUMBER((SELECT '12.5'))", "13"},
            {"SELECT TO_JSON(TO_VARIANT((SELECT 1)))", "1"},
            {"SELECT TO_JSON(PARSE_JSON((SELECT '{}')))", "{}"},
            {"SELECT MAX((SELECT 1))", "1"},
            {"SELECT COUNT((SELECT 1))", "1"},
        });
    }

    /** The same subquery beside other arguments, or inside a wider argument, as it always parsed. */
    @Test
    public void aSubqueryBesideOtherArguments() {
        assertAnswers(new String[][] {
            {"SELECT ABS((SELECT -1)) + 1", "2"},
            {"SELECT (SELECT ABS((SELECT -1)))", "1"},
            {"SELECT COUNT(*) FROM (SELECT 1 AS x) WHERE x = ABS((SELECT -1)) * -1", "0"},
            {"SELECT TO_VARCHAR((SELECT 1.5), '99.9')", "  1.5"},
            {"SELECT COALESCE((SELECT 1), 2)", "1"},
            {"SELECT COALESCE((SELECT NULL), (SELECT 2))", "2"},
            {"SELECT CAST((SELECT 1.5) AS VARCHAR)", "1.5"},
            {"SELECT TRY_CAST((SELECT '1.5') AS NUMBER(3,1))", "1.5"},
            {"SELECT EXTRACT(YEAR FROM (SELECT '2020-01-02'::DATE))", "2020"},
            {"SELECT DATEADD(DAY, (SELECT 1), '2020-01-02'::DATE)", "2020-01-03"},
            {"SELECT SUBSTRING((SELECT 'abc'), 2)", "bc"},
            {"SELECT TO_DATE((SELECT '02/01/2020'), 'DD/MM/YYYY')", "2020-01-02"},
            {"SELECT TO_NUMBER((SELECT '12.5'), 10, 1)", "12.5"},
            {"SELECT IFF(TRUE, (SELECT 1), 0)", "1"},
            {"SELECT DECODE((SELECT 1), 1, 'one', 'other')", "one"},
            {"SELECT TO_JSON(ARRAY_CONSTRUCT((SELECT 1), 2))", "[1,2]"},
        });
    }

    /** POSITION's IN form takes a subquery as its haystack; an unknown name is still unknown. */
    @Test
    public void positionTakesASubqueryHaystack() {
        assertAnswers(new String[][] {
            {"SELECT POSITION('b' IN (SELECT 'abc'))", "2"},
            {"SELECT POSITION('z' IN (SELECT 'abc'))", "0"},
            {"SELECT POSITION(('b') IN (SELECT 'abc'))", "2"},
            {"SELECT POSITION('b', (SELECT 'abc'))", "2"},
            {"SELECT CHAR_LENGTH((SELECT 'abc'))", "SQL compilation error:|Unknown function CHAR_LENGTH."},
        });
    }
}
