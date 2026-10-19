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
 * TO_QUERY reads a TABLE statement as a query, takes further positional arguments that are strings and leaves
 * them unused, refuses one that is no string as its text is, and names its text missing when it is given as
 * SQL => NULL (all live-verified).
 */
public class ToQueryArgumentsTest extends MeasuredCellsTestSupport {

    @Test
    public void toQueryReadsItsTextAndItsFurtherArguments() {
        engine.execute("CREATE OR REPLACE TABLE tq (a INT)");
        engine.execute("INSERT INTO tq VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE SEQUENCE sq");
        engine.execute("CREATE OR REPLACE PROCEDURE p_w() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END;'");
        assertAnswer("1 ;; 2", "SELECT * FROM TABLE(TO_QUERY('TABLE tq')) ORDER BY 1");
        assertAnswer("1", "SELECT * FROM TABLE(TO_QUERY('SELECT 1 AS x', 'extra'))");
        assertAnswer("1", "SELECT * FROM TABLE(TO_QUERY('SELECT 1 AS x', 'extra', 'more'))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 20 | missing required argument [SQL] "
                + "for function [TO_QUERY]",
            "SELECT * FROM TABLE(TO_QUERY(SQL => NULL))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 20 | missing required argument [SQL] "
                + "for function [TO_QUERY]",
            "SELECT * FROM TABLE(TO_QUERY(NULL))");
        assertAnswer("REFUSED SQL compilation error: | argument needs to be a string: '0'",
            "SELECT * FROM TABLE(TO_QUERY('SELECT :1 AS x', 5))");
    }
}
