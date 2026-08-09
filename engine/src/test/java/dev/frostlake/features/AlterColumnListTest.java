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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ALTER TABLE's column-action LIST, live-verified:
 * {@code ALTER TABLE t {ALTER | MODIFY} [(] [COLUMN] c1 action [, [COLUMN] c2 action]* [)]}.
 * COLUMN is optional per item and the parens are optional. The list is atomic — every item is
 * validated before any is applied. IF EXISTS forgives ONLY the named table's absence: a missing
 * schema still errors, and so does an error the action itself raises.
 */
public class AlterColumnListTest extends BaseDatabaseTest {

    @BeforeEach
    public void createTable() {
        engine.execute("CREATE SCHEMA IF NOT EXISTS sch");
        engine.execute("CREATE OR REPLACE TABLE sch.tbl (col1 INT DEFAULT 5, col2 INT NOT NULL)");
    }

    private String describeCell(final String column, final String property) {
        final ResultSet rs = engine.executeQuery("DESCRIBE TABLE sch.tbl");
        final int nameIdx = rs.getColumnIndex("name");
        final int propIdx = rs.getColumnIndex(property);
        for (int i = 0; i < rs.getRowCount(); i++) {
            if (column.equals(rs.getRows().get(i).getValue(nameIdx))) {
                return String.valueOf(rs.getRows().get(i).getValue(propIdx));
            }
        }
        throw new AssertionError("no column " + column);
    }

    private void assertBothApplied() {
        assertEquals("null", describeCell("COL1", "default"), "col1 default should be dropped");
        assertEquals("Y", describeCell("COL2", "null?"), "col2 should be nullable");
    }

    @Test
    public void aMultiColumnListAppliesEveryAction() {
        engine.execute("""
            ALTER TABLE IF EXISTS sch.tbl
                ALTER COLUMN col1 DROP DEFAULT,
                    col2 DROP NOT NULL""");
        assertBothApplied();
    }

    @Test
    public void theColumnKeywordMayBeRepeatedOrAbsent() {
        engine.execute("ALTER TABLE sch.tbl ALTER COLUMN col1 DROP DEFAULT, COLUMN col2 DROP NOT NULL");
        assertBothApplied();

        engine.execute("CREATE OR REPLACE TABLE sch.tbl (col1 INT DEFAULT 5, col2 INT NOT NULL)");
        engine.execute("ALTER TABLE sch.tbl ALTER col1 DROP DEFAULT, col2 DROP NOT NULL");
        assertBothApplied();
    }

    @Test
    public void theListMayBeParenthesizedAndModifySpells() {
        engine.execute("ALTER TABLE sch.tbl ALTER (col1 DROP DEFAULT, col2 DROP NOT NULL)");
        assertBothApplied();

        engine.execute("CREATE OR REPLACE TABLE sch.tbl (col1 INT DEFAULT 5, col2 INT NOT NULL)");
        engine.execute("ALTER TABLE sch.tbl MODIFY COLUMN col1 DROP DEFAULT, col2 DROP NOT NULL");
        assertBothApplied();
    }

    /** A list whose second item names an unknown column changes NOTHING — the list is atomic. */
    @Test
    public void aFailingItemLeavesTheWholeListUnapplied() {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE sch.tbl ALTER (col1 DROP DEFAULT, nosuch DROP NOT NULL)");
            }
        });
        assertTrue(e.getMessage().contains("invalid identifier 'NOSUCH'"), "got: " + e.getMessage());
        assertEquals("5", describeCell("COL1", "default"), "col1 default must be untouched");
    }

    @Test
    public void ifExistsForgivesOnlyTheMissingTable() {
        // Absent table: a clean no-op.
        engine.execute("ALTER TABLE IF EXISTS sch.nope ALTER COLUMN c DROP DEFAULT");

        // Absent SCHEMA: still an error, IF EXISTS or not.
        final RuntimeException schemaMissing = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE IF EXISTS nosch.tbl ALTER COLUMN c DROP DEFAULT");
            }
        });
        assertTrue(schemaMissing.getMessage().contains("Schema '")
                && schemaMissing.getMessage().contains(".NOSCH' does not exist or not authorized."),
            "got: " + schemaMissing.getMessage());

        // An error from the action itself: still an error, even under IF EXISTS.
        final RuntimeException badColumn = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE IF EXISTS sch.tbl ALTER COLUMN nosuch DROP DEFAULT");
            }
        });
        assertTrue(badColumn.getMessage().contains("invalid identifier 'NOSUCH'"),
            "got: " + badColumn.getMessage());
    }
}
