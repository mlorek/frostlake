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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The BIT family declares a width that follows its arguments' INTEGER digits — a NUMBER(p,s) counts
 * p - s, a literal its own digits — rather than NUMBER(38,0) for everything. BITAND and BITXOR take the
 * wider argument, at least two digits, a FLOAT counting for nothing and a text for thirteen, and a text in
 * second place caps the width at thirty-three; BITOR is one digit wider (a FLOAT or a text counting
 * eighteen), capped at thirty-eight; BITNOT and BITSHIFTRIGHT keep the argument's width; BITSHIFTLEFT is
 * always thirty-eight. The storage tag follows the width — at least eight bytes with a FLOAT or a text
 * argument, one with a NULL. Every cell is live-verified.
 */
public class BitwiseResultWidthTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE OR REPLACE TABLE rt (n NUMBER(5,0), f FLOAT, w NUMBER(38,0), d NUMBER(5,2), g VARCHAR(10), "
            + "s NUMBER(2,0), b NUMBER(10,0), x36 NUMBER(36,0), x30 NUMBER(30,0))");
        engine.execute("INSERT INTO rt VALUES (7, 3.0, 9, 12.34, '6', 3, 1234567890, 1, 1)");
    }

    /** SYSTEM$TYPEOF of each call over the one-row table, comma-separated. */
    private String types(final String... calls) {
        final StringBuilder sql = new StringBuilder("SELECT ");
        for (int i = 0; i < calls.length; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("SYSTEM$TYPEOF(").append(calls[i]).append(')');
        }
        final ResultSet rs = engine.executeQuery(sql.append(" FROM rt").toString());
        final StringBuilder all = new StringBuilder();
        for (int i = 0; i < calls.length; i++) {
            if (i > 0) {
                all.append(", ");
            }
            all.append(String.valueOf(rs.getRows().get(0).getValue(i)));
        }
        return all.toString();
    }

    @Test
    public void andAndXorTakeTheWiderIntegerPart() {
        assertEquals("NUMBER(5,0)[SB4], NUMBER(3,0)[SB2], NUMBER(38,0)[SB16], NUMBER(2,0)[SB1], NUMBER(3,0)[SB2], NUMBER(5,0)[SB4]",
            types("BITAND(n, 1)", "BITAND(d, 1)", "BITAND(w, 1)", "BITAND(5, 1)", "BITAND(123, 1)", "BITAND(12345, 1)"));
        assertEquals("NUMBER(5,0)[SB4], NUMBER(5,0)[SB4], NUMBER(10,0)[SB8], NUMBER(2,0)[SB1], NUMBER(2,0)[SB1]",
            types("BITAND(n, s)", "BITAND(d, n)", "BITAND(b, 1)", "BITAND(-5, 1)", "BITAND(1.5, 1)"));
        assertEquals("NUMBER(5,0)[SB4], NUMBER(2,0)[SB1], NUMBER(38,0)[SB16], NUMBER(3,0)[SB2]",
            types("BITXOR(n, 1)", "BITXOR(1, 1)", "BITXOR(n, w)", "BITXOR(d, 1)"));
    }

    @Test
    public void aFloatCountsForNothingAndATextForThirteen() {
        assertEquals("NUMBER(5,0)[SB8], NUMBER(2,0)[SB8], NUMBER(38,0)[SB16], NUMBER(3,0)[SB8], NUMBER(2,0)[SB8]",
            types("BITAND(f, n)", "BITAND(f, 1)", "BITAND(f, w)", "BITAND(f, d)", "BITXOR(f, 1)"));
        assertEquals("NUMBER(13,0)[SB8], NUMBER(13,0)[SB8], NUMBER(13,0)[SB8], NUMBER(13,0)[SB8]",
            types("BITAND(g, 1)", "BITAND(g, n)", "BITAND(n, g)", "BITXOR(g, 1)"));
        // A text in second place caps the width at thirty-three; in first place it does not.
        assertEquals("NUMBER(33,0)[SB16], NUMBER(38,0)[SB16], NUMBER(33,0)[SB16], NUMBER(36,0)[SB16], NUMBER(30,0)[SB16]",
            types("BITAND(w, g)", "BITAND(g, w)", "BITAND(x36, g)", "BITAND(g, x36)", "BITAND(x30, g)"));
        assertEquals("NUMBER(33,0)[SB16], NUMBER(38,0)[SB16]", types("BITXOR(w, g)", "BITXOR(g, w)"));
    }

    @Test
    public void orIsOneDigitWider() {
        assertEquals("NUMBER(6,0)[SB4], NUMBER(4,0)[SB2], NUMBER(2,0)[SB1], NUMBER(3,0)[SB2], NUMBER(11,0)[SB8], NUMBER(38,0)[SB16]",
            types("BITOR(n, 1)", "BITOR(d, 1)", "BITOR(1, 1)", "BITOR(s, s)", "BITOR(b, 1)", "BITOR(w, 1)"));
        assertEquals("NUMBER(19,0)[SB16], NUMBER(19,0)[SB16], NUMBER(19,0)[SB16], NUMBER(37,0)[SB16], NUMBER(37,0)[SB16]",
            types("BITOR(f, 1)", "BITOR(f, n)", "BITOR(g, 1)", "BITOR(x36, g)", "BITOR(g, x36)"));
    }

    @Test
    public void notAndTheShiftsKeepOrFixTheirWidth() {
        assertEquals("NUMBER(5,0)[SB4], NUMBER(2,0)[SB1], NUMBER(3,0)[SB2], NUMBER(10,0)[SB8], NUMBER(38,0)[SB16], NUMBER(2,0)[SB8], NUMBER(13,0)[SB8]",
            types("BITNOT(n)", "BITNOT(5)", "BITNOT(d)", "BITNOT(b)", "BITNOT(w)", "BITNOT(f)", "BITNOT(g)"));
        assertEquals("NUMBER(5,0)[SB4], NUMBER(3,0)[SB2], NUMBER(2,0)[SB8], NUMBER(13,0)[SB8], NUMBER(38,0)[SB16], NUMBER(38,0)[SB16]",
            types("BITSHIFTRIGHT(n, 1)", "BITSHIFTRIGHT(123, 1)", "BITSHIFTRIGHT(f, 1)", "BITSHIFTRIGHT(g, 1)",
                "BITSHIFTLEFT(n, 1)", "BITSHIFTLEFT(5, 1)"));
        // A NULL argument empties the interval, whatever the width.
        assertEquals("NUMBER(5,0)[SB1], NUMBER(2,0)[SB1]", types("BITAND(n, NULL)", "BITNOT(NULL)"));
    }

    @Test
    public void theValuesAreUnchanged() {
        final ResultSet rs = engine.executeQuery("SELECT BITAND(n, 1), BITAND(f, 1), BITAND(g, 1), BITAND(d, 1), BITNOT(g), "
            + "BITSHIFTLEFT(n, 1) FROM rt");
        final String[] expected = {"1", "1", "0", "0", "-7", "14"};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], String.valueOf(rs.getRows().get(0).getValue(i)), "column " + i);
        }
    }
}
