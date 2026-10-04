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

package dev.frostlake.features;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * LAST_ALTERED beyond tables, views, schemas and databases. A DML that changes no row still moves a table's (the
 * documentation: the column "is updated even when no rows are affected by the DML statement"), and the FUNCTIONS,
 * PROCEDURES, SEQUENCES, FILE_FORMATS, PIPES and STAGES views and the constraint views report the object's last DDL
 * rather than its creation. A constraint dates from the ALTER TABLE that added it, where one the CREATE TABLE
 * declared dates from the table.
 */
public class LastAlteredFollowsEveryObjectTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE la");
        engine.execute("USE SCHEMA la.PUBLIC");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private List<Object> row(final String sql) {
        final Row row = engine.executeQuery(sql).getRows().get(0);
        return row.getValues();
    }

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    /** Whether the statement moves the table's LAST_ALTERED and leaves its LAST_DDL. */
    private List<Object> afterDml(final String dml) {
        engine.execute("SET t = CURRENT_TIMESTAMP()");
        engine.execute(dml);
        return row("SELECT last_altered > $t, last_ddl <= $t FROM information_schema.tables WHERE table_name = 'X'");
    }

    @Test
    public void aDmlThatChangesNoRowStillMovesLastAltered() {
        engine.execute("CREATE TABLE x (a INT)");
        engine.execute("INSERT INTO x VALUES (1)");
        final Object token = scalar("SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME('x')");

        assertEquals(List.of(true, true), afterDml("UPDATE x SET a = 2 WHERE a = 99"), "UPDATE matching nothing");
        assertEquals(List.of(true, true), afterDml("DELETE FROM x WHERE a = 99"), "DELETE matching nothing");
        assertEquals(List.of(true, true), afterDml("INSERT INTO x SELECT 1 WHERE FALSE"), "INSERT of no rows");
        assertEquals(List.of(true, true),
            afterDml("MERGE INTO x USING (SELECT 1 b WHERE FALSE) s ON x.a = s.b WHEN MATCHED THEN DELETE"),
            "MERGE firing no branch");
        assertEquals(token, scalar("SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME('x')"),
            "the rows did not change, so neither does the change token");
    }

    @Test
    public void aMultiTableInsertMovesEveryTableItNames() {
        engine.execute("CREATE TABLE x (a INT)");
        engine.execute("CREATE TABLE y (a INT)");
        engine.execute("SET t = CURRENT_TIMESTAMP()");
        engine.execute("INSERT ALL WHEN a > 5 THEN INTO x VALUES (a) ELSE INTO y VALUES (a) SELECT 1 a");
        assertEquals(List.of(true, true), row("SELECT"
            + " (SELECT last_altered > $t FROM information_schema.tables WHERE table_name = 'X'),"
            + " (SELECT last_altered > $t FROM information_schema.tables WHERE table_name = 'Y')"),
            "X took no row and still moves, as Y does");
    }

    @Test
    public void routinesSequencesFileFormatsPipesAndStagesMoveWithTheirDdl() {
        engine.execute("CREATE TABLE x (a INT)");
        engine.execute("CREATE FUNCTION f1() RETURNS INT AS '1'");
        engine.execute("CREATE PROCEDURE p1() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
        engine.execute("CREATE SEQUENCE sq");
        engine.execute("CREATE FILE FORMAT ff TYPE = CSV");
        engine.execute("CREATE STAGE stg");
        engine.execute("CREATE PIPE pp AS COPY INTO x FROM @stg");
        assertEquals(List.of(true, true, true, true, true, true), row("SELECT"
            + " (SELECT last_altered = created FROM information_schema.functions WHERE function_name = 'F1'),"
            + " (SELECT last_altered = created FROM information_schema.procedures WHERE procedure_name = 'P1'),"
            + " (SELECT last_altered = created FROM information_schema.sequences WHERE sequence_name = 'SQ'),"
            + " (SELECT last_altered = created FROM information_schema.file_formats WHERE file_format_name = 'FF'),"
            + " (SELECT last_altered = created FROM information_schema.pipes WHERE pipe_name = 'PP'),"
            + " (SELECT last_altered = created FROM information_schema.stages WHERE stage_name = 'STG')"),
            "never altered: LAST_ALTERED is the creation");

        engine.execute("SET t = CURRENT_TIMESTAMP()");
        engine.execute("ALTER FUNCTION f1() SET COMMENT = 'f'");
        engine.execute("ALTER PROCEDURE p1() SET COMMENT = 'p'");
        engine.execute("ALTER SEQUENCE sq SET INCREMENT = 5");
        engine.execute("ALTER FILE FORMAT ff SET SKIP_HEADER = 1");
        engine.execute("ALTER PIPE pp SET COMMENT = 'pp'");
        engine.execute("ALTER STAGE stg SET COMMENT = 'stg'");
        assertEquals(List.of(true, true, true, true, true, true), row("SELECT"
            + " (SELECT last_altered > $t AND created <= $t FROM information_schema.functions"
            + " WHERE function_name = 'F1'),"
            + " (SELECT last_altered > $t AND created <= $t FROM information_schema.procedures"
            + " WHERE procedure_name = 'P1'),"
            + " (SELECT last_altered > $t AND created <= $t FROM information_schema.sequences"
            + " WHERE sequence_name = 'SQ'),"
            + " (SELECT last_altered > $t AND created <= $t FROM information_schema.file_formats"
            + " WHERE file_format_name = 'FF'),"
            + " (SELECT last_altered > $t AND created <= $t FROM information_schema.pipes WHERE pipe_name = 'PP'),"
            + " (SELECT last_altered > $t AND created <= $t FROM information_schema.stages WHERE stage_name = 'STG')"),
            "an ALTER moves LAST_ALTERED and keeps CREATED");

        engine.execute("SET t = CURRENT_TIMESTAMP()");
        engine.execute("COMMENT ON FUNCTION f1() IS 'f2'");
        engine.execute("COMMENT ON SEQUENCE sq IS 'sq'");
        engine.execute("COMMENT ON FILE FORMAT ff IS 'ff'");
        engine.execute("COMMENT ON PIPE pp IS 'pp2'");
        assertEquals(List.of(true, true, true, true), row("SELECT"
            + " (SELECT last_altered > $t FROM information_schema.functions WHERE function_name = 'F1'),"
            + " (SELECT last_altered > $t FROM information_schema.sequences WHERE sequence_name = 'SQ'),"
            + " (SELECT last_altered > $t FROM information_schema.file_formats WHERE file_format_name = 'FF'),"
            + " (SELECT last_altered > $t FROM information_schema.pipes WHERE pipe_name = 'PP')"),
            "a comment is DDL too");

        engine.execute("SET t = CURRENT_TIMESTAMP()");
        engine.execute("ALTER FUNCTION f1() RENAME TO f2");
        assertEquals(List.of(true, true), row("SELECT last_altered > $t, created <= $t"
            + " FROM information_schema.functions WHERE function_name = 'F2'"), "a rename moves it and keeps CREATED");
    }

    @Test
    public void aConstraintDatesFromItsOwnAddAndAlter() {
        engine.execute("CREATE TABLE parent (id INT PRIMARY KEY, code INT)");
        engine.execute("CREATE TABLE child (id INT, pid INT)");
        assertEquals(List.of(true, true), row("SELECT created = (SELECT created FROM information_schema.tables"
            + " WHERE table_name = 'PARENT'), last_altered = created"
            + " FROM information_schema.table_constraints WHERE table_name = 'PARENT'"),
            "a key the CREATE TABLE declared dates from the table");

        engine.execute("SET t = CURRENT_TIMESTAMP()");
        engine.execute("ALTER TABLE parent ADD CONSTRAINT uq_code UNIQUE (code)");
        engine.execute("ALTER TABLE child ADD CONSTRAINT fk_parent FOREIGN KEY (pid) REFERENCES parent (id)");
        assertEquals(List.of(true, true), row("SELECT created > $t, last_altered = created"
            + " FROM information_schema.table_constraints WHERE constraint_name = 'UQ_CODE'"),
            "an added constraint dates from its ALTER TABLE");
        assertEquals(List.of(true, true), row("SELECT created > $t, last_altered = created"
            + " FROM information_schema.referential_constraints WHERE constraint_name = 'FK_PARENT'"),
            "REFERENTIAL_CONSTRAINTS agrees");

        engine.execute("SET t = CURRENT_TIMESTAMP()");
        engine.execute("ALTER TABLE parent RENAME CONSTRAINT uq_code TO uq_code2");
        engine.execute("ALTER TABLE child ALTER CONSTRAINT fk_parent RELY");
        // A stamp is the statement's instant, which the next statement may share: CREATED is never after $t, and only
        // a statement that READ the time (the SET) puts the next one in a later millisecond.
        assertEquals(List.of(true, true), row("SELECT created <= $t, last_altered > $t"
            + " FROM information_schema.table_constraints WHERE constraint_name = 'UQ_CODE2'"),
            "a rename moves LAST_ALTERED and keeps CREATED");
        assertEquals(List.of(true, true), row("SELECT created <= $t, last_altered > $t"
            + " FROM information_schema.table_constraints WHERE constraint_name = 'FK_PARENT'"),
            "so does RELY");
    }
}
