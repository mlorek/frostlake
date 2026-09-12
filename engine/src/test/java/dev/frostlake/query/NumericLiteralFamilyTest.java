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
 * Which FAMILY a numeric literal belongs to, which is decided by its MAGNITUDE and not by how it is
 * written. An exponent is a way of spelling a fixed-point number — live folds it in and keeps the
 * exact family — right up to the point where the value needs more than thirty-eight digits, and there
 * it becomes a DOUBLE:
 *
 * <pre>
 *   1e37    NUMBER(38,0)   thirty-eight digits, the widest a NUMBER holds
 *   1e38    FLOAT          one digit more, so the family changes
 *   99…9.0  NUMBER(38,0)   thirty-eight nines: the dropped .0 is not a digit
 *   99…9.0  FLOAT          thirty-nine nines
 * </pre>
 *
 * <p>★ THIS IS WHAT MAKES THOSE LITERALS LEGAL. The point-free spelling of the same magnitude is
 * refused outright — see {@code IntegerLiteralRangeTest} — and live accepts the pointed and
 * exponent forms precisely because they are no longer integers. Frostlake used to accept them for the
 * opposite reason, keeping them exact in an impossible NUMBER(40,1), so the two engines agreed on
 * acceptance by accident rather than by rule.
 *
 * <p>★ TRAILING ZEROS ARE NOT DIGITS for this test, which is why a literal of thirty-eight nines
 * followed by {@code .0} stays exact: counting its dropped decimal would push it over a boundary live
 * keeps it inside.
 *
 * <p>NOT COVERED HERE: how an in-range literal's own trailing zeros are normalised — live answers
 * {@code 1.0} as NUMBER(1,0) holding 1 where Frostlake keeps NUMBER(2,1) holding 1.0 — which is a
 * separate surface. These assertions are written so they hold either way.
 */
public class NumericLiteralFamilyTest extends BaseDatabaseTest {

    private static final String NINES_38 = "9".repeat(38);
    private static final String NINES_39 = "9".repeat(39);

    private String one(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    /** One literal's declared type, without the storage-byte tag SYSTEM$TYPEOF appends. */
    private String family(final String literal) {
        final String typed = one("SELECT SYSTEM$TYPEOF(" + literal + ")");
        final int tag = typed.indexOf('[');
        return tag < 0 ? typed : typed.substring(0, tag);
    }

    @Test
    void theExactFamilyHoldsUntilThirtyEightDigits() {
        assertEquals("NUMBER(1,0)", family("1e0"));
        assertEquals("NUMBER(6,0)", family("1e5"));
        assertEquals("NUMBER(21,0)", family("1e20"));
        assertEquals("NUMBER(38,0)", family("1e37"));
    }

    @Test
    void oneDigitMoreIsADouble() {
        assertEquals("FLOAT", family("1e38"));
        assertEquals("FLOAT", family("1e39"));
        assertEquals("FLOAT", family("1.5e39"));
    }

    @Test
    void thePointSpellingCrossesAtTheSameWidth() {
        // Thirty-eight nines and a dropped .0 — still exact, and still all thirty-eight nines.
        assertTrue(one("SELECT " + NINES_38 + ".0").startsWith(NINES_38),
            "a thirty-eight digit literal must stay exact, saw " + one("SELECT " + NINES_38 + ".0"));
        assertEquals("FLOAT", family(NINES_39 + ".0"));
        assertEquals("FLOAT", family(NINES_39 + ".5"));
    }

    @Test
    void theDoubleCarriesTheValueAndTextOfADouble() {
        assertEquals("1.0E38", one("SELECT 1e38"));
        assertEquals("1e+38", one("SELECT TO_VARCHAR(1e38)"));
        assertEquals("1.0E39", one("SELECT " + NINES_39 + ".0"));
        assertEquals("1e+39", one("SELECT TO_VARCHAR(" + NINES_39 + ".5)"));
        assertEquals("1.5E39", one("SELECT 1.5e39"));
        assertEquals("1.5e+39", one("SELECT TO_VARCHAR(1.5e39)"));
    }

    @Test
    void anInRangeLiteralIsUntouched() {
        assertEquals("NUMBER(3,1)", family("99.5"));
        assertEquals("99.5", one("SELECT 99.5"));
        assertEquals("NUMBER(3,0)", family("1.5e2"));
        assertEquals("150", one("SELECT 1.5e2"));
        assertEquals("NUMBER(6,5)", family("1e-5"));
        assertEquals("0.00001", one("SELECT 1e-5"));
        assertEquals("100000000000000000000", one("SELECT 1e20"));
    }

    @Test
    void aStoredColumnTakesTheSameFamily() {
        engine.execute("CREATE OR REPLACE TABLE lit_family AS SELECT 1e38 AS wide, 1e37 AS exact");
        assertEquals("FLOAT", familyOfColumn("wide"));
        assertEquals("NUMBER(38,0)", familyOfColumn("exact"));
        assertEquals("1.0E38", one("SELECT wide FROM lit_family"));
        assertEquals("10000000000000000000000000000000000000", one("SELECT exact FROM lit_family"));
    }

    /** The declared family of one column of {@code lit_family}. */
    private String familyOfColumn(final String column) {
        final String typed = one("SELECT SYSTEM$TYPEOF(" + column + ") FROM lit_family");
        final int tag = typed.indexOf('[');
        return tag < 0 ? typed : typed.substring(0, tag);
    }
}
