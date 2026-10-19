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
package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An unqualified name the current schema does not hold is looked for along the session's SEARCH_PATH,
 * {@code $current, $public} by default (live-verified). A query, a DML statement, a sequence's NEXTVAL, a
 * function call, a CALL, DESCRIBE TABLE and VIEW, GET_DDL, SHOW COLUMNS, a GRANT on a table and the source of a
 * LIKE, a CLONE and a stream read the path; a statement that changes or drops the object, the describing of
 * anything but a table or a view, and the body of a view look in the current schema alone.
 */
public class SearchPathResolutionTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE test_db.public.sp_pt (a INT)");
        engine.execute("CREATE SEQUENCE test_db.public.sp_ps");
        engine.execute("CREATE FUNCTION test_db.public.sp_pf() RETURNS INT AS $$ 7 $$");
        engine.execute("CREATE VIEW test_db.public.sp_pv AS SELECT 1 AS x");
        engine.execute("CREATE PROCEDURE test_db.public.sp_pp() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 9; END; $$");
    }

    private String value(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                final ResultSet rs = engine.executeQuery(sql);
                while (rs.next()) {
                    continue;
                }
            }
        }, sql).getMessage();
    }

    private void assertMissing(final String kind, final String name, final String sql) {
        final String message = refusal(sql);
        assertTrue(message.startsWith("SQL compilation error:\n" + kind + " '" + name
            + "' does not exist or not authorized."), sql + " ==> " + message);
    }

    @Test
    public void queriesDmlAndCallsFindWhatPublicHolds() {
        assertEquals("0", value("SELECT COUNT(*) FROM sp_pt"));
        engine.execute("INSERT INTO sp_pt VALUES (1)");
        engine.execute("UPDATE sp_pt SET a = 2");
        engine.execute("DELETE FROM sp_pt WHERE a = 0");
        engine.execute("MERGE INTO sp_pt t USING (SELECT 5 AS a) s ON t.a = s.a WHEN NOT MATCHED THEN INSERT (a) VALUES (s.a)");
        assertEquals("2", value("SELECT COUNT(*) FROM sp_pt"));
        assertEquals("1", value("SELECT x FROM sp_pv"));
        assertEquals("true", value("SELECT sp_ps.nextval > 0"));
        assertEquals("7", value("SELECT sp_pf()"));
        assertEquals("9", value("CALL sp_pp()"));
        assertEquals("[\"TEST_DB.TEST_SCHEMA\", \"TEST_DB.PUBLIC\"]", value("SELECT CURRENT_SCHEMAS()"));
    }

    @Test
    public void describingAndCopyingReadThePathToo() {
        assertEquals("A", value("DESCRIBE TABLE sp_pt"));
        assertEquals("X", value("DESCRIBE VIEW sp_pv"));
        assertTrue(value("SELECT GET_DDL('TABLE', 'sp_pt')").startsWith("create or replace TABLE SP_PT ("));
        final ResultSet columns = engine.executeQuery("SHOW COLUMNS IN TABLE sp_pt");
        assertEquals("PUBLIC", String.valueOf(columns.getRows().get(0).getValue(columns.getColumnIndex("schema_name"))));
        engine.execute("GRANT SELECT ON TABLE sp_pt TO ROLE PUBLIC");
        engine.execute("CREATE TABLE sp_like LIKE sp_pt");
        engine.execute("CREATE TABLE sp_clone CLONE sp_pt");
        engine.execute("CREATE STREAM sp_stream ON TABLE sp_pt");
        assertEquals("0", value("SELECT COUNT(*) FROM sp_like"));
    }

    @Test
    public void changingAndDroppingLookInTheCurrentSchemaAlone() {
        assertMissing("Table", "TEST_DB.TEST_SCHEMA.SP_PT", "TRUNCATE TABLE sp_pt");
        assertMissing("Table", "TEST_DB.TEST_SCHEMA.SP_PT", "ALTER TABLE sp_pt ADD COLUMN b INT");
        assertMissing("Table", "TEST_DB.TEST_SCHEMA.SP_PT", "COMMENT ON TABLE sp_pt IS 'c'");
        assertMissing("Table", "TEST_DB.TEST_SCHEMA.SP_PT", "DROP TABLE sp_pt");
        assertMissing("View", "TEST_DB.TEST_SCHEMA.SP_PV", "DROP VIEW sp_pv");
        assertMissing("Function", "TEST_DB.TEST_SCHEMA.SP_PF", "DROP FUNCTION sp_pf()");
        assertMissing("Function", "TEST_DB.TEST_SCHEMA.SP_PF", "DESCRIBE FUNCTION sp_pf()");
        assertMissing("Procedure", "TEST_DB.TEST_SCHEMA.SP_PP", "DESCRIBE PROCEDURE sp_pp()");
        assertMissing("Sequence", "TEST_DB.TEST_SCHEMA.SP_PS", "DESCRIBE SEQUENCE sp_ps");
        assertMissing("Sequence", "TEST_DB.TEST_SCHEMA.SP_PS", "ALTER SEQUENCE sp_ps SET INCREMENT = 2");
        assertMissing("Object", "TEST_DB.TEST_SCHEMA.SP_PT", "CREATE VIEW sp_over AS SELECT a FROM sp_pt");
    }

    @Test
    public void theCurrentSchemaWinsAndAMissIsNamedAsWritten() {
        engine.execute("CREATE TABLE sp_pt (a INT, b INT)");
        engine.execute("INSERT INTO sp_pt VALUES (1, 2)");
        assertEquals("1", value("SELECT COUNT(*) FROM sp_pt"));
        assertEquals("0", value("SELECT COUNT(*) FROM test_db.public.sp_pt"));
        engine.execute("DROP TABLE sp_pt");
        assertEquals("0", value("SELECT COUNT(*) FROM sp_pt"));
        assertMissing("Object", "NOSUCH_ANYWHERE", "SELECT * FROM nosuch_anywhere");
        assertEquals("SQL compilation error:\nUnknown function NOSUCH_FN_ANYWHERE.", refusal("SELECT nosuch_fn_anywhere()"));
    }

    @Test
    public void thePathIsTheSessionsToSet() {
        try {
            engine.execute("ALTER SESSION SET SEARCH_PATH = '$current'");
            assertMissing("Object", "SP_PT", "SELECT COUNT(*) FROM sp_pt");
            assertEquals("[\"TEST_DB.TEST_SCHEMA\"]", value("SELECT CURRENT_SCHEMAS()"));
            engine.execute("ALTER SESSION SET SEARCH_PATH = '$public, $current'");
            assertEquals("[\"TEST_DB.PUBLIC\", \"TEST_DB.TEST_SCHEMA\"]", value("SELECT CURRENT_SCHEMAS()"));
            assertEquals("0", value("SELECT COUNT(*) FROM sp_pt"));
            assertEquals("SQL compilation error:\nSearch path cannot be empty.",
                refusal("ALTER SESSION SET SEARCH_PATH = ''"));
        } finally {
            engine.execute("ALTER SESSION UNSET SEARCH_PATH");
        }
    }
}
