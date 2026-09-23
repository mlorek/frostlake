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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * CREATE TABLE ... USING TEMPLATE: the template query's array of column descriptions — COLUMN_NAME, TYPE and
 * NULLABLE, as ARRAY_AGG(OBJECT_CONSTRUCT(*)) builds them — becomes the new table's columns, in order.
 */
public class CreateTableUsingTemplateTest extends BaseDatabaseTest {

    private static final String DESCRIPTIONS = "(SELECT ARRAY_AGG(OBJECT_CONSTRUCT('COLUMN_NAME', value:n::VARCHAR, "
        + "'TYPE', value:t::VARCHAR, 'NULLABLE', value:z::BOOLEAN)) WITHIN GROUP (ORDER BY index) FROM TABLE(FLATTEN("
        + "INPUT => PARSE_JSON('[{\"n\":\"id\",\"t\":\"NUMBER(38, 0)\",\"z\":false},"
        + "{\"n\":\"label\",\"t\":\"TEXT\",\"z\":true},{\"n\":\"amount\",\"t\":\"NUMBER(10, 2)\",\"z\":true}]'))))";

    @Override
    protected void setupTest() {
        engine.execute("USE SCHEMA test_db.test_schema");
    }

    @Test
    public void theTemplateNamesTheColumns() {
        engine.execute("CREATE TABLE tpl USING TEMPLATE " + DESCRIPTIONS);
        final ResultSet described = engine.executeQuery("DESCRIBE TABLE tpl");
        assertEquals(3, described.getRows().size());
        assertEquals("id", described.getRows().get(0).getValue(0));
        assertEquals("NUMBER(38,0)", described.getRows().get(0).getValue(1));
        assertEquals("N", described.getRows().get(0).getValue(3));
        assertEquals("label", described.getRows().get(1).getValue(0));
        assertEquals("NUMBER(10,2)", described.getRows().get(2).getValue(1));
        engine.execute("INSERT INTO tpl VALUES (1, 'a', 2.5)");
        assertEquals(1, engine.executeQuery("SELECT * FROM tpl").getRows().size());
    }

    @Test
    public void theRestOfTheStatementApplies() {
        engine.execute("CREATE TABLE tpl2 USING TEMPLATE " + DESCRIPTIONS + " COMMENT = 'from a template'");
        assertEquals("from a template", engine.executeQuery("SHOW TABLES LIKE 'TPL2' ->> SELECT \"comment\" FROM $1")
            .getRows().get(0).getValue(0));
        final String refusal = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE tpl2 USING TEMPLATE " + DESCRIPTIONS);
            }
        }).getMessage();
        assertTrue(refusal.contains("already exists"), refusal);
        engine.execute("CREATE OR REPLACE TABLE tpl2 USING TEMPLATE (SELECT ARRAY_AGG(OBJECT_CONSTRUCT("
            + "'COLUMN_NAME', value::VARCHAR, 'TYPE', 'BOOLEAN', 'NULLABLE', TRUE)) FROM TABLE(FLATTEN(INPUT => "
            + "ARRAY_CONSTRUCT('only'))))");
        assertEquals(1, engine.executeQuery("DESCRIBE TABLE tpl2").getRows().size());
    }

    @Test
    public void aTemplateMustReadATableFunctionAndAnswerDescriptions() {
        final String plain = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE tpl3 USING TEMPLATE (SELECT 1)");
            }
        }).getMessage();
        assertTrue(plain.contains("Unsupported feature 'Table function must be used in the TEMPLATE sub-query'."), plain);
        final String refusal = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE tpl3 USING TEMPLATE (SELECT value FROM TABLE(FLATTEN(INPUT => "
                    + "ARRAY_CONSTRUCT(1))))");
            }
        }).getMessage();
        assertTrue(refusal.contains("Invalid template: template must be a non-null JSON array"), refusal);
    }
}
