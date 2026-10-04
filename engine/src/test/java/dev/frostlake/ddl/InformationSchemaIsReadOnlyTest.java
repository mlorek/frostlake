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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * INFORMATION_SCHEMA is read-only. Only CREATE TABLE used to be refused in it: a view, a sequence, a function, a
 * stage, a tag, a policy, a stream or a task was created there and could not be dropped again, the schema took
 * ALTER SCHEMA ... SET COMMENT, and an ALTER TABLE ... RENAME into it refused only after the table had left its own
 * schema, losing it.
 */
public class InformationSchemaIsReadOnlyTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE p");
        engine.execute("USE SCHEMA p.PUBLIC");
        engine.execute("CREATE TABLE src (a INT)");
        engine.execute("INSERT INTO src VALUES (1), (2)");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private String refusal(final String sql) {
        try {
            engine.execute(sql);
            return "not refused";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage());
        }
    }

    private String scalar(final String sql) {
        final List<Row> rows = engine.executeQuery(sql).getRows();
        return rows.isEmpty() ? "no row" : String.valueOf(rows.get(0).getValue(0));
    }

    @Test
    public void nothingIsCreatedInIt() {
        engine.execute("USE SCHEMA p.INFORMATION_SCHEMA");
        assertEquals("Cannot create tables in INFORMATION_SCHEMA", refusal("CREATE TABLE t (a INT)"));
        assertEquals("Cannot create views in INFORMATION_SCHEMA", refusal("CREATE VIEW v AS SELECT 1 x"));
        assertEquals("Cannot create sequences in INFORMATION_SCHEMA", refusal("CREATE SEQUENCE sq"));
        assertEquals("Cannot create functions in INFORMATION_SCHEMA", refusal("CREATE FUNCTION f() RETURNS INT AS '1'"));
        assertEquals("Cannot create procedures in INFORMATION_SCHEMA",
            refusal("CREATE PROCEDURE pr() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END $$"));
        assertEquals("Cannot create stages in INFORMATION_SCHEMA", refusal("CREATE STAGE stg"));
        assertEquals("Cannot create tags in INFORMATION_SCHEMA", refusal("CREATE TAG tg"));
        assertEquals("Cannot create masking policies in INFORMATION_SCHEMA",
            refusal("CREATE MASKING POLICY mp AS (v VARCHAR) RETURNS VARCHAR -> v"));
        assertEquals("Cannot create row access policies in INFORMATION_SCHEMA",
            refusal("CREATE ROW ACCESS POLICY rap AS (v VARCHAR) RETURNS BOOLEAN -> TRUE"));
        assertEquals("Cannot create file formats in INFORMATION_SCHEMA", refusal("CREATE FILE FORMAT ff TYPE = CSV"));
        assertEquals("Cannot create streams in INFORMATION_SCHEMA", refusal("CREATE STREAM st ON TABLE p.PUBLIC.src"));
        assertEquals("Cannot create tasks in INFORMATION_SCHEMA", refusal("CREATE TASK tk AS SELECT 1"));
        assertEquals("Cannot create secrets in INFORMATION_SCHEMA",
            refusal("CREATE SECRET sec TYPE = PASSWORD USERNAME = 'a' PASSWORD = 'b'"));
        assertEquals("Cannot create notebooks in INFORMATION_SCHEMA", refusal("CREATE NOTEBOOK nb"));
        assertEquals("Cannot create materialized views in INFORMATION_SCHEMA",
            refusal("CREATE MATERIALIZED VIEW mv AS SELECT a FROM p.PUBLIC.src"));
        assertEquals("0", scalar("SELECT COUNT(*) FROM p.INFORMATION_SCHEMA.VIEWS WHERE TABLE_SCHEMA = 'INFORMATION_SCHEMA'"
            + " AND TABLE_NAME IN ('V', 'MV')"));
    }

    @Test
    public void aSystemViewIsNotReplaced() {
        assertEquals("Cannot create views in INFORMATION_SCHEMA",
            refusal("CREATE OR REPLACE VIEW p.INFORMATION_SCHEMA.TABLES AS SELECT 1 x"));
        assertEquals("1", scalar("SELECT COUNT(*) FROM p.INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'SRC'"));
    }

    @Test
    public void nothingMovesIntoItAndWhatWouldHaveStaysWhereItWas() {
        engine.execute("CREATE VIEW v AS SELECT 1 x");
        engine.execute("CREATE SEQUENCE sq");
        engine.execute("CREATE FUNCTION f() RETURNS INT AS '1'");
        engine.execute("CREATE TAG tg");
        engine.execute("CREATE MASKING POLICY mp AS (v VARCHAR) RETURNS VARCHAR -> v");
        engine.execute("CREATE FILE FORMAT ff TYPE = CSV");
        assertEquals("Cannot create tables in INFORMATION_SCHEMA",
            refusal("ALTER TABLE src RENAME TO p.INFORMATION_SCHEMA.src"));
        assertEquals("Cannot create views in INFORMATION_SCHEMA", refusal("ALTER VIEW v RENAME TO p.INFORMATION_SCHEMA.v"));
        assertEquals("Cannot create sequences in INFORMATION_SCHEMA",
            refusal("ALTER SEQUENCE sq RENAME TO p.INFORMATION_SCHEMA.sq"));
        assertEquals("Cannot create functions in INFORMATION_SCHEMA",
            refusal("ALTER FUNCTION f() RENAME TO p.INFORMATION_SCHEMA.f"));
        assertEquals("Cannot create tags in INFORMATION_SCHEMA", refusal("ALTER TAG tg RENAME TO p.INFORMATION_SCHEMA.tg"));
        assertEquals("Cannot create masking policies in INFORMATION_SCHEMA",
            refusal("ALTER MASKING POLICY mp RENAME TO p.INFORMATION_SCHEMA.mp"));
        assertEquals("Cannot create file formats in INFORMATION_SCHEMA",
            refusal("ALTER FILE FORMAT ff RENAME TO p.INFORMATION_SCHEMA.ff"));
        // The table is still where it was, rows and all.
        assertEquals("2", scalar("SELECT COUNT(*) FROM p.PUBLIC.src"));
        assertEquals("1", scalar("SELECT x FROM p.PUBLIC.v"));
        assertEquals("1", scalar("SELECT p.PUBLIC.f()"));
        assertEquals("SQ", scalar("SELECT SEQUENCE_NAME FROM p.INFORMATION_SCHEMA.SEQUENCES WHERE SEQUENCE_SCHEMA = 'PUBLIC'"));
    }

    @Test
    public void theSchemaTakesNoAlter() {
        final String refused = "Insufficient privileges to operate on schema 'INFORMATION_SCHEMA'";
        assertTrue(refusal("ALTER SCHEMA p.INFORMATION_SCHEMA SET COMMENT = 'hacked'").contains(refused));
        assertTrue(refusal("ALTER SCHEMA p.INFORMATION_SCHEMA SET DATA_RETENTION_TIME_IN_DAYS = 3").contains(refused));
        assertTrue(refusal("ALTER SCHEMA p.INFORMATION_SCHEMA UNSET COMMENT").contains(refused));
        assertTrue(refusal("ALTER SCHEMA p.INFORMATION_SCHEMA ENABLE MANAGED ACCESS").contains(refused));
        assertTrue(refusal("ALTER SCHEMA p.INFORMATION_SCHEMA RENAME TO x").contains(refused));
        assertTrue(refusal("COMMENT ON SCHEMA p.INFORMATION_SCHEMA IS 'hacked'").contains(refused));
        engine.execute("CREATE TAG p.PUBLIC.tg");
        assertTrue(refusal("ALTER SCHEMA p.INFORMATION_SCHEMA SET TAG p.PUBLIC.tg = 'x'").contains(refused));
        assertEquals("Views describing the contents of schemas in this database",
            scalar("SELECT COMMENT FROM p.INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = 'INFORMATION_SCHEMA'"));
        assertEquals("NO", scalar("SELECT IS_MANAGED_ACCESS FROM p.INFORMATION_SCHEMA.SCHEMATA"
            + " WHERE SCHEMA_NAME = 'INFORMATION_SCHEMA'"));
    }

    @Test
    public void itsViewsTakeNoAlterOrComment() {
        final String refused = "Insufficient privileges to operate on view 'TABLES'";
        assertTrue(refusal("ALTER VIEW p.INFORMATION_SCHEMA.TABLES SET COMMENT = 'c'").contains(refused));
        assertTrue(refusal("COMMENT ON VIEW p.INFORMATION_SCHEMA.TABLES IS 'c'").contains(refused));
        assertTrue(refusal("ALTER VIEW p.INFORMATION_SCHEMA.TABLES RENAME TO p.PUBLIC.t2").contains(refused));
        assertEquals("The tables defined in this database that are accessible to the current user's role.",
            scalar("SELECT COMMENT FROM p.INFORMATION_SCHEMA.VIEWS WHERE TABLE_SCHEMA = 'INFORMATION_SCHEMA'"
                + " AND TABLE_NAME = 'TABLES'"));
    }
}
