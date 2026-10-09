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
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS: the tags reaching every column of one table-like object, a
 * column's own and those it inherits.
 */
public class TagReferencesAllColumnsTest extends BaseDatabaseTest {

    private static final String PREFIX = "SQL compilation error:\n";

    @BeforeEach
    public void aTaggedTable() {
        engine.execute("CREATE SCHEMA tg_s2");
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE TAG test_schema.cc");
        engine.execute("CREATE TAG tg_s2.cc");
        engine.execute("CREATE TABLE tt (a INT, b INT)");
        engine.execute("ALTER TABLE tt SET TAG test_schema.cc = 'a', tg_s2.cc = 'b'");
    }

    private List<String> rows(final String arguments) {
        // The account promises no order, so the rows are read in one: by tag, then by column.
        final ResultSet rs = engine.executeQuery("SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS("
            + arguments + ")) ORDER BY TAG_DATABASE, TAG_SCHEMA, TAG_NAME, COLUMN_NAME");
        final List<String> out = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            final StringBuilder line = new StringBuilder();
            for (int i = 0; i < rs.getColumns().size(); i++) {
                line.append(i == 0 ? "" : "|").append(row.getValue(i));
            }
            out.add(line.toString());
        }
        return out;
    }

    private String refusal(final String arguments) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS(" + arguments
                    + "))");
            }
        }).getMessage();
    }

    @Test
    public void itAnswersTagReferencesColumns() {
        final List<String> names = new ArrayList<>();
        for (final ResultSetColumn column : engine.executeQuery(
                "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS('tt', 'TABLE'))").getColumns()) {
            names.add(column.getName());
        }
        assertEquals(Arrays.asList("TAG_DATABASE", "TAG_SCHEMA", "TAG_NAME", "TAG_VALUE", "LEVEL", "OBJECT_DATABASE",
            "OBJECT_SCHEMA", "OBJECT_NAME", "DOMAIN", "COLUMN_NAME", "APPLY_METHOD"), names);
    }

    @Test
    public void everyColumnInheritsItsTablesTags() {
        assertEquals(Arrays.asList(
            "TEST_DB|TEST_SCHEMA|CC|a|TABLE|TEST_DB|TEST_SCHEMA|TT|COLUMN|A|INHERITED",
            "TEST_DB|TEST_SCHEMA|CC|a|TABLE|TEST_DB|TEST_SCHEMA|TT|COLUMN|B|INHERITED",
            "TEST_DB|TG_S2|CC|b|TABLE|TEST_DB|TEST_SCHEMA|TT|COLUMN|A|INHERITED",
            "TEST_DB|TG_S2|CC|b|TABLE|TEST_DB|TEST_SCHEMA|TT|COLUMN|B|INHERITED"), rows("'tt', 'TABLE'"));
        assertEquals(4, rows("'test_db.test_schema.tt', 'table'").size());
    }

    @Test
    public void aColumnsOwnTagOverridesTheTables() {
        engine.execute("ALTER TABLE tt MODIFY COLUMN a SET TAG test_schema.cc = 'ca'");
        assertEquals(Arrays.asList(
            "TEST_DB|TEST_SCHEMA|CC|ca|COLUMN|TEST_DB|TEST_SCHEMA|TT|COLUMN|A|MANUAL",
            "TEST_DB|TEST_SCHEMA|CC|a|TABLE|TEST_DB|TEST_SCHEMA|TT|COLUMN|B|INHERITED",
            "TEST_DB|TG_S2|CC|b|TABLE|TEST_DB|TEST_SCHEMA|TT|COLUMN|A|INHERITED",
            "TEST_DB|TG_S2|CC|b|TABLE|TEST_DB|TEST_SCHEMA|TT|COLUMN|B|INHERITED"), rows("'tt', 'TABLE'"));
    }

    @Test
    public void aViewsColumnsInheritTheViewsTags() {
        engine.execute("CREATE VIEW tv WITH TAG (tg_s2.cc = 'v') AS SELECT a, b FROM tt");
        assertEquals(Arrays.asList(
            "TEST_DB|TG_S2|CC|v|TABLE|TEST_DB|TEST_SCHEMA|TV|COLUMN|A|INHERITED",
            "TEST_DB|TG_S2|CC|v|TABLE|TEST_DB|TEST_SCHEMA|TV|COLUMN|B|INHERITED"), rows("'tv', 'TABLE'"));
        engine.execute("CREATE TABLE tnone (a INT)");
        assertEquals(0, rows("'tnone', 'TABLE'").size());
    }

    @Test
    public void onlyATableLikeObjectIsTaken() {
        assertEquals("SQL compilation error: Invalid value VIEW for argument OBJECT_TYPE. Please use object type TABLE"
            + " for all kinds of table-like objects.", refusal("'tt', 'VIEW'"));
        assertEquals("Unsupported feature 'Only table-like objects are currently supported'.",
            refusal("'tt', 'COLUMN'"));
        assertEquals("Unsupported feature 'Only table-like objects are currently supported'.",
            refusal("'test_schema', 'SCHEMA'"));
        assertEquals("Unknown domain: NOPE.", refusal("'tt', 'NOPE'"));
        assertEquals(hinted(PREFIX + "Table 'NOSUCH_TT' does not exist or not authorized."),
            refusal("'nosuch_tt', 'TABLE'"));
        assertEquals(hinted(PREFIX + "Table 'TEST_DB.TEST_SCHEMA.NOSUCH_TT' does not exist or not authorized."),
            refusal("'test_db.test_schema.nosuch_tt', 'TABLE'"));
    }

    @Test
    public void bothArgumentsAreRequired() {
        assertEquals("SQL compilation error: error line 1 at position 419\n"
            + "missing required argument [DOMAIN] for function [TAG_REFERENCES_ALL_COLUMNS]", refusal("'tt'"));
        assertEquals("SQL compilation error: error line 1 at position 419\n"
            + "missing required argument [OBJECT_NAME] for function [TAG_REFERENCES_ALL_COLUMNS]",
            refusal("NULL, 'TABLE'"));
        assertEquals("SQL compilation error: error line 1 at position 419\n"
            + "missing required argument [DOMAIN] for function [TAG_REFERENCES_ALL_COLUMNS]", refusal("'tt', NULL"));
        assertEquals(PREFIX + "Invalid identifier TAG_REFERENCES_ALL_COLUMNS", assertThrows(RuntimeException.class,
            new Executable() {
                @Override
                public void execute() {
                    engine.executeQuery("SELECT COUNT(*) FROM TABLE(TAG_REFERENCES_ALL_COLUMNS('tt', 'TABLE'))");
                }
            }).getMessage());
    }
}
