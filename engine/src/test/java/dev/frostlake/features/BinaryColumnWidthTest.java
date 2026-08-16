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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A BINARY column's declared width is enforced on every write. A value wider than the column is refused
 * rather than truncated, on the DML envelope that names the table and the column, and the value is
 * spelled as upper-case hex whatever it was written as. Live-verified.
 */
public class BinaryColumnWidthTest extends BaseDatabaseTest {

    /** Asserts a statement is refused with a message carrying {@code fragment}. */
    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql);
        assertTrue(refused.getMessage() != null && refused.getMessage().contains(fragment),
            sql + " should be refused with \"" + fragment + "\" but read: " + refused.getMessage());
    }

    /** The row count of a table. */
    private String rowCount(final String table) {
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM " + table);
        return rs.getRows().get(0).getValue(0).toString();
    }

    private void createNarrowTable() {
        engine.execute("CREATE OR REPLACE TABLE bin_width (b BINARY(1), v VARBINARY)");
    }

    /** Every DML path enforces it, in the same words. */
    @Test
    public void everyWritePathRefusesATooWideValue() {
        createNarrowTable();
        final String tooLong = "Binary value '0102' is too long and would be truncated";
        assertRefused("INSERT INTO bin_width (b) VALUES (X'0102')", tooLong);
        assertRefused("INSERT INTO bin_width (b) SELECT X'0102'", tooLong);
        assertRefused("INSERT INTO bin_width (b) VALUES (TO_BINARY('0102'))", tooLong);
        engine.execute("INSERT INTO bin_width (b) VALUES (X'01')");
        assertRefused("UPDATE bin_width SET b = X'0102'", tooLong);
        assertRefused("MERGE INTO bin_width t USING (SELECT X'0102' AS nb) s ON 1 = 0"
            + " WHEN NOT MATCHED THEN INSERT (b) VALUES (s.nb)", tooLong);
        assertRefused("MERGE INTO bin_width t USING (SELECT X'0102' AS nb) s ON 1 = 1"
            + " WHEN MATCHED THEN UPDATE SET t.b = s.nb", tooLong);
        assertEquals("1", rowCount("bin_width"));
    }

    /** The refusal names the table and the column, as the DML envelope does. */
    @Test
    public void theRefusalNamesTheTableAndColumn() {
        createNarrowTable();
        assertRefused("INSERT INTO bin_width (b) VALUES (X'0102')",
            "DML operation to table BIN_WIDTH failed on column B with error:");
    }

    /** The value is spelled as upper-case hex, whichever way it was written. */
    @Test
    public void theValueIsSpelledAsUpperCaseHex() {
        createNarrowTable();
        assertRefused("INSERT INTO bin_width (b) VALUES (X'0aff')",
            "Binary value '0AFF' is too long and would be truncated");
        assertRefused("INSERT INTO bin_width (b) VALUES (TO_BINARY('YWJj', 'BASE64'))",
            "Binary value '616263' is too long and would be truncated");
    }

    /** A value that fits, a NULL, and a VARBINARY column are all written. */
    @Test
    public void aFittingValueIsWritten() {
        createNarrowTable();
        engine.execute("INSERT INTO bin_width (b) VALUES (X'01')");
        engine.execute("INSERT INTO bin_width (b) VALUES (NULL)");
        engine.execute("INSERT INTO bin_width (v) VALUES (X'0102030405')");
        engine.execute("UPDATE bin_width SET b = X'02' WHERE b = X'01'");
        assertEquals("3", rowCount("bin_width"));
    }

    /** A computed value is judged by what it produces, not by what its source column declared. */
    @Test
    public void aComputedValueIsJudgedByItsResult() {
        engine.execute("CREATE OR REPLACE TABLE bin_source (b5 BINARY(5))");
        engine.execute("INSERT INTO bin_source VALUES (X'0102030405')");
        engine.execute("CREATE OR REPLACE TABLE bin_target (d BINARY(5))");
        assertRefused("INSERT INTO bin_target (d) SELECT b5 || b5 FROM bin_source",
            "Binary value '01020304050102030405' is too long and would be truncated");
        assertEquals("0", rowCount("bin_target"));
    }

    /** A CTAS with a declared column list is a write like any other. */
    @Test
    public void aCtasWithADeclaredWidthRefusesToo() {
        assertRefused("CREATE OR REPLACE TABLE bin_ctas (b BINARY(1)) AS SELECT X'0102'",
            "Binary value '0102' is too long and would be truncated");
    }
}
