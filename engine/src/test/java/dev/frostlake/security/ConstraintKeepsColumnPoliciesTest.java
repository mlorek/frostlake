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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A constraint clause never detaches a column's policies or tags. Re-flagging a column for an out-of-line PRIMARY
 * KEY or UNIQUE rebuilt it without its masking policy, its projection policy and its tags — so the protected
 * value came back raw — and retyping a column or adding a key to it dropped them the same way.
 */
public class ConstraintKeepsColumnPoliciesTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE ck_db");
        engine.execute("USE SCHEMA ck_db.PUBLIC");
        engine.execute("CREATE MASKING POLICY mp AS (v STRING) RETURNS STRING -> '***'");
        engine.execute("CREATE PROJECTION POLICY pp AS () RETURNS PROJECTION_CONSTRAINT"
            + " -> PROJECTION_CONSTRAINT(ALLOW => false)");
        engine.execute("CREATE TAG tg");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private String first(final String sql) {
        for (final Row row : engine.executeQuery(sql).getRows()) {
            return String.valueOf(row.getValue(0));
        }
        return "no row";
    }

    private String refusal(final String sql) {
        try {
            engine.executeQuery(sql);
            return "not refused";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage());
        }
    }

    /** The table's masked column reads masked, its projected column is refused, and its tagged column keeps the tag. */
    private void assertProtected(final String table) {
        assertEquals("***", first("SELECT s FROM " + table), table + ": masking");
        assertTrue(refusal("SELECT p FROM " + table).contains("Projection Policy"), table + ": projection");
        assertEquals("cv", first("SELECT SYSTEM$GET_TAG('tg', '" + table + ".g', 'COLUMN')"), table + ": tag");
    }

    @Test
    public void anOutOfLineKeyKeepsTheColumnsPoliciesAndTags() {
        final String columns = "id INT, s STRING WITH MASKING POLICY mp, p STRING WITH PROJECTION POLICY pp,"
            + " g STRING WITH TAG (tg = 'cv')";
        engine.execute("CREATE TABLE with_pk (" + columns + ", CONSTRAINT pk1 PRIMARY KEY (id))");
        engine.execute("CREATE TABLE with_uk (" + columns + ", CONSTRAINT uk1 UNIQUE (id))");
        engine.execute("INSERT INTO with_pk VALUES (1, 'secret', 'proj', 'g')");
        engine.execute("INSERT INTO with_uk VALUES (1, 'secret', 'proj', 'g')");
        assertProtected("with_pk");
        assertProtected("with_uk");
    }

    @Test
    public void retypingOrKeyingAColumnKeepsItsPoliciesAndTags() {
        engine.execute("CREATE TABLE t (id INT, s VARCHAR(10) WITH MASKING POLICY mp,"
            + " p VARCHAR(10) WITH PROJECTION POLICY pp, g STRING WITH TAG (tg = 'cv'))");
        engine.execute("INSERT INTO t VALUES (1, 'secret', 'proj', 'g')");
        engine.execute("ALTER TABLE t ALTER COLUMN p SET DATA TYPE VARCHAR(20)");
        engine.execute("ALTER TABLE t ALTER COLUMN s SET DATA TYPE VARCHAR(20)");
        engine.execute("ALTER TABLE t ADD PRIMARY KEY (g)");
        assertProtected("t");
    }

    @Test
    public void theNullExtendedSideOfAnOuterJoinKeepsTheProjectionPolicy() {
        engine.execute("CREATE TABLE t (id INT, p STRING WITH PROJECTION POLICY pp)");
        engine.execute("CREATE TABLE u (id INT)");
        assertTrue(refusal("SELECT t.p FROM u LEFT JOIN t ON u.id = t.id").contains("Projection Policy"));
    }
}
