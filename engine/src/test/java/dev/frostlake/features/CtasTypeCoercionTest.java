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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * CTAS stores values coerced to the created table's column types, exactly as a plain INSERT does, and
 * types a generic (computed/aggregate) result column from its values. Without this, a UNION of literal
 * branches left STRINGS in a TIMESTAMP column (rendering identically but comparing unequal under
 * EXCEPT), and {@code CREATE TABLE t AS SELECT MAX(ts) …} produced a VARCHAR column whose stringified
 * values never joined back to the source — the vendors' expected-table and latest-row idioms both
 * depend on these.
 */
public class CtasTypeCoercionTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE events (grp VARCHAR, happened_at TIMESTAMP_NTZ(9), amount NUMBER)");
        engine.execute(
            """
            INSERT INTO events VALUES
                ('g', '2024-10-17 18:06:28.071', 1),
                ('g', '2024-10-17 18:06:27.100', 2)
            """);
    }

    @Test
    public void unionOfLiteralsStoresRealTimestamps() {
        engine.execute(
            """
            CREATE TABLE expected_events AS
            SELECT grp, happened_at FROM events WHERE FALSE
            UNION ALL
            SELECT 'g', '2024-10-17 18:06:28.071'
            """);
        final ResultSet result = engine.executeQuery("SELECT happened_at FROM expected_events");
        assertInstanceOf(LocalDateTime.class, result.getRows().get(0).getValue(0));
        final ResultSet except = engine.executeQuery(
            """
            SELECT COUNT(*) AS n FROM (
                SELECT grp, happened_at FROM expected_events
                EXCEPT
                SELECT grp, happened_at FROM events
            )
            """);
        assertEquals(0L, ((Number) except.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void aggregateColumnGetsTimestampTypeAndJoinsBack() {
        engine.execute(
            "CREATE TABLE latest_events AS SELECT grp, MAX(happened_at) AS happened_at FROM events GROUP BY grp");
        final ResultSet typed = engine.executeQuery(
            """
            SELECT data_type FROM information_schema.columns
            WHERE table_name = 'LATEST_EVENTS' AND column_name = 'HAPPENED_AT'
            """);
        assertEquals("TIMESTAMP_NTZ", typed.getRows().get(0).getValue(0));
        final ResultSet joined = engine.executeQuery(
            """
            SELECT COUNT(*) AS n
            FROM events s
            INNER JOIN latest_events l ON l.grp = s.grp AND l.happened_at = s.happened_at
            """);
        assertEquals(1L, ((Number) joined.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void aggregateNumberColumnKeepsNumericValues() {
        engine.execute("CREATE TABLE totals AS SELECT grp, SUM(amount) AS total FROM events GROUP BY grp");
        final ResultSet result = engine.executeQuery("SELECT total FROM totals WHERE total = 3");
        assertEquals(1, result.getRows().size());
    }

    @Test
    public void realVarcharDataStaysVarchar() {
        engine.execute("CREATE TABLE names_copy AS SELECT UPPER(grp) AS shout FROM events");
        final ResultSet typed = engine.executeQuery(
            """
            SELECT data_type FROM information_schema.columns
            WHERE table_name = 'NAMES_COPY' AND column_name = 'SHOUT'
            """);
        assertEquals("VARCHAR", typed.getRows().get(0).getValue(0));
    }
}
