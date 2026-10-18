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

package dev.frostlake.session;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A session whose current schema is renamed away has a current database and no current schema (live-verified):
 * CURRENT_SCHEMA() is NULL, a bare name read is missed as an object not there — {@code Table 'MC' does not exist or not
 * authorized.} — while a statement that makes, drops or changes one is {@code Cannot perform CREATE TABLE. This
 * session does not have a current schema. Call 'USE SCHEMA', or use a qualified name.}, and SHOW lists the database. A
 * USE SCHEMA gives it one again, and dropping a schema that is current falls back to PUBLIC.
 */
public class RenamedCurrentSchemaTest extends MeasuredCellsTestSupport {

    @Test
    public void aCurrentSchemaRenamedAwayIsNoCurrentSchema() {
        engine.execute("CREATE OR REPLACE SCHEMA fl_sb");
        engine.execute("CREATE OR REPLACE TABLE mc (k NUMBER)");
        engine.execute("INSERT INTO mc VALUES (1)");
        engine.execute("ALTER SCHEMA fl_sb RENAME TO fl_sc");
        assertCells(new String[][] {
            {"SHOW IMPORTED KEYS IN TABLE mc",
                "REFUSED SQL compilation error: | Table 'MC' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE MC."},
            {"SELECT * FROM mc",
                "REFUSED SQL compilation error: | Object 'MC' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE MC."},
            {"DESCRIBE TABLE mc",
                "REFUSED SQL compilation error: | Table 'MC' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE MC."},
            {"SHOW COLUMNS IN TABLE mc",
                "REFUSED SQL compilation error: | Table 'MC' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE MC."},
            {"SHOW PRIMARY KEYS IN TABLE mc",
                "REFUSED SQL compilation error: | Table 'MC' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE MC."},
            {"INSERT INTO mc VALUES (2)",
                "REFUSED SQL compilation error: | Table 'MC' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE MC."},
            {"SELECT CURRENT_SCHEMA()", "null"},
            {"CREATE TABLE mc2 (k INT)",
                "REFUSED Cannot perform CREATE TABLE. This session does not have a current schema. Call 'USE "
                + "SCHEMA', or use a qualified name."},
            {"SELECT GET_DDL('TABLE', 'mc')",
                "REFUSED SQL compilation error: | Table 'MC' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE MC."},
            {"SELECT COUNT(*) FROM fl_sc.mc", "1"},
            {"DROP TABLE mc",
                "REFUSED Cannot perform DROP. This session does not have a current schema. Call 'USE SCHEMA', or "
                + "use a qualified name."},
            {"SHOW EXPORTED KEYS IN TABLE mc",
                "REFUSED SQL compilation error: | Table 'MC' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE MC."},
            {"SHOW UNIQUE KEYS IN TABLE mc",
                "REFUSED SQL compilation error: | Table 'MC' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE MC."},
            {"TRUNCATE TABLE mc",
                "REFUSED Cannot perform TRUNCATE. This session does not have a current schema. Call 'USE SCHEMA', "
                + "or use a qualified name."},
            {"CREATE VIEW vmc AS SELECT 1 AS a",
                "REFUSED Cannot perform CREATE VIEW. This session does not have a current schema. Call 'USE "
                + "SCHEMA', or use a qualified name."},
            {"SELECT * FROM nosuch_mc",
                "REFUSED SQL compilation error: | Object 'NOSUCH_MC' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NOSUCH_MC."},
        });
    }

    @Test
    public void aWayBackAndADroppedSchema() {
        engine.execute("CREATE OR REPLACE SCHEMA fl_sb");
        engine.execute("CREATE OR REPLACE TABLE mc (k NUMBER)");
        engine.execute("ALTER SCHEMA fl_sb RENAME TO fl_sc");
        assertCells(new String[][] {
            {"SELECT CURRENT_SCHEMA()", "null"},
            {"CREATE SEQUENCE sq_cr",
                "REFUSED Cannot perform CREATE SEQUENCE. This session does not have a current schema. Call 'USE "
                + "SCHEMA', or use a qualified name."},
            {"CREATE OR REPLACE TABLE mc3 (k INT)",
                "REFUSED Cannot perform CREATE TABLE. This session does not have a current schema. Call 'USE "
                + "SCHEMA', or use a qualified name."},
            {"ALTER TABLE mc ADD COLUMN j INT",
                "REFUSED Cannot perform ALTER. This session does not have a current schema. Call 'USE SCHEMA', or "
                + "use a qualified name."},
            {"DESCRIBE VIEW mc",
                "REFUSED SQL compilation error: | View 'MC' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE MC."},
            {"SHOW VIEWS LIKE 'NOSUCH%'", ""},
            {"UNDROP TABLE mc",
                "REFUSED Cannot perform CREATE TABLE. This session does not have a current schema. Call 'USE "
                + "SCHEMA', or use a qualified name."},
            {"USE SCHEMA fl_sc", "Statement executed successfully."},
            {"SELECT CURRENT_SCHEMA()", "FL_SC"},
            {"SELECT COUNT(*) FROM mc", "0"},
            {"CREATE OR REPLACE SCHEMA fl_sd", "Schema FL_SD successfully created."},
            {"DROP SCHEMA fl_sd", "FL_SD successfully dropped."},
            {"SELECT CURRENT_SCHEMA()", "PUBLIC"},
            {"ALTER SCHEMA fl_sc RENAME TO fl_se", "Statement executed successfully."},
            {"SELECT CURRENT_SCHEMA()", "PUBLIC"},
            {"SELECT * FROM mc",
                "REFUSED SQL compilation error: | Object 'MC' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE MC."},
        });
    }
}
