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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The WAREHOUSE_SIZE vocabulary, and what a value outside it does.
 *
 * <p>★ ANY STRING WAS A SIZE. Frostlake fell back to X-Small for anything it did not recognise, so
 * {@code WAREHOUSE_SIZE = 'HUGE'} created a warehouse carrying a size that cannot exist and nothing
 * marked it — accepted-but-invalid, the direction that matters most. Live refuses:
 * {@code invalid type of property 'HUGE' for 'WAREHOUSE_SIZE'}, naming the VALUE where a property name
 * would normally go, which is live's own phrasing and not a slip.
 *
 * <p>★ THE VOCABULARY HAD TO BE MEASURED, twice over — once for what is accepted and once for what
 * SHOW then reports, since every spelling NORMALISES. Two families of spelling exist for the large
 * sizes ({@code XXLARGE} / {@code X2LARGE} / {@code 2X-LARGE} all mean the same thing) and both hyphen
 * and no-hyphen forms are taken, case-insensitively, quoted or bare.
 *
 * <p>★ THE X-REPEATING SPELLING STOPS AT THREE: {@code XXLARGE} and {@code XXXLARGE} are accepted and
 * {@code XXXXLARGE} is REFUSED, while {@code X4LARGE} and {@code 4X-LARGE} for the same size are fine.
 * A rule nobody would guess, and the reason the accepted set is a measured list rather than a pattern.
 *
 * <p>★ EIGHT SPELLINGS FROSTLAKE TOOK ARE NOT SIZES AT ALL — {@code XS}, {@code S}, {@code M},
 * {@code L}, {@code XL}, {@code XXL}, {@code XXXL}, {@code XXXXL} were its own invention, and live
 * refuses every one. They are gone.
 *
 * <p>Left for their own task: SCALING_POLICY, which Frostlake's grammar cannot even parse, and
 * WAREHOUSE_TYPE — each refused with a DIFFERENT sentence from this one and from each other.
 */
public class WarehouseSizeVocabularyTest extends BaseDatabaseTest {

    /** The size SHOW reports for a warehouse created with {@code written}. */
    private String shown(final String written) {
        engine.execute("CREATE OR REPLACE WAREHOUSE w_sz WAREHOUSE_SIZE = " + written);
        final ResultSet rs = engine.executeQuery("SHOW WAREHOUSES LIKE 'w_sz'");
        return rs.next() ? String.valueOf(rs.getValue(3)) : "<no row>";
    }

    private String refusalFor(final String sql) {
        try {
            engine.execute(sql);
            return "ACCEPTED";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private String invalidSize(final String value) {
        return "SQL compilation error:|invalid type of property '" + value
            + "' for 'WAREHOUSE_SIZE'";
    }

    /** The small end, and its two spellings. */
    @Test
    public void thesmallSizesNormaliseToOneName() {
        assertEquals("X-Small", shown("'XSMALL'"));
        assertEquals("X-Small", shown("'X-SMALL'"));
        assertEquals("Small", shown("'SMALL'"));
        assertEquals("Medium", shown("'MEDIUM'"));
        assertEquals("Large", shown("'LARGE'"));
    }

    /** ★ Three spellings for each large size, all normalising to the numeric one. */
    @Test
    public void thelargeSizesTakeThreeSpellingsEach() {
        assertEquals("X-Large", shown("'XLARGE'"));
        assertEquals("X-Large", shown("'X-LARGE'"));
        assertEquals("2X-Large", shown("'XXLARGE'"));
        assertEquals("2X-Large", shown("'X2LARGE'"));
        assertEquals("2X-Large", shown("'2X-LARGE'"));
        assertEquals("3X-Large", shown("'XXXLARGE'"));
        assertEquals("3X-Large", shown("'X3LARGE'"));
        assertEquals("3X-Large", shown("'3X-LARGE'"));
    }

    /** ★ The X-repeating spelling stops at three — X4 and up are numeric only. */
    @Test
    public void thexRepeatingSpellingStopsAtThree() {
        assertEquals(invalidSize("XXXXLARGE"),
            refusalFor("CREATE OR REPLACE WAREHOUSE w_sz WAREHOUSE_SIZE = 'XXXXLARGE'"));
        assertEquals("4X-Large", shown("'X4LARGE'"));
        assertEquals("4X-Large", shown("'4X-LARGE'"));
    }

    /** The two largest sizes, which Frostlake had no name for at all. */
    @Test
    public void thetwoLargestSizesExist() {
        assertEquals("5X-Large", shown("'X5LARGE'"));
        assertEquals("5X-Large", shown("'5X-LARGE'"));
        assertEquals("6X-Large", shown("'X6LARGE'"));
        assertEquals("6X-Large", shown("'6X-LARGE'"));
    }

    /** Case and quoting are both immaterial. */
    @Test
    public void caseAndQuotingAreImmaterial() {
        assertEquals("X-Small", shown("'xsmall'"));
        assertEquals("X-Small", shown("XSMALL"));
        assertEquals("Medium", shown("MEDIUM"));
    }

    /** ★ A value that is not a size is REFUSED, echoed exactly as written. */
    @Test
    public void anonSizeIsRefused() {
        assertEquals(invalidSize("HUGE"),
            refusalFor("CREATE OR REPLACE WAREHOUSE w_sz2 WAREHOUSE_SIZE = 'HUGE'"));
        assertEquals(invalidSize(""),
            refusalFor("CREATE OR REPLACE WAREHOUSE w_sz3 WAREHOUSE_SIZE = ''"));
        assertEquals(invalidSize("X SMALL"),
            refusalFor("CREATE OR REPLACE WAREHOUSE w_sz4 WAREHOUSE_SIZE = 'X SMALL'"),
            "a SPACE where the hyphen goes is not a spelling live takes");
        assertEquals(invalidSize("HUGE"),
            refusalFor("CREATE OR REPLACE WAREHOUSE w_sz5 WAREHOUSE_SIZE = HUGE"),
            "unquoted too, and echoed the same way");
    }

    /** ★ The abbreviations Frostlake invented are gone. */
    @Test
    public void theinventedAbbreviationsAreGone() {
        assertEquals(invalidSize("XS"),
            refusalFor("CREATE OR REPLACE WAREHOUSE w_sz6 WAREHOUSE_SIZE = 'XS'"));
        assertEquals(invalidSize("M"),
            refusalFor("CREATE OR REPLACE WAREHOUSE w_sz7 WAREHOUSE_SIZE = 'M'"));
        assertEquals(invalidSize("XXL"),
            refusalFor("CREATE OR REPLACE WAREHOUSE w_sz8 WAREHOUSE_SIZE = 'XXL'"));
    }

    /** ALTER says the same thing as CREATE. */
    @Test
    public void alterSaysTheSameThing() {
        engine.execute("CREATE OR REPLACE WAREHOUSE w_sz9 WAREHOUSE_SIZE = 'SMALL'");
        assertEquals(invalidSize("HUGE"),
            refusalFor("ALTER WAREHOUSE w_sz9 SET WAREHOUSE_SIZE = 'HUGE'"));
        assertEquals("ACCEPTED",
            refusalFor("ALTER WAREHOUSE w_sz9 SET WAREHOUSE_SIZE = 'LARGE'"));
    }
}
