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
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SHOW suffix chain and scope variants (Snowflake surface): TERSE column projection,
 * STARTS WITH (case-sensitive prefix), LIMIT n [FROM 'name'] pagination, scoped
 * PRIMARY/UNIQUE/IMPORTED KEYS, SHOW COLUMNS variants (nameless, LIKE, IN VIEW), ICEBERG/
 * APPLICATION/CLASS scopes listing nothing, IN ACCOUNT listings, and the accepted-but-inert
 * HISTORY / WITH PRIVILEGES modifiers.
 */
public class ShowSuffixAndScopeTest extends BaseDatabaseTest {

    private int nameIndex(final ResultSet rs) {
        return rs.getColumnIndex("name");
    }

    @Test
    public void terseProjectsTheReducedColumnSet() {
        engine.execute("CREATE TABLE t_terse (id INTEGER)");
        final ResultSet rs = engine.executeQuery("SHOW TERSE TABLES");
        for (final ResultSetColumn col : rs.getColumns()) {
            final String name = col.getName();
            assertTrue(List.of("created_on", "name", "kind", "database_name", "schema_name").contains(name),
                "unexpected TERSE column: " + name);
        }
        assertTrue(rs.getRowCount() >= 1);
    }

    @Test
    public void startsWithIsACaseSensitivePrefixFilter() {
        engine.execute("CREATE TABLE aaa_one (id INTEGER)");
        engine.execute("CREATE TABLE bbb_two (id INTEGER)");
        assertEquals(1, engine.executeQuery("SHOW TABLES STARTS WITH 'AAA'").getRowCount());
        assertEquals(0, engine.executeQuery("SHOW TABLES STARTS WITH 'aaa'").getRowCount(),
            "STARTS WITH is case-sensitive; stored names are uppercase");
    }

    @Test
    public void limitAndFromPaginateByName() {
        engine.execute("CREATE TABLE p1 (id INTEGER)");
        engine.execute("CREATE TABLE p2 (id INTEGER)");
        engine.execute("CREATE TABLE p3 (id INTEGER)");
        assertEquals(2, engine.executeQuery("SHOW TABLES LIMIT 2").getRowCount());
        final ResultSet afterP1 = engine.executeQuery("SHOW TABLES LIMIT 10 FROM 'P1'");
        assertEquals(2, afterP1.getRowCount());
        for (final Row row : afterP1.getRows()) {
            assertTrue(String.valueOf(row.getValue(nameIndex(afterP1))).compareTo("P1") > 0);
        }
        assertEquals(1, engine.executeQuery("SHOW TABLES LIKE 'P%' STARTS WITH 'P' LIMIT 1").getRowCount());
    }

    @Test
    public void primaryKeysListForSchemaDatabaseAndAccountScopes() {
        engine.execute("CREATE TABLE pk_t (id INTEGER PRIMARY KEY, u VARCHAR UNIQUE)");
        assertEquals(1, engine.executeQuery("SHOW PRIMARY KEYS").getRowCount());
        assertEquals(1, engine.executeQuery("SHOW PRIMARY KEYS IN DATABASE").getRowCount());
        assertEquals(1, engine.executeQuery("SHOW PRIMARY KEYS IN DATABASE test_db").getRowCount());
        assertTrue(engine.executeQuery("SHOW PRIMARY KEYS IN ACCOUNT").getRowCount() >= 1);
        assertEquals(1, engine.executeQuery("SHOW UNIQUE KEYS IN TABLE pk_t").getRowCount());
        // A bare qualified name is a TABLE scope.
        assertEquals(1, engine.executeQuery("SHOW PRIMARY KEYS IN test_db.test_schema.pk_t").getRowCount());
        final ResultSet terse = engine.executeQuery("SHOW TERSE PRIMARY KEYS IN test_db.test_schema.pk_t");
        assertEquals(1, terse.getRowCount());
    }

    @Test
    public void importedKeysListForeignKeyColumns() {
        engine.execute("CREATE TABLE fk_parent (id INTEGER PRIMARY KEY)");
        engine.execute("CREATE TABLE fk_child (id INTEGER, pid INTEGER, "
            + "CONSTRAINT fk_child_parent FOREIGN KEY (pid) REFERENCES fk_parent (id))");
        final ResultSet rs = engine.executeQuery("SHOW IMPORTED KEYS IN TABLE fk_child");
        assertEquals(1, rs.getRowCount());
        final Row row = rs.getRows().get(0);
        assertEquals("FK_CHILD", String.valueOf(row.getValue(rs.getColumnIndex("fk_table_name"))));
        assertEquals("PID", String.valueOf(row.getValue(rs.getColumnIndex("fk_column_name"))).toUpperCase());
        assertTrue(String.valueOf(row.getValue(rs.getColumnIndex("pk_table_name"))).toUpperCase().contains("FK_PARENT"));
        // Scope-less and account-wide forms include the same key.
        assertTrue(engine.executeQuery("SHOW IMPORTED KEYS").getRowCount() >= 1);
        assertTrue(engine.executeQuery("SHOW IMPORTED KEYS IN ACCOUNT").getRowCount() >= 1);
    }

    @Test
    public void showColumnsVariants() {
        engine.execute("CREATE TABLE c_one (id INTEGER, label VARCHAR)");
        engine.execute("CREATE TABLE c_two (other INTEGER)");
        final ResultSet all = engine.executeQuery("SHOW COLUMNS");
        assertEquals(3, all.getRowCount(), "nameless SHOW COLUMNS lists every table of the schema");
        final int tableIdx = all.getColumnIndex("table_name");
        boolean sawTwo = false;
        for (final Row row : all.getRows()) {
            if ("C_TWO".equals(String.valueOf(row.getValue(tableIdx)))) {
                sawTwo = true;
            }
        }
        assertTrue(sawTwo, "rows should carry the owning table_name");

        assertEquals(1, engine.executeQuery("SHOW COLUMNS LIKE 'LAB%' IN TABLE c_one").getRowCount());

        engine.execute("CREATE VIEW c_view (vc) AS SELECT id FROM c_one");
        final ResultSet viewCols = engine.executeQuery("SHOW COLUMNS IN VIEW c_view");
        assertEquals(1, viewCols.getRowCount());
        assertEquals("VC", String.valueOf(viewCols.getRows().get(0).getValue(0)).toUpperCase());
    }

    @Test
    public void acceptedScopesThatListNothing() {
        assertEquals(0, engine.executeQuery("SHOW ICEBERG TABLES").getRowCount());
        assertEquals(0, engine.executeQuery("SHOW TERSE ICEBERG TABLES IN test_db.test_schema").getRowCount());
        assertEquals(0, engine.executeQuery("SHOW PROCEDURES LIKE 'foo' IN APPLICATION app").getRowCount());
        assertEquals(0, engine.executeQuery("SHOW PROCEDURES LIKE 'foo' IN APPLICATION PACKAGE pkg").getRowCount());
        assertEquals(0, engine.executeQuery("SHOW FUNCTIONS LIKE 'foo' IN CLASS bla").getRowCount());
    }

    @Test
    public void accountWideListings() {
        engine.execute("CREATE VIEW acc_v AS SELECT 1 AS c");
        engine.execute("CREATE SEQUENCE acc_seq");
        assertTrue(engine.executeQuery("SHOW VIEWS IN ACCOUNT").getRowCount() >= 1);
        assertTrue(engine.executeQuery("SHOW SEQUENCES IN ACCOUNT").getRowCount() >= 1);
        assertTrue(engine.executeQuery("SHOW SEQUENCES LIKE 'ACC%' IN SCHEMA").getRowCount() >= 1);
    }

    @Test
    public void inertModifiersAreAccepted() {
        engine.execute("CREATE TABLE hist_t (id INTEGER)");
        assertTrue(engine.executeQuery("SHOW DATABASES HISTORY").getRowCount() >= 1);
        assertTrue(engine.executeQuery("SHOW TABLES HISTORY IN test_db.test_schema").getRowCount() >= 1);
        engine.executeQuery("SHOW WAREHOUSES LIKE 'foo' WITH PRIVILEGES USAGE, MODIFY");
        engine.executeQuery("SHOW TERSE DATABASES HISTORY LIKE 'foo' STARTS WITH 'bla' LIMIT 5 FROM 'bob' "
            + "WITH PRIVILEGES USAGE, MODIFY");
        engine.executeQuery("SHOW USERS LIKE '_foo%' STARTS WITH 'bar' LIMIT 5 FROM 'baz'");
        engine.executeQuery("SHOW TERSE USERS");
    }
}
