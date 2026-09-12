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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How a retype refusal SPELLS the two types it names. Every type was printed with whatever parameters
 * its class happened to carry, which was wrong three ways at once:
 *
 * <pre>
 *   FLOAT           was FLOAT(38,9)   the approximate family has no pair to quote — the same
 *                                     fabricated pair #344 removed from every other surface
 *   TIME, TIMESTAMP was TIME          the fractional-seconds precision was dropped, so an explicit
 *                                     TIME(3) came back as a bare TIME
 *   BINARY          was BINARY        the byte length was dropped
 * </pre>
 *
 * <p>DATE really does carry no precision, which is the cell that stops "add parameters everywhere"
 * from being the fix; and BOOLEAN, VARIANT, OBJECT and ARRAY name themselves bare.
 *
 * <p>The sentence was also missing its {@code SQL compilation error:} prefix outright — on both the
 * retype refusal and the collation one beside it.
 *
 * <p>Messages are compared with their line breaks normalised. Live puts this sentence's detail on the
 * PREFIX's own line and ends with a newline, where Frostlake's shared builder puts the detail on the
 * next line and ends without one; the positioned refusals agree byte for byte, so the difference is in
 * the un-positioned builder and is tracked separately rather than special-cased here.
 */
public class RetypeRefusalTypeTextTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE rt (f FLOAT, d DOUBLE, r REAL, n NUMBER(10,2),"
            + " i INT, v VARCHAR(10), cv VARCHAR(10) COLLATE 'en-ci')");
    }

    /** The refusal a retype raises, line breaks normalised, or "accepted" when it runs. */
    private String retype(final String column, final String target) {
        try {
            engine.execute("ALTER TABLE rt ALTER COLUMN " + column + " SET DATA TYPE " + target);
            return "accepted";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace("\n", " ").trim();
        }
    }

    /** The sentence, for a refusal that names no reason. */
    private String cannot(final String column, final String from, final String to) {
        return "SQL compilation error: cannot change column " + column
            + " from type " + from + " to " + to;
    }

    /** An approximate column names itself FLOAT, with nothing in brackets. */
    @Test
    public void anApproximateColumnHasNoPairToQuote() {
        assertEquals(cannot("F", "FLOAT", "NUMBER(10,2)"), retype("f", "NUMBER(10,2)"));
        assertEquals(cannot("F", "FLOAT", "VARCHAR(10)"), retype("f", "VARCHAR(10)"));
        assertEquals(cannot("D", "FLOAT", "NUMBER(5,0)"), retype("d", "NUMBER(5,0)"),
            "a DOUBLE is stored as FLOAT and named FLOAT");
        assertEquals(cannot("R", "FLOAT", "VARCHAR(5)"), retype("r", "VARCHAR(5)"),
            "and so is a REAL");
    }

    /** It reads the same as the TARGET of a retype, not only as its source. */
    @Test
    public void theApproximateTargetIsSpelledTheSameWay() {
        assertEquals(cannot("N", "NUMBER(10,2)", "FLOAT"), retype("n", "FLOAT"));
        assertEquals(cannot("I", "NUMBER(38,0)", "FLOAT"), retype("i", "FLOAT"));
    }

    /** A temporal names its fractional-seconds precision — except DATE, which has none. */
    @Test
    public void aTemporalNamesItsPrecision() {
        assertEquals(cannot("F", "FLOAT", "TIMESTAMP_NTZ(9)"), retype("f", "TIMESTAMP_NTZ"));
        assertEquals(cannot("F", "FLOAT", "TIMESTAMP_LTZ(9)"), retype("f", "TIMESTAMP_LTZ"));
        assertEquals(cannot("F", "FLOAT", "TIMESTAMP_TZ(9)"), retype("f", "TIMESTAMP_TZ"));
        assertEquals(cannot("F", "FLOAT", "TIME(9)"), retype("f", "TIME"));
        assertEquals(cannot("F", "FLOAT", "TIME(3)"), retype("f", "TIME(3)"),
            "an explicit precision survives");
        assertEquals(cannot("F", "FLOAT", "TIMESTAMP_NTZ(6)"), retype("f", "TIMESTAMP_NTZ(6)"));
        assertEquals(cannot("F", "FLOAT", "DATE"), retype("f", "DATE"),
            "and a DATE carries none at all");
    }

    /** A BINARY names its byte length; the parameterless families name themselves bare. */
    @Test
    public void theRemainingFamiliesSpellThemselves() {
        assertEquals(cannot("F", "FLOAT", "BINARY(8)"), retype("f", "BINARY(8)"));
        assertEquals(cannot("F", "FLOAT", "BOOLEAN"), retype("f", "BOOLEAN"));
        assertEquals(cannot("F", "FLOAT", "VARIANT"), retype("f", "VARIANT"));
        assertEquals(cannot("F", "FLOAT", "OBJECT"), retype("f", "OBJECT"));
        assertEquals(cannot("F", "FLOAT", "ARRAY"), retype("f", "ARRAY"));
        assertEquals(cannot("F", "FLOAT", "VARCHAR(16777216)"), retype("f", "TEXT"),
            "TEXT is a VARCHAR of the full width");
        assertEquals(cannot("F", "FLOAT", "NUMBER(38,0)"), retype("f", "NUMBER"),
            "a NUMBER with no parameters takes the defaults");
    }

    /** The collation refusal beside it, which shares the speller and was missing the prefix too. */
    @Test
    public void theCollationRefusalIsSpelledAlike() {
        assertEquals("SQL compilation error: cannot change column CV from type"
            + " \"VARCHAR(10) COLLATE 'en-ci'\" to \"VARCHAR(10)\""
            + " because they have incompatible collations.", retype("cv", "VARCHAR(10)"));
        assertEquals("SQL compilation error: cannot change column V from type"
            + " \"VARCHAR(10)\" to \"VARCHAR(10) COLLATE 'en-ci'\""
            + " because they have incompatible collations.",
            retype("v", "VARCHAR(10) COLLATE 'en-ci'"));
    }

    /** The retypes that are LEGAL must stay legal — the speller must not gain a refusal. */
    @Test
    public void theAcceptedRetypesAreUnchanged() {
        assertEquals("accepted", retype("f", "DOUBLE"), "within the approximate family");
        assertEquals("accepted", retype("n", "NUMBER(5,2)"));
    }

    /** And the reason clause still follows the two types when there is one. */
    @Test
    public void aRefusalReasonStillFollows() {
        assertEquals(cannot("V", "VARCHAR(10)", "VARCHAR(5)")
            + " because reducing the byte-length of a varchar is not supported.",
            retype("v", "VARCHAR(5)"));
    }
}
