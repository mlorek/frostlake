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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        assertEquals(List.of("created_on", "name", "kind", "database_name", "schema_name"),
            columnNames(rs), "SHOW TERSE TABLES has these five columns in this order");
        assertTrue(rs.getRowCount() >= 1);
    }

    private static List<String> columnNames(final ResultSet rs) {
        final List<String> names = new ArrayList<>();
        for (final ResultSetColumn col : rs.getColumns()) {
            names.add(col.getName());
        }
        return names;
    }

    @Test
    public void startsWithIsACaseSensitivePrefixFilter() {
        engine.execute("CREATE TABLE aaa_one (id INTEGER)");
        engine.execute("CREATE TABLE bbb_two (id INTEGER)");
        assertEquals(1, engine.executeQuery("SHOW TABLES STARTS WITH 'AAA'").getRowCount());
        assertEquals(0, engine.executeQuery("SHOW TABLES STARTS WITH 'aaa'").getRowCount(),
            "STARTS WITH is case-sensitive; stored names are uppercase");
    }

    /**
     * LIMIT keeps the first n rows <em>by name</em>, and FROM resumes strictly after a name — which only
     * means anything because the listing is name-ordered to begin with (a
     * schema holding DT_A, T_A…T_D and a quoted "t_lower" lists them in exactly that order, so
     * {@code LIMIT 2} returns DT_A and T_A and {@code LIMIT 10 FROM 'T_A'} returns T_B, T_C, T_D,
     * t_lower). The unlimited listing is pinned alongside so a LIMIT that silently returned everything
     * could not pass.
     */
    @Test
    public void limitAndFromPaginateByName() {
        engine.execute("CREATE TABLE p3 (id INTEGER)");
        engine.execute("CREATE TABLE p1 (id INTEGER)");
        engine.execute("CREATE TABLE p2 (id INTEGER)");
        assertEquals(List.of("P1", "P2", "P3"), names(engine.executeQuery("SHOW TABLES")),
            "the unlimited listing is name-ordered regardless of creation order");
        assertEquals(List.of("P1", "P2"), names(engine.executeQuery("SHOW TABLES LIMIT 2")),
            "LIMIT keeps the FIRST n by name, not an arbitrary n");
        assertEquals(List.of("P2", "P3"), names(engine.executeQuery("SHOW TABLES LIMIT 10 FROM 'P1'")));
        assertEquals(List.of("P2"), names(engine.executeQuery("SHOW TABLES LIMIT 1 FROM 'P1'")));
        assertEquals(1, engine.executeQuery("SHOW TABLES LIKE 'P%' STARTS WITH 'P' LIMIT 1").getRowCount());
    }

    /**
     * Byte-wise ordering, not case-insensitive: a quoted lowercase name sorts after every uppercase one.
     * Live-verified — "t_lower" is the last row of SHOW TABLES, behind T_D.
     */
    @Test
    public void listingsAreOrderedByNameByteWise() {
        engine.execute("CREATE TABLE t_b (id INTEGER)");
        engine.execute("CREATE TABLE \"t_lower\" (id INTEGER)");
        engine.execute("CREATE TABLE t_a (id INTEGER)");
        assertEquals(List.of("T_A", "T_B", "t_lower"), names(engine.executeQuery("SHOW TABLES")));
    }

    /**
     * {@code LIMIT 0} is rejected, and everywhere — live-verified across twenty listings,
     * including the ones that go on to ignore a positive LIMIT.
     */
    @Test
    public void limitZeroIsRejected() {
        engine.execute("CREATE TABLE lz (id INTEGER)");
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SHOW TABLES LIMIT 0");
            }
        });
        assertTrue(error.getMessage().contains("must be greater than 0"), error.getMessage());
        assertEquals(1, engine.executeQuery("SHOW TABLES LIMIT 1").getRowCount());
    }

    private List<String> names(final ResultSet rs) {
        final List<String> result = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            result.add(String.valueOf(row.getValue(nameIndex(rs))));
        }
        return result;
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
        // SHOW COLUMNS IN VIEW needs the FULL search path — Snowflake rejects the unqualified form
        // (live-verified) even though the TABLE form accepts a bare name.
        final RuntimeException unqualified = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SHOW COLUMNS IN VIEW c_view");
            }
        });
        assertTrue(unqualified.getMessage().contains("Must specify the full search path"),
            unqualified.getMessage());
        final ResultSet viewCols =
            engine.executeQuery("SHOW COLUMNS IN VIEW test_db.test_schema.c_view");
        assertEquals(1, viewCols.getRowCount());
        // Read by NAME: SHOW COLUMNS leads with table_name/schema_name in Snowflake's shape.
        assertEquals("VC", String.valueOf(
            viewCols.getRows().get(0).getValue(viewCols.getColumnIndex("column_name"))).toUpperCase());
    }

    @Test
    public void acceptedScopesThatListNothing() {
        // The APPLICATION / APPLICATION PACKAGE / CLASS scopes are parsed and treated as empty here;
        // a real account rejects them outright unless those object types exist, so the leniency is
        // only meaningful embedded.
        Assumptions.assumeFalse(isLiveSnowflake(),
            "APPLICATION / CLASS scopes are Frostlake leniencies a real account rejects");
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
        engine.executeQuery("SHOW TERSE DATABASES HISTORY LIKE 'foo' STARTS WITH 'bla' LIMIT 5 FROM 'bob'");
        engine.executeQuery("SHOW USERS LIKE '_foo%' STARTS WITH 'bar' LIMIT 5 FROM 'baz'");
        engine.executeQuery("SHOW TERSE USERS");
    }

    @Test
    public void withPrivilegesSuffixIsAccepted() {
        // Live-verified: SHOW ... WITH PRIVILEGES <priv-list> is real Snowflake syntax on the
        // account-level listings — `SHOW WAREHOUSES WITH PRIVILEGES USAGE, MODIFY` and
        // `SHOW DATABASES WITH PRIVILEGES USAGE` both run. The privilege LIST is mandatory: a bare
        // `SHOW TABLES WITH PRIVILEGES` is a syntax error. Whether a given object type honours the
        // filter is SEMANTIC — a real account answers `SHOW TABLES ... WITH PRIVILEGES` with
        // "Unsupported feature", i.e. it parses — so the grammar accepts it and the listing is
        // simply not privilege-filtered here.
        assertNotNull(engine.executeQuery("SHOW WAREHOUSES WITH PRIVILEGES USAGE, MODIFY"));
        assertNotNull(engine.executeQuery("SHOW DATABASES WITH PRIVILEGES USAGE"));
        assertNotNull(engine.executeQuery("SHOW SCHEMAS WITH PRIVILEGES USAGE"));
        assertNotNull(engine.executeQuery("SHOW WAREHOUSES STARTS WITH 'F' WITH PRIVILEGES USAGE"));
    }
}
