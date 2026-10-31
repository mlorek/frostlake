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

package dev.frostlake.parser;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A star's misplaced REPLACE under a CAST, followed by two words: the CAST is closed at the keyword, the first word
 * read as the item's alias and the second refused (live-verified).
 */
public class StarModifierCastWordTest extends MeasuredCellsTestSupport {

    @Test
    public void starModifierUnderCast() {
        engine.execute("CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'REPLACE'. | "
                + "syntax error line 1 at position 29 unexpected 'y'.",
            "SELECT CAST(HASH(* REPLACE x y) AS INT) FROM FZ");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
