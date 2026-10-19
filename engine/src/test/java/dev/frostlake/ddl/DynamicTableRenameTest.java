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
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ALTER DYNAMIC TABLE … RENAME TO: the dynamic table takes its new name, in its schema or in the one the name
 * places it in, and goes on answering its query. A name a table or a view holds is refused, IF EXISTS forgives
 * the table's absence alone, and the statement renames a plain table as ALTER TABLE renames a dynamic one.
 */
public class DynamicTableRenameTest extends BaseDatabaseTest {

    private static final String COMPILATION = "SQL compilation error:\n";
    private static final String DYNAMIC = " TARGET_LAG = '1 minute' WAREHOUSE = COMPUTE_WH AS SELECT a FROM dtr_base";

    @Override
    protected void setupTest() {
        engine.execute("USE ROLE ACCOUNTADMIN");
        engine.execute("CREATE WAREHOUSE IF NOT EXISTS COMPUTE_WH");
        engine.execute("CREATE OR REPLACE TABLE dtr_base (a INT)");
        engine.execute("INSERT INTO dtr_base VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE dtr_one" + DYNAMIC);
    }

    private String refusalOf(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private String countOf(final String relation) {
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM " + relation);
        return String.valueOf(rs.getRows().get(0).getValue(0));
    }

    /** The dynamic tables of the test's database whose name starts with DTR, as SCHEMA.NAME, sorted. */
    private List<String> dynamicTables() {
        final ResultSet rs = engine.executeQuery("SHOW DYNAMIC TABLES LIKE 'DTR%' IN DATABASE test_db");
        final List<String> out = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            out.add(row.getValue(rs.getColumnIndex("schema_name")) + "." + row.getValue(rs.getColumnIndex("name")));
        }
        Collections.sort(out);
        return out;
    }

    @Test
    public void aDynamicTableIsRenamedAndAnswersUnderItsNewName() {
        engine.execute("ALTER DYNAMIC TABLE dtr_one RENAME TO dtr_two");
        assertEquals(List.of("TEST_SCHEMA.DTR_TWO"), dynamicTables());
        assertEquals("2", countOf("dtr_two"));
        assertEquals(hinted(COMPILATION + "Object 'DTR_ONE' does not exist or not authorized."),
            refusalOf("SELECT COUNT(*) FROM dtr_one"));
        // A quoted name, and the name back.
        engine.execute("ALTER DYNAMIC TABLE dtr_two RENAME TO \"dtr low\"");
        assertEquals("2", countOf("\"dtr low\""));
        engine.execute("ALTER DYNAMIC TABLE \"dtr low\" RENAME TO dtr_one");
        assertEquals(List.of("TEST_SCHEMA.DTR_ONE"), dynamicTables());
    }

    @Test
    public void aNameThatIsTakenIsRefused() {
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE dtr_other" + DYNAMIC);
        engine.execute("CREATE OR REPLACE VIEW dtr_view AS SELECT a FROM dtr_base");
        assertEquals(COMPILATION + "Object 'DTR_OTHER' already exists.",
            refusalOf("ALTER DYNAMIC TABLE dtr_one RENAME TO dtr_other"));
        assertEquals(COMPILATION + "Object 'DTR_BASE' already exists.",
            refusalOf("ALTER DYNAMIC TABLE dtr_one RENAME TO dtr_base"));
        assertEquals(COMPILATION + "Object 'DTR_VIEW' already exists.",
            refusalOf("ALTER DYNAMIC TABLE dtr_one RENAME TO dtr_view"));
        assertEquals(COMPILATION + "Object 'DTR_ONE' already exists.",
            refusalOf("ALTER DYNAMIC TABLE dtr_one RENAME TO dtr_one"));
        // IF EXISTS forgives the table's absence, not the taken name.
        assertEquals(COMPILATION + "Object 'DTR_OTHER' already exists.",
            refusalOf("ALTER DYNAMIC TABLE IF EXISTS dtr_one RENAME TO dtr_other"));
        engine.execute("ALTER DYNAMIC TABLE IF EXISTS dtr_nosuch RENAME TO dtr_z");
        assertEquals(List.of("TEST_SCHEMA.DTR_ONE", "TEST_SCHEMA.DTR_OTHER"), dynamicTables());
    }

    @Test
    public void whatIsNotThereIsNamedInFull() {
        assertEquals(
            hinted(COMPILATION + "Dynamic table 'TEST_DB.TEST_SCHEMA.DTR_NOSUCH' does not exist or not authorized."),
            refusalOf("ALTER DYNAMIC TABLE dtr_nosuch RENAME TO dtr_z"));
        assertEquals(hinted(COMPILATION + "Schema 'TEST_DB.DTR_NOSUCH_SCHEMA' does not exist or not authorized."),
            refusalOf("ALTER DYNAMIC TABLE dtr_one RENAME TO dtr_nosuch_schema.dtr_z"));
        assertEquals(hinted(COMPILATION + "Database 'DTR_NOSUCH_DB' does not exist or not authorized."),
            refusalOf("ALTER DYNAMIC TABLE dtr_one RENAME TO dtr_nosuch_db.s.dtr_z"));
    }

    @Test
    public void aNameInAnotherSchemaMovesTheTableWhichGoesOnReadingItsSource() {
        engine.execute("CREATE SCHEMA IF NOT EXISTS test_db.dtr_elsewhere");
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("ALTER DYNAMIC TABLE dtr_one RENAME TO dtr_elsewhere.dtr_moved");
        assertEquals(List.of("DTR_ELSEWHERE.DTR_MOVED"), dynamicTables());
        assertEquals("2", countOf("dtr_elsewhere.dtr_moved"));
        engine.execute("ALTER DYNAMIC TABLE dtr_elsewhere.dtr_moved RENAME TO test_schema.dtr_one");
        assertEquals(List.of("TEST_SCHEMA.DTR_ONE"), dynamicTables());
        assertEquals("2", countOf("dtr_one"));
    }

    @Test
    public void theStatementRenamesAPlainTableAndAlterTableADynamicOne() {
        engine.execute("CREATE OR REPLACE TABLE dtr_plain (a INT)");
        engine.execute("ALTER DYNAMIC TABLE dtr_plain RENAME TO dtr_plain_two");
        assertEquals("0", countOf("dtr_plain_two"));
        engine.execute("ALTER TABLE dtr_one RENAME TO dtr_nine");
        assertEquals(List.of("TEST_SCHEMA.DTR_NINE"), dynamicTables());
        assertEquals("2", countOf("dtr_nine"));
        // Any other action of the statement tells a plain table apart.
        final String otherKind = "SQL compilation error: Object found is of type 'TABLE', not specified type"
            + " 'DYNAMIC_TABLE'.";
        assertEquals(otherKind, refusalOf("ALTER DYNAMIC TABLE dtr_plain_two SUSPEND"));
        assertEquals(otherKind, refusalOf("ALTER DYNAMIC TABLE dtr_plain_two REFRESH"));
        assertEquals(otherKind, refusalOf("ALTER DYNAMIC TABLE dtr_plain_two SET COMMENT = 'c'"));
    }

    @Test
    public void aFaultAfterTheRenameIsRefusedAtItsTo() {
        final String syntax = COMPILATION + "syntax error line 1 at position ";
        assertEquals(syntax + "35 unexpected 'TO'.", refusalOf("ALTER DYNAMIC TABLE dtr_one RENAME TO x y"));
        assertEquals(syntax + "35 unexpected 'TO'.", refusalOf("ALTER DYNAMIC TABLE dtr_one RENAME TO"));
        assertEquals(syntax + "34 unexpected '<EOF>'.", refusalOf("ALTER DYNAMIC TABLE dtr_one RENAME"));
    }
}
