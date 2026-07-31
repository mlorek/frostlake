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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SHOW BUILTIN FUNCTIONS — the built-in catalog without any user-defined function.
 *
 * <p>Frostlake used to reject the whole statement as a syntax error while a real account runs it, an
 * under-acceptance gap in its own right. Everything asserted here was live-verified on with
 * one UDF in the current schema:
 *
 * <ul>
 *   <li>{@code SHOW BUILTIN FUNCTIONS} returns 1134 rows over 926 distinct names, all
 *       {@code is_builtin = 'Y'};</li>
 *   <li>{@code SHOW FUNCTIONS} returns 1135 — the same 1134 plus that UDF. Comparing the two name sets
 *       leaves nothing on the SHOW BUILTIN FUNCTIONS side and only the UDF on the other, so SHOW FUNCTIONS
 *       is exactly SHOW BUILTIN FUNCTIONS plus the user functions in scope;</li>
 *   <li>{@code SHOW BUILTIN FUNCTIONS LIKE '<that udf>'} returns nothing;</li>
 *   <li>both listings, and SHOW USER FUNCTIONS, carry the same 20 columns;</li>
 *   <li>{@code SHOW BUILTIN USER FUNCTIONS} and {@code SHOW USER BUILTIN FUNCTIONS} are syntax errors —
 *       the two modifiers are mutually exclusive.</li>
 * </ul>
 */
public class ShowBuiltinFunctionsTest extends BaseDatabaseTest {

    private static final int NAME = 1;
    private static final int IS_BUILTIN = 3;

    /** The 20 columns a real account returns, in order (live-verified). */
    private static final String[] LIVE_COLUMNS = {
        "created_on", "name", "schema_name", "is_builtin", "is_aggregate", "is_ansi",
        "min_num_arguments", "max_num_arguments", "arguments", "description", "catalog_name",
        "is_table_function", "valid_for_clustering", "is_secure", "secrets",
        "external_access_integrations", "is_external_function", "language", "is_memoizable",
        "is_data_metric"
    };

    private Set<String> names(final ResultSet rs) {
        final Set<String> names = new HashSet<String>();
        for (final Row row : rs.getRows()) {
            names.add(String.valueOf(row.getValue(NAME)).toUpperCase());
        }
        return names;
    }

    @Test
    public void listsTheBuiltInCatalog() {
        final ResultSet rs = engine.executeQuery("SHOW BUILTIN FUNCTIONS");
        assertTrue(rs.getRowCount() > 400,
            "SHOW BUILTIN FUNCTIONS should list the built-in library; got " + rs.getRowCount() + " rows");
        for (final Row row : rs.getRows()) {
            assertEquals("Y", row.getValue(IS_BUILTIN), "every row of SHOW BUILTIN FUNCTIONS is a built-in");
        }
        final Set<String> names = names(rs);
        assertTrue(names.contains("UPPER"), "expected built-in scalar UPPER");
        assertTrue(names.contains("SUM"), "expected built-in aggregate SUM");
        assertTrue(names.contains("ROW_NUMBER"), "expected built-in window function ROW_NUMBER");
    }

    @Test
    public void columnShapeMatchesLiveSnowflake() {
        final ResultSet rs = engine.executeQuery("SHOW BUILTIN FUNCTIONS");
        assertEquals(LIVE_COLUMNS.length, rs.getColumns().size(),
            "SHOW BUILTIN FUNCTIONS should return the 20 columns a real account returns");
        for (int i = 0; i < LIVE_COLUMNS.length; i++) {
            assertEquals(LIVE_COLUMNS[i], rs.getColumns().get(i).getName(),
                "column " + (i + 1) + " should match the live column order");
        }
    }

    @Test
    public void showFunctionsHasTheSameColumnShape() {
        final ResultSet rs = engine.executeQuery("SHOW FUNCTIONS");
        assertEquals(LIVE_COLUMNS.length, rs.getColumns().size(),
            "SHOW FUNCTIONS returns the same 20 columns as SHOW BUILTIN FUNCTIONS on a real account");
        for (int i = 0; i < LIVE_COLUMNS.length; i++) {
            assertEquals(LIVE_COLUMNS[i], rs.getColumns().get(i).getName(),
                "column " + (i + 1) + " should match the live column order");
        }
    }

    @Test
    public void excludesUserDefinedFunctions() {
        engine.execute("CREATE FUNCTION only_a_udf(x INTEGER) RETURNS INTEGER AS 'x + 1'");

        assertFalse(names(engine.executeQuery("SHOW BUILTIN FUNCTIONS")).contains("ONLY_A_UDF"),
            "SHOW BUILTIN FUNCTIONS lists no user-defined function");
        assertEquals(0, engine.executeQuery("SHOW BUILTIN FUNCTIONS LIKE 'ONLY_A_UDF'").getRowCount(),
            "a LIKE on the UDF's name matches nothing in the built-in catalog");
        assertTrue(names(engine.executeQuery("SHOW FUNCTIONS")).contains("ONLY_A_UDF"),
            "SHOW FUNCTIONS does list it, alongside the built-ins");
    }

    @Test
    public void showFunctionsIsShowBuiltinFunctionsPlusUserFunctions() {
        engine.execute("CREATE FUNCTION extra_udf(x INTEGER) RETURNS INTEGER AS 'x + 1'");

        final Set<String> builtin = names(engine.executeQuery("SHOW BUILTIN FUNCTIONS"));
        final Set<String> all = names(engine.executeQuery("SHOW FUNCTIONS"));

        assertTrue(all.containsAll(builtin), "SHOW FUNCTIONS lists every built-in name");
        final Set<String> onlyInShowFunctions = new HashSet<String>(all);
        onlyInShowFunctions.removeAll(builtin);
        assertEquals(1, onlyInShowFunctions.size(),
            "the only extra name should be the user function; got " + onlyInShowFunctions);
        assertTrue(onlyInShowFunctions.contains("EXTRA_UDF"), onlyInShowFunctions.toString());
    }

    @Test
    public void likeFiltersTheBuiltInCatalog() {
        final ResultSet rs = engine.executeQuery("SHOW BUILTIN FUNCTIONS LIKE 'ROW_NUMBER'");
        assertEquals(1, rs.getRowCount(), "LIKE 'ROW_NUMBER' matches exactly one built-in");
        assertEquals("ROW_NUMBER", String.valueOf(rs.getRows().get(0).getValue(NAME)).toUpperCase());
    }

    @Test
    public void userAndBuiltinModifiersAreMutuallyExclusive() {
        // Live-verified: "syntax error line 1 at position 13 unexpected 'USER'" for the first
        // form and "position 10 unexpected 'BUILTIN'" for the second.
        Assumptions.assumeFalse(isLiveSnowflake(),
            "the live driver reports Snowflake's own syntax error, not Frostlake's exception type");
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SHOW BUILTIN USER FUNCTIONS");
            }
        });
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SHOW USER BUILTIN FUNCTIONS");
            }
        });
    }

    @Test
    public void builtinIsStillUsableAsAnIdentifier() {
        // Adding the BUILTIN keyword must not take the word away from user object names.
        engine.execute("CREATE TABLE builtin (builtin INTEGER)");
        engine.execute("INSERT INTO builtin VALUES (7)");
        assertEquals(1, engine.executeQuery("SELECT builtin FROM builtin").getRowCount());
    }
}
