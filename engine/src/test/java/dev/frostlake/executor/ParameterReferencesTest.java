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

package dev.frostlake.executor;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Where a routine's body reads its parameters: only where a value stands, every parameter in one pass, and by its
 * tokens where the grammar does not read the body.
 */
public class ParameterReferencesTest {

    private static String bound(final String body, final String[] names, final String[] values) {
        return ParameterReferences.bound(body, Arrays.asList(names), Arrays.asList(values));
    }

    @Test
    public void aFunctionATableAnAliasAndANamedArgumentStay() {
        assertEquals("upper('a')", bound("upper(upper)", new String[] {"UPPER"}, new String[] {"'a'"}));
        assertEquals("SELECT COUNT(*) FROM people",
            bound("SELECT COUNT(*) FROM people", new String[] {"PEOPLE"}, new String[] {"1"}));
        assertEquals("SELECT w FROM (SELECT 10 AS v, 20 AS w)",
            bound("SELECT w FROM (SELECT 10 AS v, 20 AS w)", new String[] {"V"}, new String[] {"1"}));
        assertEquals("SELECT COUNT(*) FROM TABLE(FLATTEN(input => PARSE_JSON('[1]')))",
            bound("SELECT COUNT(*) FROM TABLE(FLATTEN(input => PARSE_JSON(input)))", new String[] {"INPUT"},
                new String[] {"'[1]'"}));
        assertEquals("WITH c AS (SELECT 5 AS z) SELECT z + 0 FROM c",
            bound("WITH c AS (SELECT 5 AS z) SELECT z + 0 FROM c", new String[] {"C"}, new String[] {"1"}));
    }

    @Test
    public void aBareNameIsTheParameterAndAQualifiedOneTheColumn() {
        assertEquals("SELECT COUNT(*) FROM people p WHERE p.id > 7",
            bound("SELECT COUNT(*) FROM people p WHERE p.id > p", new String[] {"P"}, new String[] {"7"}));
        assertEquals("SELECT MAX(people.id) + 10 FROM people",
            bound("SELECT MAX(people.id) + people FROM people", new String[] {"PEOPLE"}, new String[] {"10"}));
        assertEquals("EXTRACT(year FROM DATE '2024-02-01')",
            bound("EXTRACT(year FROM year)", new String[] {"YEAR"}, new String[] {"DATE '2024-02-01'"}));
        assertEquals("'x'::DATE", bound("date::DATE", new String[] {"DATE"}, new String[] {"'x'"}));
        assertEquals("v:v::VARCHAR".replace("v:v", "PARSE_JSON('{}'):v"),
            bound("v:v::VARCHAR", new String[] {"V"}, new String[] {"PARSE_JSON('{}')"}));
    }

    @Test
    public void everyParameterIsBoundInOnePass() {
        assertEquals("(1)::NUMBER(10,2) || '/' || 2",
            bound("a || '/' || number", new String[] {"A", "NUMBER"}, new String[] {"(1)::NUMBER(10,2)", "2"}));
        assertEquals("'2024-01-02'::DATE || '/' || 5",
            bound("d || '/' || date", new String[] {"D", "DATE"}, new String[] {"'2024-01-02'::DATE", "5"}));
    }

    @Test
    public void aParameterIsNamedInUpperCaseHoweverItWasDeclared() {
        assertEquals("1 + 1", bound("\"X\" + 1", new String[] {"X"}, new String[] {"1"}));
        assertEquals("\"x\" + 1", bound("\"x\" + 1", new String[] {"X"}, new String[] {"1"}));
        assertEquals("SELECT 1 FROM t", bound("SELECT x FROM t", new String[] {"x"}, new String[] {"1"}));
        assertEquals("SELECT \"x\" FROM t", bound("SELECT \"x\" FROM t", new String[] {"x"}, new String[] {"1"}));
    }

    @Test
    public void theReplacementsMadeAreRecorded() {
        final List<int[]> replaced = new ArrayList<int[]>();
        assertEquals("SELECT 100 + 20 FROM t", ParameterReferences.bound("SELECT a + b FROM t",
            Arrays.asList("A", "B"), Arrays.asList("100", "20"), replaced));
        assertEquals(2, replaced.size());
        assertArrayEquals(new int[] {7, 8, 3, 0}, replaced.get(0));
        assertArrayEquals(new int[] {11, 12, 2, 1}, replaced.get(1));
    }

    @Test
    public void aBodyTheGrammarDoesNotReadIsBoundByItsTokens() {
        assertEquals("SELECT id FROM people LIMIT 3 3",
            bound("SELECT id FROM people LIMIT n n", new String[] {"N"}, new String[] {"3"}));
        final List<int[]> replaced = new ArrayList<int[]>();
        assertEquals("SELECT ( 5", ParameterReferences.bound("SELECT ( n", Arrays.asList("N"), Arrays.asList("5"),
            replaced));
        assertEquals(1, replaced.size());
        assertArrayEquals(new int[] {9, 10, 1, 0}, replaced.get(0));
    }
}
