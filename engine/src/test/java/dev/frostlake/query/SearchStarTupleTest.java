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
 * SEARCH's first argument may be a tuple of a star — SEARCH((*), 'q'), SEARCH((t.*), 'q'), with EXCLUDE or ILIKE
 * inside — which searches the columns the star stands for, as the bare star SEARCH(*, 'q') does, in the select list
 * and in a WHERE alike; a qualifier naming no relation and an EXCLUDE naming no column are refused as for any star
 * argument (all live-verified).
 */
public class SearchStarTupleTest extends MeasuredCellsTestSupport {

    @Test
    public void aStarTupleSearchesTheRow() {
        engine.execute("CREATE OR REPLACE TABLE ts (a VARCHAR)");
        engine.execute("INSERT INTO ts VALUES ('x y'), ('X-Y z.w'), ('hello world'), (NULL)");
        engine.execute("CREATE OR REPLACE TABLE ts2 (a VARCHAR, b VARCHAR)");
        engine.execute("INSERT INTO ts2 VALUES ('x', 'y'), ('hello', 'x y'), ('q', NULL)");
        assertAnswer("false ;; false ;; true ;; null",
            "SELECT SEARCH((ts.*), 'x y', ANALYZER => 'NO_OP_ANALYZER') FROM ts ORDER BY a");
        assertAnswer("false ;; false ;; true ;; null",
            "SELECT SEARCH((*), 'x y', ANALYZER => 'NO_OP_ANALYZER') FROM ts ORDER BY a");
        assertAnswer("hello|true ;; q|null ;; x|true", "SELECT a, SEARCH((*), 'x') FROM ts2 ORDER BY a");
        assertAnswer("hello|false ;; q|false ;; x|true", "SELECT a, SEARCH((* EXCLUDE (b)), 'x') FROM ts2 ORDER BY a");
        assertAnswer("hello|false ;; q|false ;; x|true", "SELECT a, SEARCH((* EXCLUDE b), 'x') FROM ts2 ORDER BY a");
        assertAnswer("hello|true ;; q|null ;; x|false", "SELECT a, SEARCH((* ILIKE 'b%'), 'x') FROM ts2 ORDER BY a");
        assertAnswer("hello|true ;; q|null ;; x|false", "SELECT a, SEARCH((ts2.*), 'hello') FROM ts2 ORDER BY a");
        assertAnswer("true ;; true ;; null", "SELECT SEARCH(*, 'x') FROM ts2");
        assertAnswer("x|true",
            "SELECT t1.a, SEARCH((t1.*), 'x') FROM ts2 t1 JOIN ts t2 ON t1.a = 'x' AND t2.a = 'x y' ORDER BY 1");
        assertAnswer("3", "SELECT COUNT(*) FROM ts2 t1 JOIN ts t2 ON TRUE WHERE SEARCH((*), 'world')");
        assertAnswer("hello|true ;; q|null ;; x|true", "SELECT a, SEARCH((a, b), 'x') FROM ts2 ORDER BY a");
        assertAnswer("REFUSED SQL compilation error: | Object 'NOSUCH' does not exist or not authorized.",
            "SELECT SEARCH((nosuch.*), 'x') FROM ts2");
        assertAnswer("REFUSED SQL compilation error: | column 'ZZ' does not exist",
            "SELECT SEARCH((* EXCLUDE (zz)), 'x') FROM ts2");
        assertAnswer("hello|true ;; q|null ;; x|false",
            "SELECT a, SEARCH((ts2.* EXCLUDE (a)), 'x') FROM ts2 ORDER BY a");
    }
}
