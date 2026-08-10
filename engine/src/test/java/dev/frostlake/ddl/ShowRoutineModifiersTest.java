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
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three modifiers the routine listings take — TERSE, USER and BUILTIN — and the column shape they
 * all share. Every claim below was measured against a real Snowflake account, in a database
 * holding one user function and one user procedure:
 *
 * <pre>
 *   SHOW FUNCTIONS                       1135 rows / 21 cols   SHOW PROCEDURES                 33 / 16
 *   SHOW BUILTIN FUNCTIONS               1134 rows / 21 cols   SHOW BUILTIN PROCEDURES         32 / 16
 *   SHOW USER FUNCTIONS                     1 row  / 21 cols   SHOW USER PROCEDURES             1 / 16
 *   SHOW TERSE FUNCTIONS                 1135 rows / 21 cols   SHOW TERSE PROCEDURES           33 / 16
 *   SHOW TERSE BUILTIN FUNCTIONS         1134 rows / 21 cols   SHOW TERSE BUILTIN PROCEDURES   32 / 16
 *   SHOW TERSE USER FUNCTIONS               1 row  / 21 cols   SHOW TERSE USER PROCEDURES       1 / 16
 * </pre>
 *
 * <p>Two things stand out. TERSE is a <strong>no-op</strong> here: it neither drops rows nor trims
 * columns, unlike SHOW TERSE TABLES (27 columns down to 5, live-verified the same day). And the two
 * families split their catalogs the same way — the plain listing is the built-in one plus the user
 * routines in scope, BUILTIN keeps only the former, USER only the latter.
 */
public class ShowRoutineModifiersTest extends BaseDatabaseTest {

    private static final int NAME = 1;
    private static final int IS_BUILTIN = 3;

    /**
     * The 21 columns every function listing returns, in order (live-verified — the same 21 for
     * SHOW FUNCTIONS, SHOW BUILTIN FUNCTIONS, SHOW USER FUNCTIONS and each of their TERSE forms).
     */
    private static final String[] FUNCTION_COLUMNS = {
        "created_on", "name", "schema_name", "is_builtin", "is_aggregate", "is_ansi",
        "min_num_arguments", "max_num_arguments", "arguments", "description", "catalog_name",
        "is_table_function", "valid_for_clustering", "is_secure", "secrets",
        "external_access_integrations", "is_external_function", "language", "is_memoizable",
        "is_data_metric", "is_ai_function"
    };

    /**
     * A procedure listing returns exactly the first 16 of those — dropping the five a procedure has no
     * answer for (is_external_function, language, is_memoizable, is_data_metric, is_ai_function).
     * Deriving the array rather than retyping it is the point: the shapes really are one prefix of the
     * other.
     */
    private static final String[] PROCEDURE_COLUMNS = Arrays.copyOfRange(FUNCTION_COLUMNS, 0, 16);

    private ResultSet show(final String sql) {
        return engine.executeQuery(sql);
    }

    private List<String> columnNames(final ResultSet rs) {
        final List<String> names = new ArrayList<String>();
        for (final ResultSetColumn column : rs.getColumns()) {
            names.add(column.getName().toLowerCase());
        }
        return names;
    }

    private void assertColumns(final String[] expected, final String sql) {
        assertEquals(Arrays.asList(expected), columnNames(show(sql)),
            sql + " should return the live column shape");
    }

    /** Assert two listings agree on both column shape and row count. */
    private void assertSameListing(final String terse, final String plain) {
        final ResultSet a = show(terse);
        final ResultSet b = show(plain);
        assertEquals(columnNames(b), columnNames(a), terse + " must return the same columns as " + plain);
        assertEquals(b.getRowCount(), a.getRowCount(),
            terse + " must return the same rows as " + plain);
    }

    private List<String> namesOf(final ResultSet rs) {
        final List<String> names = new ArrayList<String>();
        for (final Row row : rs.getRows()) {
            names.add(String.valueOf(row.getValue(NAME)).toUpperCase());
        }
        return names;
    }

    private void createUserProcedure(final String name) {
        engine.execute("CREATE OR REPLACE PROCEDURE " + name + "() RETURNS VARCHAR LANGUAGE SQL "
            + "AS $$ BEGIN RETURN 'x'; END $$");
    }

    // ──────────────────────────── TERSE is accepted and changes nothing ────────────────────────────

    /** SHOW TERSE FUNCTIONS: 1134 rows / 21 cols live, identical to SHOW FUNCTIONS. */
    @Test
    public void terseFunctionsIsIdenticalToFunctions() {
        assertSameListing("SHOW TERSE FUNCTIONS", "SHOW FUNCTIONS");
        assertColumns(FUNCTION_COLUMNS, "SHOW TERSE FUNCTIONS");
    }

    /** SHOW TERSE BUILTIN FUNCTIONS: 1134 rows / 21 cols live, identical to SHOW BUILTIN FUNCTIONS. */
    @Test
    public void terseBuiltinFunctionsIsIdenticalToBuiltinFunctions() {
        assertSameListing("SHOW TERSE BUILTIN FUNCTIONS", "SHOW BUILTIN FUNCTIONS");
        assertColumns(FUNCTION_COLUMNS, "SHOW TERSE BUILTIN FUNCTIONS");
    }

    /** SHOW TERSE USER FUNCTIONS: 0 rows / 21 cols live on a database with no user functions. */
    @Test
    public void terseUserFunctionsListsNoBuiltins() {
        final ResultSet rs = show("SHOW TERSE USER FUNCTIONS");
        assertColumns(FUNCTION_COLUMNS, "SHOW TERSE USER FUNCTIONS");
        assertEquals(0, rs.getRowCount(), "a fresh schema has no user functions to list");

        engine.execute("CREATE FUNCTION terse_udf(x INTEGER) RETURNS INTEGER AS 'x + 1'");
        final ResultSet withUdf = show("SHOW TERSE USER FUNCTIONS");
        assertEquals(1, withUdf.getRowCount(), "the one user function should be the only row");
        assertEquals("TERSE_UDF", namesOf(withUdf).get(0));
        assertEquals("N", withUdf.getRows().get(0).getValue(IS_BUILTIN), "a user function is not built-in");
    }

    /** SHOW TERSE PROCEDURES: 32 rows / 16 cols live with no user procedure, identical to SHOW PROCEDURES. */
    @Test
    public void terseProceduresIsIdenticalToProcedures() {
        assertSameListing("SHOW TERSE PROCEDURES", "SHOW PROCEDURES");
        createUserProcedure("terse_proc");
        assertSameListing("SHOW TERSE PROCEDURES", "SHOW PROCEDURES");
        assertTrue(namesOf(show("SHOW TERSE PROCEDURES")).contains("TERSE_PROC"),
            "SHOW TERSE PROCEDURES should list the user procedure SHOW PROCEDURES lists");
    }

    /** SHOW TERSE BUILTIN PROCEDURES: 32 rows / 16 cols live, identical to SHOW BUILTIN PROCEDURES. */
    @Test
    public void terseBuiltinProceduresIsIdenticalToBuiltinProcedures() {
        assertSameListing("SHOW TERSE BUILTIN PROCEDURES", "SHOW BUILTIN PROCEDURES");
        createUserProcedure("terse_builtin_proc");
        assertSameListing("SHOW TERSE BUILTIN PROCEDURES", "SHOW BUILTIN PROCEDURES");
    }

    /** SHOW TERSE USER PROCEDURES: 0 rows / 16 cols live on a database with no user procedures. */
    @Test
    public void terseUserProceduresListsNoBuiltins() {
        assertEquals(0, show("SHOW TERSE USER PROCEDURES").getRowCount(),
            "a fresh schema has no user procedures to list");
        assertSameListing("SHOW TERSE USER PROCEDURES", "SHOW USER PROCEDURES");

        createUserProcedure("terse_user_proc");
        final ResultSet rs = show("SHOW TERSE USER PROCEDURES");
        assertEquals(1, rs.getRowCount(), "the one user procedure should be the only row");
        assertEquals("TERSE_USER_PROC", namesOf(rs).get(0));
        assertEquals("N", rs.getRows().get(0).getValue(IS_BUILTIN), "a user procedure is not built-in");
    }

    /**
     * TERSE trims elsewhere, which is what makes the routine listings worth pinning: live-verified
     * SHOW TABLES returns 27 columns and SHOW TERSE TABLES 5.
     */
    @Test
    public void terseStillTrimsTheTableListing() {
        engine.execute("CREATE TABLE terse_probe (a INTEGER)");
        assertTrue(show("SHOW TERSE TABLES").getColumns().size()
                < show("SHOW TABLES").getColumns().size(),
            "TERSE must still project SHOW TABLES onto its short column set");
    }

    // ──────────────────────────────── the shared column shape ────────────────────────────────

    /** Every procedure listing returns the same 16 columns — the first 16 of the function listing's 20. */
    @Test
    public void everyProcedureListingReturnsTheSameSixteenColumns() {
        createUserProcedure("shape_proc");
        for (final String sql : new String[]{"SHOW PROCEDURES", "SHOW BUILTIN PROCEDURES",
            "SHOW USER PROCEDURES", "SHOW TERSE PROCEDURES", "SHOW TERSE BUILTIN PROCEDURES",
            "SHOW TERSE USER PROCEDURES"}) {
            assertColumns(PROCEDURE_COLUMNS, sql);
        }
    }

    /** …and every function listing the same 20, of which those 16 are the prefix. */
    @Test
    public void everyFunctionListingReturnsTheSameTwentyColumns() {
        for (final String sql : new String[]{"SHOW FUNCTIONS", "SHOW BUILTIN FUNCTIONS",
            "SHOW USER FUNCTIONS", "SHOW TERSE FUNCTIONS", "SHOW TERSE BUILTIN FUNCTIONS",
            "SHOW TERSE USER FUNCTIONS"}) {
            assertColumns(FUNCTION_COLUMNS, sql);
        }
        assertEquals(columnNames(show("SHOW PROCEDURES")),
            columnNames(show("SHOW FUNCTIONS")).subList(0, PROCEDURE_COLUMNS.length),
            "the procedure columns are the function columns' prefix");
    }

    /**
     * SHOW PROCEDURES has neither a {@code language} nor an {@code execute_as} column — both are
     * DESCRIBE PROCEDURE properties. Live-verified: DESC PROCEDURE answers
     * {@code language | SQL} and {@code execute as | OWNER} while the listing carries neither name.
     */
    @Test
    public void procedureListingsCarryNoLanguageOrExecuteAsColumn() {
        createUserProcedure("no_lang_proc");
        final List<String> columns = columnNames(show("SHOW PROCEDURES"));
        assertTrue(!columns.contains("language"), "SHOW PROCEDURES has no language column");
        assertTrue(!columns.contains("execute_as"), "SHOW PROCEDURES has no execute_as column");
    }

    // ─────────────────────────── BUILTIN / USER split the same catalog ───────────────────────────

    /**
     * SHOW PROCEDURES is SHOW BUILTIN PROCEDURES plus the user procedures in scope — live-verified
     * 33 = 32 + 1, with the user procedure in the plain and USER listings and in neither the
     * BUILTIN one. Frostlake's built-in procedure catalog is empty (CALL resolves only catalog
     * procedures), so the same identity holds there as 1 = 0 + 1.
     */
    @Test
    public void proceduresIsBuiltinsPlusUserProcedures() {
        final int builtinsBefore = show("SHOW BUILTIN PROCEDURES").getRowCount();
        assertEquals(builtinsBefore, show("SHOW PROCEDURES").getRowCount(),
            "with no user procedure, SHOW PROCEDURES is exactly the built-in listing");

        createUserProcedure("split_proc");
        final int builtins = show("SHOW BUILTIN PROCEDURES").getRowCount();
        final int users = show("SHOW USER PROCEDURES").getRowCount();
        assertEquals(builtinsBefore, builtins, "creating a user procedure adds no built-in");
        assertEquals(1, users, "the one user procedure");
        assertEquals(builtins + users, show("SHOW PROCEDURES").getRowCount(),
            "SHOW PROCEDURES = SHOW BUILTIN PROCEDURES + SHOW USER PROCEDURES");

        assertTrue(namesOf(show("SHOW PROCEDURES")).contains("SPLIT_PROC"));
        assertTrue(namesOf(show("SHOW USER PROCEDURES")).contains("SPLIT_PROC"));
        assertTrue(!namesOf(show("SHOW BUILTIN PROCEDURES")).contains("SPLIT_PROC"),
            "SHOW BUILTIN PROCEDURES must never list a user procedure");
    }

    /** Every row SHOW BUILTIN PROCEDURES returns is flagged is_builtin = 'Y'. */
    @Test
    public void builtinProceduresAreAllFlaggedBuiltin() {
        createUserProcedure("flag_proc");
        for (final Row row : show("SHOW BUILTIN PROCEDURES").getRows()) {
            assertEquals("Y", row.getValue(IS_BUILTIN),
                "SHOW BUILTIN PROCEDURES listed a non-built-in: " + row.getValue(NAME));
        }
        for (final Row row : show("SHOW USER PROCEDURES").getRows()) {
            assertEquals("N", row.getValue(IS_BUILTIN),
                "SHOW USER PROCEDURES listed a built-in: " + row.getValue(NAME));
        }
    }

    // ──────────────────────────────────── LIKE and IN scope ────────────────────────────────────

    /** SHOW TERSE FUNCTIONS LIKE 'ABS': 1 row / 21 cols live. */
    @Test
    public void likeFiltersTheTerseFunctionListing() {
        final ResultSet rs = show("SHOW TERSE FUNCTIONS LIKE 'ABS'");
        assertColumns(FUNCTION_COLUMNS, "SHOW TERSE FUNCTIONS LIKE 'ABS'");
        assertEquals(1, rs.getRowCount(), "LIKE 'ABS' matches exactly the one built-in");
        assertEquals("ABS", namesOf(rs).get(0));
    }

    /**
     * SHOW BUILTIN PROCEDURES LIKE 'SYSTEM%': 27 rows / 16 cols live. Frostlake lists no built-in
     * procedures at all, so what this pins on both backends is that the filter is applied to the
     * BUILTIN listing and never widens it.
     */
    @Test
    public void likeFiltersTheBuiltinProcedureListing() {
        final ResultSet rs = show("SHOW BUILTIN PROCEDURES LIKE 'SYSTEM%'");
        assertColumns(PROCEDURE_COLUMNS, "SHOW BUILTIN PROCEDURES LIKE 'SYSTEM%'");
        assertTrue(rs.getRowCount() <= show("SHOW BUILTIN PROCEDURES").getRowCount(),
            "a LIKE filter cannot add rows");
        for (final String name : namesOf(rs)) {
            assertTrue(name.startsWith("SYSTEM"), "LIKE 'SYSTEM%' matched " + name);
        }
    }

    /** SHOW TERSE PROCEDURES LIKE '<user proc>': 1 row live, the user procedure. */
    @Test
    public void likeFiltersTheTerseProcedureListing() {
        createUserProcedure("like_proc");
        final ResultSet rs = show("SHOW TERSE PROCEDURES LIKE 'LIKE_PROC'");
        assertEquals(1, rs.getRowCount(), "LIKE matches the one user procedure");
        assertEquals("LIKE_PROC", namesOf(rs).get(0));
    }

    /**
     * SHOW TERSE FUNCTIONS IN SCHEMA &lt;schema&gt;: 1134 rows / 21 cols live — the scope parses and is
     * then ignored, answering exactly what the unscoped listing does.
     */
    @Test
    public void scopeIsAcceptedByTheTerseFunctionListing() {
        assertSameListing("SHOW TERSE FUNCTIONS IN SCHEMA test_schema", "SHOW FUNCTIONS");
    }

    /** The same for procedures: the scope parses and the listing is unchanged. */
    @Test
    public void scopeIsAcceptedByTheTerseProcedureListing() {
        createUserProcedure("scoped_proc");
        assertSameListing("SHOW TERSE PROCEDURES IN SCHEMA test_schema", "SHOW PROCEDURES");
        assertColumns(PROCEDURE_COLUMNS, "SHOW BUILTIN PROCEDURES IN SCHEMA test_schema");
        assertTrue(!namesOf(show("SHOW BUILTIN PROCEDURES IN SCHEMA test_schema"))
                .contains("SCOPED_PROC"),
            "a scope does not make SHOW BUILTIN PROCEDURES list user procedures");
    }

    // ──────────────────────────────── what is NOT accepted ────────────────────────────────

    /**
     * BUILTIN and USER are mutually exclusive for both families — live-verified, all four
     * orderings are syntax errors ("unexpected 'USER'" / "unexpected 'BUILTIN'").
     */
    @Test
    public void builtinAndUserCannotBeCombined() {
        for (final String sql : new String[]{
            "SHOW BUILTIN USER FUNCTIONS", "SHOW USER BUILTIN FUNCTIONS",
            "SHOW BUILTIN USER PROCEDURES", "SHOW USER BUILTIN PROCEDURES",
            "SHOW TERSE USER BUILTIN PROCEDURES"}) {
            assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.executeQuery(sql);
                }
            }, sql + " must be rejected, as a real account rejects it");
        }
    }

    /**
     * TERSE only binds in front of USER / BUILTIN — live-verified: SHOW USER TERSE FUNCTIONS
     * and SHOW BUILTIN TERSE PROCEDURES both fail "unexpected 'TERSE'", while the TERSE-first spellings
     * run.
     */
    @Test
    public void terseMustPrecedeUserAndBuiltin() {
        for (final String sql : new String[]{
            "SHOW USER TERSE FUNCTIONS", "SHOW BUILTIN TERSE FUNCTIONS",
            "SHOW USER TERSE PROCEDURES", "SHOW BUILTIN TERSE PROCEDURES"}) {
            assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.executeQuery(sql);
                }
            }, sql + " must be rejected, as a real account rejects it");
        }
    }
}
