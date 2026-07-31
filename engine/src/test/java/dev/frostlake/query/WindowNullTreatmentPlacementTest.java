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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Null-treatment placement for window value functions: {@code IGNORE | RESPECT NULLS} may sit
 * INSIDE the argument parens ({@code FIRST_VALUE(x IGNORE NULLS) OVER ...}) as well as between the
 * call and OVER, and NTH_VALUE additionally takes {@code FROM FIRST | FROM LAST} (counting from the
 * partition end). Their window specification REQUIRES an ORDER BY (Snowflake rejects it otherwise),
 * and the default frame is the WHOLE partition.
 */
public class WindowNullTreatmentPlacementTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE TABLE w (id INTEGER, grp INTEGER, v VARCHAR)");
        engine.execute("INSERT INTO w VALUES (1, 1, NULL), (2, 1, 'b'), (3, 1, 'c')");
    }

    private Object first(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getRows().get(0).getValue(0);
    }

    @Test
    public void nullTreatmentInsideTheArgumentParens() {
        // The default frame is the whole partition, so even the first row sees every value.
        assertEquals("b", first(
            "SELECT FIRST_VALUE(v IGNORE NULLS) OVER (PARTITION BY grp ORDER BY id) FROM w LIMIT 1"));
        assertNull(first(
            "SELECT FIRST_VALUE(v RESPECT NULLS) OVER (PARTITION BY grp ORDER BY id) FROM w LIMIT 1"));
        // The pre-existing outside-parens position keeps working.
        assertEquals("b", first(
            "SELECT FIRST_VALUE(v) IGNORE NULLS OVER (PARTITION BY grp ORDER BY id) FROM w LIMIT 1"));
    }

    @Test
    public void nthValueFromLastCountsBackwards() {
        assertEquals("b", first(
            "SELECT NTH_VALUE(v, 2) FROM LAST RESPECT NULLS OVER (PARTITION BY grp ORDER BY id) FROM w LIMIT 1"));
        assertEquals("b", first(
            "SELECT NTH_VALUE(v, 2) FROM LAST IGNORE NULLS OVER (PARTITION BY grp ORDER BY id) FROM w LIMIT 1"));
        assertEquals("c", first(
            "SELECT NTH_VALUE(v, 2) FROM FIRST IGNORE NULLS OVER (PARTITION BY grp ORDER BY id) FROM w LIMIT 1"));
        assertEquals("b", first(
            "SELECT NTH_VALUE(v, 2) OVER (PARTITION BY grp ORDER BY id) FROM w LIMIT 1"),
            "plain NTH_VALUE still counts from the start");
    }

    @Test
    public void valueWindowFunctionsRequireOrderBy() {
        final RuntimeException rejected = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT FIRST_VALUE(v) OVER (PARTITION BY grp) FROM w");
            }
        });
        assertTrue(rejected.getMessage().contains("requires ORDER BY in window specification"),
            "unexpected: " + rejected.getMessage());
    }

    @Test
    public void tablesNamedFirstAreStillPlainFromSources() {
        engine.execute("CREATE TABLE first (n INTEGER)");
        engine.execute("INSERT INTO first VALUES (7)");
        assertEquals(7L, ((Number) first("SELECT MAX(n) FROM first")).longValue(),
            "FROM first (a real table) must not be eaten by the FROM FIRST window modifier");
    }
}
