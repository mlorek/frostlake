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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SHOW TABLES / VIEWS / PROCEDURES / FUNCTIONS support {@code IN DATABASE <db_name>}, listing objects
 * across every schema of the database (Snowflake scope), whereas {@code IN SCHEMA} scopes to one schema.
 */
public class ShowInDatabaseTest extends BaseDatabaseTest {

    private static final int NAME = 1;
    private static final int IS_BUILTIN = 3;

    private static final String LISTS_EVERY_ROW =
        "asserts is_builtin='N' for EVERY listed row, i.e. that the account holds no routines besides the "
        + "ones this test created — a real account lists its own user-defined routines (and its built-in "
        + "library) too, so only the presence of the created routines is portable";

    private Set<String> names(final ResultSet rs) {
        final Set<String> out = new HashSet<String>();
        for (final Row row : rs.getRows()) {
            out.add(String.valueOf(row.getValue(NAME)).toUpperCase());
        }
        return out;
    }

    /** Names read by the {@code name} COLUMN, for listings that do not share the usual column order. */
    private Set<String> namesByColumn(final ResultSet rs) {
        final Set<String> out = new HashSet<String>();
        final int nameIndex = rs.getColumnIndex("name");
        for (final Row row : rs.getRows()) {
            out.add(String.valueOf(row.getValue(nameIndex)).toUpperCase());
        }
        return out;
    }

    @Test
    public void showTablesInDatabaseSpansAllSchemas() {
        engine.execute("CREATE SCHEMA s1");
        engine.execute("CREATE SCHEMA s2");
        engine.execute("CREATE TABLE s1.t1 (a INTEGER)");
        engine.execute("CREATE TABLE s2.t2 (a INTEGER)");

        final Set<String> all = names(engine.executeQuery("SHOW TABLES IN DATABASE test_db"));
        assertTrue(all.contains("T1"), all.toString());
        assertTrue(all.contains("T2"), all.toString());

        // IN SCHEMA still scopes to a single schema.
        final Set<String> scoped = names(engine.executeQuery("SHOW TABLES IN SCHEMA s1"));
        assertTrue(scoped.contains("T1"), scoped.toString());
        assertFalse(scoped.contains("T2"), scoped.toString());
    }

    @Test
    public void showViewsInDatabaseSpansAllSchemas() {
        engine.execute("CREATE SCHEMA s1");
        engine.execute("CREATE SCHEMA s2");
        engine.execute("CREATE TABLE s1.base (a INTEGER)");
        engine.execute("CREATE VIEW s1.v1 AS SELECT a FROM s1.base");
        engine.execute("CREATE TABLE s2.base (a INTEGER)");
        engine.execute("CREATE VIEW s2.v2 AS SELECT a FROM s2.base");

        final Set<String> all = names(engine.executeQuery("SHOW VIEWS IN DATABASE test_db"));
        assertTrue(all.contains("V1"), all.toString());
        assertTrue(all.contains("V2"), all.toString());
    }

    @Test
    public void showProceduresInDatabaseSpansSchemasAndOmitsBuiltins() {
        engine.execute("CREATE SCHEMA s1");
        engine.execute("CREATE SCHEMA s2");
        engine.execute("""
            CREATE PROCEDURE s1.p1() RETURNS VARCHAR LANGUAGE SQL AS BEGIN RETURN 'a'; END;
            """);
        engine.execute("""
            CREATE PROCEDURE s2.p2() RETURNS VARCHAR LANGUAGE SQL AS BEGIN RETURN 'b'; END;
            """);

        final ResultSet rs = engine.executeQuery("SHOW PROCEDURES IN DATABASE test_db");
        final Set<String> all = names(rs);
        assertTrue(all.contains("P1"), all.toString());
        assertTrue(all.contains("P2"), all.toString());
        // The listing appends the built-in catalog as SHOW FUNCTIONS IN DATABASE does; every row is
        // is_builtin = N only because Frostlake dispatches no built-in PROCEDURES (a real account ships
        // 32 and answers 34 here, live-verified with these same two user procedures).
        Assumptions.assumeFalse(isLiveSnowflake(), LISTS_EVERY_ROW);
        for (final Row row : rs.getRows()) {
            assertEquals("N", row.getValue(IS_BUILTIN), "IN DATABASE should list only user procedures");
        }
    }

    /**
     * SHOW FUNCTIONS IN DATABASE lists the built-in catalog alongside the database's user functions —
     * scoping the command narrows which user functions it reaches, not whether the system ones exist.
     *
     * <p>This test used to assert the opposite. Live-verified on a real account with three
     * UDFs spread over two schemas of one database: {@code SHOW FUNCTIONS} answered 1136 for the schema
     * holding two of them, {@code IN SCHEMA} the other schema 1135, and {@code IN DATABASE} 1137 — 1134
     * built-ins plus whatever user functions are in scope, every time. Only {@code SHOW USER FUNCTIONS
     * IN DATABASE} drops them, answering a bare 3.
     */
    @Test
    public void showFunctionsInDatabaseSpansSchemasAndIncludesBuiltins() {
        engine.execute("CREATE SCHEMA s1");
        engine.execute("CREATE FUNCTION s1.f1(x INTEGER) RETURNS INTEGER AS 'x + 1'");

        final ResultSet rs = engine.executeQuery("SHOW FUNCTIONS IN DATABASE test_db");
        final Set<String> all = names(rs);
        assertTrue(all.contains("F1"), all.toString());
        assertTrue(all.contains("ABS"), "the built-in catalog is listed too");

        final ResultSet userOnly = engine.executeQuery("SHOW USER FUNCTIONS IN DATABASE test_db");
        final Set<String> userNames = names(userOnly);
        assertTrue(userNames.contains("F1"), userNames.toString());
        assertFalse(userNames.contains("ABS"), "SHOW USER FUNCTIONS drops the built-in half");
        Assumptions.assumeFalse(isLiveSnowflake(), LISTS_EVERY_ROW);
        for (final Row row : userOnly.getRows()) {
            assertEquals("N", row.getValue(IS_BUILTIN), "USER ... IN DATABASE lists only user functions");
        }
    }

    @Test
    public void showObjectsInDatabaseSpansAllSchemas() {
        engine.execute("CREATE SCHEMA s1");
        engine.execute("CREATE SCHEMA s2");
        engine.execute("CREATE TABLE s1.o1 (a INTEGER)");
        engine.execute("CREATE VIEW s2.o2 AS SELECT 1 AS x");

        final Set<String> all = names(engine.executeQuery("SHOW OBJECTS IN DATABASE test_db"));
        assertTrue(all.contains("O1"), all.toString());
        assertTrue(all.contains("O2"), all.toString());
    }

    @Test
    public void showSequencesInDatabaseSpansAllSchemas() {
        engine.execute("CREATE SCHEMA s1");
        engine.execute("CREATE SCHEMA s2");
        engine.execute("CREATE SEQUENCE s1.seq1");
        engine.execute("CREATE SEQUENCE s2.seq2");

        // SHOW SEQUENCES has its OWN column shape — it leads with `name`, not `created_on` (live-verified
        // on a real account) — so the name is read by column NAME, not by the shared index.
        final Set<String> all = namesByColumn(engine.executeQuery("SHOW SEQUENCES IN DATABASE test_db"));
        assertTrue(all.contains("SEQ1"), all.toString());
        assertTrue(all.contains("SEQ2"), all.toString());
    }

    @Test
    public void showTagsInDatabaseSpansAllSchemas() {
        engine.execute("CREATE SCHEMA s1");
        engine.execute("CREATE SCHEMA s2");
        engine.execute("CREATE TAG s1.tag1");
        engine.execute("CREATE TAG s2.tag2");

        final Set<String> all = names(engine.executeQuery("SHOW TAGS IN DATABASE test_db"));
        assertTrue(all.contains("TAG1"), all.toString());
        assertTrue(all.contains("TAG2"), all.toString());
    }

    @Test
    public void showStagesInDatabaseSpansAllSchemas() {
        engine.execute("CREATE SCHEMA s1");
        engine.execute("CREATE SCHEMA s2");
        engine.execute("CREATE STAGE s1.stg1");
        engine.execute("CREATE STAGE s2.stg2");

        final Set<String> all = names(engine.executeQuery("SHOW STAGES IN DATABASE test_db"));
        assertTrue(all.contains("STG1"), all.toString());
        assertTrue(all.contains("STG2"), all.toString());
    }

    @Test
    public void showMaterializedViewsInDatabaseSpansAllSchemas() {
        engine.execute("CREATE SCHEMA s1");
        engine.execute("CREATE SCHEMA s2");
        engine.execute("CREATE TABLE s1.base (id INTEGER, amount INTEGER)");
        engine.execute("CREATE MATERIALIZED VIEW s1.mv1 AS SELECT id, amount FROM s1.base WHERE amount >= 200");
        engine.execute("CREATE TABLE s2.base (id INTEGER, amount INTEGER)");
        engine.execute("CREATE MATERIALIZED VIEW s2.mv2 AS SELECT id, amount FROM s2.base WHERE amount >= 200");

        final Set<String> all = names(engine.executeQuery("SHOW MATERIALIZED VIEWS IN DATABASE test_db"));
        assertTrue(all.contains("MV1"), all.toString());
        assertTrue(all.contains("MV2"), all.toString());

        final Set<String> scoped = names(engine.executeQuery("SHOW MATERIALIZED VIEWS IN SCHEMA s1"));
        assertTrue(scoped.contains("MV1"), scoped.toString());
        assertFalse(scoped.contains("MV2"), scoped.toString());
    }

    @Test
    public void showFileFormatsInDatabaseSpansAllSchemas() {
        engine.execute("CREATE SCHEMA s1");
        engine.execute("CREATE SCHEMA s2");
        engine.execute("CREATE FILE FORMAT s1.ff1 TYPE = CSV");
        engine.execute("CREATE FILE FORMAT s2.ff2 TYPE = CSV");

        final Set<String> all = names(engine.executeQuery("SHOW FILE FORMATS IN DATABASE test_db"));
        assertTrue(all.contains("FF1"), all.toString());
        assertTrue(all.contains("FF2"), all.toString());

        final Set<String> scoped = names(engine.executeQuery("SHOW FILE FORMATS IN SCHEMA s1"));
        assertTrue(scoped.contains("FF1"), scoped.toString());
        assertFalse(scoped.contains("FF2"), scoped.toString());
    }

    @Test
    public void showMaskingPoliciesInDatabaseSpansAllSchemas() {
        engine.execute("CREATE SCHEMA s1");
        engine.execute("CREATE SCHEMA s2");
        engine.execute("CREATE MASKING POLICY s1.mp1 AS (val STRING) RETURNS STRING -> '***'");
        engine.execute("CREATE MASKING POLICY s2.mp2 AS (val STRING) RETURNS STRING -> '***'");

        final Set<String> all = names(engine.executeQuery("SHOW MASKING POLICIES IN DATABASE test_db"));
        assertTrue(all.contains("MP1"), all.toString());
        assertTrue(all.contains("MP2"), all.toString());

        final Set<String> scoped = names(engine.executeQuery("SHOW MASKING POLICIES IN SCHEMA s1"));
        assertTrue(scoped.contains("MP1"), scoped.toString());
        assertFalse(scoped.contains("MP2"), scoped.toString());
    }

    @Test
    public void showRowAccessPoliciesInDatabaseSpansAllSchemas() {
        engine.execute("CREATE SCHEMA s1");
        engine.execute("CREATE SCHEMA s2");
        engine.execute("CREATE ROW ACCESS POLICY s1.rap1 AS (v STRING) RETURNS BOOLEAN -> TRUE");
        engine.execute("CREATE ROW ACCESS POLICY s2.rap2 AS (v STRING) RETURNS BOOLEAN -> TRUE");

        final Set<String> all = names(engine.executeQuery("SHOW ROW ACCESS POLICIES IN DATABASE test_db"));
        assertTrue(all.contains("RAP1"), all.toString());
        assertTrue(all.contains("RAP2"), all.toString());

        final Set<String> scoped = names(engine.executeQuery("SHOW ROW ACCESS POLICIES IN SCHEMA s1"));
        assertTrue(scoped.contains("RAP1"), scoped.toString());
        assertFalse(scoped.contains("RAP2"), scoped.toString());
    }
}
