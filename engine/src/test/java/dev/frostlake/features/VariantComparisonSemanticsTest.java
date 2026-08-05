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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * VARIANT comparison semantics, verified row by row against LIVE Snowflake:
 * comparing a VARIANT against a VARCHAR compares the variant's DISPLAY TEXT — a variant string
 * unwraps to its content, an object/array/number/boolean to its JSON text — while comparing two
 * VARIANTs is typed (an OBJECT never equals a variant STRING of the same text). TO_VARIANT of a
 * VARCHAR produces a variant STRING (never parsed), so it differs from PARSE_JSON of the same text.
 */
public class VariantComparisonSemanticsTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void variantVersusVarcharComparesDisplayText() {
        assertEquals(Boolean.TRUE, scalar("SELECT PARSE_JSON('{\"a\":1}') = '{\"a\":1}'"));
        assertEquals(Boolean.TRUE, scalar("SELECT PARSE_JSON('[1,2]') = '[1,2]'"));
        assertEquals(Boolean.TRUE, scalar("SELECT PARSE_JSON('123') = '123'"));
        assertEquals(Boolean.TRUE, scalar("SELECT PARSE_JSON('1') < '2'"));
    }

    @Test
    public void variantStringUnwrapsToItsContentAgainstVarchar() {
        assertEquals(Boolean.TRUE, scalar("SELECT PARSE_JSON('\"abc\"') = 'abc'"));
        assertEquals(Boolean.TRUE, scalar("SELECT 'abc' = PARSE_JSON('\"abc\"')"));
        assertEquals(Boolean.FALSE, scalar("SELECT PARSE_JSON('\"abc\"') = '\"abc\"'"),
            "the quoted form is NOT the string's content");
    }

    @Test
    public void variantVersusScalarComparesByValue() {
        assertEquals(Boolean.TRUE, scalar("SELECT PARSE_JSON('123') = 123"));
        assertEquals(Boolean.TRUE, scalar("SELECT PARSE_JSON('true') = TRUE"));
    }

    @Test
    public void variantVersusVariantIsTyped() {
        assertEquals(Boolean.TRUE, scalar("SELECT PARSE_JSON('{\"a\":1}') = PARSE_JSON('{\"a\":1}')"));
        assertEquals(Boolean.TRUE, scalar("SELECT PARSE_JSON('[1,2]') = PARSE_JSON('[1,2]')"));
        assertEquals(Boolean.TRUE, scalar("SELECT PARSE_JSON('null') = PARSE_JSON('null')"));
        assertEquals(Boolean.TRUE, scalar("SELECT PARSE_JSON('1') = PARSE_JSON('1.0')"),
            "variant numbers compare by value across scales");
        assertEquals(Boolean.TRUE, scalar("SELECT PARSE_JSON('1') < PARSE_JSON('2')"));
        assertEquals(Boolean.TRUE, scalar("SELECT PARSE_JSON('\"a\"') < PARSE_JSON('\"b\"')"));
    }

    @Test
    public void toVariantOfVarcharIsAVariantString() {
        // Live-verified: TO_VARIANT never parses — an OBJECT is not equal to TO_VARIANT of its text.
        assertEquals(Boolean.FALSE, scalar("SELECT PARSE_JSON('{\"a\":1}') = TO_VARIANT('{\"a\":1}')"));
        assertEquals(Boolean.TRUE, scalar("SELECT TO_VARIANT('abc') = 'abc'"));
        assertEquals("VARCHAR", scalar("SELECT TYPEOF(TO_VARIANT('{\"a\":1}'))"),
            "TO_VARIANT of a VARCHAR is a variant STRING even when the text looks like JSON");
    }
}
