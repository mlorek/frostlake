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

package dev.frostlake.procedures;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A procedure body that completes without a RETURN implicitly returns NULL, and CALL still yields
 * its one-row result set (column named after the procedure) — never an empty result. The vendor
 * shape: {@code BEGIN <dml>; EXCEPTION WHEN OTHER THEN RETURN ...; END} returns NULL on success.
 */
public class ProcedureImplicitNullReturnTest extends BaseDatabaseTest {

    @Test
    public void testNullStatementBodyReturnsNullRow() {
        engine.execute("""
            CREATE PROCEDURE p()
            RETURNS INT
            AS
            BEGIN
                null;
            END;
            """);
        final ResultSet rs = engine.executeQuery("CALL p()");
        assertEquals(1, rs.getRows().size());
        assertEquals("P", rs.getColumns().get(0).getName());
        assertNull(rs.getRows().get(0).getValue(0));
    }

    @Test
    public void testDmlOnlyBodyReturnsNullRow() {
        engine.execute("CREATE TABLE del_marks (id VARCHAR)");
        engine.execute("""
            CREATE PROCEDURE mark_ids(ids ARRAY)
            RETURNS STRING
            LANGUAGE SQL
            AS
            $$
            BEGIN
                MERGE INTO del_marks AS target
                    USING (SELECT CAST(VALUE AS TEXT) AS id FROM TABLE(FLATTEN(input => :ids))) AS source
                    ON target.id = source.id
                    WHEN NOT MATCHED THEN INSERT (id) VALUES (source.id);
            EXCEPTION
                WHEN OTHER THEN
                    RETURN 'error';
            END;
            $$
            """);
        final ResultSet rs = engine.executeQuery("CALL mark_ids(ARRAY_CONSTRUCT('a', 'b'))");
        assertEquals(1, rs.getRows().size());
        assertNull(rs.getRows().get(0).getValue(0));
        assertEquals(2L, ((Number) engine.executeQuery("SELECT COUNT(*) FROM del_marks")
            .getRows().get(0).getValue(0)).longValue());
        // Re-run with an overlap: MERGE dedupes, still returns the NULL row.
        final ResultSet rs2 = engine.executeQuery("CALL mark_ids(ARRAY_CONSTRUCT('b', 'c'))");
        assertNull(rs2.getRows().get(0).getValue(0));
        assertEquals(3L, ((Number) engine.executeQuery("SELECT COUNT(*) FROM del_marks")
            .getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void testExplicitReturnStillWins() {
        engine.execute("""
            CREATE PROCEDURE p2()
            RETURNS INT
            AS
            BEGIN
                RETURN 7;
            END;
            """);
        final ResultSet rs = engine.executeQuery("CALL p2()");
        assertEquals(1, rs.getRows().size());
        assertEquals(7L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }
}
