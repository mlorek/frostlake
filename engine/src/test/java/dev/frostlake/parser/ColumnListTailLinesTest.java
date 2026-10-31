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
 * Column list lines: a typeless first column before a typed one is refused at its comma alone, and ALTER TABLE's
 * ADD COLUMN followed by a parenthesis at the parenthesis and the token after the first word inside it, an Iceberg
 * table's alike (all live-verified).
 */
public class ColumnListTailLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void columnListTails() {
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ','.",
            "CREATE ICEBERG TABLE nf_i59 (n, m INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '('. | syntax "
                + "error line 1 at position 41 unexpected 'VARCHAR'.",
            "ALTER ICEBERG TABLE nf_ice ADD COLUMN (x VARCHAR(10), y nosuch)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected ','.",
            "CREATE TABLE nf_t59 (n, m INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected ','. | syntax "
                + "error line 1 at position 25 unexpected ')'.",
            "CREATE TABLE nf_t59 (n, m)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '('. | syntax "
                + "error line 1 at position 33 unexpected 'VARCHAR'.",
            "ALTER TABLE nf_ice ADD COLUMN (x VARCHAR(10), y nosuch)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '('. | syntax "
                + "error line 1 at position 33 unexpected 'VARCHAR'.",
            "ALTER TABLE nf_ice ADD COLUMN (x VARCHAR(10))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected ','.",
            "CREATE ICEBERG TABLE nf_i59 (n INT, m, k INT)");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
