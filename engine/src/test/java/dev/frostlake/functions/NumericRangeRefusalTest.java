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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

/**
 * A number too wide for where it is going. Live spells its INTERNAL type into the refusal — the storage
 * class, the declared width and the nullability — and Frostlake used to answer a sentence of its own on
 * the CAST path and nothing at all on the WRITE path, where a four-digit value simply settled into a
 * two-digit column.
 *
 * <p>★ THE CLASS IS THE MEASURED HALF. It is the narrowest signed-integer width that holds the number,
 * not a digit count, and the number it is read off differs by path: a cast, an INSERT … SELECT, an UPDATE
 * and a MERGE all measure the VALUE, while INSERT … VALUES measures the COLUMN. The pair pinned below
 * (10^11 into a NUMBER(2,0), written twice) is what proves it: SB1 through VALUES, SB8 through SET.
 *
 * <p>★ THE THREE SOURCE FAMILIES GET THREE SENTENCES: an exact number the typed one, a VARIANT one with
 * the type left out entirely, and a STRING one that names the text and no type at all.
 */
public class NumericRangeRefusalTest extends BaseDatabaseTest {

    private String refusalOf(final String sql) {
        return assertThrows(RuntimeException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
    }

    @Test
    public void castNamesTheStorageClassTheWidthAndTheNullability() {
        assertEquals("Number out of representable range: type FIXED[SB2](2,0){not null}, value 1000",
            refusalOf("SELECT CAST(1000 AS NUMBER(2,0)) AS v"));
        // The class follows the VALUE, so the same target with a smaller value reads narrower.
        assertEquals("Number out of representable range: type FIXED[SB1](2,0){not null}, value 100",
            refusalOf("SELECT CAST(100 AS NUMBER(2,0)) AS v"));
    }

    @Test
    public void precisionZeroPrintsNoWidthAtAll() {
        assertEquals("Number out of representable range: type FIXED[SB1]{not null}, value 1",
            refusalOf("SELECT CAST(1 AS NUMBER(0,0)) AS v"));
    }

    @Test
    public void theClassCountsTheValueShiftedToTheTargetScale() {
        // 12.39 at scale 1 is 124, which a single byte holds; at its OWN scale it would be 1239, which
        // would not — the shift is the target's, and this cell is what says so.
        assertEquals("Number out of representable range: type FIXED[SB1](2,1){not null}, value 12.39",
            refusalOf("SELECT CAST(12.39 AS NUMBER(2,1)) AS v"));
        assertEquals("Number out of representable range: type FIXED[SB2](4,3){not null}, value 12.3456",
            refusalOf("SELECT CAST(12.3456 AS NUMBER(4,3)) AS v"));
    }

    @Test
    public void theValueIsPrintedAsWrittenWithTrailingZerosDropped() {
        assertEquals("Number out of representable range: type FIXED[SB2](2,0){not null}, value 1000",
            refusalOf("SELECT CAST(1000.00 AS NUMBER(2,0)) AS v"));
    }

    @Test
    public void nullabilityComesFromTheSourceColumn() {
        engine.execute("CREATE TABLE nr_src (n NUMBER(38,0), m NUMBER(38,0) NOT NULL)");
        engine.execute("INSERT INTO nr_src VALUES (1000, 1000)");
        assertEquals("Number out of representable range: type FIXED[SB2](2,0){nullable}, value 1000",
            refusalOf("SELECT CAST(n AS NUMBER(2,0)) AS v FROM nr_src"));
        assertEquals("Number out of representable range: type FIXED[SB2](2,0){not null}, value 1000",
            refusalOf("SELECT CAST(m AS NUMBER(2,0)) AS v FROM nr_src"));
    }

    @Test
    public void aStringSourceNamesTheTextAndNoType() {
        assertEquals("Numeric value '1000' is out of range",
            refusalOf("SELECT CAST('1000' AS NUMBER(2,0)) AS v"));
    }

    @Test
    public void aVariantSourceLeavesTheTypeOut() {
        assertEquals("Number out of representable range: type FIXED, value 1000",
            refusalOf("SELECT CAST(PARSE_JSON('1000') AS NUMBER(2,0)) AS v"));
    }

    @Test
    public void anApproximateSourceIsClassedByTheTargetInstead() {
        // A FLOAT has no unscaled integer to measure, so the class is the NUMBER(2,0) slot's own SB1 —
        // where the same value arriving exact would have read SB2.
        assertEquals("Number out of representable range: type FIXED[SB1](2,0){not null}, value 1000",
            refusalOf("SELECT CAST(1000.0::FLOAT AS NUMBER(2,0)) AS v"));
    }

    @Test
    public void aWriteIntoTooNarrowAColumnIsRefusedAndWrapped() {
        engine.execute("CREATE TABLE nr_small (s NUMBER(2,0))");
        assertEquals("DML operation to table NR_SMALL failed on column S with error: "
            + "Number out of representable range: type FIXED[SB1](2,0){nullable}, value 1000",
            refusalOf("INSERT INTO nr_small VALUES (1000)"));
        final ResultSet after = engine.executeQuery("SELECT COUNT(*) AS c FROM nr_small");
        assertEquals("0", String.valueOf(after.getRows().get(0).getValue(0)));
    }

    @Test
    public void theWriteReadsItsNullabilityOffTheTargetColumn() {
        engine.execute("CREATE TABLE nr_nn (s NUMBER(2,0) NOT NULL)");
        assertEquals("DML operation to table NR_NN failed on column S with error: "
            + "Number out of representable range: type FIXED[SB1](2,0){not null}, value 1000",
            refusalOf("INSERT INTO nr_nn VALUES (1000)"));
    }

    @Test
    public void valuesClassesTheColumnWhereSetClassesTheValue() {
        engine.execute("CREATE TABLE nr_two (s NUMBER(2,0))");
        engine.execute("INSERT INTO nr_two VALUES (1)");
        // One column, one value, two spellings of the write — and two different classes.
        assertEquals("DML operation to table NR_TWO failed on column S with error: "
            + "Number out of representable range: type FIXED[SB1](2,0){nullable}, value 100000000000",
            refusalOf("INSERT INTO nr_two VALUES (100000000000)"));
        assertEquals("DML operation to table NR_TWO failed on column S with error: "
            + "Number out of representable range: type FIXED[SB8](2,0){nullable}, value 100000000000",
            refusalOf("UPDATE nr_two SET s = 100000000000"));
    }

    @Test
    public void aQuerySourcedInsertAndAMergeBothClassTheValue() {
        engine.execute("CREATE TABLE nr_dst (s NUMBER(2,0))");
        engine.execute("INSERT INTO nr_dst VALUES (1)");
        assertEquals("DML operation to table NR_DST failed on column S with error: "
            + "Number out of representable range: type FIXED[SB2](2,0){nullable}, value 1000",
            refusalOf("INSERT INTO nr_dst SELECT 1000"));
        assertEquals("DML operation to table NR_DST failed on column S with error: "
            + "Number out of representable range: type FIXED[SB2](2,0){nullable}, value 1000",
            refusalOf("""
                MERGE INTO nr_dst USING (SELECT 1 AS k) src ON nr_dst.s = src.k
                WHEN MATCHED THEN UPDATE SET s = 1000"""));
    }

    @Test
    public void aStringWrittenIntoTooNarrowAColumnKeepsTheTextSentence() {
        engine.execute("CREATE TABLE nr_str (s NUMBER(2,0))");
        assertEquals("DML operation to table NR_STR failed on column S with error: "
            + "Numeric value '1000' is out of range",
            refusalOf("INSERT INTO nr_str VALUES ('1000')"));
    }

    @Test
    public void anOverflowingDefaultIsRefusedTheSameWay() {
        engine.execute("CREATE TABLE nr_def (s NUMBER(2,0) DEFAULT 1000)");
        assertEquals("DML operation to table NR_DEF failed on column S with error: "
            + "Number out of representable range: type FIXED[SB1](2,0){nullable}, value 1000",
            refusalOf("INSERT INTO nr_def VALUES (DEFAULT)"));
    }

    @Test
    public void theWholeFamilyIsRowTime() {
        // No rows, no values, no refusal — on either engine. Nothing here is a compile-time check.
        engine.execute("CREATE TABLE nr_empty (n NUMBER(38,0))");
        engine.execute("CREATE TABLE nr_target (s NUMBER(2,0))");
        final ResultSet cast = engine.executeQuery("SELECT CAST(n AS NUMBER(2,0)) AS v FROM nr_empty");
        assertEquals(0, cast.getRows().size());
        engine.execute("INSERT INTO nr_target SELECT n FROM nr_empty");
        final ResultSet loaded = engine.executeQuery("SELECT COUNT(*) AS c FROM nr_target");
        assertEquals("0", String.valueOf(loaded.getRows().get(0).getValue(0)));
    }

    @Test
    public void toNumberEnforcesTheWidthItDeclares() {
        assertEquals("Number out of representable range: type FIXED[SB2](2,0){not null}, value 1000",
            refusalOf("SELECT TO_NUMBER(1000, 2, 0) AS v"));
        // A lone precision argument means scale 0 …
        assertEquals("Number out of representable range: type FIXED[SB2](2,0){not null}, value 1000",
            refusalOf("SELECT TO_NUMBER(1000, 2) AS v"));
        // … and the declared scale shifts the value before its class is read.
        assertEquals("Number out of representable range: type FIXED[SB4](4,2){not null}, value 1000",
            refusalOf("SELECT TO_DECIMAL(1000, 4, 2) AS v"));
        // A string source keeps the text sentence, with the decoration it was WRITTEN with.
        assertEquals("Numeric value '1,234.56' is out of range",
            refusalOf("SELECT TO_NUMBER('1,234.56', '9,999.99', 3, 1) AS v"));
    }

    @Test
    public void aWidthItFitsIsAnswered() {
        final ResultSet fits = engine.executeQuery("SELECT TO_NUMBER(10, 2) AS a, TO_NUMBER(1.5, 2, 1) AS b,"
            + " TO_NUMBER(1000) AS c, TO_NUMBER(1000, 38, 0) AS d, CAST(1.239 AS NUMBER(3,2)) AS e");
        assertEquals("10", String.valueOf(fits.getRows().get(0).getValue(0)));
        assertEquals("1.5", String.valueOf(fits.getRows().get(0).getValue(1)));
        assertEquals("1000", String.valueOf(fits.getRows().get(0).getValue(2)));
        assertEquals("1000", String.valueOf(fits.getRows().get(0).getValue(3)));
        assertEquals("1.24", String.valueOf(fits.getRows().get(0).getValue(4)));
    }

    @Test
    public void theTryFormsAnswerNullAndFloatNeverOverflows() {
        final ResultSet tried = engine.executeQuery("SELECT TRY_CAST('1000' AS NUMBER(2,0)) AS a,"
            + " TRY_TO_NUMBER('1000', 2, 0) AS b, CAST(1e308 AS FLOAT) AS c");
        assertNull(tried.getRows().get(0).getValue(0));
        assertNull(tried.getRows().get(0).getValue(1));
        assertEquals("1.0E308", String.valueOf(tried.getRows().get(0).getValue(2)));
    }
}
