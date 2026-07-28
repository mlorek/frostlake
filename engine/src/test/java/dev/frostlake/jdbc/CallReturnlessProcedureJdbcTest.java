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

import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * executeQuery("CALL p()") on a procedure whose body completes without RETURN must yield a one-row
 * result set with a NULL value (implicit NULL return) — not "Query did not return a result set".
 * This is the vendor deletion-proc shape: DML body, RETURN only in the EXCEPTION handler.
 */
public class CallReturnlessProcedureJdbcTest extends BaseJdbcTest {

    @Test
    public void testCallOfReturnlessProcYieldsNullRow() throws Exception {
        statement.execute("""
            CREATE PROCEDURE p()
            RETURNS INT
            AS
            BEGIN
                null;
            END;
            """);
        try (final ResultSet rs = statement.executeQuery("CALL p()")) {
            assertTrue(rs.next());
            assertNull(rs.getObject(1));
            assertFalse(rs.next());
        }
    }

    @Test
    public void testCallOfDmlProcYieldsNullRowAndApplies() throws Exception {
        statement.execute("CREATE TABLE marks (id VARCHAR)");
        statement.execute("""
            CREATE PROCEDURE mark(ids ARRAY)
            RETURNS STRING
            LANGUAGE SQL
            AS
            $$
            BEGIN
                MERGE INTO marks AS target
                    USING (SELECT CAST(VALUE AS TEXT) AS id FROM TABLE(FLATTEN(input => :ids))) AS source
                    ON target.id = source.id
                    WHEN NOT MATCHED THEN INSERT (id) VALUES (source.id);
            EXCEPTION
                WHEN OTHER THEN
                    RETURN 'error';
            END;
            $$
            """);
        try (final ResultSet rs = statement.executeQuery("CALL mark(ARRAY_CONSTRUCT('x', 'y'))")) {
            assertTrue(rs.next());
            assertNull(rs.getObject(1));
        }
        try (final ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM marks")) {
            assertTrue(rs.next());
            assertEquals(2, rs.getInt(1));
        }
    }
}
