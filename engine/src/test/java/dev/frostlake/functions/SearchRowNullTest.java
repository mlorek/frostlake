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

package dev.frostlake.functions;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * SEARCH over a row of columns (live-verified): a number in the row is not searched, a star stands for the row of the
 * columns it names, and where no searched column matches and one of them is NULL the answer is NULL, in OR and AND
 * mode alike.
 */
public class SearchRowNullTest extends MeasuredCellsTestSupport {

    @Test
    public void aRowWithANullAndNoMatchIsNull() {
        engine.execute("CREATE OR REPLACE TABLE ts2 (a INT, b VARCHAR)");
        engine.execute("INSERT INTO ts2 VALUES (2, 'x'), (2, NULL), (3, 'y'), (NULL, 'x'), (NULL, NULL)");
        assertCells(new String[][] {
            {"SELECT a, b, SEARCH((a, b), 'x') FROM ts2 ORDER BY a NULLS LAST, b NULLS LAST",
                "2|x|true ;; 2|null|null ;; 3|y|false ;; null|x|true ;; null|null|null"},
            {"SELECT a, b, SEARCH((b, b), 'x') FROM ts2 ORDER BY a NULLS LAST, b NULLS LAST",
                "2|x|true ;; 2|null|null ;; 3|y|false ;; null|x|true ;; null|null|null"},
            {"SELECT a, b, SEARCH((b), 'x') FROM ts2 ORDER BY a NULLS LAST, b NULLS LAST",
                "2|x|true ;; 2|null|null ;; 3|y|false ;; null|x|true ;; null|null|null"},
            {"SELECT a, b, SEARCH(ts2.*, 'x') FROM ts2 ORDER BY a NULLS LAST, b NULLS LAST",
                "2|x|true ;; 2|null|null ;; 3|y|false ;; null|x|true ;; null|null|null"},
            {"SELECT a, b, SEARCH((a, b), '2') FROM ts2 ORDER BY a NULLS LAST, b NULLS LAST",
                "2|x|false ;; 2|null|null ;; 3|y|false ;; null|x|false ;; null|null|null"},
            {"SELECT a, b, SEARCH((a, b), 'x y') FROM ts2 ORDER BY a NULLS LAST, b NULLS LAST",
                "2|x|true ;; 2|null|null ;; 3|y|true ;; null|x|true ;; null|null|null"},
            {"SELECT a, b, SEARCH((a, b), 'x y', SEARCH_MODE => 'AND') FROM ts2 ORDER BY a NULLS LAST, b NULLS LAST",
                "2|x|false ;; 2|null|null ;; 3|y|false ;; null|x|false ;; null|null|null"},
        });
    }
}
