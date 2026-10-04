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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ALTER TABLE ... RENAME COLUMN moves a column's name and nothing else, so it is no way around a policy. The rename
 * rebuilt the column from its name, type, nullability and default alone: its masking policy went, and the renamed
 * column read the protected value raw. Its projection policy, tags, collation and IDENTITY went the same way, and a
 * row access or aggregation policy kept naming the old column, so every read of the table failed.
 */
public class RenamedColumnKeepsItsPoliciesTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE rn_db");
        engine.execute("USE SCHEMA rn_db.PUBLIC");
        engine.execute("CREATE MASKING POLICY mp AS (v VARCHAR) RETURNS VARCHAR -> '***'");
        engine.execute("CREATE PROJECTION POLICY pp AS () RETURNS PROJECTION_CONSTRAINT"
            + " -> PROJECTION_CONSTRAINT(ALLOW => false)");
        engine.execute("CREATE ROW ACCESS POLICY rap AS (r VARCHAR) RETURNS BOOLEAN -> r <> 'hidden'");
        engine.execute("CREATE AGGREGATION POLICY ap AS () RETURNS AGGREGATION_CONSTRAINT"
            + " -> AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => 1)");
        engine.execute("CREATE TAG sens");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.append(out.length() > 0 ? "; " : "").append(row.getValues());
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

    private TableColumn column(final String table, final String name) {
        return engine.getCatalog().getDatabase("RN_DB").getSchema("PUBLIC").getTable(table).getColumn(name);
    }

    @Test
    public void aRenamedColumnStillReadsMasked() {
        engine.execute("CREATE TABLE t (a INT, s VARCHAR WITH MASKING POLICY mp)");
        engine.execute("INSERT INTO t VALUES (1, 'secret')");
        assertEquals("[***]", rows("SELECT s FROM t"));

        engine.execute("ALTER TABLE t RENAME COLUMN s TO s2");

        assertEquals("[***]", rows("SELECT s2 FROM t"));
        assertEquals("[1, ***]", rows("SELECT * FROM t"));
        assertEquals("RN_DB.PUBLIC.MP", column("T", "S2").getMaskingPolicyName());
    }

    @Test
    public void aRenamedColumnKeepsItsProjectionPolicy() {
        engine.execute("CREATE TABLE t (id INT, p VARCHAR)");
        engine.execute("INSERT INTO t VALUES (1, 'x')");
        engine.execute("ALTER TABLE t ALTER COLUMN p SET PROJECTION POLICY pp");

        engine.execute("ALTER TABLE t RENAME COLUMN p TO p2");

        assertTrue(refusal("SELECT p2 FROM t").contains("restricted by a Projection Policy"));
        assertEquals("[1]", rows("SELECT id FROM t"));
    }

    @Test
    public void aRowAccessPolicyFollowsTheColumnItIsAttachedOn() {
        engine.execute("CREATE TABLE t (id INT, r VARCHAR)");
        engine.execute("INSERT INTO t VALUES (1, 'shown'), (2, 'hidden')");
        engine.execute("ALTER TABLE t ADD ROW ACCESS POLICY rap ON (r)");

        engine.execute("ALTER TABLE t RENAME COLUMN r TO r2");

        assertEquals("[1, shown]", rows("SELECT * FROM t ORDER BY id"));
        assertEquals("[RAP, [ \"R2\" ]]", rows("SELECT policy_name, ref_arg_column_names FROM TABLE("
            + "INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 't', REF_ENTITY_DOMAIN => 'table'))"));
    }

    @Test
    public void anAggregationPolicysEntityKeyFollowsTheRename() {
        engine.execute("CREATE TABLE t (id INT, grp VARCHAR)");
        engine.execute("ALTER TABLE t SET AGGREGATION POLICY ap ENTITY KEY (grp)");

        engine.execute("ALTER TABLE t RENAME COLUMN grp TO grp2");

        assertEquals("[AP, [ \"GRP2\" ]]", rows("SELECT policy_name, ref_arg_column_names FROM TABLE("
            + "INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 't', REF_ENTITY_DOMAIN => 'table'))"));
    }

    @Test
    public void aRenamedColumnKeepsItsTagsCollationAndIdentity() {
        engine.execute("CREATE TABLE t (id INT IDENTITY(10, 5), p VARCHAR COLLATE 'en-ci', q VARCHAR)");
        engine.execute("ALTER TABLE t MODIFY COLUMN q SET TAG sens = 'yes'");
        engine.execute("INSERT INTO t (p, q) VALUES ('a', 'b')");

        engine.execute("ALTER TABLE t RENAME COLUMN id TO id2");
        engine.execute("ALTER TABLE t RENAME COLUMN p TO p2");
        engine.execute("ALTER TABLE t RENAME COLUMN q TO q2");
        engine.execute("INSERT INTO t (p2, q2) VALUES ('c', 'd')");

        assertEquals("[yes]", rows("SELECT SYSTEM$GET_TAG('sens', 't.q2', 'COLUMN')"));
        assertEquals("en-ci", column("T", "P2").getCollation());
        assertEquals("[10]; [15]", rows("SELECT id2 FROM t ORDER BY id2"));
        assertEquals(10L, column("T", "ID2").getIdentityStart());
        assertEquals(5L, column("T", "ID2").getIdentityIncrement());
    }

    @Test
    public void aQuotedColumnRenamedToAnotherQuotedNameStaysMasked() {
        engine.execute("CREATE TABLE t (\"mixed\" VARCHAR WITH MASKING POLICY mp, k INT)");
        engine.execute("INSERT INTO t VALUES ('secret', 1)");
        assertEquals("[***]", rows("SELECT \"mixed\" FROM t"));

        engine.execute("ALTER TABLE t RENAME COLUMN \"mixed\" TO \"Other\"");

        assertEquals("[***]", rows("SELECT \"Other\" FROM t"));
        assertEquals("[***, 1]", rows("SELECT * FROM t"));
        assertEquals("RN_DB.PUBLIC.MP", column("T", "Other").getMaskingPolicyName());
    }
}
