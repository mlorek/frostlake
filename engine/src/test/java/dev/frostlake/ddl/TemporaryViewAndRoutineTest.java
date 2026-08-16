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
import dev.frostlake.DatabaseEngine;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.View;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A view, function or procedure may be created TEMPORARY, and the keyword order is not the same for
 * all three — every cell below was measured on a real account rather than read off the documentation,
 * which lists neither the VOLATILE spelling for routines nor TEMPORARY for a procedure at all.
 *
 * <pre>
 *   VIEW        CREATE [OR REPLACE] [SECURE] [{LOCAL|GLOBAL}]? {TEMPORARY|TEMP|VOLATILE} VIEW
 *   FUNCTION    CREATE [OR REPLACE] {TEMPORARY|TEMP|VOLATILE} [SECURE] FUNCTION
 *   PROCEDURE   CREATE [OR REPLACE] {TEMPORARY|TEMP|VOLATILE} PROCEDURE
 * </pre>
 *
 * <p>SECURE sits on the OPPOSITE side of the temporary keyword for a view and for a function, and only
 * a view takes the LOCAL / GLOBAL prefix. Both asymmetries are refusals on live, so they are asserted
 * in both directions here: accepting {@code CREATE TEMPORARY SECURE VIEW} would be exactly the kind of
 * over-acceptance this suite exists to catch.
 *
 * <p>A leading VOLATILE is the temporary keyword and not the volatility attribute a function may carry
 * after RETURNS — live-verified by the lifetime itself: a second session sees no {@code VOLATILE}
 * function, view or procedure created in the first.
 */
public class TemporaryViewAndRoutineTest extends BaseDatabaseTest {

    private void ok(final String sql) {
        engine.execute(sql);
    }

    private void refused(final String sql) {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, "Snowflake refuses this spelling: " + sql);
    }

    private String one(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void everyTemporaryViewSpellingIsAccepted() {
        ok("CREATE TEMPORARY VIEW tv1 AS SELECT 1 AS n");
        ok("CREATE TEMP VIEW tv2 AS SELECT 2 AS n");
        ok("CREATE VOLATILE VIEW tv3 AS SELECT 3 AS n");
        ok("CREATE LOCAL TEMPORARY VIEW tv4 AS SELECT 4 AS n");
        ok("CREATE GLOBAL TEMPORARY VIEW tv5 AS SELECT 5 AS n");
        ok("CREATE GLOBAL TEMP VIEW tv6 AS SELECT 6 AS n");
        ok("CREATE LOCAL VOLATILE VIEW tv7 AS SELECT 7 AS n");
        ok("CREATE SECURE TEMPORARY VIEW tv8 AS SELECT 8 AS n");
        ok("CREATE SECURE LOCAL TEMPORARY VIEW tv9 AS SELECT 9 AS n");
        assertEquals("1", one("SELECT n FROM tv1"));
        assertEquals("9", one("SELECT n FROM tv9"));
    }

    /** On a VIEW, SECURE comes BEFORE the temporary keyword — the other order is a syntax error. */
    @Test
    public void onAViewSecurePrecedesTheTemporaryKeyword() {
        refused("CREATE TEMPORARY SECURE VIEW bad1 AS SELECT 1 AS n");
        refused("CREATE TEMP SECURE VIEW bad2 AS SELECT 1 AS n");
    }

    @Test
    public void everyTemporaryFunctionSpellingIsAccepted() {
        ok("CREATE TEMPORARY FUNCTION tf1() RETURNS INTEGER AS '1'");
        ok("CREATE TEMP FUNCTION tf2() RETURNS INTEGER AS '2'");
        ok("CREATE VOLATILE FUNCTION tf3() RETURNS INTEGER AS '3'");
        ok("CREATE TEMPORARY SECURE FUNCTION tf4() RETURNS INTEGER AS '4'");
        ok("CREATE TEMP SECURE FUNCTION tf5() RETURNS INTEGER AS '5'");
        ok("CREATE VOLATILE SECURE FUNCTION tf6() RETURNS INTEGER AS '6'");
        ok("CREATE TEMPORARY FUNCTION tf7(x INTEGER) RETURNS INTEGER AS 'x * 2'");
        assertEquals("1", one("SELECT tf1()"));
        assertEquals("42", one("SELECT tf7(21)"));
    }

    /**
     * On a FUNCTION the temporary keyword comes FIRST and there is no LOCAL / GLOBAL prefix — the
     * exact opposite of the view rule, and all three orders below are syntax errors on live.
     */
    @Test
    public void onAFunctionTheTemporaryKeywordPrecedesSecure() {
        refused("CREATE SECURE TEMPORARY FUNCTION bad1() RETURNS INTEGER AS '1'");
        refused("CREATE LOCAL TEMPORARY FUNCTION bad2() RETURNS INTEGER AS '1'");
        refused("CREATE GLOBAL TEMPORARY FUNCTION bad3() RETURNS INTEGER AS '1'");
    }

    @Test
    public void everyTemporaryProcedureSpellingIsAccepted() {
        ok("CREATE TEMPORARY PROCEDURE tp1() RETURNS INTEGER LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
        ok("CREATE TEMP PROCEDURE tp2() RETURNS INTEGER LANGUAGE SQL AS 'BEGIN RETURN 2; END'");
        ok("CREATE VOLATILE PROCEDURE tp3() RETURNS INTEGER LANGUAGE SQL AS 'BEGIN RETURN 3; END'");
        refused("CREATE LOCAL TEMPORARY PROCEDURE bad() RETURNS INTEGER LANGUAGE SQL"
            + " AS 'BEGIN RETURN 1; END'");
        assertEquals("1", one("CALL tp1()"));
    }

    /** OR REPLACE, IF NOT EXISTS and the duplicate refusal behave as they do for a permanent view. */
    @Test
    public void temporaryViewsReplaceAndRefuseDuplicates() {
        ok("CREATE TEMPORARY VIEW rv AS SELECT 1 AS n");
        assertEquals("1", one("SELECT n FROM rv"));
        ok("CREATE OR REPLACE TEMPORARY VIEW rv AS SELECT 2 AS n");
        assertEquals("2", one("SELECT n FROM rv"));
        // IF NOT EXISTS over an existing name is a no-op, not an error — the first definition stands.
        ok("CREATE TEMPORARY VIEW IF NOT EXISTS rv AS SELECT 3 AS n");
        assertEquals("2", one("SELECT n FROM rv"));
        refused("CREATE TEMPORARY VIEW rv AS SELECT 4 AS n");
    }

    /**
     * The only place the temporary-ness of a view is visible is SHOW VIEWS' {@code text}, which is the
     * CREATE statement as typed — live-measured: there is no is_temporary column on SHOW VIEWS and
     * none in INFORMATION_SCHEMA.VIEWS either.
     */
    @Test
    public void showViewsRepeatsTheStatementAsTyped() {
        ok("CREATE TEMPORARY VIEW shown AS SELECT 1 AS n");
        final ResultSet rs = engine.executeQuery("SHOW VIEWS LIKE 'SHOWN'");
        assertEquals(1, rs.getRowCount());
        final int textColumn = rs.getColumnIndex("text");
        final String text = String.valueOf(rs.getRows().get(0).getValue(textColumn));
        assertTrue(text.toUpperCase().contains("TEMPORARY"),
            "SHOW VIEWS text should repeat the CREATE as typed, was: " + text);
    }

    /**
     * The lifetime, on Frostlake's own engine: a temporary view, function and procedure are gone when
     * the session that created them ends, while permanent ones remain.
     *
     * <p>This one drives a second engine to its shutdown, so it exercises the embedded engine even on
     * a live run — there the session ending IS the connection closing, which is how the rule was
     * measured in the first place and is not something a test can stage over JDBC.
     *
     * <p>What Frostlake does NOT model is the other half of live's behaviour: there a temporary object
     * is invisible to other sessions and SHADOWS a permanent object of the same name, with DROP
     * revealing the permanent one again. Frostlake keeps one namespace per catalog — the same
     * simplification it already makes for temporary TABLES — so it models the lifetime and not the
     * isolation.
     */
    @Test
    public void temporaryObjectsDoNotOutliveTheirSession() {
        final DatabaseEngine own = new DatabaseEngine();
        own.execute("CREATE DATABASE tmp_life_db");
        own.execute("USE DATABASE tmp_life_db");
        own.execute("CREATE SCHEMA s");
        own.execute("USE SCHEMA s");
        own.execute("CREATE TEMPORARY VIEW tv AS SELECT 1 AS n");
        own.execute("CREATE TEMPORARY FUNCTION tf() RETURNS INTEGER AS '1'");
        own.execute("CREATE TEMPORARY PROCEDURE tp() RETURNS INTEGER LANGUAGE SQL"
            + " AS 'BEGIN RETURN 1; END'");
        own.execute("CREATE VIEW pv AS SELECT 2 AS n");
        own.execute("CREATE FUNCTION pf() RETURNS INTEGER AS '2'");

        final Schema schema = own.getCatalog().getDatabase("TMP_LIFE_DB").getSchema("S");
        assertTrue(hasView(schema, "TV"), "the temporary view exists while the session lives");
        assertTrue(hasView(schema, "PV"));

        own.shutdown();

        assertFalse(hasView(schema, "TV"), "a temporary view must not outlive its session");
        assertEquals(0, temporaryRoutines(schema), "no temporary routine may outlive its session");
        assertTrue(hasView(schema, "PV"), "a permanent view is untouched");
        assertEquals(1, schema.getFunctions().size(), "the permanent function is untouched");
    }

    /** Scanned rather than fetched: {@code getView} raises the does-not-exist error, it does not answer null. */
    private boolean hasView(final Schema schema, final String name) {
        for (final View view : schema.getViews()) {
            if (name.equalsIgnoreCase(view.getName())) {
                return true;
            }
        }
        return false;
    }

    private int temporaryRoutines(final Schema schema) {
        int count = 0;
        for (final Function function : schema.getFunctions()) {
            if (function.isTemporary()) {
                count++;
            }
        }
        for (final Procedure procedure : schema.getProcedures()) {
            if (procedure.isTemporary()) {
                count++;
            }
        }
        return count;
    }
}
