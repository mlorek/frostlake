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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests ALTER TABLE a SWAP WITH b — exchanging two tables' row storage.
 */
public class SwapWithTest extends BaseDatabaseTest {

    private long count(final String table) {
        return ((Number) engine.executeQuery("SELECT COUNT(*) FROM " + table)
            .getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void swapExchangesData() {
        engine.execute("CREATE TABLE blue (id INTEGER)");
        engine.execute("CREATE TABLE green (id INTEGER)");
        engine.execute("INSERT INTO blue VALUES (1), (2)");
        engine.execute("INSERT INTO green VALUES (99)");

        engine.execute("ALTER TABLE blue SWAP WITH green");

        assertEquals(1, count("blue"));    // blue now holds green's single row
        assertEquals(2, count("green"));   // green now holds blue's two rows
        final ResultSet rs = engine.executeQuery("SELECT id FROM blue");
        assertEquals(99, ((Number) rs.getRows().get(0).getValue(0)).intValue());
    }
}
