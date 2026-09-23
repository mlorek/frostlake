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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Where a schema-level SHOW listing looks when its IN clause names no container, or is missing.
 *
 * <p>With no IN clause, TABLES, VIEWS, OBJECTS, COLUMNS, FUNCTIONS, PROCEDURES, SEQUENCES, STREAMS, TASKS, FILE
 * FORMATS, TAGS, ALERTS and MATERIALIZED VIEWS list the session's SEARCH_PATH ({@code $current, $public} by
 * default): each schema in path order, and of the objects sharing a name only the one the path reaches first.
 * The other listings — STAGES, PIPES, the policies, EVENT and DYNAMIC TABLES, NOTEBOOKS, STREAMLITS, CONTACTS and
 * the KEYS — list the current schema alone. {@code IN DATABASE} and {@code IN SCHEMA} without a name are the
 * current ones. A session with no current schema lists its whole database instead, and one with no current
 * database the account. A TABLE scope holds only columns and keys. All live-verified.
 */
public class ShowListingScopeTest extends BaseDatabaseTest {

    /** Tables SLS_B and SLS_C in SLS_S1, SLS_A in SLS_S2, SLS_A and SLS_Z in PUBLIC. */
    private void createSchemas() {
        engine.execute("CREATE SCHEMA test_db.sls_s1");
        engine.execute("CREATE SCHEMA test_db.sls_s2");
        engine.execute("CREATE TABLE test_db.sls_s1.sls_b (b_col INT)");
        engine.execute("CREATE TABLE test_db.sls_s1.sls_c (c_col INT)");
        engine.execute("CREATE TABLE test_db.sls_s2.sls_a (sls_a1 INT, sls_a2 INT)");
        engine.execute("CREATE TABLE test_db.public.sls_a (sls_p1 INT)");
        engine.execute("CREATE TABLE test_db.public.sls_z (sls_z1 INT)");
    }

    /** Each row of a listing as SCHEMA.NAME, in the order it came; columns listed as SCHEMA.TABLE.COLUMN. */
    private List<String> listed(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> rows = new ArrayList<>();
        final boolean columns = sql.contains("COLUMNS");
        while (rs.next()) {
            rows.add(columns
                ? rs.getValue("schema_name") + "." + rs.getValue("table_name") + "." + rs.getValue("column_name")
                : rs.getValue("schema_name") + "." + rs.getValue("name"));
        }
        return rows;
    }

    /** The first line of a refusal, or "answered" when the statement runs. */
    private String refusal(final String sql) {
        try {
            engine.executeQuery(sql);
            return "answered";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage());
        }
    }

    /** No IN clause: the search path, in path order, a name the path reaches first hiding the later ones. */
    @Test
    public void anUnscopedListingReadsTheSearchPath() {
        createSchemas();
        engine.execute("USE SCHEMA test_db.sls_s1");
        assertEquals(Arrays.asList("SLS_S1.SLS_B", "SLS_S1.SLS_C", "PUBLIC.SLS_A", "PUBLIC.SLS_Z"),
            listed("SHOW TABLES LIKE 'SLS%'"));
        assertEquals(Arrays.asList("SLS_S1.SLS_B", "SLS_S1.SLS_C"), listed("SHOW TABLES LIKE 'SLS%' LIMIT 2"));
        engine.execute("USE SCHEMA test_db.sls_s2");
        assertEquals(Arrays.asList("SLS_S2.SLS_A", "PUBLIC.SLS_Z"), listed("SHOW TABLES LIKE 'SLS%'"),
            "PUBLIC.SLS_A is hidden by the SLS_A the path reaches first");
        assertEquals(Arrays.asList("SLS_S2.SLS_A", "PUBLIC.SLS_Z"), listed("SHOW OBJECTS LIKE 'SLS%'"));
        engine.execute("USE SCHEMA test_db.public");
        assertEquals(Arrays.asList("PUBLIC.SLS_A", "PUBLIC.SLS_Z"), listed("SHOW TABLES LIKE 'SLS%'"),
            "PUBLIC is on the path twice and listed once");
    }

    /** The session's own SEARCH_PATH decides the schemas and their order. */
    @Test
    public void aSessionSearchPathIsFollowed() {
        createSchemas();
        engine.execute("USE SCHEMA test_db.sls_s1");
        try {
            engine.execute("ALTER SESSION SET SEARCH_PATH = 'sls_s2, $current, $public'");
            assertEquals(Arrays.asList("SLS_S2.SLS_A", "SLS_S1.SLS_B", "SLS_S1.SLS_C", "PUBLIC.SLS_Z"),
                listed("SHOW TABLES LIKE 'SLS%'"));
            engine.execute("ALTER SESSION SET SEARCH_PATH = '$current'");
            assertEquals(Arrays.asList("SLS_S1.SLS_B", "SLS_S1.SLS_C"), listed("SHOW TABLES LIKE 'SLS%'"));
        } finally {
            engine.execute("ALTER SESSION UNSET SEARCH_PATH");
        }
    }

    /** A column is reached through its relation's name: the first relation keeps all its columns. */
    @Test
    public void aColumnListingKeepsTheFirstRelationWhole() {
        createSchemas();
        engine.execute("USE SCHEMA test_db.sls_s2");
        assertEquals(Arrays.asList("SLS_S2.SLS_A.SLS_A1", "SLS_S2.SLS_A.SLS_A2", "PUBLIC.SLS_Z.SLS_Z1"),
            listed("SHOW COLUMNS LIKE 'SLS%'"));
    }

    /** A routine is reached by its name and argument types, and the routines list in name order. */
    @Test
    public void aRoutineIsReachedByItsArgumentTypes() {
        engine.execute("CREATE SCHEMA test_db.sls_s2");
        engine.execute("CREATE FUNCTION test_db.public.sls_fa() RETURNS INT AS '1'");
        engine.execute("CREATE FUNCTION test_db.public.sls_fa(x INT) RETURNS INT AS '2'");
        engine.execute("CREATE FUNCTION test_db.sls_s2.sls_fa(x INT) RETURNS INT AS '3'");
        engine.execute("CREATE FUNCTION test_db.sls_s2.sls_fb() RETURNS INT AS '4'");
        engine.execute("CREATE FUNCTION test_db.public.sls_fc(y INT) RETURNS INT AS '5'");
        engine.execute("CREATE FUNCTION test_db.sls_s2.sls_fc(x INT) RETURNS INT AS '6'");
        engine.execute("USE SCHEMA test_db.sls_s2");
        // SLS_FA(x INT) in SLS_S2 hides PUBLIC's identical declaration, while PUBLIC's SLS_FA() is another routine
        // and stays; PUBLIC's SLS_FC(y INT) differs from SLS_S2's SLS_FC(x INT) in its parameter's name and stays
        // too. The name orders the rows, the path the rows of one name.
        assertEquals(Arrays.asList("SLS_S2.SLS_FA", "PUBLIC.SLS_FA", "SLS_S2.SLS_FB", "SLS_S2.SLS_FC",
            "PUBLIC.SLS_FC"), listed("SHOW USER FUNCTIONS LIKE 'SLS%'"));
    }

    /** The current-schema listings do not follow the path. */
    @Test
    public void aStageListingReadsOnlyTheCurrentSchema() {
        engine.execute("CREATE SCHEMA test_db.sls_s1");
        engine.execute("CREATE STAGE test_db.public.sls_st_p");
        engine.execute("CREATE STAGE test_db.sls_s1.sls_st_s");
        engine.execute("USE SCHEMA test_db.sls_s1");
        assertEquals(Arrays.asList("SLS_S1.SLS_ST_S"), listed("SHOW STAGES LIKE 'SLS%'"));
    }

    /** IN DATABASE and IN SCHEMA without a name are the current database and schema. */
    @Test
    public void aMissingNameIsTheCurrentContainer() {
        createSchemas();
        engine.execute("CREATE FUNCTION test_db.sls_s1.sls_fn() RETURNS INT AS '1'");
        engine.execute("CREATE STAGE test_db.public.sls_st");
        engine.execute("USE SCHEMA test_db.sls_s1");
        assertEquals(Arrays.asList("PUBLIC.SLS_A", "PUBLIC.SLS_Z", "SLS_S1.SLS_B", "SLS_S1.SLS_C", "SLS_S2.SLS_A"),
            listed("SHOW TABLES LIKE 'SLS%' IN DATABASE"));
        assertEquals(Arrays.asList("SLS_S1.SLS_B", "SLS_S1.SLS_C"), listed("SHOW TABLES LIKE 'SLS%' IN SCHEMA"));
        assertEquals(Arrays.asList("PUBLIC.SLS_A.SLS_P1", "PUBLIC.SLS_Z.SLS_Z1", "SLS_S2.SLS_A.SLS_A1",
            "SLS_S2.SLS_A.SLS_A2"), listed("SHOW COLUMNS LIKE 'SLS%' IN DATABASE"));
        assertEquals(Arrays.asList("SLS_S1.SLS_FN"), listed("SHOW USER FUNCTIONS LIKE 'SLS%' IN DATABASE"));
        assertEquals(Arrays.asList("SLS_S1.SLS_FN"), listed("SHOW FUNCTIONS LIKE 'SLS%' IN SCHEMA"));
        assertEquals(Arrays.asList("PUBLIC.SLS_ST"), listed("SHOW STAGES LIKE 'SLS%' IN DATABASE"));
        assertEquals(new ArrayList<String>(), listed("SHOW STAGES LIKE 'SLS%' IN SCHEMA"));
    }

    /** With no current schema, the unscoped listings and IN SCHEMA read the whole current database. */
    @Test
    public void noCurrentSchemaListsTheDatabase() {
        createSchemas();
        engine.execute("CREATE STAGE test_db.sls_s1.sls_st");
        engine.execute("DROP SCHEMA test_db.public");
        engine.execute("USE DATABASE test_db");
        final List<String> tables = Arrays.asList("SLS_S1.SLS_B", "SLS_S1.SLS_C", "SLS_S2.SLS_A");
        assertEquals(tables, listed("SHOW TABLES LIKE 'SLS%'"));
        assertEquals(tables, listed("SHOW TABLES LIKE 'SLS%' IN SCHEMA"));
        assertEquals(Arrays.asList("SLS_S1.SLS_ST"), listed("SHOW STAGES LIKE 'SLS%'"));
    }

    /** A TABLE holds only columns and keys; every other listing refuses the scope before looking it up. */
    @Test
    public void aTableScopeHoldsOnlyColumnsAndKeys() {
        engine.execute("CREATE TABLE sls_t (id INT)");
        assertEquals("SQL compilation error:\nUnsupported statement type 'Cannot show objects of type TABLE in TABLE'.",
            refusal("SHOW TABLES IN TABLE sls_t"));
        assertEquals("SQL compilation error:\nUnsupported statement type 'Cannot show objects of type VIEW in TABLE'.",
            refusal("SHOW VIEWS IN TABLE nosuch"));
        assertEquals("SQL compilation error:\nUnsupported statement type 'Cannot show objects of type STAGE in TABLE'.",
            refusal("SHOW STAGES IN TABLE"));
        assertEquals("SQL compilation error:\nUnsupported statement type 'Cannot show objects of type FUNCTION in TABLE'.",
            refusal("SHOW USER FUNCTIONS IN TABLE sls_t"));
        assertEquals("SQL compilation error:\nUnsupported statement type 'Cannot show objects of type KEY VALUE TABLE"
            + " in TABLE'.", refusal("SHOW HYBRID TABLES IN TABLE sls_t"));
        assertEquals("SQL compilation error:\nUnsupported statement type 'Cannot show objects of type TABLE in TABLE'.",
            refusal("SHOW TABLES IN TABLE sls_t LIMIT 0"), "the shape is refused before the page size");
        assertEquals("answered", refusal("SHOW COLUMNS IN TABLE sls_t"));
        assertEquals("answered", refusal("SHOW PRIMARY KEYS IN TABLE sls_t"));
    }
}
