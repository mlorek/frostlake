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
 * GREATEST and LEAST order a mixed temporal set in its WIDEST member's family — a DATE beside a
 * TIMESTAMP is read at midnight and the answer is a timestamp, whichever side each was written on:
 *
 * <pre>
 *   d = 2020-06-01, dl = 2019-01-01, ts = 2020-01-01 10:00:00
 *
 *   GREATEST(d, ts)      2020-06-01 00:00:00.000     the DATE wins, read as a timestamp
 *   LEAST(d, ts)         2020-01-01 10:00:00.000     the timestamp wins
 *   GREATEST(dl, ts)     2020-01-01 10:00:00.000     and the other way round
 *   GREATEST(d, ts, dl)  2020-06-01 00:00:00.000     three arguments order the same way
 * </pre>
 *
 * <p>Frostlake refused all of these — "Invalid argument types for function 'GREATEST': (DATE,
 * TIMESTAMP_NTZ(9))" — because the value channel compared LocalDate with LocalDateTime and found no
 * common ordering. A TIME stays outside the order: live refuses it beside a timestamp, and so does
 * this, which is what keeps the widening honest.
 *
 * <p>Values are read through TO_VARCHAR so the ENGINE renders them: read raw, the two JDBC drivers
 * format timestamps differently and the comparison measures the driver rather than the answer.
 */
public class TemporalOrderingTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE tor (d DATE, dl DATE, ts TIMESTAMP_NTZ, tm TIME)");
        engine.execute("INSERT INTO tor SELECT '2020-06-01', '2019-01-01',"
            + " '2020-01-01 10:00:00', '10:00:00'");
    }

    private String rendered(final String expr) {
        final ResultSet rs = engine.executeQuery("SELECT TO_VARCHAR(" + expr + ") FROM tor");
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    private String refusal(final String expr) {
        try {
            rendered(expr);
            return "accepted";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', ' ');
        }
    }

    /** A DATE beside a TIMESTAMP is read at midnight, and the answer is a timestamp. */
    @Test
    public void aDateBesideATimestampIsReadAtMidnight() {
        assertEquals("2020-06-01 00:00:00.000", rendered("GREATEST(d, ts)"));
        assertEquals("2020-01-01 10:00:00.000", rendered("LEAST(d, ts)"));
    }

    /** Which side each was written on changes nothing. */
    @Test
    public void theWrittenOrderChangesNothing() {
        assertEquals("2020-06-01 00:00:00.000", rendered("GREATEST(ts, d)"));
        assertEquals("2020-01-01 10:00:00.000", rendered("LEAST(ts, d)"));
    }

    /** And the DATE does not always win — an earlier one loses to the timestamp. */
    @Test
    public void theEarlierDateLosesToTheTimestamp() {
        assertEquals("2020-01-01 10:00:00.000", rendered("GREATEST(dl, ts)"));
        assertEquals("2019-01-01 00:00:00.000", rendered("LEAST(dl, ts)"));
    }

    /** Three arguments order the same way. */
    @Test
    public void threeArgumentsOrderTheSameWay() {
        assertEquals("2020-06-01 00:00:00.000", rendered("GREATEST(d, ts, dl)"));
        assertEquals("2019-01-01 00:00:00.000", rendered("LEAST(d, ts, dl)"));
    }

    /** Two DATEs stay a DATE — the widening only happens where something wider is present. */
    @Test
    public void twoDatesStayADate() {
        assertEquals("2020-06-01", rendered("GREATEST(d, dl)"));
        assertEquals("2019-01-01", rendered("LEAST(d, dl)"));
        assertEquals("10:00:00", rendered("GREATEST(tm, tm)"));
    }

    /** A TIME is outside the order, and stays refused. */
    @Test
    public void aTimeStaysOutsideTheOrder() {
        assertTrue(refusal("GREATEST(tm, ts)").contains("incompatible types")
            || refusal("GREATEST(tm, ts)").contains("Can not convert parameter"),
            refusal("GREATEST(tm, ts)"));
    }
}
