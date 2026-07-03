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
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * UNPIVOT excludes rows with a NULL unpivoted value by default (Snowflake semantics), and the optional
 * {@code INCLUDE NULLS} / {@code EXCLUDE NULLS} modifier controls it. Previously NULL rows were always
 * emitted and the modifier failed to parse.
 */
public class UnpivotNullsTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE monthly_sales (empid NUMBER, jan NUMBER, feb NUMBER, mar NUMBER, apr NUMBER)");
        // empid 2 has a NULL for feb.
        engine.execute("INSERT INTO monthly_sales VALUES (1, 10, 20, 30, 40), (2, 50, NULL, 70, 80)");
    }

    @Test
    public void excludesNullsByDefault() {
        final ResultSet rs = engine.executeQuery(
                "SELECT * FROM monthly_sales UNPIVOT (sales FOR month IN (jan, feb, mar, apr))");
        // 4 rows for empid 1 + 3 for empid 2 (feb NULL dropped) = 7.
        assertEquals(7, rs.getRows().size());
        for (final Row r : rs.getRows()) {
            assertFalse(r.getValues().contains(null), "no NULL sales value should be emitted by default");
        }
    }

    @Test
    public void excludeNullsModifierMatchesDefault() {
        final ResultSet rs = engine.executeQuery(
                "SELECT * FROM monthly_sales UNPIVOT EXCLUDE NULLS (sales FOR month IN (jan, feb, mar, apr))");
        assertEquals(7, rs.getRows().size());
    }

    @Test
    public void includeNullsKeepsNullRows() {
        final ResultSet rs = engine.executeQuery(
                "SELECT * FROM monthly_sales UNPIVOT INCLUDE NULLS (sales FOR month IN (jan, feb, mar, apr))");
        // All 8 (2 rows x 4 columns), including the NULL feb for empid 2.
        assertEquals(8, rs.getRows().size());
    }
}
