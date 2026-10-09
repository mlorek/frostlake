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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A CREATE names the schema and the database its object goes into exactly: with a schema SS and no "ss",
 * {@code CREATE TABLE "ss".t} is refused as a missing schema, for every schema-level kind, where it used to land
 * in SS (live-verified kind by kind).
 */
public class CreateExactContainerTest extends BaseDatabaseTest {

    private static final String NO_SCHEMA =
        "SQL compilation error:\nSchema 'TEST_DB.\"ss\"' does not exist or not authorized.";
    private static final String NO_DATABASE =
        "SQL compilation error:\nDatabase '\"test_db\"' does not exist or not authorized.";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE SCHEMA ss");
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE OR REPLACE TABLE fz795 (a INT)");
    }

    private void refused(final String expectedStart, final String sql) {
        final String message = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
        assertTrue(message.startsWith(expectedStart), sql + " -> " + message);
    }

    @Test
    public void everySchemaLevelKindIsRefusedUnderASchemaOfAnotherCase() {
        refused(NO_SCHEMA, "CREATE TABLE \"ss\".t (a INT)");
        refused(NO_SCHEMA, "CREATE OR REPLACE TABLE \"ss\".orp (a INT)");
        refused(NO_SCHEMA, "CREATE TABLE IF NOT EXISTS \"ss\".ine (a INT)");
        refused(NO_SCHEMA, "CREATE TEMPORARY TABLE \"ss\".tmp (a INT)");
        refused(NO_SCHEMA, "CREATE TABLE \"ss\".ct AS SELECT 1 AS a");
        refused(NO_SCHEMA, "CREATE TABLE \"ss\".lk LIKE fz795");
        refused(NO_SCHEMA, "CREATE TABLE \"ss\".cl CLONE fz795");
        refused(NO_SCHEMA, "CREATE VIEW \"ss\".v AS SELECT 1 AS x");
        refused(NO_SCHEMA, "CREATE MATERIALIZED VIEW \"ss\".mv AS SELECT a FROM fz795");
        refused(NO_SCHEMA, "CREATE SEQUENCE \"ss\".q");
        refused(NO_SCHEMA, "CREATE FUNCTION \"ss\".f() RETURNS INT AS '1'");
        refused(NO_SCHEMA, "CREATE PROCEDURE \"ss\".p() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
        refused(NO_SCHEMA, "CREATE STAGE \"ss\".st");
        refused(NO_SCHEMA, "CREATE FILE FORMAT \"ss\".ff TYPE = CSV");
        refused(NO_SCHEMA, "CREATE STREAM \"ss\".sm ON TABLE fz795");
        refused(NO_SCHEMA, "CREATE STREAM \"ss\".sm2 ON TABLE nosuch795");
        refused(NO_SCHEMA, "CREATE TASK \"ss\".tk AS SELECT 1");
        refused(NO_SCHEMA, "CREATE TAG \"ss\".tg");
        refused(NO_SCHEMA, "CREATE MASKING POLICY \"ss\".mp AS (v VARCHAR) RETURNS VARCHAR -> v");
        refused(NO_SCHEMA, "CREATE ROW ACCESS POLICY \"ss\".rp AS (v INT) RETURNS BOOLEAN -> TRUE");
        refused(NO_SCHEMA, "CREATE PIPE \"ss\".pi AS COPY INTO fz795 FROM @~/x");
        assertTrue(!engine.executeQuery("SHOW TABLES IN SCHEMA ss").next(), "nothing was created in SS");
    }

    @Test
    public void aDatabaseOfAnotherCaseIsRefusedTheSameWay() {
        refused(NO_DATABASE, "CREATE TABLE \"test_db\".public.t2 (a INT)");
        refused(NO_DATABASE, "CREATE VIEW \"test_db\".public.v2 AS SELECT 1 AS x");
        refused(NO_DATABASE, "CREATE SCHEMA \"test_db\".s9");
        refused(NO_DATABASE, "CREATE FUNCTION \"test_db\".public.f2() RETURNS INT AS '1'");
        refused(NO_DATABASE, "CREATE TABLE \"test_db\".\"ss\".t3 (a INT)");
    }

    @Test
    public void theExactNameIsTaken() {
        final ResultSet upper = engine.executeQuery("CREATE TABLE \"SS\".t4 (a INT)");
        assertTrue(upper.next());
        assertEquals("Table T4 successfully created.", upper.getValue(0));
        engine.execute("CREATE OR REPLACE SCHEMA \"ss\"");
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE TABLE \"ss\".t5 (a INT)");
        final ResultSet lower = engine.executeQuery("SHOW TABLES IN SCHEMA \"ss\"");
        assertTrue(lower.next());
        assertEquals("T5", lower.getValue("name"));
        assertEquals("ss", lower.getValue("schema_name"));
        final ResultSet held = engine.executeQuery("SHOW TABLES IN SCHEMA ss");
        assertTrue(held.next());
        assertEquals("T4", held.getValue("name"));
        engine.execute("DROP SCHEMA \"ss\"");
    }

    @Test
    public void aTableReplacesNoTableOfAnotherCase() {
        engine.execute("CREATE OR REPLACE TABLE \"cv\" (a VARCHAR)");
        engine.execute("INSERT INTO \"cv\" VALUES ('lower')");
        engine.execute("CREATE OR REPLACE TABLE CV (a VARCHAR)");
        final ResultSet kept = engine.executeQuery("SELECT a FROM \"cv\"");
        assertTrue(kept.next());
        assertEquals("lower", kept.getValue(0));
        assertTrue(!engine.executeQuery("SELECT a FROM CV").next());
    }
}
