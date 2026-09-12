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

package dev.frostlake.expressions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A VARIANT holding a number compares one way against a STRING and another against a NUMBER: as TEXT
 * on one side, numerically on the other. Frostlake's path extraction hands the value back as a bare
 * number, so the string side took the number's rule instead — it coerced the text, and raised on text
 * that would not read.
 *
 * <p>Two cells prove the string side really is text and not a numeric coercion, and they are the
 * whole point of this class:
 *
 * <pre>
 *   src:score = '7.50'   FALSE — "7.5" is not the text "7.50"   (coerced, it would be TRUE)
 *   src:score &gt; '10'     TRUE  — "7.5" sorts after "10"          (coerced, it would be FALSE)
 * </pre>
 *
 * <p>The number side is untouched and pinned here beside it, because the fix must not reach it:
 * {@code src:score = 7.50} stays TRUE. Every surface that compares inherits the rule — the operator,
 * IN, BETWEEN, a simple CASE and DECODE.
 */
public class VariantComparisonTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE vc (src VARIANT, i INT, v VARCHAR(10))");
        engine.execute("INSERT INTO vc SELECT PARSE_JSON('{\"score\": 7.5, \"n\": 7,"
            + " \"name\": \"x\", \"empty\": \"\", \"obj\": {\"a\": 1}, \"arr\": [1,2]}'), 7, 'x'");
    }

    /** The expression's answer, or the message of the refusal it raised. */
    private String outcome(final String expr) {
        try {
            final ResultSet rs = engine.executeQuery(
                "SELECT COALESCE(TO_VARCHAR(" + expr + "), '<NULL>') AS x FROM vc");
            rs.next();
            return String.valueOf(rs.getValue(0));
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace("\n", " ");
        }
    }

    /** The discriminator: equality against a string is TEXT equality, trailing zero and all. */
    @Test
    public void aVariantComparesToAStringAsText() {
        assertEquals("true", outcome("src:score = '7.5'"));
        assertEquals("false", outcome("src:score = '7.50'"),
            "a numeric comparison would call these equal");
        assertEquals("true", outcome("src:n = '7'"));
        assertEquals("false", outcome("src:n = '7.0'"), "the same shape on an integer member");
    }

    /** Ordering against a string is text ordering too — lexical, not numeric. */
    @Test
    public void orderingAgainstAStringIsLexical() {
        assertEquals("true", outcome("src:score < '8'"));
        assertEquals("true", outcome("src:score > '10'"),
            "\"7.5\" sorts after \"10\"; numerically 7.5 is less");
        assertEquals("true", outcome("src:score BETWEEN '7' AND '8'"));
        assertEquals("true", outcome("src:score BETWEEN '10' AND '8'"),
            "a range that only makes sense read as text");
    }

    /** Text that could never read as a number is simply unequal, never a refusal. */
    @Test
    public void unreadableTextIsUnequalNotAnError() {
        assertEquals("false", outcome("src:score = ''"));
        assertEquals("false", outcome("src:score = 'abc'"));
        assertEquals("true", outcome("src:score != 'abc'"));
    }

    /** Against a NUMBER it still compares numerically — the half that must not change. */
    @Test
    public void aVariantComparesToANumberNumerically() {
        assertEquals("true", outcome("src:score = 7.5"));
        assertEquals("true", outcome("src:score = 7.50"), "trailing zero is irrelevant to a number");
        assertEquals("true", outcome("src:score > 7"));
        assertEquals("true", outcome("src:n = i"));
    }

    /** Every surface that compares inherits both halves of the rule. */
    @Test
    public void theSurfacesThatCompareInheritIt() {
        assertEquals("true", outcome("src:score IN ('7.5')"));
        assertEquals("false", outcome("src:score IN ('7.50')"));
        assertEquals("false", outcome("src:score IN ('abc')"));
        assertEquals("eq", outcome("CASE src:score WHEN '7.5' THEN 'eq' ELSE 'ne' END"));
        assertEquals("ne", outcome("CASE src:score WHEN '7.50' THEN 'eq' ELSE 'ne' END"));
        assertEquals("eq", outcome("DECODE(src:score, '7.5', 'eq', 'ne')"));
        assertEquals("ne", outcome("DECODE(src:score, '7.50', 'eq', 'ne')"));
        assertEquals("ne", outcome("DECODE(src:score, 'abc', 'eq', 'ne')"));
    }

    /** A variant STRING member and the containers already behaved, and must keep behaving. */
    @Test
    public void stringMembersAndContainersAreUnchanged() {
        assertEquals("true", outcome("src:name = 'x'"));
        assertEquals("false", outcome("src:name = 'y'"));
        assertEquals("true", outcome("src:name = v"));
        assertEquals("false", outcome("src:obj = 'x'"));
        assertEquals("false", outcome("src:arr = 'x'"));
    }

    /** And nothing about the value itself moved: its type, its arithmetic, its cast. */
    @Test
    public void theValueItselfIsUnchanged() {
        assertEquals("DECIMAL", outcome("TYPEOF(src:score)"));
        assertEquals("INTEGER", outcome("TYPEOF(src:n)"));
        assertEquals("8.5", outcome("src:score + 1"));
        assertEquals("14", outcome("src:n * 2"));
        assertEquals("7.5", outcome("SUM(src:score)"));
        assertEquals("7.50", outcome("src:score::NUMBER(10,2)"));
    }
}
