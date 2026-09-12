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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A numeric constant may leave either side of its decimal point empty: {@code .5} is 0.5 and {@code 1.} is
 * 1, with or without an exponent ({@code .5e1} is 5, {@code 1.e2} is 100), typed exactly as its full
 * spelling is ({@code .5} NUMBER(2,1), {@code 1.} NUMBER(1,0)). So {@code 1. AS v} is the number 1 under the
 * alias V — never a field access on it — and {@code 1.v} is the same thing written tighter, while a second
 * point after a complete number starts a second number, which is a syntax error. Every expectation is
 * live-verified.
 */
public class NumericConstantSpellingTest extends BaseDatabaseTest {

    /** Every cell of the first row, comma-separated. */
    private String cells(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder all = new StringBuilder();
        for (int i = 0; i < rs.getColumns().size(); i++) {
            if (i > 0) {
                all.append(", ");
            }
            all.append(String.valueOf(rs.getRows().get(0).getValue(i)));
        }
        return all.toString();
    }

    /** One statement's refusal, or "ACCEPTED". */
    private String refusal(final String sql) {
        try {
            engine.executeQuery(sql);
            return "ACCEPTED";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    @Test
    public void eitherSideOfThePointMayBeEmpty() {
        assertEquals("0.5", cells("SELECT .5"));
        assertEquals("-0.5", cells("SELECT -.5"));
        assertEquals("0.5", cells("SELECT +.5"));
        assertEquals("1", cells("SELECT 1."));
        assertEquals("2", cells("SELECT 1. + 1"));
        assertEquals("5, 0.05", cells("SELECT .5e1, .5E-1"));
        assertEquals("100, 0.05", cells("SELECT 1.E+2, .5E-1"));
        assertEquals("100", cells("SELECT 1.e2"));
        assertEquals("0.5", cells("SELECT (.5)"));
        assertEquals("1.0, 0.5", cells("SELECT 2*.5, 1-.5"));
        assertEquals("2", cells("SELECT 3.-1"));
        assertEquals("[0.5,1]", cells("SELECT ARRAY_CONSTRUCT(.5, 1.)"));
    }

    @Test
    public void aBarePointConstantIsTypedAsItsFullSpelling() {
        assertEquals("NUMBER(2,1)[SB1], NUMBER(2,1)[SB1], NUMBER(2,1)[SB1], NUMBER(2,1)[SB1], NUMBER(2,1)[SB1]",
            cells("SELECT SYSTEM$TYPEOF(0.5), SYSTEM$TYPEOF(.5), SYSTEM$TYPEOF(.50), SYSTEM$TYPEOF(00.5), SYSTEM$TYPEOF(-.5)"));
        assertEquals("NUMBER(1,0)[SB1], NUMBER(1,0)[SB1], NUMBER(1,0)[SB1], NUMBER(2,0)[SB1], NUMBER(3,0)[SB1], NUMBER(1,0)[SB1]",
            cells("SELECT SYSTEM$TYPEOF(1), SYSTEM$TYPEOF(1.), SYSTEM$TYPEOF(1.0), SYSTEM$TYPEOF(10.), "
                + "SYSTEM$TYPEOF(1.e2), SYSTEM$TYPEOF(.5e1)"));
    }

    @Test
    public void aTrailingPointBeforeANameIsANumberUnderAnAlias() {
        final ResultSet aliased = engine.executeQuery("SELECT 1. AS v");
        assertEquals("V", aliased.getColumns().get(0).getName());
        assertEquals("1", String.valueOf(aliased.getRows().get(0).getValue(0)));
        final ResultSet tight = engine.executeQuery("SELECT 1.v");
        assertEquals("V", tight.getColumns().get(0).getName());
        assertEquals("1", String.valueOf(tight.getRows().get(0).getValue(0)));
        assertEquals("0.5, 1", cells("SELECT .5 AS a, 1. AS b"));
    }

    @Test
    public void aSecondPointOrAnUnfinishedExponentIsRefused() {
        assertEquals("SQL compilation error:|syntax error line 1 at position 9 unexpected '.2'.", refusal("SELECT 1..2"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 9 unexpected '.5'.", refusal("SELECT .5.5"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 11 unexpected 'AS'.", refusal("SELECT 1.x AS y"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 11 unexpected '<EOF>'.", refusal("SELECT 1.AS"));
        assertEquals("SQL compilation error:|parse error line 1 at position 10 near '44'.", refusal("SELECT 1.e, 2"));
        // Live stacks a second, syntax-error line under these two; the first line is the one pinned.
        assertTrue(refusal("SELECT .5e").startsWith("SQL compilation error:|parse error line 1 at position 10 near '<EOF>'."));
        assertTrue(refusal("SELECT .5e+").startsWith("SQL compilation error:|parse error line 1 at position 11 near '<EOF>'."));
    }
}
