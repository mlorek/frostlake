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
import dev.frostlake.metastore.model.Table;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A policy attachment names its policy wherever the session is, and keeps naming it through renames and moves.
 * The engine recorded a row access attachment as written and resolved every attachment against the session's
 * schema. A query from another schema therefore met no row access policy and saw every row. A schema or database
 * rename left masking pointing at a name that no longer existed, and an attachment that resolved to nothing
 * returned the data unprotected.
 */
public class PolicyResolvesWhereItLivesTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE p");
        engine.execute("CREATE SCHEMA p.sec");
        engine.execute("USE SCHEMA p.sec");
        engine.execute("CREATE MASKING POLICY mp AS (v STRING) RETURNS STRING -> '***'");
        engine.execute("CREATE ROW ACCESS POLICY rap AS (k STRING) RETURNS BOOLEAN -> k = 'visible'");
        engine.execute("CREATE TABLE t (s STRING WITH MASKING POLICY mp, k STRING)");
        engine.execute("ALTER TABLE t ADD ROW ACCESS POLICY rap ON (k)");
        engine.execute("INSERT INTO t VALUES ('secret1', 'visible'), ('secret2', 'hidden')");
        engine.execute("CREATE SCHEMA p.other");
        // CREATE SCHEMA makes the new schema current; the tests start where the table is.
        engine.execute("USE SCHEMA p.sec");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    /** A query's rows, each one's values joined by '|', and the rows joined by " ; ". */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.append(out.length() > 0 ? " ; " : "");
            for (int i = 0; i < row.size(); i++) {
                out.append(i > 0 ? "|" : "").append(row.getValue(i));
            }
        }
        return out.toString();
    }

    private String ddl(final String table) {
        return rows("SELECT GET_DDL('TABLE', '" + table + "')");
    }

    private String refusal(final String sql) {
        try {
            engine.executeQuery(sql);
            return "not refused";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage());
        }
    }

    @Test
    public void aQueryFromAnotherSchemaMeetsTheSamePolicies() {
        assertEquals("***|visible", rows("SELECT s, k FROM t"), "from the table's own schema");
        engine.execute("USE SCHEMA p.PUBLIC");
        assertEquals("***|visible", rows("SELECT s, k FROM p.sec.t"), "from another schema");
        engine.execute("CREATE DATABASE elsewhere");
        engine.execute("USE SCHEMA elsewhere.PUBLIC");
        assertEquals("***|visible", rows("SELECT s, k FROM p.sec.t"), "from another database");
        assertTrue(ddl("p.sec.t").contains("ROW ACCESS POLICY P.SEC.RAP ON (K)"), ddl("p.sec.t"));
    }

    @Test
    public void renamingOrMovingTheSchemaOrDatabaseKeepsTheAttachments() {
        engine.execute("USE SCHEMA p.PUBLIC");
        engine.execute("ALTER SCHEMA p.sec RENAME TO sec2");
        assertEquals("***|visible", rows("SELECT s, k FROM p.sec2.t"), "after the schema rename");
        assertTrue(ddl("p.sec2.t").contains("MASKING POLICY P.SEC2.MP"), ddl("p.sec2.t"));
        assertTrue(ddl("p.sec2.t").contains("ROW ACCESS POLICY P.SEC2.RAP"), ddl("p.sec2.t"));

        engine.execute("ALTER DATABASE p RENAME TO p2");
        assertEquals("***|visible", rows("SELECT s, k FROM p2.sec2.t"), "after the database rename");

        engine.execute("CREATE DATABASE q");
        engine.execute("ALTER SCHEMA p2.sec2 RENAME TO q.sec3");
        assertEquals("***|visible", rows("SELECT s, k FROM q.sec3.t"), "after the schema moved database");
        assertTrue(ddl("q.sec3.t").contains("MASKING POLICY Q.SEC3.MP"), ddl("q.sec3.t"));
    }

    @Test
    public void renamingOrMovingAPolicyKeepsItsAttachments() {
        engine.execute("CREATE PROJECTION POLICY pp AS () RETURNS PROJECTION_CONSTRAINT"
            + " -> PROJECTION_CONSTRAINT(ALLOW => false)");
        engine.execute("CREATE AGGREGATION POLICY agp AS () RETURNS AGGREGATION_CONSTRAINT"
            + " -> AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => 2)");
        engine.execute("CREATE JOIN POLICY jp AS () RETURNS JOIN_CONSTRAINT"
            + " -> JOIN_CONSTRAINT(JOIN_REQUIRED => TRUE)");
        engine.execute("CREATE TABLE pt (id INT, p STRING WITH PROJECTION POLICY pp)");
        engine.execute("CREATE TABLE agt (id INT) WITH AGGREGATION POLICY agp");
        engine.execute("CREATE TABLE jnt (id INT) WITH JOIN POLICY jp");

        engine.execute("ALTER MASKING POLICY p.sec.mp RENAME TO p.sec.mp2");
        engine.execute("ALTER ROW ACCESS POLICY p.sec.rap RENAME TO p.other.rap2");
        engine.execute("ALTER PROJECTION POLICY p.sec.pp RENAME TO p.other.pp2");
        engine.execute("ALTER AGGREGATION POLICY p.sec.agp RENAME TO p.other.agp2");
        engine.execute("ALTER JOIN POLICY p.sec.jp RENAME TO p.other.jp2");

        engine.execute("USE SCHEMA p.PUBLIC");
        assertEquals("***|visible", rows("SELECT s, k FROM p.sec.t"));
        assertTrue(ddl("p.sec.t").contains("MASKING POLICY P.SEC.MP2"), ddl("p.sec.t"));
        assertTrue(ddl("p.sec.t").contains("ROW ACCESS POLICY P.OTHER.RAP2"), ddl("p.sec.t"));
        assertTrue(refusal("SELECT p FROM p.sec.pt").contains("restricted by a Projection Policy"));
        assertEquals("SQL compilation error: Aggregation policy violation: aggregation required.",
            refusal("SELECT * FROM p.sec.agt"));
        assertEquals("SQL compilation error: Join Policy violation, please contact the policy admin for details",
            refusal("SELECT * FROM p.sec.jnt"));
    }

    /** Dropping the schema that holds a policy is the way an attachment is left pointing at nothing. */
    @Test
    public void anAttachmentWhosePolicyIsGoneRefusesTheQuery() {
        engine.execute("CREATE SCHEMA p.pol");
        engine.execute("CREATE MASKING POLICY p.pol.gone_mp AS (v STRING) RETURNS STRING -> '***'");
        engine.execute("CREATE ROW ACCESS POLICY p.pol.gone_rap AS (k STRING) RETURNS BOOLEAN -> TRUE");
        engine.execute("CREATE TABLE p.sec.masked (s STRING WITH MASKING POLICY p.pol.gone_mp, k STRING)");
        engine.execute("CREATE TABLE p.sec.filtered (s STRING, k STRING)");
        engine.execute("ALTER TABLE p.sec.filtered ADD ROW ACCESS POLICY p.pol.gone_rap ON (k)");
        engine.execute("INSERT INTO p.sec.masked VALUES ('secret', 'k')");
        engine.execute("INSERT INTO p.sec.filtered VALUES ('secret', 'k')");
        engine.execute("DROP SCHEMA p.pol");

        assertTrue(refusal("SELECT s FROM p.sec.masked").contains("Masking policy 'P.POL.GONE_MP' does not exist"),
            refusal("SELECT s FROM p.sec.masked"));
        assertEquals("k", rows("SELECT k FROM p.sec.masked"), "a column the policy does not mask still reads");
        assertTrue(refusal("SELECT k FROM p.sec.filtered")
                .contains("Row access policy 'P.POL.GONE_RAP' does not exist"),
            refusal("SELECT k FROM p.sec.filtered"));
    }

    @Test
    public void aCloneNamesItsOwnCopiesOfThePoliciesInsideIt() {
        engine.execute("CREATE DATABASE x");
        engine.execute("CREATE SCHEMA x.pol");
        engine.execute("CREATE MASKING POLICY x.pol.fmp AS (v STRING) RETURNS STRING -> 'foreign'");
        engine.execute("CREATE MASKING POLICY p.other.omp AS (v STRING) RETURNS STRING -> 'other'");
        engine.execute("CREATE TABLE p.sec.f (c STRING WITH MASKING POLICY x.pol.fmp,"
            + " o STRING WITH MASKING POLICY p.other.omp)");
        engine.execute("INSERT INTO p.sec.f VALUES ('raw', 'raw')");
        engine.execute("CREATE DATABASE q CLONE p");
        engine.execute("CREATE SCHEMA p.sec_copy CLONE p.sec");
        // The source's own policies change after the clones; each clone keeps its own copies.
        engine.execute("ALTER MASKING POLICY p.sec.mp SET BODY -> 'source'");
        engine.execute("ALTER ROW ACCESS POLICY p.sec.rap SET BODY -> TRUE");

        engine.execute("USE SCHEMA p.PUBLIC");
        assertEquals("source|hidden ; source|visible", rows("SELECT s, k FROM p.sec.t ORDER BY k"));
        assertEquals("***|visible", rows("SELECT s, k FROM q.sec.t"), "the database clone");
        assertEquals("***|visible", rows("SELECT s, k FROM p.sec_copy.t"), "the schema clone");
        assertTrue(ddl("q.sec.t").contains("MASKING POLICY Q.SEC.MP"), ddl("q.sec.t"));
        assertTrue(ddl("q.sec.t").contains("ROW ACCESS POLICY Q.SEC.RAP"), ddl("q.sec.t"));
        assertTrue(ddl("p.sec_copy.t").contains("ROW ACCESS POLICY P.SEC_COPY.RAP"), ddl("p.sec_copy.t"));

        // A policy outside what was cloned stays the one attached: another database's for the database
        // clone, another schema's for the schema clone.
        assertEquals("foreign|other", rows("SELECT c, o FROM q.sec.f"));
        assertTrue(ddl("q.sec.f").contains("MASKING POLICY X.POL.FMP"), ddl("q.sec.f"));
        assertTrue(ddl("q.sec.f").contains("MASKING POLICY Q.OTHER.OMP"), ddl("q.sec.f"));
        assertTrue(ddl("p.sec_copy.f").contains("MASKING POLICY P.OTHER.OMP"), ddl("p.sec_copy.f"));
    }

    /** A name recorded with less than its full path, as older snapshots hold them, is read where its table lives. */
    @Test
    public void aShortNameResolvesWhereItsTableLives() throws Exception {
        final Table table = engine.getCatalog().getDatabase("P").getSchema("SEC").getTable("T");
        table.setRowAccessPolicyName("RAP");
        table.getColumn("S").setMaskingPolicyName("MP");
        engine.execute("USE SCHEMA p.PUBLIC");
        assertEquals("***|visible", rows("SELECT s, k FROM p.sec.t"), "the table's schema, not the session's");

        final DatabaseEngine restored = engine.cloneInstance();
        try {
            final Table copy = restored.getCatalog().getDatabase("P").getSchema("SEC").getTable("T");
            assertEquals("P.SEC.RAP", copy.getRowAccessPolicyName(), "completed when restored");
            assertEquals("P.SEC.MP", copy.getColumn("S").getMaskingPolicyName(), "completed when restored");
        } finally {
            restored.shutdown();
        }
    }
}
