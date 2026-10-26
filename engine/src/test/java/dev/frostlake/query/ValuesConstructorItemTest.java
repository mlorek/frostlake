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
 * A VALUES list in FROM takes no array or object constructor as an item, as an INSERT's VALUES list takes none: the
 * statement is refused while it compiles, the item echoed as the plan spells it (live-verified for {@code [1]}). A
 * constructor inside another call's argument is no item of its own and runs.
 */
public class ValuesConstructorItemTest extends MeasuredCellsTestSupport {

    private static final String REFUSED = "REFUSED SQL compilation error: | Invalid expression [";

    @Test
    public void aConstructorItemIsRefusedWhileCompiling() {
        assertCells(new String[][] {
            {"SELECT TO_VARCHAR(a[0]::DATE, 'YYYYMMDD') FROM (SELECT column1 AS a FROM VALUES ([1]))",
                REFUSED + "ARRAY_CONSTRUCT(1)] in VALUES clause"},
            {"SELECT * FROM VALUES ([1, 2])", REFUSED + "ARRAY_CONSTRUCT(1, 2)] in VALUES clause"},
            {"SELECT * FROM VALUES (ARRAY_CONSTRUCT(1, 2))", REFUSED + "ARRAY_CONSTRUCT(1, 2)] in VALUES clause"},
            {"SELECT * FROM VALUES ({'a': 1})", REFUSED + "OBJECT_CONSTRUCT('a', 1)] in VALUES clause"},
            {"SELECT * FROM (VALUES ([1]))", REFUSED + "ARRAY_CONSTRUCT(1)] in VALUES clause"},
        });
    }

    @Test
    public void aConstructorInsideAnItemRuns() {
        assertCells(new String[][] {
            {"SELECT * FROM VALUES (ARRAY_SIZE([1]))", "1"},
            {"SELECT * FROM VALUES (1), (2) ORDER BY 1", "1 ;; 2"},
        });
    }
}
