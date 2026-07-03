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

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SHOW FUNCTIONS lists the built-in function library (is_builtin = 'Y') as well as user-defined
 * functions, matching Snowflake. Previously it returned only user functions, so a fresh session with
 * no user functions produced zero rows.
 */
public class ShowFunctionsTest extends BaseDatabaseTest {

    // Column ordinals of the SHOW FUNCTIONS result set.
    private static final int NAME = 1;
    private static final int IS_BUILTIN = 3;

    @Test
    public void listsBuiltinsWhenNoUserFunctionsExist() {
        final ResultSet rs = engine.executeQuery("SHOW FUNCTIONS");
        assertTrue(rs.getRowCount() > 100,
            "SHOW FUNCTIONS should list the built-in library; got " + rs.getRowCount() + " rows");

        final Set<String> names = new HashSet<String>();
        boolean sawBuiltin = false;
        for (final Row row : rs.getRows()) {
            names.add(String.valueOf(row.getValue(NAME)).toUpperCase());
            if ("Y".equals(row.getValue(IS_BUILTIN))) {
                sawBuiltin = true;
            }
        }
        assertTrue(sawBuiltin, "expected rows flagged is_builtin = 'Y'");
        assertTrue(names.contains("UPPER"), "expected built-in scalar UPPER");
        assertTrue(names.contains("ABS"), "expected built-in scalar ABS");
        assertTrue(names.contains("SUM"), "expected built-in aggregate SUM");
    }

    @Test
    public void listsUserFunctionsAlongsideBuiltins() {
        engine.execute("CREATE FUNCTION my_udf(x INTEGER) RETURNS INTEGER AS 'x + 1'");
        final ResultSet rs = engine.executeQuery("SHOW FUNCTIONS");

        boolean sawUdf = false;
        boolean sawBuiltin = false;
        for (final Row row : rs.getRows()) {
            final String name = String.valueOf(row.getValue(NAME)).toUpperCase();
            if (name.equals("MY_UDF")) {
                sawUdf = true;
                assertEquals("N", row.getValue(IS_BUILTIN), "a user function is not a built-in");
            }
            if ("Y".equals(row.getValue(IS_BUILTIN))) {
                sawBuiltin = true;
            }
        }
        assertTrue(sawUdf, "the user-defined function should be listed");
        assertTrue(sawBuiltin, "built-in functions should be listed alongside it");
    }
}
