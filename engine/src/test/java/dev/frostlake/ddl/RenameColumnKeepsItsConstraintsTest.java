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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.metastore.model.ForeignKeyConstraint;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Everything that names a column follows ALTER TABLE ... RENAME COLUMN: the primary key in its order, UNIQUE and
 * FOREIGN KEY constraints, a foreign key in another table that references the column, CHECK constraints, the
 * clustering key, search optimization and data metric functions. The rename moved the primary key's column to the
 * end of the key and left every other reference naming a column the table no longer had.
 */
public class RenameColumnKeepsItsConstraintsTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE rk_db");
        engine.execute("USE SCHEMA rk_db.PUBLIC");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private String column(final String sql, final String name) {
        final ResultSet listed = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        while (listed.next()) {
            out.append(out.length() > 0 ? "," : "").append(listed.getValue(name));
        }
        return out.toString();
    }

    private String refusal(final String sql) {
        try {
            engine.execute(sql);
            return "not refused";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage());
        }
    }

    private Table table(final String schema, final String name) {
        return engine.getCatalog().getDatabase("RK_DB").getSchema(schema).getTable(name);
    }

    @Test
    public void aCompositePrimaryKeyKeepsItsColumnOrder() {
        engine.execute("CREATE TABLE t (k1 INT, k2 INT, CONSTRAINT pk_t PRIMARY KEY (k1, k2))");

        engine.execute("ALTER TABLE t RENAME COLUMN k1 TO first_key");

        assertEquals("FIRST_KEY,K2", column("SHOW PRIMARY KEYS IN TABLE t", "column_name"));
        assertEquals("PK_T,PK_T", column("SHOW PRIMARY KEYS IN TABLE t", "constraint_name"));
        assertEquals(Arrays.asList("FIRST_KEY", "K2"), table("PUBLIC", "T").getPrimaryKeys());
    }

    @Test
    public void uniqueAndForeignKeysFollowTheRenameInBothTables() {
        engine.execute("CREATE TABLE parent (id INT PRIMARY KEY, code VARCHAR, CONSTRAINT uq_code UNIQUE (code))");
        engine.execute("CREATE TABLE child (cid INT, pid INT, CONSTRAINT fk_parent FOREIGN KEY (pid)"
            + " REFERENCES parent (id))");
        engine.execute("CREATE SCHEMA other");
        engine.execute("CREATE TABLE other.inline_child (x INT REFERENCES rk_db.PUBLIC.parent (id))");
        engine.execute("USE SCHEMA rk_db.PUBLIC");

        engine.execute("ALTER TABLE parent RENAME COLUMN id TO parent_id");
        engine.execute("ALTER TABLE parent RENAME COLUMN code TO parent_code");
        engine.execute("ALTER TABLE child RENAME COLUMN pid TO parent_ref");

        assertEquals("PARENT_CODE", column("SHOW UNIQUE KEYS IN TABLE parent", "column_name"));
        assertEquals("UQ_CODE", column("SHOW UNIQUE KEYS IN TABLE parent", "constraint_name"));
        final ForeignKeyConstraint childKey = table("PUBLIC", "CHILD").getForeignKeys().get(0);
        assertEquals(Arrays.asList("PARENT_REF"), childKey.getColumnNames());
        assertEquals(Arrays.asList("PARENT_ID"), childKey.getReferencedColumns());
        assertEquals("PARENT_ID", column("SHOW IMPORTED KEYS IN TABLE child", "pk_column_name"));
        assertEquals("PARENT_REF", column("SHOW IMPORTED KEYS IN TABLE child", "fk_column_name"));
        assertEquals("PARENT_ID", table("OTHER", "INLINE_CHILD").getColumn("X").getReferencedColumn());
    }

    @Test
    public void aSelfReferencingForeignKeyFollowsTheRename() {
        engine.execute("CREATE TABLE node (id INT PRIMARY KEY, parent INT, CONSTRAINT fk_self FOREIGN KEY (parent)"
            + " REFERENCES node (id))");

        engine.execute("ALTER TABLE node RENAME COLUMN id TO node_id");

        final ForeignKeyConstraint self = table("PUBLIC", "NODE").getForeignKeys().get(0);
        assertEquals(Arrays.asList("PARENT"), self.getColumnNames());
        assertEquals(Arrays.asList("NODE_ID"), self.getReferencedColumns());
        assertEquals("FK_SELF", self.getConstraintName());
    }

    @Test
    public void aCheckConstraintIsStillEnforcedUnderTheNewName() {
        engine.execute("CREATE TABLE t (k INT, CONSTRAINT ck CHECK (k > 0))");

        engine.execute("ALTER TABLE t RENAME COLUMN k TO amount");

        assertTrue(refusal("INSERT INTO t VALUES (-1)").contains("CHECK constraint CK, which requires that AMOUNT > 0"));
        engine.execute("INSERT INTO t VALUES (1)");
        assertEquals("AMOUNT > 0", column("DESCRIBE TABLE t", "check"));
    }

    @Test
    public void theClusteringKeySearchOptimizationAndDataMetricsFollowTheRename() {
        engine.execute("CREATE TABLE t (k INT, q VARCHAR, v INT) CLUSTER BY (k, UPPER(q))");
        engine.execute("ALTER TABLE t ADD SEARCH OPTIMIZATION ON EQUALITY(q)");
        engine.execute("ALTER TABLE t ADD DATA METRIC FUNCTION SNOWFLAKE.CORE.NULL_COUNT ON (v)");

        engine.execute("ALTER TABLE t RENAME COLUMN k TO kk");
        engine.execute("ALTER TABLE t RENAME COLUMN q TO qq");
        engine.execute("ALTER TABLE t RENAME COLUMN v TO vv");

        assertEquals("LINEAR(KK, UPPER(QQ))", column("SHOW TABLES LIKE 'T'", "cluster_by"));
        assertEquals("QQ", column("DESCRIBE SEARCH OPTIMIZATION ON t", "target"));
        assertEquals("[{\"domain\":\"COLUMN\",\"name\":\"VV\"}]", column("SELECT * FROM TABLE("
            + "INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't', REF_ENTITY_DOMAIN =>"
            + " 'table'))", "REF_ARGUMENTS"));
        engine.execute("ALTER TABLE t DROP DATA METRIC FUNCTION SNOWFLAKE.CORE.NULL_COUNT ON (vv)");
    }

    @Test
    public void aNameInsideALiteralOrAFunctionIsNotTheColumn() {
        engine.execute("CREATE TABLE t (k INT, v VARCHAR, CONSTRAINT ck CHECK (v <> 'K' AND k >= 0))");

        engine.execute("ALTER TABLE t RENAME COLUMN k TO amount");

        assertEquals("v <> 'K' AND AMOUNT >= 0", table("PUBLIC", "T").getCheckConstraints().get(0).getExpression());
        assertEquals("not refused", refusal("INSERT INTO t VALUES (1, 'K2')"));
    }

    @Test
    public void aCloneAsOfAPointBeforeTheRenameTakesTheOldName() {
        engine.execute("CREATE TABLE t (k INT, s VARCHAR)");
        engine.execute("INSERT INTO t VALUES (1, 'a')");
        engine.execute("SET q1 = LAST_QUERY_ID()");

        engine.execute("ALTER TABLE t RENAME COLUMN s TO s2");
        engine.execute("CREATE TABLE t_then CLONE t AT(STATEMENT => $q1)");

        assertEquals("K,S", column("DESCRIBE TABLE t_then", "name"));
        assertEquals("K,S2", column("DESCRIBE TABLE t", "name"));
        for (final Row row : engine.executeQuery("SELECT s FROM t_then").getRows()) {
            assertEquals("a", row.getValue(0));
        }
    }
}
