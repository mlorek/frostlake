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
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a STRING arm contributes to a set operation's fold when the other arms do not all agree.
 *
 * <p>A string arm does not contribute ITSELF — it contributes whatever the type it MEETS makes of it,
 * which is how {@code i UNION v} comes out NUMBER(38,5) rather than VARCHAR. The question Frostlake
 * used to ask was "do all the NON-STRING arms agree with each other?", and it switched that
 * substitution off entirely when they did not:
 *
 * <pre>
 *   SELECT i UNION SELECT v UNION SELECT bn        both engines REFUSE, and named different types
 *       live  expected BINARY(5),  got NUMBER(38,5)
 *       was   expected VARCHAR(5), got NUMBER(38,0)
 * </pre>
 *
 * <p>Live folds i with v into NUMBER(38,5) — the very type it gives the two-arm {@code i UNION v} —
 * and breaks on the BINARY. Frostlake never folded i with v at all: it broke an arm EARLIER, on the
 * VARCHAR, and reported the running fold as the NUMBER(38,0) it started with. The arms AFTER a string
 * have nothing to say about what that string meets.
 *
 * <p>THE SENTENCE IS THE EVIDENCE. Both engines refuse all of these, so nothing here is about
 * acceptance — the two type names are the only place the fold's shape is visible from outside, which
 * is why a wording gap is worth closing rather than shrugging at.
 *
 * <p>ONE ACCEPTANCE DID CHANGE, and in the safe direction: {@code v UNION bn} was ACCEPTED as
 * VARCHAR(5) and live refuses it. A leading string arm now looks ahead to the first non-string arm to
 * see what it will meet, which is what catches it.
 */
public class MixedArmStringFoldTest extends BaseDatabaseTest {

    private static final String REFUSAL =
        "inconsistent data type for result columns for set operator input branches, ";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE sa (i INT, n NUMBER(10,2), v VARCHAR(5),"
            + " bn BINARY(5), d DATE, b BOOLEAN, f FLOAT)");
    }

    /** The folded type of a UNION over these columns, or its refusal. */
    private String fold(final String... cols) {
        final StringBuilder sql = new StringBuilder();
        for (final String c : cols) {
            if (sql.length() > 0) {
                sql.append(" UNION ");
            }
            sql.append("SELECT ").append(c).append(" FROM sa");
        }
        try {
            final ResultSet rs = engine.executeQuery(sql.toString());
            final DataType t = rs.getColumns().get(0).getDataType();
            if (t instanceof NumericType && !"FLOAT".equals(t.getName())) {
                return t.getName() + "(" + ((NumericType) t).getPrecision() + ","
                    + ((NumericType) t).getScale() + ")";
            }
            if (t instanceof StringType) {
                return t.getName() + "(" + ((StringType) t).getMaxLength() + ")";
            }
            return t == null ? "null" : t.getName();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private String refusal(final String expected, final String got) {
        return "SQL compilation error:|" + REFUSAL + "expected " + expected + ", got " + got;
    }

    /** The two-arm fold every three-arm case builds on — the string takes the NUMBER's shape. */
    @Test
    public void aStringBesideANumberTakesItsShape() {
        assertEquals("NUMBER(38,5)", fold("i", "v"));
        assertEquals("NUMBER(38,5)", fold("v", "i"), "and it does so from either side");
        assertEquals("NUMBER(18,5)", fold("n", "v"));
    }

    /** With a BINARY at the end, the fold reaches it — it does not break on the string first. */
    @Test
    public void theFoldReachesTheBinary() {
        assertEquals(refusal("BINARY(5)", "NUMBER(38,5)"), fold("i", "v", "bn"));
        assertEquals(refusal("BINARY(5)", "NUMBER(38,5)"), fold("v", "i", "bn"),
            "the string leading changes nothing — it still meets the NUMBER");
    }

    /** A DATE and a BOOLEAN behave the same way, though their string pairings differ. */
    @Test
    public void aDateAndABooleanAreReachedToo() {
        assertEquals(refusal("DATE", "NUMBER(38,5)"), fold("i", "v", "d"));
        assertEquals(refusal("DATE", "NUMBER(38,5)"), fold("v", "i", "d"));
        assertEquals(refusal("BOOLEAN", "NUMBER(38,5)"), fold("i", "v", "b"));
        assertEquals(refusal("BOOLEAN", "NUMBER(38,5)"), fold("v", "i", "b"));
    }

    /** When the offending arm comes FIRST the fold breaks later, and the sentence swaps round. */
    @Test
    public void anOffendingLeadingArmBreaksLater() {
        assertEquals(refusal("NUMBER(38,0)", "DATE"), fold("d", "v", "i"),
            "d and v fold to DATE, and the NUMBER is what DATE cannot take");
        assertEquals(refusal("VARCHAR(5)", "BINARY(5)"), fold("bn", "v", "i"),
            "a BINARY takes no string at all, so the fold stops at the second arm");
        assertEquals(refusal("DATE", "NUMBER(38,0)"), fold("i", "d", "v"),
            "and a pair that cannot fold breaks before the string is ever reached");
    }

    /** A string beside a BINARY is refused outright — the acceptance this closed. */
    @Test
    public void aStringBesideABinaryIsRefused() {
        assertEquals(refusal("BINARY(5)", "VARCHAR(5)"), fold("v", "bn"));
    }

    /** A string beside a DATE or a BOOLEAN is NOT — each pairing has its own answer. */
    @Test
    public void aStringBesideADateOrABooleanFolds() {
        assertEquals("DATE", fold("v", "d"));
        assertEquals("VARCHAR(5)", fold("v", "b"));
        assertEquals("BOOLEAN", fold("b", "v", "i"),
            "and this three-arm shape reads, which the change had to leave alone");
    }

    /** The folds that SUCCEED must keep succeeding, at the same width. */
    @Test
    public void theSucceedingFoldsAreUnchanged() {
        assertEquals("NUMBER(38,5)", fold("i", "v", "n"));
        assertEquals("NUMBER(38,5)", fold("v", "i", "n"));
        assertEquals("NUMBER(38,5)", fold("i", "n", "v"));
        assertEquals("NUMBER(38,5)", fold("v", "v", "i"));
        assertEquals("NUMBER(38,5)", fold("i", "i", "v"));
        assertEquals("FLOAT", fold("i", "v", "f"));
        assertEquals("FLOAT", fold("i", "v", "n", "f"));
        assertEquals("NUMBER(38,2)", fold("i", "n"), "and the string-free folds beside them");
        assertEquals("FLOAT", fold("i", "f"));
    }

    /** With no string among them the fold is untouched, which is the guard's own control. */
    @Test
    public void aStringFreeFoldIsUntouched() {
        assertEquals(refusal("BINARY(5)", "NUMBER(38,2)"), fold("i", "n", "bn"),
            "the NUMBERs fold with each other first, so the running answer carries n's scale");
        assertEquals(refusal("BINARY(5)", "NUMBER(38,0)"), fold("i", "bn", "d"));
        assertEquals(refusal("BINARY(5)", "NUMBER(38,0)"), fold("i", "bn"));
        assertEquals(refusal("DATE", "NUMBER(38,0)"), fold("i", "d"));
        assertEquals(refusal("BOOLEAN", "NUMBER(38,0)"), fold("i", "b"));
    }
}
