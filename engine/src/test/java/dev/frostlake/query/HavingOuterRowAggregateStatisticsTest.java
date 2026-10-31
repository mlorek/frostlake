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

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frostlake.DatabaseEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A HAVING aggregate over a subquery whose own aggregate reads only the outer group's column, unfiltered, is refused
 * as the aggregate CORRELATION nests in the outer one, the refusal the account gives where no table statistics apply
 * (over a VALUES list, live-verified). Over a table the account answers such a statement from the table's statistics
 * instead - every group reads the table's maximum, and the query's own WHERE is ignored - an answer the engine does
 * not imitate, so the refusal is held by the embedded engine alone.
 */
public class HavingOuterRowAggregateStatisticsTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE hors");
        engine.execute("USE SCHEMA hors.public");
        engine.execute("CREATE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private String refusal(final String sql) {
        try {
            engine.executeQuery(sql);
            return "answered";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String nested(final String inner, final String outer) {
        return "SQL compilation error: |Aggregate functions cannot be nested: [" + inner + "] nested in [" + outer
            + "]";
    }

    @Test
    public void anUnfilteredSubqueryIsRefusedAsWhereNoStatisticsApply() {
        assertEquals(nested("MAX(FZ.ID)", "MAX(CORRELATION(MAX(FZ.ID)))"),
            refusal("SELECT b FROM fz GROUP BY b HAVING MAX((SELECT MAX(fz.id) FROM g)) = 7 ORDER BY b"));
        assertEquals(nested("MAX(FZ.ID)", "SUM(CORRELATION(MAX(FZ.ID)))"),
            refusal("SELECT b FROM fz WHERE id < 7 GROUP BY b HAVING SUM((SELECT MAX(fz.id) FROM g)) = 14 ORDER BY b"));
        assertEquals(nested("MIN(FZ.ID)", "COUNT(CORRELATION(MIN(FZ.ID)) + 1)"),
            refusal("SELECT b FROM fz GROUP BY b HAVING COUNT((SELECT MIN(fz.id) + 1 FROM g)) = 1 ORDER BY b"));
    }
}
