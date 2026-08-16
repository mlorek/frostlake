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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A narrowing VARCHAR or CHAR cast of a text value, compared by equality or IN with constants that fit its
 * width inside a WHERE or an ON, is answered without converting: the equality can only hold when the value
 * fits too. Every other use of the cast still refuses a value that does not fit. Every cell is
 * live-verified.
 */
public class NarrowingCastFilterTest extends BaseDatabaseTest {

    /** Every row's cells, a comma between cells and a bar between rows. */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                out.append(row.getValue(i));
            }
        }
        return out.toString();
    }

    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(fragment), refused.getMessage());
    }

    /** In a WHERE, a JOIN ON and a DML WHERE, either way round, inside AND and OR, nested or parenthesised. */
    @Test
    public void aFittingEqualityIsAnsweredWithoutTheWidthCheck() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P448_DB");
            engine.execute("CREATE OR REPLACE TABLE P448_DB.PUBLIC.NC (s VARCHAR)");
            engine.execute("INSERT INTO P448_DB.PUBLIC.NC VALUES ('abcdefgh')");
            engine.execute("CREATE OR REPLACE TABLE P448_DB.PUBLIC.NC2 (t VARCHAR)");
            engine.execute("INSERT INTO P448_DB.PUBLIC.NC2 VALUES ('x')");
            assertEquals("0",
                rows("SELECT COUNT(*) FROM NC WHERE CAST(s AS VARCHAR(5)) = 'x'"));
            assertEquals("0",
                rows("SELECT COUNT(*) FROM NC WHERE 'x' = CAST(s AS VARCHAR(5))"));
            assertEquals("0",
                rows("SELECT COUNT(*) FROM NC WHERE CAST(s AS VARCHAR(5)) = 'x' AND 1 = 1"));
            assertEquals("0",
                rows("SELECT COUNT(*) FROM NC WHERE CAST(s AS VARCHAR(5)) = 'x' OR 1 = 0"));
            assertRefused("SELECT COUNT(*) FROM NC WHERE NOT (CAST(s AS VARCHAR(5)) = 'x')",
                "String 'abcdefgh' is too long and would be truncated");
            assertEquals("0",
                rows("SELECT COUNT(*) FROM NC JOIN NC2 ON CAST(NC.s AS VARCHAR(5)) = 'x'"));
            assertEquals("0",
                rows("SELECT COUNT(*) FROM NC WHERE CAST(s AS VARCHAR(5)) = CAST('x' AS VARCHAR(1))"));
            engine.execute("DELETE FROM NC WHERE CAST(s AS VARCHAR(5)) = 'x'");
            engine.execute("UPDATE NC SET s = s WHERE CAST(s AS VARCHAR(5)) = 'x'");
            assertEquals("0",
                rows("SELECT COUNT(*) FROM NC WHERE CAST(s AS VARCHAR(5)) = 'x' AND CAST(s AS VARCHAR(3)) = 'y'"));
            assertRefused("SELECT COUNT(*) FROM NC WHERE CAST(s AS VARCHAR(5)) IN ('x', 'abcdefgh')",
                "String 'abcdefgh' is too long and would be truncated");
            assertRefused("SELECT COUNT(*) FROM NC WHERE CAST(s AS VARCHAR(5)) = 'x' OR CAST(s AS VARCHAR(5)) > 'a'",
                "String 'abcdefgh' is too long and would be truncated");
            assertEquals("0",
                rows("SELECT COUNT(*) FROM NC WHERE (CAST(s AS VARCHAR(5))) = 'x'"));
            assertEquals("0",
                rows("SELECT COUNT(*) FROM NC WHERE CAST(CAST(s AS VARCHAR(5)) AS VARCHAR(5)) = 'x'"));
            assertEquals("0",
                rows("SELECT COUNT(*) FROM NC WHERE CAST(s AS VARCHAR(5)) = ('x')"));
            assertRefused("SELECT COUNT(*) FROM NC WHERE CAST(s AS VARCHAR(5)) = 'x' QUALIFY 1 = 1",
                "found QUALIFY clause but no window function.");
            assertEquals("0",
                rows("SELECT COUNT(*) FROM (SELECT s FROM NC WHERE CAST(s AS VARCHAR(5)) = 'x')"));
            assertEquals("0",
                rows("SELECT COUNT(*) FROM NC WHERE s::VARCHAR(5) = 'x ' "));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P448_DB");
        }
    }

    /** A literal that does not fit, other operators, wraps, a literal or non-text source, and the select list. */
    @Test
    public void everyOtherUseStillChecksTheWidth() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P448B_DB");
            engine.execute("CREATE OR REPLACE TABLE P448B_DB.PUBLIC.NC (s VARCHAR)");
            engine.execute("INSERT INTO P448B_DB.PUBLIC.NC VALUES ('abcdefgh')");
            engine.execute("CREATE OR REPLACE TABLE P448B_DB.PUBLIC.T (n INT, b BOOLEAN)");
            engine.execute("INSERT INTO P448B_DB.PUBLIC.T VALUES (123, TRUE)");
            engine.execute("CREATE OR REPLACE VIEW P448B_DB.PUBLIC.V AS SELECT CAST(s AS VARCHAR(5)) AS c FROM P448B_DB.PUBLIC.NC");
            engine.execute("CREATE OR REPLACE VIEW P448B_DB.PUBLIC.V2 AS SELECT s FROM P448B_DB.PUBLIC.NC WHERE CAST(s AS VARCHAR(5)) = 'x'");
            assertEquals("0",
                rows("SELECT COUNT(*) FROM NC WHERE CAST(s AS VARCHAR(5)) = 'abcde'"));
            assertEquals("0",
                rows("SELECT COUNT(*) FROM NC WHERE s::VARCHAR(5) = 'x'"));
            assertEquals("0",
                rows("SELECT COUNT(*) FROM NC WHERE CAST(s AS VARCHAR(5)) IN ('x', 'y')"));
            assertEquals("0",
                rows("SELECT COUNT(*) FROM NC WHERE CAST(s AS CHAR(5)) = 'x'"));
            assertRefused("SELECT COUNT(*) FROM NC WHERE CAST(s AS VARCHAR(5)) = 'abcdefgh'",
                "String 'abcdefgh' is too long and would be truncated");
            assertRefused("SELECT COUNT(*) FROM NC WHERE CAST(s AS VARCHAR(5)) <> 'x'",
                "String 'abcdefgh' is too long and would be truncated");
            assertRefused("SELECT COUNT(*) FROM NC WHERE CAST(s AS VARCHAR(5)) > 'a'",
                "String 'abcdefgh' is too long and would be truncated");
            assertRefused("SELECT COUNT(*) FROM NC WHERE CAST(s AS VARCHAR(5)) LIKE 'a%'",
                "String 'abcdefgh' is too long and would be truncated");
            assertRefused("SELECT COUNT(*) FROM NC WHERE CAST(s AS VARCHAR(5)) IS NOT NULL",
                "String 'abcdefgh' is too long and would be truncated");
            assertRefused("SELECT COUNT(*) FROM NC WHERE UPPER(CAST(s AS VARCHAR(5))) = 'X'",
                "String 'abcdefgh' is too long and would be truncated");
            assertRefused("SELECT COUNT(*) FROM NC WHERE LENGTH(CAST(s AS VARCHAR(5))) = 8",
                "String 'abcdefgh' is too long and would be truncated");
            assertRefused("SELECT COUNT(*) FROM NC WHERE CAST('abcdefgh' AS VARCHAR(5)) = 'x'",
                "String 'abcdefgh' is too long and would be truncated");
            assertRefused("SELECT CAST(s AS VARCHAR(5)) = 'abcdefgh' FROM NC",
                "String 'abcdefgh' is too long and would be truncated");
            assertRefused("SELECT IFF(CAST(s AS VARCHAR(5)) = 'x', 1, 0) FROM NC",
                "String 'abcdefgh' is too long and would be truncated");
            assertRefused("SELECT COUNT(*) FROM T WHERE n::VARCHAR(2) = '1'",
                "String '123' is too long and would be truncated");
            assertRefused("SELECT COUNT(*) FROM T WHERE n::VARCHAR(2) IN ('1', '2')",
                "String '123' is too long and would be truncated");
            assertRefused("SELECT COUNT(*) FROM T WHERE b::VARCHAR(2) = 'x'",
                "String 'true' is too long and would be truncated");
            assertRefused("SELECT COUNT(*) FROM T WHERE 123::VARCHAR(2) = '1'",
                "String '123' is too long and would be truncated");
            assertEquals("0",
                rows("SELECT COUNT(*) FROM V2"));
            assertEquals("0",
                rows("SELECT COUNT(*) FROM NC WHERE TRY_CAST(s AS VARCHAR(5)) = 'x'"));
            assertEquals("0",
                rows("SELECT COUNT(*) FROM NC WHERE CAST(s AS VARCHAR(5)) = 'x' AND s IS NOT NULL"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P448B_DB");
        }
    }
}
