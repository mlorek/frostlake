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

package dev.frostlake.expressions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The declared type of an exact sum or difference: the wider scale, and an integer part one digit wider
 * than the wider operand's — three digits at least when the operands' scales differ (live types 2 + 3.5
 * NUMBER(4,1), where 2 + 3 is NUMBER(2,0)) — capped at 38. Every cell is live-verified.
 */
public class MixedScaleSumTypeTest extends BaseDatabaseTest {

    /** Every row's cells, a comma between cells and a bar between rows. */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                out.append(row.getValue(i));
            }
        }
        return out.toString();
    }

    /** Every pair of eighteen declared types, summed: operands at different scales give an integer part of three digits at least. */
    @Test
    public void columnSumsAtDifferentScalesTakeAThreeDigitIntegerPart() {
        engine.execute("CREATE OR REPLACE TABLE tn (c1_0 NUMBER(1,0), c2_0 NUMBER(2,0), c3_0 NUMBER(3,0), c2_1 NUMBER(2,1), c3_1 NUMBER(3,1), c4_1 NUMBER(4,1), c3_2 NUMBER(3,2), c4_2 NUMBER(4,2), c5_2 NUMBER(5,2), c4_3 NUMBER(4,3), c5_3 NUMBER(5,3), c6_3 NUMBER(6,3), c1_1 NUMBER(1,1), c2_2 NUMBER(2,2), c38_0 NUMBER(38,0), c38_2 NUMBER(38,2), c37_1 NUMBER(37,1), c36_2 NUMBER(36,2))");
        engine.execute("INSERT INTO tn VALUES (0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)");
        assertEquals("NUMBER(3,0)[SB1]", rows("SELECT SYSTEM$TYPEOF(c1_0 + c2_0) FROM tn"));
        assertEquals("NUMBER(4,0)[SB1]", rows("SELECT SYSTEM$TYPEOF(c1_0 + c3_0) FROM tn"));
        assertEquals("NUMBER(4,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(c1_0 + c2_1) FROM tn"));
        assertEquals("NUMBER(4,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(c1_0 + c3_1) FROM tn"));
        assertEquals("NUMBER(5,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(c1_0 + c4_1) FROM tn"));
        assertEquals("NUMBER(5,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c1_0 + c3_2) FROM tn"));
        assertEquals("NUMBER(5,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c1_0 + c4_2) FROM tn"));
        assertEquals("NUMBER(6,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c1_0 + c5_2) FROM tn"));
        assertEquals("NUMBER(6,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c1_0 + c4_3) FROM tn"));
        assertEquals("NUMBER(6,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c1_0 + c5_3) FROM tn"));
        assertEquals("NUMBER(7,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c1_0 + c6_3) FROM tn"));
        assertEquals("NUMBER(4,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(c1_0 + c1_1) FROM tn"));
        assertEquals("NUMBER(5,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c1_0 + c2_2) FROM tn"));
        assertEquals("NUMBER(38,0)[SB1]", rows("SELECT SYSTEM$TYPEOF(c1_0 + c38_0) FROM tn"));
        assertEquals("NUMBER(38,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c1_0 + c38_2) FROM tn"));
        assertEquals("NUMBER(38,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(c1_0 + c37_1) FROM tn"));
        assertEquals("NUMBER(37,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c1_0 + c36_2) FROM tn"));
        assertEquals("NUMBER(4,0)[SB1]", rows("SELECT SYSTEM$TYPEOF(c2_0 + c3_0) FROM tn"));
        assertEquals("NUMBER(4,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(c2_0 + c2_1) FROM tn"));
        assertEquals("NUMBER(4,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(c2_0 + c3_1) FROM tn"));
        assertEquals("NUMBER(5,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(c2_0 + c4_1) FROM tn"));
        assertEquals("NUMBER(5,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c2_0 + c3_2) FROM tn"));
        assertEquals("NUMBER(5,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c2_0 + c4_2) FROM tn"));
        assertEquals("NUMBER(6,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c2_0 + c5_2) FROM tn"));
        assertEquals("NUMBER(6,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c2_0 + c4_3) FROM tn"));
        assertEquals("NUMBER(6,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c2_0 + c5_3) FROM tn"));
        assertEquals("NUMBER(7,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c2_0 + c6_3) FROM tn"));
        assertEquals("NUMBER(4,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(c2_0 + c1_1) FROM tn"));
        assertEquals("NUMBER(5,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c2_0 + c2_2) FROM tn"));
        assertEquals("NUMBER(38,0)[SB1]", rows("SELECT SYSTEM$TYPEOF(c2_0 + c38_0) FROM tn"));
        assertEquals("NUMBER(38,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c2_0 + c38_2) FROM tn"));
        assertEquals("NUMBER(38,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(c2_0 + c37_1) FROM tn"));
        assertEquals("NUMBER(37,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c2_0 + c36_2) FROM tn"));
        assertEquals("NUMBER(5,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_0 + c2_1) FROM tn"));
        assertEquals("NUMBER(5,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_0 + c3_1) FROM tn"));
        assertEquals("NUMBER(5,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_0 + c4_1) FROM tn"));
        assertEquals("NUMBER(6,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_0 + c3_2) FROM tn"));
        assertEquals("NUMBER(6,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_0 + c4_2) FROM tn"));
        assertEquals("NUMBER(6,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_0 + c5_2) FROM tn"));
        assertEquals("NUMBER(7,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_0 + c4_3) FROM tn"));
        assertEquals("NUMBER(7,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_0 + c5_3) FROM tn"));
        assertEquals("NUMBER(7,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_0 + c6_3) FROM tn"));
        assertEquals("NUMBER(5,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_0 + c1_1) FROM tn"));
        assertEquals("NUMBER(6,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_0 + c2_2) FROM tn"));
        assertEquals("NUMBER(38,0)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_0 + c38_0) FROM tn"));
        assertEquals("NUMBER(38,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_0 + c38_2) FROM tn"));
        assertEquals("NUMBER(38,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_0 + c37_1) FROM tn"));
        assertEquals("NUMBER(37,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_0 + c36_2) FROM tn"));
        assertEquals("NUMBER(4,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(c2_1 + c3_1) FROM tn"));
        assertEquals("NUMBER(5,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(c2_1 + c4_1) FROM tn"));
        assertEquals("NUMBER(5,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c2_1 + c3_2) FROM tn"));
        assertEquals("NUMBER(5,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c2_1 + c4_2) FROM tn"));
        assertEquals("NUMBER(6,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c2_1 + c5_2) FROM tn"));
        assertEquals("NUMBER(6,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c2_1 + c4_3) FROM tn"));
        assertEquals("NUMBER(6,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c2_1 + c5_3) FROM tn"));
        assertEquals("NUMBER(7,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c2_1 + c6_3) FROM tn"));
        assertEquals("NUMBER(3,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(c2_1 + c1_1) FROM tn"));
        assertEquals("NUMBER(5,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c2_1 + c2_2) FROM tn"));
        assertEquals("NUMBER(38,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(c2_1 + c38_0) FROM tn"));
        assertEquals("NUMBER(38,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c2_1 + c38_2) FROM tn"));
        assertEquals("NUMBER(38,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(c2_1 + c37_1) FROM tn"));
        assertEquals("NUMBER(37,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c2_1 + c36_2) FROM tn"));
        assertEquals("NUMBER(5,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_1 + c4_1) FROM tn"));
        assertEquals("NUMBER(5,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_1 + c3_2) FROM tn"));
        assertEquals("NUMBER(5,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_1 + c4_2) FROM tn"));
        assertEquals("NUMBER(6,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_1 + c5_2) FROM tn"));
        assertEquals("NUMBER(6,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_1 + c4_3) FROM tn"));
        assertEquals("NUMBER(6,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_1 + c5_3) FROM tn"));
        assertEquals("NUMBER(7,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_1 + c6_3) FROM tn"));
        assertEquals("NUMBER(4,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_1 + c1_1) FROM tn"));
        assertEquals("NUMBER(5,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_1 + c2_2) FROM tn"));
        assertEquals("NUMBER(38,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_1 + c38_0) FROM tn"));
        assertEquals("NUMBER(38,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_1 + c38_2) FROM tn"));
        assertEquals("NUMBER(38,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_1 + c37_1) FROM tn"));
        assertEquals("NUMBER(37,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_1 + c36_2) FROM tn"));
        assertEquals("NUMBER(6,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c4_1 + c3_2) FROM tn"));
        assertEquals("NUMBER(6,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c4_1 + c4_2) FROM tn"));
        assertEquals("NUMBER(6,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c4_1 + c5_2) FROM tn"));
        assertEquals("NUMBER(7,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c4_1 + c4_3) FROM tn"));
        assertEquals("NUMBER(7,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c4_1 + c5_3) FROM tn"));
        assertEquals("NUMBER(7,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c4_1 + c6_3) FROM tn"));
        assertEquals("NUMBER(5,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(c4_1 + c1_1) FROM tn"));
        assertEquals("NUMBER(6,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c4_1 + c2_2) FROM tn"));
        assertEquals("NUMBER(38,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(c4_1 + c38_0) FROM tn"));
        assertEquals("NUMBER(38,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c4_1 + c38_2) FROM tn"));
        assertEquals("NUMBER(38,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(c4_1 + c37_1) FROM tn"));
        assertEquals("NUMBER(37,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c4_1 + c36_2) FROM tn"));
        assertEquals("NUMBER(5,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_2 + c4_2) FROM tn"));
        assertEquals("NUMBER(6,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_2 + c5_2) FROM tn"));
        assertEquals("NUMBER(6,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_2 + c4_3) FROM tn"));
        assertEquals("NUMBER(6,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_2 + c5_3) FROM tn"));
        assertEquals("NUMBER(7,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_2 + c6_3) FROM tn"));
        assertEquals("NUMBER(5,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_2 + c1_1) FROM tn"));
        assertEquals("NUMBER(4,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_2 + c2_2) FROM tn"));
        assertEquals("NUMBER(38,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_2 + c38_0) FROM tn"));
        assertEquals("NUMBER(38,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_2 + c38_2) FROM tn"));
        assertEquals("NUMBER(38,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_2 + c37_1) FROM tn"));
        assertEquals("NUMBER(37,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_2 + c36_2) FROM tn"));
        assertEquals("NUMBER(6,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c4_2 + c5_2) FROM tn"));
        assertEquals("NUMBER(6,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c4_2 + c4_3) FROM tn"));
        assertEquals("NUMBER(6,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c4_2 + c5_3) FROM tn"));
        assertEquals("NUMBER(7,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c4_2 + c6_3) FROM tn"));
        assertEquals("NUMBER(5,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c4_2 + c1_1) FROM tn"));
        assertEquals("NUMBER(5,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c4_2 + c2_2) FROM tn"));
        assertEquals("NUMBER(38,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c4_2 + c38_0) FROM tn"));
        assertEquals("NUMBER(38,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c4_2 + c38_2) FROM tn"));
        assertEquals("NUMBER(38,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c4_2 + c37_1) FROM tn"));
        assertEquals("NUMBER(37,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c4_2 + c36_2) FROM tn"));
        assertEquals("NUMBER(7,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c5_2 + c4_3) FROM tn"));
        assertEquals("NUMBER(7,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c5_2 + c5_3) FROM tn"));
        assertEquals("NUMBER(7,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c5_2 + c6_3) FROM tn"));
        assertEquals("NUMBER(6,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c5_2 + c1_1) FROM tn"));
        assertEquals("NUMBER(6,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c5_2 + c2_2) FROM tn"));
        assertEquals("NUMBER(38,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c5_2 + c38_0) FROM tn"));
        assertEquals("NUMBER(38,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c5_2 + c38_2) FROM tn"));
        assertEquals("NUMBER(38,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c5_2 + c37_1) FROM tn"));
        assertEquals("NUMBER(37,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c5_2 + c36_2) FROM tn"));
        assertEquals("NUMBER(6,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c4_3 + c5_3) FROM tn"));
        assertEquals("NUMBER(7,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c4_3 + c6_3) FROM tn"));
        assertEquals("NUMBER(6,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c4_3 + c1_1) FROM tn"));
        assertEquals("NUMBER(6,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c4_3 + c2_2) FROM tn"));
        assertEquals("NUMBER(38,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c4_3 + c38_0) FROM tn"));
        assertEquals("NUMBER(38,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c4_3 + c38_2) FROM tn"));
        assertEquals("NUMBER(38,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c4_3 + c37_1) FROM tn"));
        assertEquals("NUMBER(38,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c4_3 + c36_2) FROM tn"));
        assertEquals("NUMBER(7,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c5_3 + c6_3) FROM tn"));
        assertEquals("NUMBER(6,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c5_3 + c1_1) FROM tn"));
        assertEquals("NUMBER(6,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c5_3 + c2_2) FROM tn"));
        assertEquals("NUMBER(38,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c5_3 + c38_0) FROM tn"));
        assertEquals("NUMBER(38,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c5_3 + c38_2) FROM tn"));
        assertEquals("NUMBER(38,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c5_3 + c37_1) FROM tn"));
        assertEquals("NUMBER(38,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c5_3 + c36_2) FROM tn"));
        assertEquals("NUMBER(7,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c6_3 + c1_1) FROM tn"));
        assertEquals("NUMBER(7,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c6_3 + c2_2) FROM tn"));
        assertEquals("NUMBER(38,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c6_3 + c38_0) FROM tn"));
        assertEquals("NUMBER(38,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c6_3 + c38_2) FROM tn"));
        assertEquals("NUMBER(38,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c6_3 + c37_1) FROM tn"));
        assertEquals("NUMBER(38,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c6_3 + c36_2) FROM tn"));
        assertEquals("NUMBER(5,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c1_1 + c2_2) FROM tn"));
        assertEquals("NUMBER(38,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(c1_1 + c38_0) FROM tn"));
        assertEquals("NUMBER(38,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c1_1 + c38_2) FROM tn"));
        assertEquals("NUMBER(38,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(c1_1 + c37_1) FROM tn"));
        assertEquals("NUMBER(37,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c1_1 + c36_2) FROM tn"));
        assertEquals("NUMBER(38,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c2_2 + c38_0) FROM tn"));
        assertEquals("NUMBER(38,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c2_2 + c38_2) FROM tn"));
        assertEquals("NUMBER(38,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c2_2 + c37_1) FROM tn"));
        assertEquals("NUMBER(37,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c2_2 + c36_2) FROM tn"));
        assertEquals("NUMBER(38,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c38_0 + c38_2) FROM tn"));
        assertEquals("NUMBER(38,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(c38_0 + c37_1) FROM tn"));
        assertEquals("NUMBER(38,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c38_0 + c36_2) FROM tn"));
        assertEquals("NUMBER(38,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c38_2 + c37_1) FROM tn"));
        assertEquals("NUMBER(38,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c38_2 + c36_2) FROM tn"));
        assertEquals("NUMBER(38,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c37_1 + c36_2) FROM tn"));
    }

    /** Equal types keep the plain width, the integer part one wider than the operand's, up to 38 digits. */
    @Test
    public void aColumnPlusItselfKeepsThePlainWidth() {
        engine.execute("CREATE OR REPLACE TABLE tn (c1_0 NUMBER(1,0), c2_0 NUMBER(2,0), c3_0 NUMBER(3,0), c2_1 NUMBER(2,1), c3_1 NUMBER(3,1), c4_1 NUMBER(4,1), c3_2 NUMBER(3,2), c4_2 NUMBER(4,2), c5_2 NUMBER(5,2), c4_3 NUMBER(4,3), c5_3 NUMBER(5,3), c6_3 NUMBER(6,3), c1_1 NUMBER(1,1), c2_2 NUMBER(2,2), c38_0 NUMBER(38,0), c38_2 NUMBER(38,2), c37_1 NUMBER(37,1), c36_2 NUMBER(36,2))");
        engine.execute("INSERT INTO tn VALUES (0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)");
        assertEquals("NUMBER(2,0)[SB1]", rows("SELECT SYSTEM$TYPEOF(c1_0 + c1_0) FROM tn"));
        assertEquals("NUMBER(3,0)[SB1]", rows("SELECT SYSTEM$TYPEOF(c2_0 + c2_0) FROM tn"));
        assertEquals("NUMBER(4,0)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_0 + c3_0) FROM tn"));
        assertEquals("NUMBER(3,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(c2_1 + c2_1) FROM tn"));
        assertEquals("NUMBER(4,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_1 + c3_1) FROM tn"));
        assertEquals("NUMBER(5,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(c4_1 + c4_1) FROM tn"));
        assertEquals("NUMBER(4,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_2 + c3_2) FROM tn"));
        assertEquals("NUMBER(5,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c4_2 + c4_2) FROM tn"));
        assertEquals("NUMBER(6,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c5_2 + c5_2) FROM tn"));
        assertEquals("NUMBER(5,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c4_3 + c4_3) FROM tn"));
        assertEquals("NUMBER(6,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c5_3 + c5_3) FROM tn"));
        assertEquals("NUMBER(7,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c6_3 + c6_3) FROM tn"));
        assertEquals("NUMBER(2,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(c1_1 + c1_1) FROM tn"));
        assertEquals("NUMBER(3,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c2_2 + c2_2) FROM tn"));
        assertEquals("NUMBER(38,0)[SB1]", rows("SELECT SYSTEM$TYPEOF(c38_0 + c38_0) FROM tn"));
        assertEquals("NUMBER(38,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c38_2 + c38_2) FROM tn"));
        assertEquals("NUMBER(38,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(c37_1 + c37_1) FROM tn"));
        assertEquals("NUMBER(37,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c36_2 + c36_2) FROM tn"));
    }

    /** A difference is typed as the sum of the same operands. */
    @Test
    public void differencesFollowTheSameRule() {
        engine.execute("CREATE OR REPLACE TABLE tn (c1_0 NUMBER(1,0), c2_0 NUMBER(2,0), c3_0 NUMBER(3,0), c2_1 NUMBER(2,1), c3_1 NUMBER(3,1), c4_1 NUMBER(4,1), c3_2 NUMBER(3,2), c4_2 NUMBER(4,2), c5_2 NUMBER(5,2), c4_3 NUMBER(4,3), c5_3 NUMBER(5,3), c6_3 NUMBER(6,3), c1_1 NUMBER(1,1), c2_2 NUMBER(2,2), c38_0 NUMBER(38,0), c38_2 NUMBER(38,2), c37_1 NUMBER(37,1), c36_2 NUMBER(36,2))");
        engine.execute("INSERT INTO tn VALUES (0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)");
        assertEquals("NUMBER(4,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(c1_0 - c2_1) FROM tn"));
        assertEquals("NUMBER(4,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(c2_1 - c1_0) FROM tn"));
        assertEquals("NUMBER(5,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c1_0 - c3_2) FROM tn"));
        assertEquals("NUMBER(5,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(c3_1 - c2_2) FROM tn"));
        assertEquals("NUMBER(7,3)[SB1]", rows("SELECT SYSTEM$TYPEOF(c5_2 - c4_3) FROM tn"));
        assertEquals("NUMBER(38,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(c38_0 - c2_1) FROM tn"));
    }

    /** Literals, typed with their leading zero and without trailing zeros, sum by the same rule. */
    @Test
    public void literalSumsFollowTheSameRule() {
        assertEquals("NUMBER(4,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(2 + 3.5)"));
        assertEquals("NUMBER(4,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(3.5 + 2)"));
        assertEquals("NUMBER(4,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(2.0 + 3.5)"));
        assertEquals("NUMBER(4,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(1 + 0.5)"));
        assertEquals("NUMBER(3,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(0.5 + 0.5)"));
        assertEquals("NUMBER(5,2)[SB2]", rows("SELECT SYSTEM$TYPEOF(2 + 3.55)"));
        assertEquals("NUMBER(5,2)[SB2]", rows("SELECT SYSTEM$TYPEOF(1.5 + 2.25)"));
        assertEquals("NUMBER(4,1)[SB2]", rows("SELECT SYSTEM$TYPEOF(22 + 3.5)"));
        assertEquals("NUMBER(4,1)[SB2]", rows("SELECT SYSTEM$TYPEOF(2 + 33.5)"));
        assertEquals("NUMBER(4,1)[SB2]", rows("SELECT SYSTEM$TYPEOF(99 + 9.5)"));
        assertEquals("NUMBER(2,0)[SB1]", rows("SELECT SYSTEM$TYPEOF(2 + 3)"));
        assertEquals("NUMBER(6,3)[SB2]", rows("SELECT SYSTEM$TYPEOF(2 + 3.555)"));
        assertEquals("NUMBER(6,3)[SB2]", rows("SELECT SYSTEM$TYPEOF(2 + 0.555)"));
        assertEquals("NUMBER(4,1)[SB2]", rows("SELECT SYSTEM$TYPEOF(22 + 0.5)"));
        assertEquals("NUMBER(6,2)[SB2]", rows("SELECT SYSTEM$TYPEOF(222 + 3.55)"));
        assertEquals("NUMBER(5,2)[SB2]", rows("SELECT SYSTEM$TYPEOF(2.5 + 3.55)"));
        assertEquals("NUMBER(5,2)[SB2]", rows("SELECT SYSTEM$TYPEOF(25.5 + 3.55)"));
        assertEquals("NUMBER(5,2)[SB1]", rows("SELECT SYSTEM$TYPEOF(0.25 + 0.5)"));
        assertEquals("NUMBER(5,2)[SB2]", rows("SELECT SYSTEM$TYPEOF(12.34 + 5.6)"));
        assertEquals("NUMBER(1,0)[SB1]", rows("SELECT SYSTEM$TYPEOF(2)"));
        assertEquals("NUMBER(2,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(3.5)"));
        assertEquals("NUMBER(2,1)[SB1]", rows("SELECT SYSTEM$TYPEOF(0.5)"));
        assertEquals("NUMBER(2,0)[SB1]", rows("SELECT SYSTEM$TYPEOF(22)"));
        assertEquals("NUMBER(3,2)[SB2]", rows("SELECT SYSTEM$TYPEOF(3.55)"));
        assertEquals("NUMBER(4,3)[SB2]", rows("SELECT SYSTEM$TYPEOF(0.555)"));
        assertEquals("NUMBER(1,0)[SB1]", rows("SELECT SYSTEM$TYPEOF(2.0)"));
    }
}
