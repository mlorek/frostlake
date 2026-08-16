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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A DOUBLE inside an ARRAY or OBJECT displays in the account's fifteen-decimal form, exactly as TO_JSON
 * converts it: nested or beside other members, from a FLOAT, from an exponent-written JSON number and
 * from an aggregate alike. A DECIMAL or INTEGER member displays as written, a non-finite DOUBLE as its
 * bare word, and JSON_INDENT changes the layout but never the number. Every cell is live-verified.
 */
public class VariantDoubleDisplayTest extends BaseDatabaseTest {

    private String displayed(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void aDoubleMemberDisplaysInTheFifteenDecimalForm() {
        assertEquals("[1.000000000000000e+00,2.500000000000000e+00]",
            displayed("SELECT ARRAY_CONSTRUCT(1.0::FLOAT, 2.5::FLOAT)"));
        assertEquals("{\"k\":1.000000000000000e+00}", displayed("SELECT OBJECT_CONSTRUCT('k', 1.0::FLOAT)"));
        assertEquals("{\"a\":1.000000000000000e+02}", displayed("SELECT PARSE_JSON('{\"a\":1e2}')"));
        assertEquals("[-1.500000000000000e+00]", displayed("SELECT ARRAY_CONSTRUCT(-1.5::FLOAT)"));
        assertEquals("[1.000000000000000e+02,-2.500000000000000e-03]",
            displayed("SELECT PARSE_JSON('[1e2, -2.5e-3]')"));
        assertEquals("[1.000000000000000e-05,1.234567890123457e+16]",
            displayed("SELECT PARSE_JSON('[1e-5, 12345678901234567e0]')"));
        assertEquals("{\"a\":[1.500000000000000e+00,{\"b\":2.500000000000000e+00}]}",
            displayed("SELECT OBJECT_CONSTRUCT('a', ARRAY_CONSTRUCT(1.5::FLOAT, OBJECT_CONSTRUCT('b', 2.5::FLOAT)))"));
        assertEquals("{\"a\":-0.000000000000000e+00}", displayed("SELECT PARSE_JSON('{\"a\":-0e0}')"));
        assertEquals("[-0.000000000000000e+00]", displayed("SELECT PARSE_JSON('[-0e0]')"));
    }

    @Test
    public void everyProducerOfAContainerAgrees() {
        assertEquals("{\"v\":1.500000000000000e+00}",
            displayed("SELECT OBJECT_CONSTRUCT('v', TO_VARIANT(1.5::FLOAT))"));
        assertEquals("[1.500000000000000e+00]", displayed("SELECT ARRAY_CONSTRUCT(1.5::FLOAT)::VARIANT"));
        assertEquals("[1.500000000000000e+00,2.250000000000000e+00]", displayed("SELECT ARRAY_AGG(x)"
            + " WITHIN GROUP (ORDER BY x) FROM (SELECT 1.5::FLOAT AS x UNION ALL SELECT 2.25::FLOAT)"));
        assertEquals("[2.500000000000000e+00]", displayed("SELECT ARRAY_AGG(v) FROM (SELECT PARSE_JSON('2.5e0') AS v)"));
        assertEquals("{\"k\":5.000000000000000e-01}", displayed("SELECT OBJECT_AGG('k', TO_VARIANT(0.5::FLOAT))"));
        assertEquals("[1.500000000000000e+00,\"x\",undefined]",
            displayed("SELECT ARRAY_CONSTRUCT(1.5::FLOAT, 'x', NULL)"));
    }

    @Test
    public void aDecimalOrIntegerMemberDisplaysAsWritten() {
        assertEquals("[1.5,2,\"x\",{\"d\":0.1}]", displayed("SELECT PARSE_JSON('[1.5, 2, \"x\", {\"d\": 0.1}]')"));
        assertEquals("[1.5]", displayed("SELECT PARSE_JSON('[1.50]')"));
        assertEquals("[123456789012345678901234567890.5]",
            displayed("SELECT PARSE_JSON('[123456789012345678901234567890.5]')"));
        assertEquals("[1]", displayed("SELECT PARSE_JSON('[1.0]')"));
        assertEquals("[1.5]", displayed("SELECT ARRAY_CONSTRUCT(1.5::NUMBER(10,2))"));
        assertEquals("{\"a\":1.5}", displayed("SELECT OBJECT_CONSTRUCT('a', PARSE_JSON('1.5'))"));
    }

    @Test
    public void aNonFiniteDoubleIsItsBareWord() {
        assertEquals("[NaN,Infinity,-Infinity]",
            displayed("SELECT ARRAY_CONSTRUCT('NaN'::FLOAT, 'inf'::FLOAT, '-inf'::FLOAT)"));
        assertEquals("{\"x\":NaN}", displayed("SELECT OBJECT_CONSTRUCT('x', 'NaN'::FLOAT)"));
    }

    @Test
    public void jsonIndentLaysOutTheSameNumbers() {
        Assumptions.assumeFalse(isLiveSnowflake(), "the live harness compacts every container it reads");
        engine.execute("ALTER SESSION SET JSON_INDENT = 2");
        try {
            assertEquals("[\n  1.500000000000000e+00,\n  2\n]", displayed("SELECT ARRAY_CONSTRUCT(1.5::FLOAT, 2)"));
            assertEquals("{\n  \"k\": 1.500000000000000e+00\n}", displayed("SELECT OBJECT_CONSTRUCT('k', 1.5::FLOAT)"));
            assertEquals("[\n  1.500000000000000e+00,\n  \"x\",\n  undefined\n]",
                displayed("SELECT ARRAY_CONSTRUCT(1.5::FLOAT, 'x', NULL)"));
        } finally {
            engine.execute("ALTER SESSION UNSET JSON_INDENT");
        }
    }
}
