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

package dev.frostlake.udf;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** ALTER FUNCTION / PROCEDURE … RENAME TO a qualified name, and the secure flag SHOW USER FUNCTIONS reports. */
public class RoutineRenameAndSecureTest extends BaseDatabaseTest {

    @Test
    public void renameToAQualifiedNameMovesTheFunction() {
        engine.execute("CREATE SCHEMA other");
        engine.execute("CREATE FUNCTION f1(x NUMBER) RETURNS NUMBER AS 'x + 1'");
        engine.execute("ALTER FUNCTION f1(NUMBER) RENAME TO test_db.other.f2");
        assertEquals(0, engine.executeQuery("SHOW USER FUNCTIONS LIKE 'F%' IN SCHEMA test_db.test_schema")
            .getRows().size());
        assertEquals(1, engine.executeQuery("SHOW USER FUNCTIONS LIKE 'F2' IN SCHEMA test_db.other").getRows().size());
        assertEquals(3L, ((Number) engine.executeQuery("SELECT test_db.other.f2(2)").getRows().get(0).getValue(0))
            .longValue());
    }

    @Test
    public void renameToAQualifiedNameMovesTheProcedure() {
        engine.execute("CREATE SCHEMA other");
        engine.execute("CREATE PROCEDURE p1() RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN ''ok''; END'");
        engine.execute("ALTER PROCEDURE p1() RENAME TO other.p2");
        assertEquals(1, engine.executeQuery("SHOW PROCEDURES LIKE 'P2' IN SCHEMA test_db.other").getRows().size());
        assertEquals(0, engine.executeQuery("SHOW PROCEDURES LIKE 'P1' IN SCHEMA test_db.test_schema")
            .getRows().size());
    }

    @Test
    public void aSecureFunctionIsListedAsSecure() {
        engine.execute("CREATE SECURE FUNCTION s1() RETURNS NUMBER AS '1'");
        engine.execute("CREATE FUNCTION s2() RETURNS NUMBER AS '2'");
        final ResultSet rs = engine.executeQuery("SHOW USER FUNCTIONS LIKE 'S_' IN SCHEMA test_db.test_schema");
        assertEquals("Y", rs.getRows().get(0).getValue(rs.getColumnIndex("is_secure")));
        assertEquals("N", rs.getRows().get(1).getValue(rs.getColumnIndex("is_secure")));
    }
}
