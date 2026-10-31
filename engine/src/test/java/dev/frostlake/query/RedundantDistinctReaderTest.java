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

import org.junit.jupiter.api.Test;

/**
 * A SELECT DISTINCT body read by a block that cannot tell duplicate rows apart loses its DISTINCT, so an item the
 * block never reads is never computed; TOP, LIMIT, an ORDER BY, a window and a join keep it (all live-verified).
 */
public class RedundantDistinctReaderTest extends MeasuredCellsTestSupport {

    @Test
    public void distinctOverUnion() {
        engine.execute("CREATE OR REPLACE TABLE ct (s VARCHAR, c VARCHAR COLLATE 'en-ci', d VARCHAR COLLATE 'en-cs')");
        engine.execute("INSERT INTO ct VALUES ('a', 'a', 'a'), ('A', 'A', 'A'), ('b', 'b', 'b'), ('B', 'B', 'B')");
        assertAnswer("A ;; B ;; a ;; b",
            "SELECT DISTINCT y FROM (SELECT DISTINCT * FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM "
                + "ct)) ORDER BY 1");
        assertAnswer("A ;; B ;; a ;; b",
            "SELECT DISTINCT y FROM (SELECT DISTINCT x, y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM "
                + "ct)) ORDER BY 1");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "SELECT COUNT(DISTINCT y) FROM (SELECT DISTINCT * FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s "
                + "FROM ct))");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "SELECT y FROM (SELECT DISTINCT * FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct)) ORDER "
                + "BY 1");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "SELECT DISTINCT * FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct)");
        assertAnswer("A ;; B ;; a ;; b",
            "SELECT DISTINCT y FROM (SELECT DISTINCT * FROM (SELECT d AS x, s AS y FROM ct UNION SELECT 1, s FROM "
                + "ct)) ORDER BY 1");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }

    @Test
    public void distinctReaders() {
        engine.execute("CREATE OR REPLACE TABLE ct (s VARCHAR, c VARCHAR COLLATE 'en-ci', d VARCHAR COLLATE 'en-cs')");
        engine.execute("INSERT INTO ct VALUES ('a', 'a', 'a'), ('A', 'A', 'A'), ('b', 'b', 'b'), ('B', 'B', 'B')");
        engine.execute("CREATE OR REPLACE VIEW vd AS SELECT DISTINCT * FROM (SELECT c AS x, s AS y FROM ct UNION "
            + "SELECT 1, s FROM ct)");
        assertAnswer("b",
            "SELECT MAX(y) FROM (SELECT DISTINCT * FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct))");
        assertAnswer("A ;; B ;; a ;; b",
            "WITH q AS (SELECT DISTINCT * FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct)) SELECT "
                + "DISTINCT y FROM q ORDER BY 1");
        assertAnswer("A ;; B ;; a ;; b",
            "SELECT DISTINCT y FROM (SELECT DISTINCT * FROM (SELECT DISTINCT * FROM (SELECT c AS x, s AS y FROM ct "
                + "UNION SELECT 1, s FROM ct))) ORDER BY 1");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "SELECT DISTINCT y FROM (SELECT DISTINCT TOP 10 * FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s "
                + "FROM ct)) ORDER BY 1");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "SELECT DISTINCT y FROM (SELECT DISTINCT * FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM "
                + "ct) LIMIT 10) ORDER BY 1");
        assertAnswer("A ;; B ;; a ;; b",
            "SELECT DISTINCT y FROM (SELECT DISTINCT s AS y, 1 / (LENGTH(s) - 1) AS z FROM ct) ORDER BY 1");
        assertAnswer("b",
            "SELECT DISTINCT y FROM (SELECT DISTINCT * FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM "
                + "ct)) WHERE y > 'a' ORDER BY 1");
        assertAnswer("A ;; B ;; a ;; b",
            "SELECT DISTINCT y FROM (SELECT DISTINCT y, x FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM "
                + "ct)) ORDER BY 1");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "SELECT COUNT(*) FROM (SELECT DISTINCT * FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct))");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "SELECT DISTINCT y FROM (SELECT DISTINCT *, COUNT(*) OVER () AS n FROM (SELECT c AS x, s AS y FROM ct "
                + "UNION SELECT 1, s FROM ct)) ORDER BY 1");
        assertAnswer("A|A ;; B|B ;; a|a ;; b|b",
            "SELECT y, MIN(y) FROM (SELECT DISTINCT * FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM "
                + "ct)) GROUP BY y ORDER BY 1");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "SELECT DISTINCT t1.y FROM (SELECT DISTINCT * FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM "
                + "ct)) t1, ct ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 105 | found QUALIFY clause but no "
                + "window function.",
            "SELECT DISTINCT y FROM (SELECT DISTINCT * FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM "
                + "ct) QUALIFY TRUE) ORDER BY 1");
        assertAnswer("A ;; B ;; a ;; b",
            "SELECT DISTINCT y FROM (SELECT DISTINCT * EXCLUDE x FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, "
                + "s FROM ct)) ORDER BY 1");
        assertAnswer("A ;; B ;; a ;; b", "SELECT DISTINCT y FROM vd ORDER BY 1");
        assertAnswer("REFUSED Division by zero",
            "SELECT DISTINCT y FROM (SELECT DISTINCT s AS y, 1 / (LENGTH(s) - 1) AS z FROM ct) WHERE z IS NULL");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
