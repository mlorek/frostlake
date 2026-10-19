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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A name reaches an object by its canonical spelling alone, whatever the statement: an unquoted name is its upper-cased
 * self and never a quoted lower-case object (documented), so EXECUTE TASK, DESCRIBE / ALTER / DROP PIPE, DESCRIBE VIEW
 * and SYSTEM$PIPE_STATUS miss them as every other statement already did; a database or a schema written unquoted
 * misses a quoted one for COMMENT, ALTER and DESCRIBE as it does for CREATE and DROP; and a DESCRIBE with no current
 * database is refused as a statement with none is. Engine only: the live account allows no CREATE DATABASE, and a pipe
 * carries a COPY statement.
 */
public class ExactNameResolutionTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE en");
        engine.execute("USE SCHEMA en.PUBLIC");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return refused.getMessage();
    }

    private String answer(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void anUnquotedNameReachesNoQuotedLowerCaseObject() {
        engine.execute("CREATE TABLE cst (a INT)");
        engine.execute("CREATE TASK \"tk\" SCHEDULE = '1 MINUTE' AS SELECT 1");
        engine.execute("CREATE STAGE \"st\"");
        engine.execute("CREATE PIPE \"pp\" AS COPY INTO cst FROM @\"st\"");
        engine.execute("CREATE VIEW \"vw\" AS SELECT 1 x");
        assertTrue(refusal("EXECUTE TASK TK").contains("Task 'EN.PUBLIC.TK' does not exist"));
        assertTrue(refusal("DESCRIBE PIPE PP").contains("Pipe 'EN.PUBLIC.PP' does not exist"));
        assertTrue(refusal("ALTER PIPE PP SET COMMENT = 'x'").contains("Pipe 'EN.PUBLIC.PP' does not exist"));
        assertTrue(refusal("DROP PIPE PP").contains("Pipe 'EN.PUBLIC.PP' does not exist"));
        assertTrue(refusal("DESCRIBE VIEW VW").contains("View 'VW' does not exist"));
        assertTrue(refusal("SELECT SYSTEM$PIPE_STATUS('PP')").contains("Pipe 'PP' does not exist"));
        // The quoted names reach them.
        assertEquals("Task tk is scheduled to run immediately.", answer("EXECUTE TASK \"tk\""));
        assertEquals("pp", String.valueOf(engine.executeQuery("DESCRIBE PIPE \"pp\"").getRows().get(0).getValue(1)));
        assertEquals("X", String.valueOf(engine.executeQuery("DESCRIBE VIEW \"vw\"").getRows().get(0).getValue(0)));
        assertTrue(answer("SELECT SYSTEM$PIPE_STATUS('\"pp\"')").contains("RUNNING"));
        engine.execute("DROP PIPE \"pp\"");
    }

    @Test
    public void aDatabaseOrASchemaWrittenUnquotedMissesAQuotedOne() {
        engine.execute("CREATE DATABASE \"lowdb\"");
        engine.execute("CREATE SCHEMA \"lowdb\".\"sch\"");
        engine.execute("CREATE TABLE \"lowdb\".\"sch\".rt (a INT)");
        engine.execute("CREATE STAGE \"lowdb\".\"sch\".STG");
        engine.execute("CREATE PIPE \"lowdb\".\"sch\".PP AS COPY INTO \"lowdb\".\"sch\".rt"
            + " FROM @\"lowdb\".\"sch\".STG");
        engine.execute("CREATE FILE FORMAT \"lowdb\".\"sch\".FF TYPE = CSV");
        engine.execute("CREATE TAG \"lowdb\".\"sch\".TG");
        assertTrue(refusal("SELECT SYSTEM$PIPE_STATUS('LOWDB.SCH.PP')").contains("Pipe 'LOWDB.SCH.PP' does not exist"));
        assertTrue(answer("SELECT SYSTEM$PIPE_STATUS('\"lowdb\".\"sch\".PP')").contains("RUNNING"));
        assertTrue(refusal("DESCRIBE STAGE LOWDB.SCH.STG").contains("Database 'LOWDB' does not exist"));
        assertTrue(refusal("DESCRIBE PIPE LOWDB.SCH.PP").contains("Database 'LOWDB' does not exist"));
        assertTrue(refusal("DESCRIBE FILE FORMAT LOWDB.SCH.FF").contains("Database 'LOWDB' does not exist"));
        assertTrue(refusal("COMMENT ON TAG LOWDB.SCH.TG IS 'x'").contains("Database 'LOWDB' does not exist"));
        engine.execute("CREATE SCHEMA en.\"mixedSch\"");
        engine.execute("CREATE SEQUENCE en.\"mixedSch\".ms");
        engine.execute("CREATE FILE FORMAT en.\"mixedSch\".mf TYPE = CSV");
        engine.execute("CREATE TAG en.\"mixedSch\".mg");
        engine.execute("CREATE MASKING POLICY en.\"mixedSch\".mm AS (v VARCHAR) RETURNS VARCHAR -> v");
        engine.execute("CREATE FUNCTION en.\"mixedSch\".mfn() RETURNS INT AS '1'");
        final String missing = "Schema 'EN.MIXEDSCH' does not exist";
        assertTrue(refusal("COMMENT ON MASKING POLICY EN.MIXEDSCH.mm IS 'x'").contains(missing));
        assertTrue(refusal("COMMENT ON FUNCTION EN.MIXEDSCH.mfn() IS 'x'").contains(missing));
        assertTrue(refusal("COMMENT ON TAG EN.MIXEDSCH.mg IS 'x'").contains(missing));
        assertTrue(refusal("COMMENT ON SEQUENCE EN.MIXEDSCH.ms IS 'x'").contains(missing));
        assertTrue(refusal("COMMENT ON FILE FORMAT EN.MIXEDSCH.mf IS 'x'").contains(missing));
        assertTrue(refusal("ALTER MASKING POLICY EN.MIXEDSCH.mm SET COMMENT = 'x'").contains(missing));
        assertTrue(refusal("ALTER FUNCTION EN.MIXEDSCH.mfn() SET COMMENT = 'x'").contains(missing));
        assertTrue(refusal("ALTER TAG EN.MIXEDSCH.mg SET COMMENT = 'x'").contains(missing));
        assertTrue(refusal("ALTER SEQUENCE EN.MIXEDSCH.ms SET COMMENT = 'x'").contains(missing));
        assertTrue(refusal("ALTER FILE FORMAT EN.MIXEDSCH.mf SET COMMENT = 'x'").contains(missing));
        // The quoted schema reaches them.
        engine.execute("COMMENT ON TAG en.\"mixedSch\".mg IS 'x'");
        engine.execute("ALTER SEQUENCE en.\"mixedSch\".ms SET COMMENT = 'x'");
    }

    @Test
    public void anImportNamesItsStageAsAnIdentifierDoes() throws Exception {
        engine.execute("CREATE STAGE jst");
        final Path dir = Files.createTempDirectory("fl_import_case_");
        final Path jar = dir.resolve("h.jar");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new ZipEntry("README"));
            out.closeEntry();
        }
        engine.execute("PUT 'file://" + jar.toAbsolutePath() + "' @jst AUTO_COMPRESS=FALSE");
        // The location names the stage in lower case: as an identifier, it is EN.PUBLIC.JST.
        engine.execute("CREATE OR REPLACE FUNCTION fimp(x NUMBER) RETURNS NUMBER LANGUAGE JAVA"
            + " IMPORTS = ('@en.public.jst/h.jar') HANDLER = 'H.f'"
            + " AS $$ class H { public static int f(int x) { return x; } } $$");
        assertEquals("7", answer("SELECT fimp(7)"));
    }

    @Test
    public void aDescribeWithNoCurrentDatabaseIsRefusedAsAStatementIs() {
        engine.execute("CREATE DATABASE ncd");
        engine.execute("USE DATABASE ncd");
        engine.execute("DROP DATABASE ncd");
        assertEquals("null", answer("SELECT CURRENT_DATABASE()"));
        final String refused = refusal("DESCRIBE FUNCTION f(int)");
        assertEquals("Cannot perform DESCRIBE. This session does not have a current database. Call 'USE DATABASE',"
            + " or use a qualified name.", refused);
        assertTrue(refusal("DESCRIBE TABLE t").contains("Table 'T' does not exist"));
    }
}
