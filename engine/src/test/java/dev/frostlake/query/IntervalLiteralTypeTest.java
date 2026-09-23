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

/**
 * A unit-suffixed interval literal is a typed value of its own: {@code INTERVAL '1' DAY} is an
 * {@code INTERVAL DAY(9)}, {@code INTERVAL '1' SECOND} an {@code INTERVAL SECOND(9,9)}, {@code INTERVAL '1' YEAR}
 * an {@code INTERVAL YEAR(9)} — each with the storage tag the account prints for it — and a projection of one
 * declares that type, where it used to declare the text placeholder and SYSTEM$TYPEOF answered NULL[LOB].
 */
public class IntervalLiteralTypeTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE ilt (ts TIMESTAMP_NTZ, ts2 TIMESTAMP_NTZ)");
        engine.execute("INSERT INTO ilt VALUES ('2020-01-02 01:00:00', '2020-01-01 00:00:00')");
    }

    private String typeOf(final String expr) {
        final ResultSet rs = engine.executeQuery("SELECT SYSTEM$TYPEOF(" + expr + ") FROM ilt");
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    private String declared(final String expr) {
        return engine.executeQuery("SELECT " + expr + " FROM ilt").getColumns().get(0).getDataType().getName();
    }

    /** SYSTEM$TYPEOF names each literal's own type and storage tag. */
    @Test
    public void eachLiteralNamesItsOwnType() {
        assertEquals("INTERVAL DAY(9)[SB16]", typeOf("INTERVAL '1' DAY"));
        assertEquals("INTERVAL HOUR(9)[SB16]", typeOf("INTERVAL '1' HOUR"));
        assertEquals("INTERVAL MINUTE(9)[SB16]", typeOf("INTERVAL '1' MINUTE"));
        assertEquals("INTERVAL SECOND(9,9)[SB8]", typeOf("INTERVAL '1' SECOND"));
        assertEquals("INTERVAL YEAR(9)[SB8]", typeOf("INTERVAL '1' YEAR"));
        assertEquals("INTERVAL MONTH(9)[SB4]", typeOf("INTERVAL '1' MONTH"));
        assertEquals("INTERVAL DAY(9)[SB16]", typeOf("INTERVAL '-1' DAY"));
        assertEquals("INTERVAL DAY(9) TO SECOND(9)[SB16]", typeOf("ts - ts2"));
    }

    /** Two intervals of one family fold to the leading one's type; the two families never meet. */
    @Test
    public void sameFamilyArmsFoldToTheLeadingOne() {
        assertEquals("INTERVAL HOUR(9)", engine.executeQuery(
            "SELECT INTERVAL '2' HOUR UNION ALL SELECT INTERVAL '1' DAY").getColumns().get(0).getDataType().getName());
        assertEquals("INTERVAL DAY(9)", engine.executeQuery(
            "SELECT INTERVAL '1' DAY UNION ALL SELECT INTERVAL '2' HOUR").getColumns().get(0).getDataType().getName());
        assertEquals("INTERVAL YEAR(9)", engine.executeQuery(
            "SELECT INTERVAL '1' YEAR UNION ALL SELECT INTERVAL '2' MONTH").getColumns().get(0).getDataType().getName());
        assertEquals("INTERVAL HOUR(9)[SB16]", typeOf("COALESCE(INTERVAL '2' HOUR, INTERVAL '1' DAY)"));
        assertEquals("SQL compilation error:\ninconsistent data type for result columns for set operator input "
            + "branches, expected INTERVAL YEAR(9), got INTERVAL DAY(9)",
            refusal("SELECT INTERVAL '1' DAY UNION ALL SELECT INTERVAL '1' YEAR"));
        assertEquals("SQL compilation error:\ninconsistent data type for result columns for set operator input "
            + "branches, expected NUMBER(1,0), got INTERVAL DAY(9)",
            refusal("SELECT INTERVAL '1' DAY UNION ALL SELECT 1"));
    }

    private String refusal(final String sql) {
        try {
            engine.executeQuery(sql);
            return "answered";
        } catch (final RuntimeException refused) {
            return refused.getMessage();
        }
    }

    /** A projection of one declares that type, through a conditional as well. */
    @Test
    public void aProjectionDeclaresTheLiteralsType() {
        assertEquals("INTERVAL DAY(9)", declared("INTERVAL '1' DAY"));
        assertEquals("INTERVAL HOUR(9)", declared("INTERVAL '2' HOUR"));
        assertEquals("INTERVAL SECOND(9,9)", declared("INTERVAL '1' SECOND"));
        assertEquals("INTERVAL YEAR(9)", declared("INTERVAL '1' YEAR"));
        assertEquals("INTERVAL MONTH(9)", declared("INTERVAL '14' MONTH"));
        assertEquals("INTERVAL DAY(9) TO SECOND(9)", declared("ts - ts2"));
        assertEquals("INTERVAL DAY(9)", declared("COALESCE(INTERVAL '1' DAY, INTERVAL '2' DAY)"));
        assertEquals("INTERVAL DAY(9)", declared("CASE WHEN TRUE THEN INTERVAL '1' DAY END"));
    }
}
