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
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TARGET_PATH on a Java or Scala routine (live-verified): the handler's compiled classes are kept as a JAR at
 * the path, which another routine may then import. The path names a JAR on an existing stage that is not there
 * yet - except the routine's own, which a replacement writes again - and it is read before the routine's language
 * is looked at.
 */
public class RoutineTargetPathTest extends BaseDatabaseTest {

    private static final String JAVA = " LANGUAGE JAVA HANDLER = 'H.h'";
    private static final String BODY = " AS $$ class H { public static long h(long x) { return x; } } $$";

    private static String function(final String name, final String clauses) {
        return "CREATE OR REPLACE FUNCTION " + name + "(x NUMBER) RETURNS NUMBER" + JAVA + clauses + BODY;
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
    }

    private String first(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private String described(final String function, final String property) {
        final ResultSet rows = engine.executeQuery("DESCRIBE FUNCTION " + function + "(NUMBER)");
        return cell(rows, soleRowWhere(rows, "property", property), "value");
    }

    private List<String> staged(final String stage) {
        final ResultSet listed = engine.executeQuery("LIST @" + stage);
        final List<String> names = new ArrayList<>();
        for (final Row row : listed.getRows()) {
            names.add(cell(listed, row, "name"));
        }
        return names;
    }

    @Test
    public void theClassesAreKeptAtThePathAndCanBeImported() {
        engine.execute("CREATE STAGE tp_st");
        engine.execute(function("tp_a", " TARGET_PATH = '@tp_st/sub/Tp.JAR'"));
        assertTrue(staged("tp_st").contains("tp_st/sub/Tp.JAR"), "the JAR is on the stage, its path as written");
        assertEquals("@TEST_DB.TEST_SCHEMA.TP_ST/sub/Tp.JAR", described("tp_a", "target_path"),
            "the stage is told in full");
        assertEquals("5", first("SELECT tp_a(5)"));
        assertTrue(first("SELECT GET_DDL('FUNCTION', 'tp_a(NUMBER)')").contains("HANDLER = 'H.h'\n"
            + "TARGET_PATH = '@TEST_DB.TEST_SCHEMA.TP_ST/sub/Tp.JAR'\nAS "));

        engine.execute("CREATE FUNCTION tp_b(x NUMBER) RETURNS NUMBER" + JAVA + " IMPORTS = ('@tp_st/sub/Tp.JAR')");
        assertEquals("7", first("SELECT tp_b(7)"), "the kept classes run a routine that imports them");
        assertEquals("[@TEST_DB.TEST_SCHEMA.TP_ST/sub/Tp.JAR]", described("tp_b", "imports"));
        assertNull(described("tp_b", "body"), "a routine without a body still tells a body row");
        assertNull(described("tp_b", "target_path"));
        assertTrue(first("SELECT GET_DDL('FUNCTION', 'tp_b(NUMBER)')").endsWith("HANDLER = 'H.h'\n"
            + "IMPORTS = ('@TEST_DB.TEST_SCHEMA.TP_ST/sub/Tp.JAR')\n;"), "and its DDL ends without one");
    }

    @Test
    public void aPathNamesAJarThatIsNotThereYet() {
        engine.execute("CREATE STAGE tp_st");
        final String noJar = "SQL compilation error: TARGET_PATH must specify JAR name and location.";
        assertEquals(noJar, refusal(function("tp_a", " TARGET_PATH = '@tp_st'")));
        assertEquals(noJar, refusal(function("tp_a", " TARGET_PATH = '@tp_st/sub/'")));
        assertEquals(noJar, refusal(function("tp_a", " TARGET_PATH = '@tp_st/x'")));
        assertEquals(noJar, refusal(function("tp_a", " TARGET_PATH = '@tp_st/x.zip'")));
        assertTrue(staged("tp_st").isEmpty(), "a refused path leaves nothing behind");

        engine.execute(function("tp_a", " TARGET_PATH = '@test_db.test_schema.tp_st/q.jar'"));
        assertEquals("SQL compilation error: TARGET_PATH cannot point to an existing file.",
            refusal(function("tp_other", " TARGET_PATH = '@tp_st/q.jar'")));
        engine.execute(function("tp_a", " TARGET_PATH = '@tp_st/q.jar'"));
        assertEquals("@TEST_DB.TEST_SCHEMA.TP_ST/q.jar", described("tp_a", "target_path"),
            "a routine is replaced over its own JAR");
        engine.execute(function("tp_a", " TARGET_PATH = '@tp_st/t1.jar' TARGET_PATH = '@tp_st/t2.jar'"));
        assertTrue(staged("tp_st").contains("tp_st/t2.jar") && !staged("tp_st").contains("tp_st/t1.jar"),
            "a repeated path is taken once, the last one written");
        engine.execute("DROP FUNCTION tp_a(NUMBER)");
        assertTrue(staged("tp_st").contains("tp_st/q.jar") && staged("tp_st").contains("tp_st/t2.jar"),
            "the JARs outlive the routine");
    }

    @Test
    public void aPathIsReadBeforeTheLanguageIsLookedAt() {
        engine.execute("CREATE STAGE tp_st");
        final String python = "CREATE OR REPLACE FUNCTION tp_p(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON"
            + " RUNTIME_VERSION = '3.11' HANDLER = 'h' TARGET_PATH = ";
        final String pythonBody = " AS $$\ndef h(x):\n    return x\n$$";
        assertTrue(refusal(function("tp_a", " TARGET_PATH = ''")).contains("invalid URL prefix found in: ''"));
        assertTrue(refusal(function("tp_a", " TARGET_PATH = 'nostage'"))
            .contains("invalid URL prefix found in: 'nostage'"));
        assertTrue(refusal(function("tp_a", " TARGET_PATH = '@'")).contains("missing stage name in URL: @"));
        final String noStage = "Stage 'TEST_DB.TEST_SCHEMA.TP_NOSUCH' does not exist or not authorized.";
        assertTrue(refusal(function("tp_a", " TARGET_PATH = '@tp_nosuch/'")).contains(noStage),
            "a missing stage is told before a missing JAR name");
        assertTrue(refusal(python + "'@tp_nosuch/x.jar'" + pythonBody).contains(noStage),
            "and before the language");
        assertTrue(refusal(python + "'@tp_st/x.jar'" + pythonBody)
            .contains("invalid property 'TARGET_PATH' for 'PYTHON functions'"));
        assertTrue(refusal("CREATE OR REPLACE FUNCTION tp_s(x NUMBER) RETURNS NUMBER TARGET_PATH = '@tp_st/s.jar'"
            + " AS 'x'").contains("invalid property 'TARGET_PATH' for 'SQL functions'"));
        assertTrue(refusal("CREATE OR REPLACE PROCEDURE tp_sp() RETURNS VARCHAR LANGUAGE SQL TARGET_PATH ="
            + " '@tp_st/sp.jar' AS 'BEGIN RETURN 1; END'")
            .contains("invalid property 'TARGET_PATH' for 'SQL functions'"), "a procedure is told as a function");
        assertEquals("SQL compilation error: Cannot specify TARGET_PATH without a function BODY.",
            refusal("CREATE OR REPLACE FUNCTION tp_n(x NUMBER) RETURNS NUMBER" + JAVA
                + " TARGET_PATH = '@tp_st/y.jar'"));
        assertEquals("Invalid value specified for property 'TARGET_PATH', use 'single quoted string literals'"
            + " instead.", refusal(function("tp_a", " TARGET_PATH = tp_st")));
    }

    @Test
    public void aTableStageAndAQuotedStageAreToldInTheirOwnSpelling() {
        engine.execute("CREATE TABLE tp_t (a INT)");
        engine.execute(function("tp_a", " TARGET_PATH = '@%tp_t/tt.jar'"));
        assertEquals("@TEST_DB.TEST_SCHEMA.\"%TP_T\"/tt.jar", described("tp_a", "target_path"));
        engine.execute("CREATE STAGE \"tp low\"");
        engine.execute(function("tp_a", " TARGET_PATH = '@\"tp low\"/q.jar'"));
        assertEquals("@TEST_DB.TEST_SCHEMA.\"tp low\"/q.jar", described("tp_a", "target_path"));
    }
}
