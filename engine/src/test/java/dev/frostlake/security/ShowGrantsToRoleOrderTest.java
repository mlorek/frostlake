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

package dev.frostlake.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * SHOW GRANTS TO ROLE orders its rows by the kind of object granted on, then by the object's name, then by the
 * privilege — never by when the grants were made.
 */
public class ShowGrantsToRoleOrderTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("USE ROLE ACCOUNTADMIN");
        // Privileges come from the primary role alone, and the refusals name only it.
        engine.execute("USE SECONDARY ROLES NONE");
        engine.execute("CREATE ROLE IF NOT EXISTS ord_r");
    }

    @Override
    protected void teardownTest() {
        try {
            engine.execute("DROP ROLE IF EXISTS ord_r");
        } catch (final RuntimeException ignored) {
            // cleanup only
        }
    }

    @Test
    public void rowsGoByKindThenNameThenPrivilege() {
        engine.execute("CREATE SCHEMA ord_b");
        engine.execute("CREATE SCHEMA ord_a");
        engine.execute("CREATE VIEW ord_a.vz AS SELECT 1 AS a");
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("GRANT MODIFY ON SCHEMA ord_b TO ROLE ord_r");
        engine.execute("GRANT SELECT ON VIEW ord_a.vz TO ROLE ord_r");
        engine.execute("GRANT CREATE TABLE ON SCHEMA ord_a TO ROLE ord_r");
        engine.execute("GRANT USAGE ON DATABASE test_db TO ROLE ord_r");
        engine.execute("GRANT CREATE ALERT ON SCHEMA ord_b TO ROLE ord_r");
        engine.execute("GRANT USAGE ON SCHEMA ord_a TO ROLE ord_r");
        engine.execute("GRANT ADD SEARCH OPTIMIZATION ON SCHEMA ord_b TO ROLE ord_r");
        engine.execute("GRANT CREATE VIEW ON SCHEMA ord_a TO ROLE ord_r");
        final ResultSet shown = engine.executeQuery("SHOW GRANTS TO ROLE ord_r");
        final List<String> order = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        for (final Row row : shown.getRows()) {
            order.add(row.getValue(shown.getColumnIndex("granted_on")) + " "
                + row.getValue(shown.getColumnIndex("privilege")));
            names.add(String.valueOf(row.getValue(shown.getColumnIndex("name"))));
        }
        assertEquals(List.of("DATABASE USAGE", "SCHEMA CREATE TABLE", "SCHEMA CREATE VIEW", "SCHEMA USAGE",
            "SCHEMA ADD SEARCH OPTIMIZATION", "SCHEMA CREATE ALERT", "SCHEMA MODIFY", "VIEW SELECT"), order);
        for (int i = 1; i <= 3; i++) {
            assertTrue(names.get(i).endsWith("ORD_A"), names.toString());
            assertTrue(names.get(i + 3).endsWith("ORD_B"), names.toString());
        }
    }
}
