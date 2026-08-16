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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which family a JSON number belongs to, decided by HOW IT WAS WRITTEN.
 *
 * <p>★ THE RULE IS THE NOTATION, and nothing else. An exponent makes the number a DOUBLE whatever its
 * sign or magnitude — {@code 1.0e0}, {@code 2e0}, {@code 0e0}, {@code 1.0e1} and {@code 1e-5} are all
 * DOUBLE — while the same values written plainly are not. The task this closes framed it as "an
 * exponent that is not negative", and the control cell settled the shape: {@code [1.0]} is INTEGER on
 * BOTH engines, so nothing is wrong with the descaling; only the notation was being lost.
 *
 * <p>★ THE NOTATION DOES NOT SURVIVE THE PARSE, which is why this needed a reader change rather than a
 * rule change. {@code new BigDecimal("1.0e0")} and {@code new BigDecimal("1.0")} are the same object —
 * unscaled 10, scale 1 — so after parsing there is nothing left to read. Only a NEGATIVE scale survives
 * ({@code 1e5} keeps scale −5), which is why exactly the large positive exponents used to work and
 * everything else did not. The notation is now read off the document text.
 *
 * <p>★ THE DIGIT-COUNT STAND-IN WAS BACKWARDS. More than 15 significant digits used to read as
 * double provenance, standing in for the exponent it could not see. Live types the plain
 * {@code [1.234567890123456]} as DECIMAL, so that rule was inventing a DOUBLE rather than recovering
 * one — and every value it was there to rescue carries a real exponent anyway.
 *
 * <p>The rendering follows the family: a DOUBLE renders in the fifteen-decimal form, {@code 1.0e0} as
 * {@code 1.000000000000000e+00} and {@code 1e-5} as {@code 1.000000000000000e-05}, where a DECIMAL that
 * descaled renders {@code 1} and the plain spelling of the small one {@code 0.00001} — all four
 * measured.
 *
 * <p>NOT FIXED HERE: a 16-significant-digit PLAIN fraction read back through a path EXTRACTION still
 * reads DOUBLE, because the canonical text a VARIANT stores cannot carry the family across a re-parse.
 * That is the round-trip channel rather than the reader, and it is tracked on its own.
 */
public class JsonNumberFamilyTest extends BaseDatabaseTest {

    /** The family of an array's first element. */
    private String family(final String document) {
        final ResultSet rs = engine.executeQuery(
            "SELECT TYPEOF(PARSE_JSON('" + document + "')[0])");
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    /** The document as it renders back. */
    private String rendered(final String document) {
        final ResultSet rs = engine.executeQuery("SELECT PARSE_JSON('" + document + "')");
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    /** ★ A plainly written number descales — the control the whole rule rests on. */
    @Test
    public void aplainNumberDescales() {
        assertEquals("INTEGER", family("[1]"));
        assertEquals("INTEGER", family("[1.0]"), "a whole fraction is an INTEGER, not a DECIMAL");
        assertEquals("INTEGER", family("[1.00]"));
        assertEquals("DECIMAL", family("[1.5]"));
        assertEquals("[1]", rendered("[1.0]"));
        assertEquals("[1.5]", rendered("[1.5]"));
    }

    /** ★ AN EXPONENT MAKES IT A DOUBLE, whatever the exponent is. */
    @Test
    public void anexponentMakesItADouble() {
        assertEquals("DOUBLE", family("[1.0e0]"), "a ZERO exponent counts");
        assertEquals("DOUBLE", family("[1e0]"));
        assertEquals("DOUBLE", family("[2e0]"));
        assertEquals("DOUBLE", family("[0e0]"), "and so does a zero VALUE");
        assertEquals("DOUBLE", family("[1.5e0]"));
        assertEquals("DOUBLE", family("[1.0e1]"), "a positive exponent that descales to a whole number");
        assertEquals("DOUBLE", family("[1.0e-1]"));
        assertEquals("DOUBLE", family("[1e5]"));
        assertEquals("DOUBLE", family("[1e-5]"));
    }

    /** The SPELLING of the exponent is immaterial — upper case and an explicit plus both count. */
    @Test
    public void thespellingOfTheExponentIsImmaterial() {
        assertEquals("DOUBLE", family("[1.0E0]"));
        assertEquals("DOUBLE", family("[1.0e+0]"));
    }

    /** ★ And the rendering follows the family, which is how the difference is visible at all. */
    @Test
    public void therenderingFollowsTheFamily() {
        assertEquals("[1.000000000000000e+00]", rendered("[1.0e0]"), "where the plain spelling renders [1]");
        assertEquals("[2.000000000000000e+00]", rendered("[2e0]"));
        assertEquals("[0.000000000000000e+00]", rendered("[0e0]"));
        assertEquals("[1.000000000000000e+01]", rendered("[1.0e1]"));
        assertEquals("[1.000000000000000e-01]", rendered("[1.0e-1]"));
        assertEquals("[1.000000000000000e-05]", rendered("[1e-5]"),
            "a DOUBLE is always scientific, where 0.00001 written plainly is a DECIMAL");
        assertEquals("[1.000000000000000e+05]", rendered("[1e5]"));
    }

    /** ★ The digit count decides NOTHING — a long plain fraction is a DECIMAL. */
    @Test
    public void thedigitCountDecidesNothing() {
        assertEquals("[1.234567890123456]", rendered("[1.234567890123456]"));
        assertEquals("[12345678901234567890]", rendered("[12345678901234567890]"),
            "and a 20-digit whole number is an INTEGER, never rounded through a double");
        assertEquals("INTEGER", family("[12345678901234567890]"));
    }

    /** An OBJECT member follows the same rule as an array element. */
    @Test
    public void anobjectMemberIsNoDifferent() {
        final ResultSet exponent = engine.executeQuery(
            "SELECT PARSE_JSON('{\"k\":1.0e0}'), TYPEOF(PARSE_JSON('{\"k\":1.0e0}'):k)");
        exponent.next();
        assertEquals("{\"k\":1.000000000000000e+00}", String.valueOf(exponent.getValue(0)));
        assertEquals("DOUBLE", String.valueOf(exponent.getValue(1)));
        final ResultSet plain = engine.executeQuery(
            "SELECT PARSE_JSON('{\"k\":1.0}'), TYPEOF(PARSE_JSON('{\"k\":1.0}'):k)");
        plain.next();
        assertEquals("{\"k\":1}", String.valueOf(plain.getValue(0)));
        assertEquals("INTEGER", String.valueOf(plain.getValue(1)));
    }

    /** A WHOLE-DOCUMENT scalar, with no container to read it out of, keeps the rule too. */
    @Test
    public void awholeDocumentScalarKeepsTheRule() {
        final ResultSet rs = engine.executeQuery("SELECT PARSE_JSON('1.0e0'),"
            + " TYPEOF(PARSE_JSON('1.0e0')), PARSE_JSON('1.0'), TYPEOF(PARSE_JSON('1.0'))");
        rs.next();
        assertEquals("1.0", String.valueOf(rs.getValue(0)));
        assertEquals("DOUBLE", String.valueOf(rs.getValue(1)));
        assertEquals("1", String.valueOf(rs.getValue(2)));
        assertEquals("INTEGER", String.valueOf(rs.getValue(3)));
    }

    /** The programmatic channels are untouched — a cast still decides its own family. */
    @Test
    public void theprogrammaticChannelsAreUntouched() {
        final ResultSet rs = engine.executeQuery("SELECT TYPEOF(TO_VARIANT(1.0::FLOAT)),"
            + " TYPEOF(TO_VARIANT(1.0)), ARRAY_CONSTRUCT(1.0::FLOAT), ARRAY_CONSTRUCT(1.0)");
        rs.next();
        assertEquals("DOUBLE", String.valueOf(rs.getValue(0)));
        assertEquals("INTEGER", String.valueOf(rs.getValue(1)));
        assertEquals("[1.000000000000000e+00]", String.valueOf(rs.getValue(2)));
        assertEquals("[1]", String.valueOf(rs.getValue(3)));
    }

    /** A document with no exponent at all is not touched by the notation pass. */
    @Test
    public void adocumentWithNoExponentIsUnchanged() {
        assertEquals("{\"a\":[1,2.5],\"b\":\"text\"}",
            rendered("{\"b\":\"text\",\"a\":[1,2.50]}"));
    }

    /** An 'e' inside a STRING is not an exponent, however much it looks like one. */
    @Test
    public void anletterEInsideAStringIsNotAnExponent() {
        assertEquals("{\"k\":\"1e5\"}", rendered("{\"k\":\"1e5\"}"));
        final ResultSet rs = engine.executeQuery(
            "SELECT TYPEOF(PARSE_JSON('{\"k\":\"1e5\"}'):k)");
        rs.next();
        assertEquals("VARCHAR", String.valueOf(rs.getValue(0)));
    }
}
