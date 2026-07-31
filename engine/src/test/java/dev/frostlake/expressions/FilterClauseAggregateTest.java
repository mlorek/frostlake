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

package dev.frostlake.expressions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The {@code FILTER (WHERE cond)} aggregate clause does not exist in Snowflake — live-verified: every
 * use is a syntax error, in plain selects, grouped queries, larger expressions, HAVING, and combined
 * with OVER. The Snowflake-portable equivalents (a CASE-wrapped aggregate argument and COUNT_IF) are
 * covered as positives, and FILTER stays usable as a plain identifier.
 */
public class FilterClauseAggregateTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE TABLE ft (a INTEGER, b INTEGER, c INTEGER, g INTEGER)");
        engine.execute("INSERT INTO ft VALUES (1, 1, 1, 1), (100, 100, -1, 1), (7, 7, 2, 2)");
    }

    private Object scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getRows().get(0).getValue(0);
    }

    private void assertRejected(final String sql) {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
    }

    @Test
    public void filterOnAggregatesIsRejected() {
        assertRejected("SELECT SUM(a) FILTER(WHERE c > 0) FROM ft");
        assertRejected("SELECT COUNT(a) FILTER(WHERE c > 0) FROM ft");
        assertRejected("SELECT g, SUM(a) FILTER(WHERE c > 0) FROM ft GROUP BY g ORDER BY g");
        assertRejected("SELECT MIN_BY(a, b) FILTER(WHERE c = 2) FROM ft");
        assertRejected("SELECT SUM(a) FILTER(WHERE c > 0) + 1 FROM ft");
        assertRejected("SELECT g FROM ft GROUP BY g HAVING SUM(a) FILTER(WHERE c > 0) > 5");
    }

    @Test
    public void filterWithOverIsRejected() {
        assertRejected("SELECT SUM(a) FILTER(WHERE c > 0) OVER (PARTITION BY g) FROM ft");
    }

    @Test
    public void conditionalAggregationUsesCaseOrCountIf() {
        // The Snowflake-portable spellings of a conditional aggregate.
        assertEquals(8L, ((Number) scalar("SELECT SUM(CASE WHEN c > 0 THEN a END) FROM ft")).longValue());
        assertEquals(2L, ((Number) scalar("SELECT COUNT_IF(c > 0) FROM ft")).longValue());
        assertEquals(108L, ((Number) scalar("SELECT SUM(a) FROM ft")).longValue(),
            "the unconditional aggregate still sees every row");
    }

    @Test
    public void filterRemainsUsableAsAnIdentifier() {
        engine.execute("CREATE TABLE ftab (filter VARCHAR)");
        engine.execute("INSERT INTO ftab VALUES ('x')");
        assertEquals("x", scalar("SELECT filter FROM ftab"));
        assertEquals("FILTER", engine.executeQuery("SELECT 1 AS filter").getColumns().get(0).getName());
    }
}
