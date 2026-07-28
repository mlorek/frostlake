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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Comparing a NUMBER with a BOOLEAN uses Snowflake's implicit numeric-to-boolean coercion: 0 is FALSE,
 * any non-zero number is TRUE — so {@code 1 = TRUE}, {@code 0 = FALSE} and {@code 2 = TRUE} all hold.
 * The idiom appears in real predicates as {@code flag_expr = TRUE} where the flag was computed with
 * {@code IFF(cond, 1, 0)}.
 */
public class BooleanNumericComparisonTest extends BaseDatabaseTest {

    private Object scalar(final String expr) {
        final ResultSet result = engine.executeQuery("SELECT " + expr + " AS r");
        return result.getRows().get(0).getValue(0);
    }

    @Test
    public void numericBooleanEquality() {
        assertEquals(Boolean.TRUE, scalar("1 = TRUE"));
        assertEquals(Boolean.TRUE, scalar("0 = FALSE"));
        assertEquals(Boolean.TRUE, scalar("TRUE = 1"));
        assertEquals(Boolean.TRUE, scalar("2 = TRUE"));
        assertEquals(Boolean.FALSE, scalar("0 = TRUE"));
        assertEquals(Boolean.FALSE, scalar("1 = FALSE"));
        assertEquals(Boolean.TRUE, scalar("1.0 = TRUE"));
    }

    @Test
    public void numericBooleanInequality() {
        assertEquals(Boolean.TRUE, scalar("0 != TRUE"));
        assertEquals(Boolean.FALSE, scalar("1 != TRUE"));
    }

    @Test
    public void iffComputedFlagComparedToBooleanInWhere() {
        engine.execute("CREATE TABLE flags (name VARCHAR, hits INTEGER)");
        engine.execute("INSERT INTO flags VALUES ('with', 3), ('without', 0)");
        final ResultSet result = engine.executeQuery(
            "SELECT name FROM flags WHERE IFF(hits > 0, 1, 0) = TRUE");
        assertEquals(1, result.getRows().size());
        assertEquals("with", result.getRows().get(0).getValue(0));
    }
}
