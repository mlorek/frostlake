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

package dev.frostlake.stage;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A stage path's segment is any word written straight after a '/', a reserved one included: LIST @st/select/from,
 * SELECT … FROM @st/inner and REMOVE @st/inner/join read those paths, while the same word after a space is the
 * keyword it is (live-verified).
 */
public class StagePathReservedWordsTest extends MeasuredCellsTestSupport {

    @Test
    public void aPathSegmentMayBeAReservedWord() {
        engine.execute("CREATE OR REPLACE STAGE st");
        assertAnswer("", "LIST @st/dir/inner");
        assertAnswer("", "LIST @st/select/from/where");
        assertAnswer("", "SELECT $1 FROM @st/dir/inner");
        assertAnswer("", "REMOVE @st/inner/join");
        assertAnswer("", "LIST @st/a.b/on");
        assertAnswer("", "LIST @st/as/by/order");
        assertAnswer("", "LIST @st/dir/inner/");
        assertAnswer("0", "SELECT COUNT(*) FROM @st/f AS x INNER JOIN (SELECT 1 AS y) z ON TRUE");
        assertAnswer("0", "SELECT COUNT(*) FROM @st/f INNER JOIN (SELECT 1 AS y) z ON TRUE");
        assertAnswer("0", "SELECT COUNT(*) FROM @st/inner");
        assertAnswer("0", "SELECT COUNT(*) FROM @st/f, (SELECT 1 AS y) z");
        assertAnswer("", "LIST @st/union/minus");
        assertAnswer("", "LIST @st/dir/null");
        assertAnswer("", "LIST @st/dir/true");
    }
}
