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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests SHOW PRIMARY KEYS / SHOW UNIQUE KEYS — one row per key column, for a named table or every table in
 * the current schema. Snowflake's column shape (live-verified) is {@code created_on, database_name,
 * schema_name, table_name, column_name, key_sequence, constraint_name, rely, comment}; every assertion
 * here reads by column NAME so it stays honest about the shape rather than encoding positions.
 */
public class ShowKeysTest extends BaseDatabaseTest {

    private String value(final ResultSet rs, final Row row, final String column) {
        return String.valueOf(row.getValue(rs.getColumnIndex(column)));
    }

    @Test
    public void showPrimaryKeysForTable() {
        engine.execute("CREATE TABLE pk_t (id INTEGER PRIMARY KEY, name VARCHAR)");
        final ResultSet rs = engine.executeQuery("SHOW PRIMARY KEYS IN TABLE pk_t");
        assertEquals(1, rs.getRows().size());
        final Row row = rs.getRows().get(0);
        assertEquals("ID", value(rs, row, "column_name").toUpperCase());
        assertEquals("PK_T", value(rs, row, "table_name").toUpperCase());
        assertEquals("TEST_SCHEMA", value(rs, row, "schema_name").toUpperCase());
        assertEquals("TEST_DB", value(rs, row, "database_name").toUpperCase());
        assertEquals("1", value(rs, row, "key_sequence"));
        assertEquals("false", value(rs, row, "rely"), "a constraint without RELY reports false");
        assertTrue(value(rs, row, "constraint_name").startsWith("SYS_CONSTRAINT_"),
            "an unnamed PRIMARY KEY is auto-named SYS_CONSTRAINT_<uuid>");
    }

    @Test
    public void showPrimaryKeysNumbersCompositeKeyColumns() {
        engine.execute("CREATE TABLE ck_t (a INTEGER, b INTEGER, PRIMARY KEY (a, b))");
        final ResultSet rs = engine.executeQuery("SHOW PRIMARY KEYS IN TABLE ck_t");
        assertEquals(2, rs.getRows().size());
        // key_sequence is the column's position WITHIN the constraint, and both columns belong to the
        // same (single) PRIMARY KEY constraint, so they share one name.
        assertEquals("1", value(rs, rs.getRows().get(0), "key_sequence"));
        assertEquals("2", value(rs, rs.getRows().get(1), "key_sequence"));
        assertEquals(value(rs, rs.getRows().get(0), "constraint_name"),
                     value(rs, rs.getRows().get(1), "constraint_name"));
    }

    @Test
    public void showUniqueKeysForTable() {
        engine.execute("CREATE TABLE uk_t (id INTEGER, email VARCHAR UNIQUE)");
        final ResultSet rs = engine.executeQuery("SHOW UNIQUE KEYS IN TABLE uk_t");
        assertEquals(1, rs.getRows().size());
        final Row row = rs.getRows().get(0);
        assertEquals("EMAIL", value(rs, row, "column_name").toUpperCase());
        assertEquals("UK_T", value(rs, row, "table_name").toUpperCase());
        assertEquals("1", value(rs, row, "key_sequence"));
        assertTrue(value(rs, row, "constraint_name").startsWith("SYS_CONSTRAINT_"),
            "an unnamed UNIQUE constraint is auto-named SYS_CONSTRAINT_<uuid>");
    }

    @Test
    public void showPrimaryKeysKeepsOneRowPerColumnUnderTheExplicitName() {
        engine.execute("CREATE TABLE ck (a INTEGER, b INTEGER, c VARCHAR, "
            + "CONSTRAINT my_pk PRIMARY KEY (a, b), CONSTRAINT my_uq UNIQUE (c))");
        final ResultSet rs = engine.executeQuery("SHOW PRIMARY KEYS IN TABLE ck");
        // Unlike TABLE_CONSTRAINTS (one row per constraint), SHOW PRIMARY KEYS stays one row per COLUMN —
        // live-verified — with both rows sharing the one constraint's name.
        assertEquals(2, rs.getRows().size());
        assertEquals("A", value(rs, rs.getRows().get(0), "column_name").toUpperCase());
        assertEquals("B", value(rs, rs.getRows().get(1), "column_name").toUpperCase());
        assertEquals("1", value(rs, rs.getRows().get(0), "key_sequence"));
        assertEquals("2", value(rs, rs.getRows().get(1), "key_sequence"));
        assertEquals("MY_PK", value(rs, rs.getRows().get(0), "constraint_name"));
        assertEquals("MY_PK", value(rs, rs.getRows().get(1), "constraint_name"));
    }

    @Test
    public void showUniqueKeysReportsTheExplicitConstraintName() {
        engine.execute("CREATE TABLE uq_named (id INTEGER, email VARCHAR, CONSTRAINT my_uq UNIQUE (email))");
        final ResultSet rs = engine.executeQuery("SHOW UNIQUE KEYS IN TABLE uq_named");
        assertEquals(1, rs.getRows().size());
        assertEquals("EMAIL", value(rs, rs.getRows().get(0), "column_name").toUpperCase());
        assertEquals("MY_UQ", value(rs, rs.getRows().get(0), "constraint_name"));
    }

    @Test
    public void showUniqueKeysNumbersMultiColumnConstraintColumns() {
        engine.execute("CREATE TABLE uq_multi (a INTEGER, b INTEGER, CONSTRAINT uq_ab UNIQUE (a, b))");
        final ResultSet rs = engine.executeQuery("SHOW UNIQUE KEYS IN TABLE uq_multi");
        // A multi-column UNIQUE is ONE constraint, so its columns number 1..n and share its name.
        assertEquals(2, rs.getRows().size());
        assertEquals("1", value(rs, rs.getRows().get(0), "key_sequence"));
        assertEquals("2", value(rs, rs.getRows().get(1), "key_sequence"));
        assertEquals("UQ_AB", value(rs, rs.getRows().get(0), "constraint_name"));
        assertEquals("UQ_AB", value(rs, rs.getRows().get(1), "constraint_name"));
    }

    @Test
    public void showImportedKeysReportsExplicitConstraintNames() {
        engine.execute("CREATE TABLE ik_parent (id INTEGER, CONSTRAINT ik_pk PRIMARY KEY (id))");
        engine.execute("CREATE TABLE ik_child (a INTEGER, "
            + "CONSTRAINT ik_fk FOREIGN KEY (a) REFERENCES ik_parent(id))");
        final ResultSet rs = engine.executeQuery("SHOW IMPORTED KEYS IN TABLE ik_child");
        assertEquals(1, rs.getRows().size());
        assertEquals("IK_FK", value(rs, rs.getRows().get(0), "fk_name"));
        assertEquals("IK_PK", value(rs, rs.getRows().get(0), "pk_name"),
            "the referenced key reports the parent's explicitly named PRIMARY KEY");
    }

    @Test
    public void showPrimaryKeysAllTablesInSchema() {
        engine.execute("CREATE TABLE ka (x INTEGER PRIMARY KEY)");
        engine.execute("CREATE TABLE kb (y INTEGER PRIMARY KEY)");
        final ResultSet rs = engine.executeQuery("SHOW PRIMARY KEYS");
        int seen = 0;
        for (final Row row : rs.getRows()) {
            final String table = value(rs, row, "table_name").toUpperCase();
            if ("KA".equals(table) || "KB".equals(table)) {
                seen++;
            }
        }
        assertTrue(seen >= 2, "SHOW PRIMARY KEYS should list PK columns of all tables in the schema");
    }
}
