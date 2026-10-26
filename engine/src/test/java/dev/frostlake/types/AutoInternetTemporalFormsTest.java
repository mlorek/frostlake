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

package dev.frostlake.types;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * AUTO detection reads the Internet (RFC) forms its documentation lists beside the ISO ones (Snowflake's date and
 * time input documentation, "Supported formats for AUTO detection"): the timestamps
 * {@code DY, DD MON YYYY HH24:MI:SS[.FF] [TZHTZM]} and {@code DY, DD MON YYYY HH12:MI:SS[.FF] AM [TZHTZM]}, the
 * timestamp {@code DY MON DD HH24:MI:SS TZHTZM YYYY}, and the times {@code HH12:MI[:SS[.FF]] AM} and
 * {@code HH24:MI:SS.FFTZH:TZM}. A form outside the list is still not recognized.
 */
public class AutoInternetTemporalFormsTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE d");
        engine.execute("USE DATABASE d");
        engine.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    /** What a statement answers: its one row, or its refusal. */
    private String answer(final String sql) {
        final ResultSet rs;
        try {
            rs = engine.executeQuery(sql);
        } catch (final RuntimeException refused) {
            return "REFUSED " + refused.getMessage();
        }
        final StringBuilder out = new StringBuilder();
        for (final Row row : rs.getRows()) {
            for (int i = 0; i < row.getValues().size(); i++) {
                out.append(i == 0 ? "" : "|").append(row.getValue(i));
            }
        }
        return out.toString();
    }

    @Test
    public void internetTimestamps() {
        assertEquals("2000-12-21T16:01:07+02:00", answer("SELECT 'Thu, 21 Dec 2000 16:01:07 +0200'::TIMESTAMP_TZ"));
        assertEquals("2000-12-21T16:01:07.123456789+02:00",
            answer("SELECT 'Thu, 21 Dec 2000 16:01:07.123456789 +0200'::TIMESTAMP_TZ"));
        assertEquals("2000-12-21T16:01:07", answer("SELECT 'Thu, 21 Dec 2000 16:01:07'::TIMESTAMP_NTZ"));
        assertEquals("2000-12-21T16:01:07+02:00",
            answer("SELECT TO_TIMESTAMP_TZ('Thu, 21 Dec 2000 04:01:07 PM +0200', 'AUTO')"));
        assertEquals("2000-12-21T16:01:07.123456789",
            answer("SELECT 'Thu, 21 Dec 2000 04:01:07.123456789 PM'::TIMESTAMP_NTZ"));
        assertEquals("2000-12-21T00:01:07", answer("SELECT 'Thu, 21 Dec 2000 12:01:07 AM'::TIMESTAMP_NTZ"));
        assertEquals("2013-07-08T18:09:51Z", answer("SELECT 'Mon Jul 08 18:09:51 +0000 2013'::TIMESTAMP_TZ"));
        assertEquals("2000-12-21T14:01:07Z", answer("SELECT 'Thu, 21 Dec 2000 16:01:07 +0200'::TIMESTAMP_LTZ"));
    }

    @Test
    public void internetAndZonedTimes() {
        assertEquals("04:01|19:57:01.123456789|04:01:07|00:30|12:30",
            answer("SELECT TO_TIME('04:01 AM'), TO_TIME('07:57:01.123456789 PM'), TO_TIME('04:01:07 AM'), "
                + "TO_TIME('12:30 AM'), TO_TIME('12:30 PM')"));
        assertEquals("20:57:01.123456789", answer("SELECT TO_TIME('20:57:01.123456789+07:00')"));
    }

    @Test
    public void formsOutsideTheListAreNotRecognized() {
        assertEquals("null|null|null", answer("SELECT TRY_TO_TIME('13:01 PM'), TRY_TO_TIME('00:01 AM'), "
            + "TRY_TO_TIME('20:57 +07:00')"));
        assertEquals("REFUSED Timestamp 'Thu, 21 Dec 2000 16:01:07 PM' is not recognized",
            answer("SELECT 'Thu, 21 Dec 2000 16:01:07 PM'::TIMESTAMP_NTZ"));
        assertEquals("REFUSED Timestamp 'Thu 21 Dec 2000 16:01:07' is not recognized",
            answer("SELECT 'Thu 21 Dec 2000 16:01:07'::TIMESTAMP_NTZ"));
        assertEquals("REFUSED Timestamp 'Mon Jul 08 18:09:51 2013' is not recognized",
            answer("SELECT 'Mon Jul 08 18:09:51 2013'::TIMESTAMP_TZ"));
    }
}
