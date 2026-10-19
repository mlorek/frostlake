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

package dev.frostlake.jdbc;

import dev.frostlake.BaseJdbcTest;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * getDate, getTime and getTimestamp over the in-process driver, as the account's driver answers them
 * (live-verified, see {@link TemporalGetterSupport}): a pairing the account's driver does not convert is refused with
 * an SQLException, never with an unchecked java.time exception, and a NULL cell reads as null with wasNull set.
 */
public class TemporalGetterReadsTest extends BaseJdbcTest {

    private static final String NOT = "REFUSED 0A000 200038 Cannot convert value in the driver from type:";

    /** What every temporal column answers to each getter. */
    static final List<String> TEMPORAL = Arrays.asList(
        "D.getDate 2024-01-02",
        "D.getTime " + NOT + "91 to type:time, value=19724.",
        "D.getTimestamp 2024-01-02 00:00:00.0",
        "TM.getDate " + NOT + "92 to type:date, value=36672.345000000.",
        "TM.getTime 10:11:12 at 1970-01-01 10:11:12.345",
        "TM.getTimestamp 1970-01-01 10:11:12.345",
        "NTZ.getDate 2024-01-02",
        "NTZ.getTime 10:11:12",
        "NTZ.getTimestamp 2024-01-02 10:11:12.345",
        "LTZ.getDate 2024-01-02",
        "LTZ.getTime 10:11:12",
        "LTZ.getTimestamp 2024-01-02 10:11:12.345",
        "TZ.getDate 2024-01-02",
        "TZ.getTime 10:11:12",
        "TZ.getTimestamp 2024-01-02 10:11:12.345");

    /** What the other columns answer: a refusal quoting the cell as the account's wire carries it. */
    static final List<String> OTHER = Arrays.asList(
        "S.getDate " + NOT + "12 to type:date, value=2024-01-02.",
        "S.getTime " + NOT + "12 to type:time, value=2024-01-02.",
        "S.getTimestamp " + NOT + "12 to type:timestamp, value=2024-01-02.",
        "N.getDate " + NOT + "-5 to type:date, value=5.",
        "N.getTime " + NOT + "-5 to type:time, value=5.",
        "N.getTimestamp " + NOT + "-5 to type:timestamp, value=5.",
        "B.getDate " + NOT + "16 to type:date, value=1.",
        "B.getTime " + NOT + "16 to type:time, value=1.",
        "B.getTimestamp " + NOT + "16 to type:timestamp, value=1.",
        "F.getDate " + NOT + "8 to type:date, value=1.5.",
        "F.getTime " + NOT + "8 to type:time, value=1.5.",
        "F.getTimestamp " + NOT + "8 to type:timestamp, value=1.5.",
        "V.getDate " + NOT + "12 to type:date, value=\"2024-01-02\".",
        "V.getTime " + NOT + "12 to type:time, value=\"2024-01-02\".",
        "V.getTimestamp " + NOT + "12 to type:timestamp, value=\"2024-01-02\".",
        "EARLY.getDate 1969-12-31",
        "EARLY.getTime " + NOT + "91 to type:time, value=-1.",
        "EARLY.getTimestamp 1969-12-31 00:00:00.0",
        "T0.getDate " + NOT + "92 to type:date, value=36672.",
        "T0.getTime 10:11:12 at 1970-01-01 10:11:12.0",
        "T0.getTimestamp 1970-01-01 10:11:12.0",
        "T3.getDate " + NOT + "92 to type:date, value=36672.500.",
        "T3.getTime 10:11:12 at 1970-01-01 10:11:12.5",
        "T3.getTimestamp 1970-01-01 10:11:12.5",
        "MIDNIGHT.getDate " + NOT + "92 to type:date, value=0.000000000.",
        "MIDNIGHT.getTime 00:00:00 at 1970-01-01 00:00:00.0",
        "MIDNIGHT.getTimestamp 1970-01-01 00:00:00.0");

    /** A NULL cell reads as null, wasNull set, whatever its column's type. */
    static final List<String> NULLS = Arrays.asList(
        "DN.getDate null (null)",
        "DN.getTime null (null)",
        "DN.getTimestamp null (null)",
        "TN.getDate null (null)",
        "TN.getTime null (null)",
        "TN.getTimestamp null (null)",
        "SN.getDate null (null)",
        "SN.getTime null (null)",
        "SN.getTimestamp null (null)");

    /** The Calendar overloads and the typed getObject read and refuse as the plain getters do. */
    static final List<String> OVERLOADS = Arrays.asList(
        "TM.getDate(cal) " + NOT + "92 to type:date, value=36672.345000000.",
        "TM.getTimestamp(cal) 1970-01-01 10:11:12.345",
        "D.getTime(cal) " + NOT + "91 to type:time, value=19724.",
        "D.getObject(Time) " + NOT + "91 to type:time, value=19724.",
        "TM.getObject(Timestamp) 1970-01-01 10:11:12.345");

    /**
     * Checks every reading through one connection.
     *
     * @param connection the connection
     * @throws SQLException when a query fails
     */
    static void requireReadings(final Connection connection) throws SQLException {
        assertEquals(TEMPORAL, TemporalGetterSupport.readAll(connection, TemporalGetterSupport.TEMPORAL));
        assertEquals(OTHER, TemporalGetterSupport.readAll(connection, TemporalGetterSupport.OTHER));
        assertEquals(NULLS, TemporalGetterSupport.readAll(connection, TemporalGetterSupport.NULLS));
        assertEquals(OVERLOADS, TemporalGetterSupport.readOverloads(connection));
    }

    @Test
    public void eachGetterReadsTheTypesTheAccountsDriverReadsAndRefusesTheRest() throws SQLException {
        assertEquals(TEMPORAL, TemporalGetterSupport.readAll(connection, TemporalGetterSupport.TEMPORAL));
    }

    @Test
    public void aRefusalQuotesTheCellAsTheWireCarriesIt() throws SQLException {
        assertEquals(OTHER, TemporalGetterSupport.readAll(connection, TemporalGetterSupport.OTHER));
    }

    @Test
    public void aNullCellReadsAsNullWhateverItsType() throws SQLException {
        assertEquals(NULLS, TemporalGetterSupport.readAll(connection, TemporalGetterSupport.NULLS));
    }

    @Test
    public void theCalendarAndTypedReadsAnswerAsThePlainGetters() throws SQLException {
        assertEquals(OVERLOADS, TemporalGetterSupport.readOverloads(connection));
    }
}
