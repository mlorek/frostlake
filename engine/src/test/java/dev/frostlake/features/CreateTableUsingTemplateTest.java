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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CREATE TABLE ... USING TEMPLATE (live-verified): the template query, written in parentheses, reads INFER_SCHEMA
 * as the first relation of its FROM clause and answers one array of column descriptions — COLUMN_NAME, TYPE and
 * NULLABLE, as ARRAY_AGG(OBJECT_CONSTRUCT(*)) builds them — which become the new table's columns, in order. A
 * query that reads something else there is refused before it runs.
 */
public class CreateTableUsingTemplateTest extends BaseDatabaseTest {

    private static final String INFER = "TABLE(INFER_SCHEMA(LOCATION => '@tpl_st/a/', FILE_FORMAT => 'tpl_ff'))";

    /** The inferred columns, in file order: i, s and d. */
    private static final String DESCRIPTIONS = "SELECT ARRAY_AGG(OBJECT_CONSTRUCT(*)) WITHIN GROUP (ORDER BY order_id) FROM ";

    private static final String NO_TABLE_FUNCTION =
        "Unsupported feature 'Table function must be used in the TEMPLATE sub-query'.";

    private static final String NO_INFER_SCHEMA =
        "Unsupported feature 'INFER_SCHEMA function must be used in the TEMPLATE sub-query'.";

    @Override
    protected void setupTest() {
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE STAGE tpl_st");
        engine.execute("CREATE FILE FORMAT tpl_ff TYPE = CSV PARSE_HEADER = TRUE");
        stageLocalFile("tpl_st/a/", "clean.csv", "i,s,d\n1,x,2020-01-01\n");
        engine.execute("CREATE TABLE tpl_src (a INT)");
        engine.execute("INSERT INTO tpl_src VALUES (1)");
    }

    /** The table's columns: name, type and nullability, in order. */
    private List<String> described(final String table) {
        final ResultSet rs = engine.executeQuery("DESCRIBE TABLE " + table);
        final List<String> columns = new ArrayList<>();
        while (rs.next()) {
            columns.add(rs.getValue(0) + " " + rs.getValue(1) + " " + rs.getValue(3));
        }
        return columns;
    }

    private String refusal(final String template) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE tpl_refused USING TEMPLATE " + template);
            }
        }).getMessage();
    }

    @Test
    public void theTemplateNamesTheColumns() {
        engine.execute("CREATE TABLE tpl USING TEMPLATE (" + DESCRIPTIONS + INFER + ")");
        assertEquals(Arrays.asList("i NUMBER(1,0) Y", "s VARCHAR(16777216) Y", "d DATE Y"), described("tpl"));
        engine.execute("INSERT INTO tpl VALUES (1, 'a', '2020-01-02')");
        assertEquals(1, engine.executeQuery("SELECT * FROM tpl").getRows().size());
        engine.execute("CREATE TABLE tpl_named USING TEMPLATE (SELECT ARRAY_AGG(OBJECT_CONSTRUCT('COLUMN_NAME',"
            + " 'x_' || column_name, 'TYPE', type, 'NULLABLE', nullable)) WITHIN GROUP (ORDER BY order_id) FROM "
            + INFER + " WHERE column_name <> 's')");
        assertEquals(Arrays.asList("x_i NUMBER(1,0) Y", "x_d DATE Y"), described("tpl_named"),
            "the descriptions are the query's own to shape");
        engine.execute("CREATE TABLE tpl_literal USING TEMPLATE (SELECT ARRAY_CONSTRUCT(OBJECT_CONSTRUCT("
            + "'COLUMN_NAME', 'a', 'TYPE', 'TEXT', 'NULLABLE', TRUE)) FROM " + INFER + " LIMIT 1)");
        assertEquals(Arrays.asList("a VARCHAR(16777216) Y"), described("tpl_literal"));
    }

    @Test
    public void theRestOfTheStatementApplies() {
        engine.execute("CREATE TABLE tpl2 USING TEMPLATE (" + DESCRIPTIONS + INFER + ") COMMENT = 'from a template'");
        assertEquals("from a template", engine.executeQuery("SHOW TABLES LIKE 'TPL2' ->> SELECT \"comment\" FROM $1")
            .getRows().get(0).getValue(0));
        final String again = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE tpl2 USING TEMPLATE (" + DESCRIPTIONS + INFER + ")");
            }
        }).getMessage();
        assertTrue(again.contains("Object 'TPL2' already exists."), again);
        engine.execute("CREATE OR REPLACE TABLE tpl2 USING TEMPLATE (" + DESCRIPTIONS + INFER
            + " WHERE column_name = 'i')");
        assertEquals(Arrays.asList("i NUMBER(1,0) Y"), described("tpl2"));
        engine.execute("CREATE TABLE tpl3 USING TEMPLATE ((" + DESCRIPTIONS + INFER + "))");
        assertEquals(3, described("tpl3").size(), "a second pair of parentheses changes nothing");
    }

    @Test
    public void theQueryReadsInferSchemaFirstInItsFromClause() {
        final String description = "SELECT ARRAY_AGG(OBJECT_CONSTRUCT('COLUMN_NAME', 'a', 'TYPE', 'TEXT',"
            + " 'NULLABLE', TRUE)) FROM ";
        assertEquals(NO_TABLE_FUNCTION, refusal("(SELECT 1)"));
        assertEquals(NO_TABLE_FUNCTION, refusal("(" + description + "tpl_src)"));
        assertEquals(NO_TABLE_FUNCTION, refusal("(WITH s AS (SELECT * FROM " + INFER + ") " + DESCRIPTIONS + "s)"),
            "not through a common table expression");
        assertEquals(NO_TABLE_FUNCTION, refusal("(" + DESCRIPTIONS + "(SELECT * FROM " + INFER + "))"),
            "nor through a subquery");
        assertEquals(NO_TABLE_FUNCTION, refusal("(" + DESCRIPTIONS + INFER + " UNION ALL SELECT NULL)"));
        assertEquals(NO_TABLE_FUNCTION, refusal("(" + DESCRIPTIONS + "tpl_src JOIN " + INFER + " ON TRUE)"),
            "nor behind another relation");
        assertEquals(NO_INFER_SCHEMA, refusal("(SELECT value FROM TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(1))))"));
        assertEquals(NO_INFER_SCHEMA, refusal("(" + description + "TABLE(GENERATOR(ROWCOUNT => 1)))"));
        assertEquals(NO_INFER_SCHEMA, refusal("(" + description + "TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(1))) f JOIN "
            + INFER + " ON TRUE)"));
        engine.execute("CREATE TABLE tpl_joined USING TEMPLATE (SELECT ARRAY_AGG(OBJECT_CONSTRUCT('COLUMN_NAME',"
            + " column_name, 'TYPE', type, 'NULLABLE', nullable)) FROM " + INFER + ", tpl_src)");
        assertEquals(3, described("tpl_joined").size(), "a relation after INFER_SCHEMA is fine");
        final String bare = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE tpl_bare USING TEMPLATE SELECT 1");
            }
        }).getMessage();
        assertTrue(bare.contains("syntax error line 1 at position 37 unexpected 'SELECT'."), bare);
    }

    @Test
    public void theQueryAnswersOneArrayOfDescriptions() {
        final String notAnArray = "Invalid template: template must be a non-null JSON array";
        assertTrue(refusal("(" + DESCRIPTIONS + INFER + " WHERE 1 = 0)").contains(notAnArray));
        assertTrue(refusal("(SELECT 1 FROM " + INFER + " LIMIT 1)").contains(notAnArray));
        assertTrue(refusal("(SELECT ARRAY_CONSTRUCT() FROM " + INFER + " LIMIT 1)").contains(notAnArray));
        assertTrue(refusal("(SELECT * FROM " + INFER + ")").contains(
            "Invalid template: Exactly 1 template is expected. Found: 3"));
        assertTrue(refusal("(SELECT ARRAY_AGG(OBJECT_CONSTRUCT('COLUMN_NAME', column_name)) WITHIN GROUP (ORDER BY"
            + " order_id) FROM " + INFER + ")").contains(
            "Invalid template: TYPE field is missing in {\"COLUMN_NAME\":\"i\"}"));
    }
}
