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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a database's INFORMATION_SCHEMA contributes to the listings that walk a whole container.
 *
 * <p>It is a schema full of VIEWS — 63 of them, no tables — so a container-scoped listing counts them
 * too: SHOW VIEWS IN DATABASE over a database holding one user view answers 64, and SHOW OBJECTS 65,
 * while SHOW TABLES answers 1 because none of the 63 is a table. Those totals are asserted here
 * exactly, on both sides.
 *
 * <p>They are owned by NOBODY, which is the cell most easily invented: a created view names the role
 * that made it, and these name no one at all — the same absence the INFORMATION_SCHEMA schema itself
 * reports, and the schema also carries a fixed comment that a created schema does not.
 *
 * <p>The one listing that does NOT tally is SHOW COLUMNS: Frostlake models the views as objects but not
 * their columns, so a container-scoped SHOW COLUMNS counts user objects only. That is recorded in
 * docs/scope.md, which is why the assertion here is "at least mine" rather than a total.
 */
public class InformationSchemaListingTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE is_t (a NUMBER, b VARCHAR(10))");
        engine.execute("CREATE VIEW is_v AS SELECT a FROM is_t");
    }

    private int rows(final String sql) {
        return engine.executeQuery(sql).getRowCount();
    }

    /** The schema holds 63 views and no tables. */
    @Test
    public void informationSchemaHoldsSixtyThreeViewsAndNoTables() {
        assertEquals(63, rows("SHOW VIEWS IN SCHEMA test_db.INFORMATION_SCHEMA"));
        assertEquals(0, rows("SHOW TABLES IN SCHEMA test_db.INFORMATION_SCHEMA"));
        assertEquals(63, rows("SHOW OBJECTS IN SCHEMA test_db.INFORMATION_SCHEMA"));
    }

    /** So a database-wide listing counts them beside the user's own objects. */
    @Test
    public void aDatabaseWideListingCountsThemToo() {
        assertEquals(64, rows("SHOW VIEWS IN DATABASE test_db"), "63 system views plus is_v");
        assertEquals(1, rows("SHOW TABLES IN DATABASE test_db"), "no system TABLES exist to count");
        assertEquals(65, rows("SHOW OBJECTS IN DATABASE test_db"), "63 system views plus is_t and is_v");
    }

    /** Three schemas, and INFORMATION_SCHEMA is one of them. */
    @Test
    public void theSchemaItselfIsListed() {
        final ResultSet rs = engine.executeQuery("SHOW SCHEMAS IN DATABASE test_db");
        int found = 0;
        while (rs.next()) {
            if ("INFORMATION_SCHEMA".equals(String.valueOf(rs.getValue("name")))) {
                found++;
                assertEquals("", String.valueOf(rs.getValue("owner")), "owned by nobody");
                assertEquals("Views describing the contents of schemas in this database",
                    String.valueOf(rs.getValue("comment")));
            }
        }
        assertEquals(1, found, "INFORMATION_SCHEMA should be listed exactly once");
        assertEquals(3, rs.getRowCount(), "INFORMATION_SCHEMA, PUBLIC and the test schema");
    }

    /** And every view in it is owned by nobody, where a created view names its role. */
    @Test
    public void everySystemViewIsOwnedByNobody() {
        final ResultSet rs = engine.executeQuery("SHOW OBJECTS IN SCHEMA test_db.INFORMATION_SCHEMA");
        int checked = 0;
        while (rs.next()) {
            assertEquals("VIEW", String.valueOf(rs.getValue("kind")));
            assertEquals("", String.valueOf(rs.getValue("owner")),
                rs.getValue("name") + " should have no owner");
            // With no owner there is no role type either — the same pairing the schema row makes.
            assertEquals("", String.valueOf(rs.getValue("owner_role_type")),
                rs.getValue("name") + " should have no owner role type");
            checked++;
        }
        assertEquals(63, checked);
        final ResultSet mine = engine.executeQuery("SHOW OBJECTS IN SCHEMA test_db.test_schema");
        mine.next();
        assertTrue(String.valueOf(mine.getValue("owner")).length() > 0,
            "a created object still names its owner");
    }

    /**
     * SHOW COLUMNS is the listing that does not tally, so it is asserted as a floor: the user table's
     * two columns and the view's one are all Frostlake contributes, and live adds the system views'
     * on top. Stated here so the gap is visible in the suite rather than only in docs/scope.md.
     */
    @Test
    public void showColumnsCountsAtLeastTheUserObjects() {
        assertTrue(rows("SHOW COLUMNS IN DATABASE test_db") >= 3,
            "the user table's two columns and the view's one");
    }
}
