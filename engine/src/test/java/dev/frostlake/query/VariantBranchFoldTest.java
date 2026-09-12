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
import dev.frostlake.types.DataType;
import dev.frostlake.types.StringType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a conditional declares when a VARIANT branch stands beside a SCALAR one. Frostlake fell to the
 * VARCHAR(16777216) placeholder for every such pairing; live has a specific answer for each, and they
 * do not follow one rule:
 *
 * <pre>
 *   VARIANT beside NUMBER / FLOAT              VARIANT      either way round
 *   VARIANT beside VARCHAR                     VARCHAR      either way round, WIDENED to 16MB
 *   VARIANT beside DATE / TIME / TIMESTAMP     that temporal, either way round
 *   VARIANT beside BOOLEAN / OBJECT / ARRAY    whichever branch came FIRST
 * </pre>
 *
 * <p>THE RELATION IS NOT TRANSITIVE, which is why no precedence order can express it: VARCHAR beats
 * VARIANT, VARIANT beats NUMBER, and NUMBER beats VARCHAR. So a conditional with three branches depends
 * on their order, and live resolves it RIGHT TO LEFT — {@code COALESCE(i, va, s)} is NUMBER because
 * {@code (va, s)} folds to VARCHAR first and a NUMBER beats that, while {@code COALESCE(i, va, i)} is
 * VARIANT because {@code (va, i)} folds to VARIANT. A left-to-right fold gets the first of those wrong,
 * which is what makes the pair worth asserting together.
 *
 * <p>The whole rule was read off the FULL ordered pair matrix — every one of eleven types against every
 * other, both ways round, over an EMPTY table so that no row-time cast could be mistaken for a declared
 * type. That mattered: over a populated table half these cells come back as
 * "Failed to cast variant value 1 to DATE" instead, which says nothing about what the column declares.
 *
 * <p>Frostlake was already right for 105 of those 121 pairs, so this changes only conditionals that
 * carry a VARIANT — plus one straggler, VARCHAR beside TIME, whose DATE and TIMESTAMP siblings already
 * worked.
 *
 * <p>NOT ASSERTED HERE: what the VALUE does. Live raises a row-time cast failure when the VARIANT
 * cannot become the folded type; Frostlake declares the column correctly and hands the value through
 * uncast. That is the value channel, and it is tracked separately.
 */
public class VariantBranchFoldTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        // EMPTY on purpose — see the note above.
        engine.execute("CREATE OR REPLACE TABLE ve (va VARIANT, i INT, n NUMBER(10,2), f FLOAT,"
            + " s VARCHAR(5), b BOOLEAN, d DATE, t TIME, ts TIMESTAMP_NTZ, ob OBJECT, ar ARRAY)");
    }

    /** The declared type NAME of the expression's result column. */
    private String typeOf(final String expr) {
        final DataType type =
            engine.executeQuery("SELECT " + expr + " FROM ve").getColumns().get(0).getDataType();
        return type == null ? "null" : type.getName();
    }

    /** Both written orders of a two-branch COALESCE, which must give the same answer. */
    private void bothOrders(final String expected, final String left, final String right) {
        assertEquals(expected, typeOf("COALESCE(" + left + ", " + right + ")"), left + "," + right);
        assertEquals(expected, typeOf("COALESCE(" + right + ", " + left + ")"), right + "," + left);
    }

    /** A VARIANT beside a number keeps the VARIANT, whichever is written first. */
    @Test
    public void aVariantBesideANumberStaysVariant() {
        bothOrders("VARIANT", "va", "i");
        bothOrders("VARIANT", "va", "n");
        bothOrders("VARIANT", "va", "f");
        assertEquals("VARIANT", typeOf("COALESCE(va, 1)"), "a literal counts the same as a column");
        assertEquals("VARIANT", typeOf("COALESCE(1, va)"));
        assertEquals("VARIANT", typeOf("COALESCE(PARSE_JSON('1'), i)"),
            "and a VARIANT-producing CALL counts the same as a VARIANT column");
        assertEquals("VARIANT", typeOf("COALESCE(i, TO_VARIANT(1))"));
    }

    /** A VARIANT beside a string becomes the string — the one pairing Frostlake already had right. */
    @Test
    public void aVariantBesideAStringBecomesTheString() {
        bothOrders("VARCHAR", "va", "s");
        assertEquals("VARCHAR", typeOf("COALESCE(va, 'x')"));
        assertEquals("VARCHAR", typeOf("COALESCE('x', va)"));
    }

    /**
     * And the string WIDENS rather than keeping its own length — to the same 128MB an unknown-length
     * string takes beside any family it cannot measure.
     *
     * <p>The DECLARED width and the STORED one are different numbers for one expression: a CTAS clamps
     * this to the 16MB a stored column may hold. Both are asserted, because keeping the branch's own
     * VARCHAR(5) was wrong on both surfaces and either one alone would leave the other unpinned.
     */
    @Test
    public void theStringWidensToTheUnknownLength() {
        final DataType folded =
            engine.executeQuery("SELECT COALESCE(va, s) FROM ve").getColumns().get(0).getDataType();
        assertEquals(134217728, ((StringType) folded).getMaxLength());
        final DataType plain =
            engine.executeQuery("SELECT COALESCE(s, s) FROM ve").getColumns().get(0).getDataType();
        assertEquals(5, ((StringType) plain).getMaxLength(),
            "two strings keep their own length — only the VARIANT widens it");
        engine.execute("CREATE OR REPLACE TABLE ve_out AS SELECT COALESCE(va, s) AS c FROM ve");
        assertEquals("VARCHAR(16777216)", storedTypeOfC(),
            "the CTAS clamps it to what a stored column may hold");
    }

    /** The stored type of column C in ve_out. */
    private String storedTypeOfC() {
        final dev.frostlake.storage.ResultSet rs = engine.executeQuery("DESCRIBE TABLE ve_out");
        String stored = "?";
        while (rs.next()) {
            if ("C".equalsIgnoreCase(String.valueOf(rs.getValue(0)))) {
                stored = String.valueOf(rs.getValue(1));
            }
        }
        return stored;
    }

    /** A VARIANT beside a temporal becomes that temporal, whichever is written first. */
    @Test
    public void aVariantBesideATemporalBecomesTheTemporal() {
        bothOrders("DATE", "va", "d");
        bothOrders("TIME", "va", "t");
        bothOrders("TIMESTAMP_NTZ", "va", "ts");
    }

    /** The three pairings that turn on POSITION rather than on the pair. */
    @Test
    public void booleanAndTheContainersGoByPosition() {
        assertEquals("VARIANT", typeOf("COALESCE(va, b)"));
        assertEquals("BOOLEAN", typeOf("COALESCE(b, va)"));
        assertEquals("VARIANT", typeOf("COALESCE(va, ob)"));
        assertEquals("OBJECT", typeOf("COALESCE(ob, va)"));
        assertEquals("VARIANT", typeOf("COALESCE(va, ar)"));
        assertEquals("ARRAY", typeOf("COALESCE(ar, va)"));
    }

    /**
     * THREE branches fold RIGHT TO LEFT. The first two lines are the pair that proves the direction:
     * same first branch, same middle branch, and the answer changes with the LAST one.
     */
    @Test
    public void threeBranchesFoldRightToLeft() {
        assertEquals("NUMBER", typeOf("COALESCE(i, va, s)"),
            "(va, s) folds to VARCHAR first, and a NUMBER beats a VARCHAR");
        assertEquals("VARIANT", typeOf("COALESCE(i, va, i)"),
            "(va, i) folds to VARIANT first, and a NUMBER does NOT beat a VARIANT");
        assertEquals("NUMBER", typeOf("COALESCE(i, s, va)"));
        assertEquals("VARIANT", typeOf("COALESCE(i, i, va)"));
        assertEquals("VARIANT", typeOf("COALESCE(va, i, s)"));
        assertEquals("VARIANT", typeOf("COALESCE(va, s, i)"));
        assertEquals("VARCHAR", typeOf("COALESCE(s, i, va)"));
        assertEquals("VARCHAR", typeOf("COALESCE(s, va, i)"));
        assertEquals("VARCHAR", typeOf("COALESCE(s, va, s)"));
        assertEquals("NUMBER", typeOf("COALESCE(i, va, s, b)"), "four branches follow the same fold");
    }

    /** Every conditional spelling agrees — it is the fold, not COALESCE. */
    @Test
    public void everyConditionalSpellingAgrees() {
        assertEquals("VARIANT", typeOf("IFF(i > 0, va, i)"));
        assertEquals("VARIANT", typeOf("IFF(i > 0, i, va)"));
        assertEquals("VARIANT", typeOf("CASE WHEN i > 0 THEN va ELSE i END"));
        assertEquals("VARIANT", typeOf("CASE WHEN i > 0 THEN i ELSE va END"));
        assertEquals("VARIANT", typeOf("NVL(va, i)"));
        assertEquals("VARIANT", typeOf("IFNULL(i, va)"));
        assertEquals("BOOLEAN", typeOf("IFF(i > 0, b, va)"), "position decides here too");
        assertEquals("VARIANT", typeOf("IFF(i > 0, va, b)"));
        assertEquals("NUMBER", typeOf("CASE WHEN i > 0 THEN i WHEN i > 1 THEN va ELSE s END"),
            "and a three-branch CASE folds right to left like a three-argument COALESCE");
    }

    /** The straggler: a string beside a TIME, whose DATE and TIMESTAMP siblings already worked. */
    @Test
    public void aStringBesideATimeBecomesTheTime() {
        bothOrders("TIME", "s", "t");
        bothOrders("DATE", "s", "d");
        bothOrders("TIMESTAMP_NTZ", "s", "ts");
    }

    /** The folds with no VARIANT in them, which this must not disturb. */
    @Test
    public void theOrdinaryFoldsAreUnchanged() {
        bothOrders("NUMBER", "i", "n");
        bothOrders("NUMBER", "i", "s");
        bothOrders("FLOAT", "i", "f");
        bothOrders("BOOLEAN", "i", "b");
        bothOrders("TIMESTAMP_NTZ", "d", "ts");
        assertEquals("BOOLEAN", typeOf("COALESCE(b, s)"), "a BOOLEAN beside a string goes by position");
        assertEquals("VARCHAR", typeOf("COALESCE(s, b)"));
        assertEquals("NUMBER", typeOf("COALESCE(i, s, i)"));
        assertEquals("NUMBER", typeOf("COALESCE(s, i, s)"));
        assertEquals("VARIANT", typeOf("COALESCE(va, va)"));
    }
}
