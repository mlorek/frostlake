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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A remainder's storage tag is the wider of its dividend's and its divisor's intervals over columns of
 * distinct values: MOD(n, 1000) over a NUMBER(10,2) holding 1.50 and 2.50 is [SB4], the divisor's, and
 * MOD(1000, n) [SB4], the dividend's. A negative side counts by its magnitude. Two single values fold to the
 * remainder itself, whether they are constants or columns holding one value, which the account's statistics
 * serve as constants. Every cell is live-verified.
 */
public class RemainderStorageTagTest extends BaseDatabaseTest {

    private static final String N_SB2 = "NUMBER(10,2)[SB2]";
    private static final String N_SB4 = "NUMBER(10,2)[SB4]";
    private static final String N_SB8 = "NUMBER(10,2)[SB8]";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE tn (n NUMBER(10,2), b NUMBER(18,0), i INTEGER)");
        engine.execute("INSERT INTO tn VALUES (1.50, 5, 3), (2.50, 6, 4)");
        engine.execute("CREATE TABLE tw (n NUMBER(10,2), b NUMBER(18,0))");
        engine.execute("INSERT INTO tw VALUES (1.50, 5), (99999999.99, 123456789012)");
        engine.execute("CREATE TABLE tg (n NUMBER(10,2))");
        engine.execute("INSERT INTO tg VALUES (-2.50), (1.50)");
        engine.execute("CREATE TABLE t1 (n NUMBER(10,2), b NUMBER(18,0))");
        engine.execute("INSERT INTO t1 VALUES (2.50, 5)");
    }

    private String firstRow(final String sql) {
        final Row row = engine.executeQuery(sql).getRows().get(0);
        final StringBuilder line = new StringBuilder();
        for (int c = 0; c < row.getValues().size(); c++) {
            if (c > 0) {
                line.append(", ");
            }
            line.append(row.getValue(c));
        }
        return line.toString();
    }

    @Test
    public void theWiderOfDividendAndDivisorOverNarrowColumns() {
        assertEquals(String.join(", ", N_SB2, N_SB2, N_SB2, N_SB4, N_SB4, N_SB4), firstRow("""
            SELECT SYSTEM$TYPEOF(n % 3), SYSTEM$TYPEOF(MOD(n, 7)), SYSTEM$TYPEOF(MOD(7, n)), SYSTEM$TYPEOF(MOD(n, 1000)),
                SYSTEM$TYPEOF(MOD(1000, n)), SYSTEM$TYPEOF(MOD(n, 100000)) FROM tn"""));
        assertEquals("NUMBER(20,2)[SB2], NUMBER(20,2)[SB2], NUMBER(18,0)[SB1], NUMBER(18,0)[SB1], " + N_SB2
            + ", NUMBER(18,0)[SB2]", firstRow("""
            SELECT SYSTEM$TYPEOF(MOD(n, b)), SYSTEM$TYPEOF(MOD(b, n)), SYSTEM$TYPEOF(MOD(b, 7)), SYSTEM$TYPEOF(MOD(7, b)),
                SYSTEM$TYPEOF(MOD(n, n)), SYSTEM$TYPEOF(MOD(b, 1000)) FROM tn"""));
        assertEquals("NUMBER(38,0)[SB1], NUMBER(38,0)[SB2], NUMBER(38,0)[SB2], NUMBER(38,0)[SB1], NUMBER(38,0)[SB1], "
            + "NUMBER(38,0)[SB1]", firstRow("""
            SELECT SYSTEM$TYPEOF(MOD(i, 2)), SYSTEM$TYPEOF(MOD(i, 1000)), SYSTEM$TYPEOF(MOD(1000, i)), SYSTEM$TYPEOF(MOD(i, b)),
                SYSTEM$TYPEOF(MOD(b, i)), SYSTEM$TYPEOF(i % 3) FROM tn"""));
    }

    @Test
    public void aWideSideWidensTheRemainder() {
        assertEquals(String.join(", ", N_SB8, N_SB8, N_SB8, N_SB8, N_SB8, N_SB8), firstRow("""
            SELECT SYSTEM$TYPEOF(n % 3), SYSTEM$TYPEOF(MOD(n, 7)), SYSTEM$TYPEOF(MOD(7, n)), SYSTEM$TYPEOF(MOD(n, 1000)),
                SYSTEM$TYPEOF(MOD(1000, n)), SYSTEM$TYPEOF(MOD(n, 100000)) FROM tw"""));
        assertEquals("NUMBER(20,2)[SB8], NUMBER(20,2)[SB8], NUMBER(18,0)[SB8], NUMBER(18,0)[SB8], " + N_SB8
            + ", NUMBER(18,0)[SB8]", firstRow("""
            SELECT SYSTEM$TYPEOF(MOD(n, b)), SYSTEM$TYPEOF(MOD(b, n)), SYSTEM$TYPEOF(MOD(b, 7)), SYSTEM$TYPEOF(MOD(7, b)),
                SYSTEM$TYPEOF(MOD(n, n)), SYSTEM$TYPEOF(MOD(b, 1000)) FROM tw"""));
    }

    @Test
    public void aNegativeSideCountsByItsMagnitude() {
        assertEquals(String.join(", ", N_SB2, N_SB2, N_SB2, N_SB4, N_SB4, N_SB2), firstRow("""
            SELECT SYSTEM$TYPEOF(n % 3), SYSTEM$TYPEOF(MOD(n, 7)), SYSTEM$TYPEOF(MOD(7, n)), SYSTEM$TYPEOF(MOD(n, 1000)),
                SYSTEM$TYPEOF(MOD(1000, n)), SYSTEM$TYPEOF(MOD(n, -7)) FROM tg"""));
    }

    @Test
    public void singleValuesFoldToTheRemainder() {
        assertEquals(N_SB2 + ", NUMBER(20,2)[SB1], NUMBER(20,2)[SB2], NUMBER(10,2)[SB1]", firstRow("""
            SELECT SYSTEM$TYPEOF(MOD(n, 7)), SYSTEM$TYPEOF(MOD(b, n)), SYSTEM$TYPEOF(MOD(n, b)), SYSTEM$TYPEOF(MOD('5', n))
            FROM t1"""));
        assertEquals("NUMBER(5,0)[SB1]", firstRow("SELECT SYSTEM$TYPEOF(MOD(12345, 2))"));
    }
}
