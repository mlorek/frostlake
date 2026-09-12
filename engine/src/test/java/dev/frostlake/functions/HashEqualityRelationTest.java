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
 * WHICH arguments HASH alike. The 64-bit values themselves are Snowflake's own and are not reproduced, so
 * every assertion here is an EQUALITY between two hashes — a shape that holds on both engines even though
 * neither number matches. That is the whole reason this file compares hashes instead of asserting them.
 *
 * <p>Frostlake used to get both halves of the rule exactly INVERTED: it separated numbers that differed
 * only in scale, and conflated a numeric string with the number it spells. Live does neither.
 *
 * <p>The classes, all measured:
 *
 * <ul>
 *   <li>A number hashes by VALUE. Scale, declared type and the FIXED/FLOAT split are all invisible.</li>
 *   <li>BOOLEAN is IN the number class — TRUE hashes as 1 — while the string 'true' is not.</li>
 *   <li>Every temporal is in the number class too, as its epoch offset in its OWN unit: days for a DATE,
 *       seconds for a TIME or TIMESTAMP. So a DATE and a TIME can collide, while a DATE and the TIMESTAMP
 *       at its own midnight cannot.</li>
 *   <li>A BINARY hashes as the string of its bytes, on the raw bytes rather than a decode.</li>
 *   <li>A VARIANT hashes as the value it holds, with a VARIANT boolean the one measured exception.</li>
 *   <li>The VARIANT JSON null is NOT SQL NULL and does not hash like it — while a MISSING key, which is
 *       SQL NULL rather than a JSON null, does.</li>
 * </ul>
 *
 * <p>TIMESTAMP_LTZ and TIMESTAMP_TZ are deliberately absent: their epoch depends on the session time zone,
 * which the two engines default differently, so a cell over them would be measuring that and not the hash.
 */
public class HashEqualityRelationTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE hrel (n NUMBER(10,2), i INT, f FLOAT, s VARCHAR,"
            + " d DATE, t TIMESTAMP_NTZ, v VARIANT)");
        engine.execute("INSERT INTO hrel SELECT 2.50, 1, 1.0, '1', DATE '2024-01-05',"
            + " '2024-01-05 00:00:00'::TIMESTAMP_NTZ, PARSE_JSON('1')");
    }

    /** "true" or "false" for a comparison, or the refusal. */
    private String answer(final String expr) {
        try {
            final ResultSet rs = engine.executeQuery("SELECT " + expr);
            return rs.next() ? String.valueOf(rs.getValue(0)) : "<no rows>";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** A number's SCALE is invisible, whatever spelling carries it. */
    @Test
    public void aNumbersScaleIsInvisible() {
        assertEquals("true", answer("HASH(1) = HASH(1.00)"));
        assertEquals("true", answer("HASH(1) = HASH(1.0000)"));
        assertEquals("true", answer("HASH(2.5) = HASH(2.50)"));
        assertEquals("true", answer("HASH(0) = HASH(0.00)"));
        assertEquals("true", answer("HASH(-1) = HASH(-1.00)"));
        assertEquals("true", answer("HASH(0.1) = HASH(0.10)"));
        assertEquals("true", answer("HASH(100) = HASH(1e2)"));
        assertEquals("true", answer("HASH(1) = HASH(1::NUMBER(38,10))"),
            "the DECLARED type is invisible too, not only the literal's spelling");
        assertEquals("true", answer("HASH(n) = HASH(2.5) FROM hrel"),
            "and a stored NUMBER(10,2) equals the unscaled literal");
        assertEquals("true", answer("HASH(12345678901234567890123456789012345678)"
            + " = HASH(12345678901234567890123456789012345678.00)"),
            "at 38 digits, where the value must never be widened to a double");
    }

    /** A FLOAT joins the exact numbers, by its shortest round-trip decimal. */
    @Test
    public void aFloatJoinsTheExactNumbers() {
        assertEquals("true", answer("HASH(1) = HASH(1.0::FLOAT)"));
        assertEquals("true", answer("HASH(2.5) = HASH(2.5::FLOAT)"));
        assertEquals("true", answer("HASH(0.1::FLOAT) = HASH(0.1)"));
        assertEquals("true", answer("HASH(0.3::FLOAT) = HASH(0.3)"));
        assertEquals("true", answer("HASH(3.0::DOUBLE) = HASH(3)"));
        assertEquals("true", answer("HASH(-0.0::FLOAT) = HASH(0)"), "and a negative zero is zero");
        assertEquals("true", answer("HASH(1e20::FLOAT) = HASH(100000000000000000000)"),
            "a whole float is its PLAIN integer, not its scientific spelling");
        assertEquals("true", answer("HASH(0.12345678901234::FLOAT) = HASH(0.12345678901234)"),
            "fourteen digits survive, so the join is the SHORTEST decimal and not a display rounding");
        assertEquals("false", answer("HASH((1.0/3)::FLOAT) = HASH(0.333333333333333)"),
            "but a double that needs sixteen digits does not equal a fifteen-digit decimal");
        assertEquals("true", answer("HASH(f) = HASH(1) FROM hrel"));
    }

    /** A STRING is never the number it spells. */
    @Test
    public void aStringIsNeverItsNumber() {
        assertEquals("false", answer("HASH('1') = HASH(1)"));
        assertEquals("false", answer("HASH(1) = HASH(1::VARCHAR)"));
        assertEquals("false", answer("HASH(s) = HASH(i) FROM hrel"));
    }

    /** BOOLEAN is in the number class, and only there. */
    @Test
    public void booleanIsInTheNumberClass() {
        assertEquals("true", answer("HASH(TRUE) = HASH(1)"));
        assertEquals("true", answer("HASH(FALSE) = HASH(0)"));
        assertEquals("true", answer("HASH(TRUE) = HASH(1.0)"));
        assertEquals("true", answer("HASH(TRUE) = HASH(1.0::FLOAT)"));
        assertEquals("false", answer("HASH(TRUE) = HASH('true')"), "the WORD is a string, not the value");
    }

    /** Every temporal is a number too, in its own unit. */
    @Test
    public void everyTemporalIsANumberInItsOwnUnit() {
        assertEquals("true", answer("HASH(DATE '1970-01-01') = HASH(0)"));
        assertEquals("true", answer("HASH(DATE '1970-01-02') = HASH(1)"), "a DATE counts DAYS");
        assertEquals("true", answer("HASH(DATE '1969-12-31') = HASH(-1)"), "and counts them backwards");
        assertEquals("true", answer("HASH(DATE '2024-01-05') = HASH(19727)"));
        assertEquals("true", answer("HASH('00:00:01'::TIME) = HASH(1)"), "a TIME counts SECONDS");
        assertEquals("true", answer("HASH('00:01:00'::TIME) = HASH(60)"));
        assertEquals("true", answer("HASH('00:00:01.5'::TIME) = HASH(1.5)"), "fractions included");
        assertEquals("true", answer("HASH('1970-01-01 00:00:00'::TIMESTAMP_NTZ) = HASH(0)"));
        assertEquals("true", answer("HASH('1970-01-01 00:00:01'::TIMESTAMP_NTZ) = HASH(1)"),
            "a TIMESTAMP counts epoch SECONDS");
        assertEquals("true", answer("HASH('1970-01-01 00:00:01.5'::TIMESTAMP_NTZ) = HASH(1.5)"));
        assertEquals("true",
            answer("HASH('1970-01-01 00:00:00.123456789'::TIMESTAMP_NTZ) = HASH(0.123456789)"),
            "down to the nanosecond");
        assertEquals("true", answer("HASH('1969-12-31 23:59:59'::TIMESTAMP_NTZ) = HASH(-1)"),
            "and before the epoch, where the fraction must not flip the sign");
    }

    /** Which temporals therefore collide, and which cannot. */
    @Test
    public void theUnitDecidesWhichTemporalsCollide() {
        assertEquals("true", answer("HASH(DATE '1970-01-02') = HASH('00:00:01'::TIME)"),
            "one day and one second are both the number 1, so these DO collide");
        assertEquals("false", answer("HASH(d) = HASH(t) FROM hrel"),
            "but a DATE and the TIMESTAMP at its own midnight never do — days against seconds");
        assertEquals("false", answer("HASH(DATE '2024-01-05') = HASH('2024-01-05'::TIMESTAMP_NTZ)"));
        assertEquals("false", answer("HASH(d) = HASH('2024-01-05') FROM hrel"),
            "and no temporal is the string that spells it");
        assertEquals("false", answer("HASH('00:00:01'::TIME) = HASH('00:00:01')"));
    }

    /** A BINARY hashes as the string of its RAW bytes. */
    @Test
    public void aBinaryHashesAsItsBytes() {
        assertEquals("true", answer("HASH(X'31') = HASH('1')"));
        assertEquals("true", answer("HASH(X'3132') = HASH('12')"));
        assertEquals("true", answer("HASH(X'C3A9') = HASH('é')"), "the UTF-8 bytes of the character");
        assertEquals("true", answer("HASH(X'') = HASH('')"));
        assertEquals("false", answer("HASH(X'31') = HASH('31')"), "not its HEX text");
        assertEquals("false", answer("HASH(X'FF') = HASH('')"));
        assertEquals("false", answer("HASH(X'FF') = HASH(X'FE')"),
            "the raw bytes are hashed, so two invalid-UTF-8 binaries stay apart");
        assertEquals("false", answer("HASH(X'FF') = HASH(X'EFBFBD')"));
    }

    /** A VARIANT hashes as the value it holds — with one measured exception. */
    @Test
    public void aVariantHashesAsWhatItHolds() {
        assertEquals("true", answer("HASH(v) = HASH(1) FROM hrel"));
        assertEquals("true", answer("HASH(v) = HASH(1.00) FROM hrel"), "scale is invisible inside too");
        assertEquals("true", answer("HASH(PARSE_JSON('\"1\"')) = HASH('1')"));
        assertEquals("false", answer("HASH(PARSE_JSON('true')) = HASH(TRUE)"),
            "a VARIANT boolean keeps its own class, though a bare BOOLEAN joins the numbers");
    }

    /**
     * ★ The VARIANT JSON null is its OWN value, and the hash is where that has to show.
     *
     * <p>The encoder always carried a separate tag for it; what it never received was the value. HASH
     * reads its arguments as scalars everywhere else in the engine, and reading a JSON null as a scalar
     * is exactly what turns it into SQL NULL — so the two collided. A hash encodes IDENTITY rather than
     * a scalar reading, which is what puts it beside EQUAL_NULL and the semi-structured family instead.
     *
     * <p>The MISSING key is the control: it is SQL NULL and not a JSON null at all, so it must keep
     * colliding with NULL. Getting one cell right by breaking the other would look like a fix.
     */
    @Test
    public void thejsonNullIsNotSqlNull() {
        assertEquals("false", answer("HASH(PARSE_JSON('null')) = HASH(NULL)"));
        assertEquals("false", answer("HASH(PARSE_JSON('null'), 1) = HASH(NULL, 1)"),
            "the position behaves no differently");
        assertEquals("false", answer("HASH(PARSE_JSON('{\"b\":null}'):b) = HASH(NULL)"),
            "and it arrives the same way out of an object");
        assertEquals("true", answer("HASH(PARSE_JSON('{\"a\":1}'):zz) = HASH(NULL)"),
            "a MISSING key is SQL NULL, not a JSON null, and still collides with it");
        assertEquals("true",
            answer("HASH(PARSE_JSON('null')) = HASH(PARSE_JSON('{\"b\":null}'):b)"),
            "two JSON nulls are one value");
        assertEquals("false", answer("HASH(PARSE_JSON('null')) = HASH('null')"),
            "and never the string that spells it");
    }

    /** The same rule inside a container, where the two nulls do not even render alike. */
    @Test
    public void acontainedJsonNullIsNotAContainedSqlNull() {
        assertEquals("false",
            answer("HASH(ARRAY_CONSTRUCT(PARSE_JSON('null'))) = HASH(ARRAY_CONSTRUCT(NULL))"));
        final ResultSet rendered = engine.executeQuery(
            "SELECT ARRAY_CONSTRUCT(PARSE_JSON('null')), ARRAY_CONSTRUCT(NULL)");
        rendered.next();
        assertEquals("[null]", String.valueOf(rendered.getValue(0)));
        assertEquals("[undefined]", String.valueOf(rendered.getValue(1)),
            "which is why they cannot hash alike");
    }

    /** ★ And out of a STORED VARIANT column, which is the shape a real table has. */
    @Test
    public void astoredJsonNullIsNotAStoredSqlNull() {
        engine.execute("CREATE OR REPLACE TABLE hjn (id INT, v VARIANT)");
        engine.execute("INSERT INTO hjn SELECT 1, PARSE_JSON('null')");
        engine.execute("INSERT INTO hjn SELECT 2, NULL");
        engine.execute("INSERT INTO hjn SELECT 3, PARSE_JSON('null')");
        assertEquals("false", answer("(SELECT HASH(v) FROM hjn WHERE id = 1)"
            + " = (SELECT HASH(v) FROM hjn WHERE id = 2)"));
        assertEquals("true", answer("(SELECT HASH(v) FROM hjn WHERE id = 1)"
            + " = (SELECT HASH(v) FROM hjn WHERE id = 3)"),
            "two stored JSON nulls still agree");
        assertEquals("false", answer("(SELECT HASH_AGG(v) FROM hjn WHERE id = 1)"
            + " = (SELECT HASH_AGG(v) FROM hjn WHERE id = 2)"),
            "HASH_AGG shares the encoding, so it shares the distinction");
        assertEquals("true", answer("(SELECT HASH_AGG(v) FROM hjn WHERE id = 1)"
            + " = (SELECT HASH_AGG(v) FROM hjn WHERE id = 3)"));
    }

    /** Arity and order both matter, and NULL occupies a position. */
    @Test
    public void arityAndOrderBothMatter() {
        assertEquals("false", answer("HASH(1,2) = HASH(2,1)"));
        assertEquals("false", answer("HASH(1,1) = HASH(1)"));
        assertEquals("false", answer("HASH(1,2) = HASH('12')"), "arguments are not concatenated");
        assertEquals("false", answer("HASH(NULL) = HASH(NULL, NULL)"));
        assertEquals("false", answer("HASH(1, NULL) = HASH(1)"), "a NULL still occupies its position");
        assertEquals("false", answer("HASH(NULL, 1) = HASH(1, NULL)"));
        assertEquals("true", answer("HASH(1, 2.50) = HASH(1.00, 2.5)"),
            "and each argument descales on its own");
        assertEquals("SQL compilation error: error line 1 at position 7"
            + "|not enough arguments for function [HASH()], expected 1, got 0",
            answer("HASH()"));
    }

    /** A container hashes as its canonical JSON, which decides what collides. */
    @Test
    public void aContainerHashesAsItsCanonicalJson() {
        assertEquals("true", answer("HASH(ARRAY_CONSTRUCT(1.00)) = HASH(ARRAY_CONSTRUCT(1))"));
        assertEquals("true",
            answer("HASH(OBJECT_CONSTRUCT('k',1.00)) = HASH(OBJECT_CONSTRUCT('k',1))"));
        assertEquals("true", answer("HASH(OBJECT_CONSTRUCT('a',1,'b',2))"
            + " = HASH(OBJECT_CONSTRUCT('b',2,'a',1))"), "an object is unordered");
        assertEquals("false", answer("HASH(ARRAY_CONSTRUCT(1,2)) = HASH(ARRAY_CONSTRUCT(2,1))"),
            "an array is not");
        assertEquals("false", answer("HASH(ARRAY_CONSTRUCT(1)) = HASH('[1]')"),
            "and never equals the STRING of its own text");
        assertEquals("false", answer("HASH(ARRAY_CONSTRUCT(1)) = HASH(ARRAY_CONSTRUCT('1'))"));
        assertEquals("false", answer("HASH(ARRAY_CONSTRUCT(1)) = HASH(OBJECT_CONSTRUCT('k',1))"));
        assertEquals("false", answer("HASH(ARRAY_CONSTRUCT(1)) = HASH(1)"));
        assertEquals("false", answer("HASH(ARRAY_CONSTRUCT()) = HASH(OBJECT_CONSTRUCT())"),
            "an empty array is not an empty object");
    }

    /** HASH_AGG shares the encoding, so a group's scale is invisible there too. */
    @Test
    public void hashAggSharesTheEncoding() {
        assertEquals("true", answer("(SELECT HASH_AGG(x) FROM (SELECT 1 AS x) t)"
            + " = (SELECT HASH_AGG(x) FROM (SELECT 1.00 AS x) t)"));
        assertEquals("true", answer("(SELECT HASH_AGG(x) FROM (SELECT 1 AS x UNION ALL SELECT 2) a)"
            + " = (SELECT HASH_AGG(x) FROM (SELECT 2 AS x UNION ALL SELECT 1) b)"),
            "and the aggregate stays order-independent");
    }
}
