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
package dev.frostlake.udf;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the names a SQL function's body writes without a schema are looked for (live-verified): at CREATE where
 * the SESSION is, whatever schema the function is created in, and at a call where the FUNCTION lives, whatever
 * schema the caller is in — a function then along the search path, so PUBLIC is reached from both, and a table in
 * that one schema alone. CURRENT_SCHEMA() inside the body is the function's. A procedure follows its rights: the
 * owner's look where the procedure lives, the caller's where the caller is.
 */
public class UdfBodyResolutionTest extends BaseDatabaseTest {

    private static final String HOME = "USE SCHEMA test_db.test_schema";
    private static final String OTHER = "USE SCHEMA test_db.ubr_o";

    @Override
    protected void setupTest() {
        engine.execute("CREATE SCHEMA ubr_o");
        engine.execute("CREATE FUNCTION test_db.ubr_o.ubr_f() RETURNS INT AS '1'");
        engine.execute(HOME);
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

    @Test
    public void aBodyCompilesWhereTheSessionIsAndRunsWhereTheFunctionLives() {
        try {
            engine.execute(OTHER);
            engine.execute("CREATE FUNCTION test_db.test_schema.ubr_v16() RETURNS INT AS 'ubr_f()'");
            assertEquals("SQL compilation error:\nUnknown function UBR_F.", refusal("SELECT test_db.test_schema.ubr_v16()"));
            engine.execute(HOME);
            assertEquals("SQL compilation error:\nUnknown function UBR_F.",
                refusal("CREATE FUNCTION test_db.test_schema.ubr_v17() RETURNS INT AS 'ubr_f()'"));
            assertEquals("SQL compilation error:\nUnknown function UBR_F.", refusal("SELECT ubr_v16()"));
            engine.execute("CREATE FUNCTION test_db.test_schema.ubr_f() RETURNS INT AS '2'");
            assertEquals("2", value("SELECT ubr_v16()"));
            engine.execute(OTHER);
            assertEquals("2", value("SELECT test_db.test_schema.ubr_v16()"));
        } finally {
            engine.execute(HOME);
        }
    }

    @Test
    public void publicIsReachedAtCreateAndAtACall() {
        try {
            engine.execute("CREATE FUNCTION test_db.public.ubr_p() RETURNS INT AS '7'");
            engine.execute(OTHER);
            engine.execute("CREATE FUNCTION test_db.test_schema.ubr_v18() RETURNS INT AS 'ubr_p()'");
            assertEquals("7", value("SELECT test_db.test_schema.ubr_v18()"));
            engine.execute(HOME);
            assertEquals("7", value("SELECT ubr_v18()"));
        } finally {
            engine.execute(HOME);
        }
    }

    @Test
    public void aTableInABodyIsLookedForTheSameWay() {
        try {
            engine.execute("CREATE TABLE test_db.ubr_o.ubr_t (a INT)");
            engine.execute(OTHER);
            engine.execute("CREATE FUNCTION test_db.test_schema.ubr_v19() RETURNS INT AS 'SELECT MAX(a) FROM ubr_t'");
            final String atCall = refusal("SELECT test_db.test_schema.ubr_v19()");
            assertTrue(atCall.startsWith("SQL compilation error:\nObject 'TEST_DB.TEST_SCHEMA.UBR_T' does not exist"
                + " or not authorized."), atCall);
            engine.execute("CREATE FUNCTION test_db.test_schema.ubr_tf() RETURNS TABLE (a INT) AS 'SELECT a FROM ubr_t'");
            final String read = refusal("SELECT * FROM TABLE(test_db.test_schema.ubr_tf())");
            assertTrue(read.startsWith("SQL compilation error:\nObject 'TEST_DB.TEST_SCHEMA.UBR_T' does not exist"
                + " or not authorized."), read);
            engine.execute(HOME);
            final String atCreate =
                refusal("CREATE FUNCTION test_db.test_schema.ubr_v20() RETURNS INT AS 'SELECT MAX(a) FROM ubr_t'");
            assertTrue(atCreate.startsWith("SQL compilation error:\nObject 'TEST_DB.TEST_SCHEMA.UBR_T' does not exist"
                + " or not authorized."), atCreate);
        } finally {
            engine.execute(HOME);
        }
    }

    @Test
    public void currentSchemaInsideABodyIsTheFunctions() {
        engine.execute("CREATE FUNCTION test_db.ubr_o.ubr_cs() RETURNS VARCHAR AS 'CURRENT_SCHEMA()'");
        assertEquals("UBR_O", value("SELECT test_db.ubr_o.ubr_cs()"));
        assertEquals("TEST_SCHEMA", value("SELECT CURRENT_SCHEMA()"));
    }

    @Test
    public void aCallNamedThroughIdentifierIsJudgedAtCreate() {
        assertEquals("SQL compilation error:\nUnknown function NOSUCHFUNC.",
            refusal("CREATE FUNCTION ubr_i() RETURNS INT AS 'IDENTIFIER(''nosuchfunc'')(1)'"));
        engine.execute("CREATE FUNCTION test_db.test_schema.ubr_f() RETURNS INT AS '2'");
        engine.execute("CREATE FUNCTION ubr_i2() RETURNS INT AS 'IDENTIFIER(''ubr_f'')()'");
        assertEquals("2", value("SELECT ubr_i2()"));
    }

    @Test
    public void aProcedureLooksWhereItsRightsPutIt() {
        try {
            engine.execute("CREATE FUNCTION test_db.test_schema.ubr_f() RETURNS INT AS '2'");
            engine.execute(OTHER);
            engine.execute("CREATE PROCEDURE test_db.test_schema.ubr_owner() RETURNS INT LANGUAGE SQL AS"
                + " 'BEGIN RETURN ubr_f(); END'");
            engine.execute("CREATE PROCEDURE test_db.test_schema.ubr_caller() RETURNS INT LANGUAGE SQL"
                + " EXECUTE AS CALLER AS 'BEGIN RETURN ubr_f(); END'");
            assertEquals("2", value("CALL test_db.test_schema.ubr_owner()"));
            assertEquals("1", value("CALL test_db.test_schema.ubr_caller()"));
        } finally {
            engine.execute(HOME);
        }
    }
}
