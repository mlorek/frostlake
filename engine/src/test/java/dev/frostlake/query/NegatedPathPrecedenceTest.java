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

/**
 * What a unary MINUS binds to when a semi-structured PATH follows it. {@code -src:score} used to raise
 * "Cannot negate non-number: {…}" — and the message was the diagnosis, because the value it tried to
 * negate was the WHOLE OBJECT. The minus had bound tighter than the path, so the expression was
 * {@code (-src)} with {@code :score} applied afterwards.
 *
 * <p>That is why teaching the NEGATE path to read a VARIANT could never have fixed it: by the time
 * negate ran, its operand was the object. The fix is precedence — in a left-recursive rule the earlier
 * alternative binds tighter, and the unary alternative was written above the path ones.
 *
 * <p>LIVE'S ORDER, measured: the paths ({@code :}, {@code []}, {@code .}) and the {@code ::} cast bind
 * tighter than the sign, and the sign binds tighter than {@code ||}, {@code *} and {@code +}. The cast
 * cell is the one that pins the middle of that: {@code -src:score::INT} is {@code -8}, so the cast is
 * reached before the minus and 7.5 rounds before it is negated.
 *
 * <p>A VARIANT operand FLOATS, which is the same rule the binary arithmetic follows — {@code -src:n}
 * over the integer 7 is {@code -7.0} and not {@code -7}. That half is not the precedence at all; it is
 * the operand rule, and it had to be applied to negation separately because negation never went through
 * the shared helper.
 *
 * <p>Ordinary columns are untouched, which is the control that matters: this is a precedence edit to one
 * of the commonest operators there is.
 */
public class NegatedPathPrecedenceTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE vc (src VARIANT, arr VARIANT, c INT, f FLOAT)");
        engine.execute("INSERT INTO vc SELECT"
            + " PARSE_JSON('{\"score\": 7.5, \"n\": 7, \"flag\": true, \"a\": {\"b\": 3}}'),"
            + " PARSE_JSON('[10, 20]'), 4, 2.5");
    }

    /** The first row's first value, or the refusal. */
    private String answer(final String expr) {
        try {
            final ResultSet rs = engine.executeQuery("SELECT " + expr + " FROM vc");
            return rs.next() ? String.valueOf(rs.getValue(0)) : "<no rows>";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** The path binds tighter than the sign, so the MEMBER is what gets negated. */
    @Test
    public void theSignNegatesTheMemberNotTheObject() {
        assertEquals("-7.5", answer("-src:score"));
        assertEquals("-7.0", answer("-src:n"));
        assertEquals("-3.0", answer("-src:a:b"), "a nested path too");
        assertEquals("-7.5", answer("- src:score"), "and a space before it changes nothing");
    }

    /** The BRACKET path binds the same way, by name and by index. */
    @Test
    public void theBracketPathBindsTheSameWay() {
        assertEquals("-7.5", answer("-src['score']"));
        assertEquals("-10.0", answer("-arr[0]"));
    }

    /** The CAST binds tighter than the sign as well — this is what pins the middle of the order. */
    @Test
    public void theCastIsReachedBeforeTheSign() {
        assertEquals("-8", answer("-src:score::INT"), "7.5 rounds to 8, and THEN is negated");
        assertEquals("-8", answer("-(src:score::INT)"), "the explicit grouping agrees");
    }

    /** And the sign still binds tighter than the arithmetic that follows it. */
    @Test
    public void theSignStillBindsTighterThanArithmetic() {
        assertEquals("-6.5", answer("-src:score + 1"));
        assertEquals("-15.0", answer("-src:score * 2"));
        assertEquals("-7.5", answer("(-src:score)"));
        assertEquals("-7.5", answer("-(src:score)"));
    }

    /** A VARIANT operand FLOATS — the operand rule, not the precedence. */
    @Test
    public void aVariantOperandFloats() {
        assertEquals("-7.0", answer("-src:n"), "the member is the integer 7");
        assertEquals("-10.0", answer("-arr[0]"));
        assertEquals("7.5", answer("ABS(-src:score)"));
        assertEquals("true", answer("-src:score < 0"));
    }

    /** The ORDINARY forms, which a precedence edit to unary minus must not disturb. */
    @Test
    public void theOrdinaryFormsAreUnchanged() {
        assertEquals("-4", answer("-c"));
        assertEquals("-2.5", answer("-f"));
        assertEquals("-3", answer("-c + 1"), "the sign binds before the addition, so this is (-4)+1");
        assertEquals("-4", answer("-vc.c"), "a qualified column, whose dot is a path in the grammar");
        assertEquals("-7.5", answer("-7.5"));
    }

    /** The path with no sign, and the other prefix operator, both unchanged. */
    @Test
    public void theOtherPrefixFormsAreUnchanged() {
        assertEquals("7.5", answer("src:score"));
        assertEquals("7", answer("src:n"), "unnegated, the member keeps its own type");
        assertEquals("7.5", answer("+src:score"));
        assertEquals("false", answer("NOT src:flag"));
        assertEquals("false", answer("NOT (src:flag)"));
        assertEquals("10", answer("arr[0]"));
    }
}
