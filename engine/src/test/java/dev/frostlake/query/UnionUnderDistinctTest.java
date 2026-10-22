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
 * A derived table's, a common table expression's or a view's UNION read by a block that cannot tell duplicate rows
 * apart - DISTINCT, grouped or a column's MAX, MIN or ANY_VALUE, over the relation's columns, constants, COLLATION or
 * SYSTEM$TYPEOF - keeps every row, so a text arm that would fail to convert toward a number arm is converted only
 * where the block reads it; a computed item, COUNT, a join, IN, EXISTS or a plain projection keeps the check and
 * refuses; a collated text beside a number answers COLLATION NULL (all live-verified).
 */
public class UnionUnderDistinctTest extends MeasuredCellsTestSupport {

    @Test
    public void unionsUnderDistinct() {
        engine.execute("CREATE OR REPLACE TABLE ct (s VARCHAR, c VARCHAR COLLATE 'en-ci', d VARCHAR COLLATE 'en-cs')");
        engine.execute("INSERT INTO ct VALUES ('a', 'a', 'a'), ('A', 'A', 'A'), ('b', 'b', 'b'), ('B', 'B', 'B')");
        engine.execute("CREATE OR REPLACE VIEW vu1916 AS SELECT c AS x FROM ct UNION SELECT 1 FROM ct");
        assertAnswer("null", "SELECT DISTINCT COLLATION(x) FROM (SELECT c AS x FROM ct UNION SELECT 1 FROM ct)");
        assertAnswer("null", "SELECT DISTINCT COLLATION(x) FROM (SELECT c AS x FROM ct UNION ALL SELECT 1 FROM ct)");
        assertAnswer("NUMBER(18,5)[SB8]",
            "SELECT DISTINCT SYSTEM$TYPEOF(x) FROM (SELECT c AS x FROM ct UNION SELECT 1 FROM ct)");
        assertAnswer("NUMBER(18,5)[SB8]",
            "SELECT DISTINCT SYSTEM$TYPEOF(x) FROM (SELECT c AS x FROM ct UNION ALL SELECT 1 FROM ct)");
        assertAnswer("8", "SELECT COUNT(*) FROM (SELECT c AS x FROM ct UNION ALL SELECT 1 FROM ct)");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "SELECT COUNT(*) FROM (SELECT c AS x FROM ct UNION SELECT 1 FROM ct)");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "SELECT COLLATION(x) FROM (SELECT c AS x FROM ct UNION SELECT 1 FROM ct) LIMIT 1");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "SELECT 1 FROM (SELECT c AS x FROM ct UNION SELECT 1 FROM ct) LIMIT 1");
        assertAnswer("1", "SELECT DISTINCT 1 FROM (SELECT c AS x FROM ct UNION SELECT 1 FROM ct)");
        assertAnswer("k", "SELECT DISTINCT 'k' FROM (SELECT c AS x FROM ct UNION SELECT 1 FROM ct)");
        assertAnswer("null|1", "SELECT DISTINCT COLLATION(x), 1 FROM (SELECT c AS x FROM ct UNION SELECT 1 FROM ct)");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "SELECT DISTINCT COLLATION(x) FROM (SELECT c AS x FROM ct UNION SELECT 1 FROM ct) WHERE x IS NOT NULL");
        assertAnswer("null",
            "SELECT DISTINCT COLLATION(x) FROM (SELECT c AS x FROM ct UNION SELECT 1 FROM ct) ORDER BY 1");
        assertAnswer("null", "SELECT COLLATION(x) FROM (SELECT c AS x FROM ct UNION SELECT 1 FROM ct) GROUP BY 1");
        assertAnswer("null", "SELECT DISTINCT COLLATION(x) FROM (SELECT c AS x FROM ct UNION SELECT 1 FROM ct) s");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "SELECT DISTINCT COLLATION(x) FROM (SELECT c AS x FROM ct INTERSECT SELECT 1 FROM ct)");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "SELECT DISTINCT COLLATION(x) FROM (SELECT c AS x FROM ct EXCEPT SELECT 1 FROM ct)");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "SELECT DISTINCT COLLATION(x) FROM (SELECT c AS x FROM ct UNION SELECT 1 FROM ct UNION SELECT 2 FROM ct)");
        assertAnswer("null",
            "SELECT DISTINCT COLLATION(x) FROM (SELECT c AS x FROM ct UNION ALL SELECT 1 FROM ct UNION SELECT 2 FROM "
                + "ct)");
        assertAnswer("null",
            "WITH w AS (SELECT c AS x FROM ct UNION SELECT 1 FROM ct) SELECT DISTINCT COLLATION(x) FROM w");
        assertAnswer("null", "SELECT DISTINCT COLLATION(x) FROM vu1916");
        assertAnswer("null",
            "SELECT DISTINCT COLLATION(x) FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct)");
        assertAnswer("A ;; B ;; a ;; b",
            "SELECT DISTINCT y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) ORDER BY 1");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "SELECT DISTINCT x FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct)");
        assertAnswer("A ;; B ;; a ;; b",
            "SELECT y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) GROUP BY y ORDER BY 1");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "SELECT DISTINCT UPPER(y) FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) ORDER BY 1");
        assertAnswer("A ;; B ;; a ;; b",
            "SELECT DISTINCT y FROM (SELECT c AS x, s AS y FROM ct UNION ALL SELECT 1, s FROM ct) ORDER BY 1");
        assertAnswer("A ;; A ;; B ;; B ;; a ;; a ;; b ;; b",
            "SELECT y FROM (SELECT c AS x, s AS y FROM ct UNION ALL SELECT 1, s FROM ct) ORDER BY y");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "SELECT y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) ORDER BY y");
        assertAnswer("b", "SELECT MAX(y) FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct)");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "SELECT COUNT(DISTINCT y) FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct)");
        assertAnswer("a",
            "SELECT DISTINCT y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) WHERE y = 'a'");
        assertAnswer("A ;; B",
            "SELECT DISTINCT y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) ORDER BY 1 LIMIT 2");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "SELECT 1 WHERE 'a' IN (SELECT y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct))");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "SELECT EXISTS (SELECT 1 FROM (SELECT c AS x FROM ct UNION SELECT 1 FROM ct))");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "SELECT DISTINCT COLLATION(x) FROM (SELECT c AS x FROM ct UNION SELECT 1 FROM ct), ct");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "SELECT DISTINCT ct.s FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) u JOIN ct ON ct.s = "
                + "u.y ORDER BY 1");
        assertAnswer("null", "SELECT DISTINCT COLLATION(x) FROM (SELECT 1 AS x FROM ct UNION SELECT c FROM ct)");
        assertAnswer("NUMBER(18,5)[SB8]",
            "SELECT DISTINCT SYSTEM$TYPEOF(x) FROM (SELECT d AS x FROM ct UNION SELECT 1 FROM ct)");
        assertAnswer("null", "SELECT DISTINCT COLLATION(x) FROM (SELECT s AS x FROM ct UNION SELECT 1 FROM ct)");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "SELECT y, COUNT(*) FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) GROUP BY y ORDER BY 1");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "SELECT DISTINCT y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) QUALIFY ROW_NUMBER() "
                + "OVER (ORDER BY y) = 1");
    }

    @Test
    public void unionReaderEdges() {
        engine.execute("CREATE OR REPLACE TABLE ct (s VARCHAR, c VARCHAR COLLATE 'en-ci', d VARCHAR COLLATE 'en-cs')");
        engine.execute("INSERT INTO ct VALUES ('a', 'a', 'a'), ('A', 'A', 'A'), ('b', 'b', 'b'), ('B', 'B', 'B')");
        assertAnswer("A", "SELECT MIN(y) FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct)");
        assertAnswer("b|A", "SELECT MAX(y), MIN(y) FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct)");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "SELECT MAX(UPPER(y)) FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct)");
        assertAnswer("b|1", "SELECT MAX(y), 1 FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct)");
        assertAnswer("A|A ;; B|B ;; a|a ;; b|b",
            "SELECT y, MAX(y) FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) GROUP BY y ORDER BY 1");
        assertAnswer("A|1 ;; B|1 ;; a|1 ;; b|1",
            "SELECT DISTINCT y, 1 FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) ORDER BY 1");
        assertAnswer("A ;; B ;; a ;; b",
            "SELECT DISTINCT y AS z FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) ORDER BY 1");
        assertAnswer("A ;; B ;; a ;; b",
            "SELECT DISTINCT u.y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) u ORDER BY 1");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "SELECT DISTINCT * FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct)");
        assertAnswer("b",
            "SELECT y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) GROUP BY y HAVING MAX(y) > 'a' "
                + "ORDER BY 1");
        assertAnswer("A ;; B",
            "SELECT DISTINCT TOP 2 y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) ORDER BY 1");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "SELECT DISTINCT y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct LIMIT 10) ORDER BY 1");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "SELECT DISTINCT y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct ORDER BY 2) ORDER BY 1");
        assertAnswer("A ;; B ;; a ;; b",
            "SELECT DISTINCT y FROM (SELECT c AS x, s AS y FROM ct UNION BY NAME SELECT 1 AS x, s AS y FROM ct) "
                + "ORDER BY 1");
        assertAnswer("A ;; B ;; a ;; b",
            "SELECT DISTINCT y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct INTERSECT SELECT 1, s "
                + "FROM ct) ORDER BY 1");
        assertAnswer("true",
            "SELECT ANY_VALUE(y) IS NOT NULL FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct)");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "SELECT DISTINCT y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) WHERE x = 1");
        assertAnswer("A ;; B ;; a ;; b",
            "SELECT * FROM (SELECT DISTINCT y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct)) ORDER "
                + "BY 1");
        assertAnswer("A ;; B ;; a ;; b ;; z",
            "SELECT DISTINCT y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) UNION SELECT 'z' ORDER "
                + "BY 1");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "WITH w AS (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) SELECT DISTINCT y FROM w UNION ALL "
                + "SELECT COUNT(*)::VARCHAR FROM w");
        assertAnswer("b",
            "WITH w AS (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) SELECT (SELECT MAX(y) FROM w)");
        assertAnswer("A ;; B ;; a ;; b",
            "WITH w AS (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) SELECT DISTINCT y FROM w ORDER BY 1");
        assertAnswer("null",
            "SELECT DISTINCT COLLATION(x) FROM (SELECT c AS x FROM ct UNION SELECT 1 FROM ct) WHERE 1 = 1");
        assertAnswer("en-ci", "SELECT COLLATION(x) FROM (SELECT c AS x FROM ct UNION ALL SELECT 'z' FROM ct) LIMIT 1");
        assertAnswer("null", "SELECT COLLATION(x) FROM (SELECT 1 AS x FROM ct UNION ALL SELECT c FROM ct) LIMIT 1");
        assertAnswer("a ;; b",
            "SELECT DISTINCT y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) WHERE y IN ('a', 'b') "
                + "ORDER BY 1");
        assertAnswer("A ;; B ;; a ;; b",
            "SELECT y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) GROUP BY 1 ORDER BY 1");
        assertAnswer("A|null ;; B|null ;; a|null ;; b|null",
            "SELECT DISTINCT y, COLLATION(x) FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) ORDER BY "
                + "1");
    }
}
