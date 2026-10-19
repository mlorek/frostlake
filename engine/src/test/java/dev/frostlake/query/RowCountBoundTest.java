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

import static dev.frostlake.query.QueryAnswers.assertCells;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

/**
 * A row count is taken up to 2^62 - 1 and refused past it, alike in LIMIT, FETCH, TOP and OFFSET (live-verified).
 */
public class RowCountBoundTest extends BaseDatabaseTest {

    private static String invalid(final String count, final String clause) {
        return "SQL compilation error:|Invalid row count '" + count + "' in " + clause + " clause";
    }

    @Test
    public void countsUpToTheBoundAreTaken() {
        engine.execute("CREATE TABLE t AS SELECT SEQ4() AS i FROM TABLE(GENERATOR(ROWCOUNT => 3))");
        assertCells(engine, new String[][] {
            {"SELECT COUNT(*) FROM (SELECT TOP 2147483648 i FROM t)", "3"},
            {"SELECT COUNT(*) FROM (SELECT TOP 4611686018427387903 i FROM t)", "3"},
            {"SELECT COUNT(*) FROM (SELECT i FROM t LIMIT 4611686018427387903)", "3"},
            {"SELECT COUNT(*) FROM (SELECT i FROM t ORDER BY i FETCH FIRST 2147483648 ROWS ONLY)", "3"},
            {"SELECT COUNT(*) FROM (SELECT TOP 0 i FROM t)", "0"},
        });
    }

    @Test
    public void countsPastTheBoundAreRefused() {
        engine.execute("CREATE TABLE t AS SELECT SEQ4() AS i FROM TABLE(GENERATOR(ROWCOUNT => 3))");
        assertCells(engine, new String[][] {
            {"SELECT COUNT(*) FROM (SELECT TOP 4611686018427387904 i FROM t)", invalid("4611686018427387904", "limit")},
            {"SELECT COUNT(*) FROM (SELECT i FROM t LIMIT 4611686018427387904)",
                invalid("4611686018427387904", "limit")},
            {"SELECT COUNT(*) FROM (SELECT i FROM t LIMIT 9223372036854775807)",
                invalid("9223372036854775807", "limit")},
            {"SELECT COUNT(*) FROM (SELECT i FROM t ORDER BY i FETCH FIRST 4611686018427387904 ROWS ONLY)",
                invalid("4611686018427387904", "limit")},
            {"SELECT COUNT(*) FROM (SELECT i FROM t LIMIT 1 OFFSET 4611686018427387904)",
                invalid("4611686018427387904", "result offset")},
            {"SELECT COUNT(*) FROM (SELECT TOP 99999999999999999999 i FROM t)",
                invalid("99999999999999999999", "limit")},
        });
    }
}
