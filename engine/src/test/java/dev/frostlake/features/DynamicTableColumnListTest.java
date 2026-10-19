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
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The column list a dynamic table declares over its query: the names it gives its columns, the types it casts
 * them to and the comments it gives them, and the lists that are refused.
 */
public class DynamicTableColumnListTest extends BaseDatabaseTest {

    private static final String PREFIX = "SQL compilation error:\n";

    private String options;

    @BeforeEach
    public void aBaseTable() {
        final Object warehouse = engine.executeQuery("SELECT CURRENT_WAREHOUSE()").getRows().get(0).getValue(0);
        options = " TARGET_LAG = '1 hour' WAREHOUSE = " + (warehouse == null ? "COMPUTE_WH" : warehouse) + " ";
        engine.execute("CREATE TABLE base (i INT, n VARCHAR)");
        engine.execute("INSERT INTO base VALUES (1, 'a'), (2, 'b')");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    /** A listing's rows, the named columns of each joined by a bar. */
    private List<String> cells(final String sql, final String... columns) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> out = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            final StringBuilder line = new StringBuilder();
            for (final String column : columns) {
                line.append(line.length() == 0 ? "" : "|").append(row.getValue(rs.getColumnIndex(column)));
            }
            out.add(line.toString());
        }
        return out;
    }

    private List<String> rows(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
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

    @Test
    public void aListOfNamesRenamesTheQuerysColumns() {
        engine.execute("CREATE DYNAMIC TABLE dt2 (x, y)" + options + "AS SELECT i, n FROM base");
        assertEquals(Arrays.asList("1|a", "2|b"), rows("SELECT x, y FROM dt2 ORDER BY 1"));
        assertEquals(Arrays.asList("X|NUMBER(38,0)|null", "Y|VARCHAR(16777216)|null"),
            cells("DESCRIBE DYNAMIC TABLE dt2", "name", "type", "comment"));
        assertTrue(rows("SELECT GET_DDL('DYNAMIC_TABLE', 'dt2')").get(0).contains("dynamic table DT2(\n\tX,\n\tY\n)"));
        assertEquals(Arrays.asList("X", "Y"), cells("SHOW COLUMNS IN TABLE dt2", "column_name"));
        assertEquals(Arrays.asList("X|NUMBER", "Y|TEXT"), rows("SELECT COLUMN_NAME, DATA_TYPE FROM"
            + " INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'DT2' ORDER BY ORDINAL_POSITION"));
    }

    @Test
    public void aTypedColumnCastsTheQuerysValuesAndKeepsItsComment() {
        engine.execute("CREATE DYNAMIC TABLE dt3 (x NUMBER(10,2) COMMENT 'cx', y VARCHAR(5))" + options
            + "AS SELECT i, n FROM base");
        assertEquals(Arrays.asList("1.00|a", "2.00|b"), rows("SELECT x, y FROM dt3 ORDER BY 1"));
        assertEquals(Arrays.asList("X|NUMBER(10,2)|cx", "Y|VARCHAR(5)|null"),
            cells("DESCRIBE DYNAMIC TABLE dt3", "name", "type", "comment"));
        // The types are the columns' own, and are not written back; the comment is.
        assertTrue(rows("SELECT GET_DDL('DYNAMIC_TABLE', 'dt3')").get(0).contains(
            "dynamic table DT3(\n\tX COMMENT 'cx',\n\tY\n)"));
    }

    @Test
    public void aCommentNeedsNoTypeAndAQuotedNameIsKept() {
        engine.execute("CREATE DYNAMIC TABLE dt5 (x COMMENT 'cx', y)" + options + "AS SELECT i, n FROM base");
        assertEquals(Arrays.asList("X|NUMBER(38,0)|cx", "Y|VARCHAR(16777216)|null"),
            cells("DESCRIBE DYNAMIC TABLE dt5", "name", "type", "comment"));
        engine.execute("CREATE DYNAMIC TABLE dt7 (\"x\", \"Y y\")" + options + "AS SELECT i, n FROM base");
        assertEquals(Arrays.asList("x", "Y y"), cells("DESCRIBE DYNAMIC TABLE dt7", "name"));
        assertEquals(Arrays.asList("1|a", "2|b"), rows("SELECT \"x\", \"Y y\" FROM dt7 ORDER BY 1"));
    }

    @Test
    public void aListThatDoesNotFitTheQueryIsRefused() {
        final String create = "CREATE OR REPLACE DYNAMIC TABLE dt4 ";
        assertEquals(PREFIX + "Invalid column definition list", refusal(create + "(x)" + options
            + "AS SELECT i, n FROM base"));
        assertEquals(PREFIX + "Invalid column definition list", refusal(create + "(x, y, z)" + options
            + "AS SELECT i, n FROM base"));
        assertEquals(PREFIX + "duplicate column name 'X'", refusal(create + "(x, x)" + options
            + "AS SELECT i, n FROM base"));
        assertEquals(PREFIX + "incompatible types: [NUMBER(38,0)] and [DATE]", refusal(create
            + "(x DATE, y VARCHAR)" + options + "AS SELECT i, n FROM base"));
    }

    @Test
    public void aValueTheDeclaredTypeCannotHoldFailsTheFirstRefresh() {
        // One row, so that the value the refusal names is the same whichever row is read first.
        engine.execute("CREATE TABLE narrow (i INT, n VARCHAR)");
        engine.execute("INSERT INTO narrow VALUES (2, 'b')");
        final String refused = refusal("CREATE OR REPLACE DYNAMIC TABLE dt6 (x NUMBER(1,0), y VARCHAR(1))" + options
            + "AS SELECT i * 100, n || 'zz' FROM narrow");
        assertTrue(refused.startsWith("SQL compilation error: Failed to refresh dynamic table with refresh_trigger"
            + " INITIAL at data_timestamp "), refused);
        assertTrue(refused.endsWith(" because of the error: SQL compilation error: Target table failed to refresh:"
            + " Number out of representable range: type FIXED[SB2](1,0){nullable}, value 200"), refused);
        assertEquals(0, engine.executeQuery("SHOW DYNAMIC TABLES LIKE 'DT6'").getRowCount());
    }
}
