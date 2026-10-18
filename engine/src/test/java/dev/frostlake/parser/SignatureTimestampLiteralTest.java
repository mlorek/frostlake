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
 * Two readings of a signature after a DESCRIBE's or a DROP's name (live-verified): a literal after the
 * signature's own TIMESTAMP is named twice, as the word's longer spellings and the list both read none of them,
 * and the signature is given up at the next word or ')', which the properties then read; and a DROP gives a
 * TABLE item's path up at its fourth part, passing the rest of the path over.
 */
public class SignatureTimestampLiteralTest extends MeasuredCellsTestSupport {

    @Test
    public void aTimestampLiteralAndALongPathAreRefusedAsLiveReadsThem() {
        engine.execute("CREATE OR REPLACE TABLE t1 (x INT)");
        assertCells(new String[][] {
            {"DESCRIBE TABLE t1 (TIMESTAMP 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '1'. | syntax error "
                + "line 1 at position 29 unexpected '1'. | syntax error line 1 at position 30 unexpected ')'."},
            {"DESCRIBE TABLE t1 (TIMESTAMP 1.5)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '1.5'. | syntax "
                + "error line 1 at position 29 unexpected '1.5'. | syntax error line 1 at position 32 unexpected ')'."},
            {"DESCRIBE TABLE t1 (TIMESTAMP 'x')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ''x''. | syntax "
                + "error line 1 at position 29 unexpected ''x''. | syntax error line 1 at position 32 unexpected ')'."},
            {"DESCRIBE TABLE t1 (TIMESTAMP 1, INT)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '1'. | syntax error "
                + "line 1 at position 29 unexpected '1'. | syntax error line 1 at position 35 unexpected ')'."},
            {"DESCRIBE TABLE t1 (TIMESTAMP.)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '.'. | syntax error "
                + "line 1 at position 28 unexpected '.'."},
            {"DESCRIBE TABLE t1 (TIMESTAMP 1 x)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '1'. | syntax error "
                + "line 1 at position 29 unexpected '1'. | syntax error line 1 at position 32 unexpected ')'."},
            {"DESCRIBE TABLE t1 (INT, TIMESTAMP 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '1'. | syntax error "
                + "line 1 at position 34 unexpected '1'. | syntax error line 1 at position 35 unexpected ')'."},
            {"DESCRIBE TABLE t1 (TIMESTAMP 1) x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '1'. | syntax error "
                + "line 1 at position 29 unexpected '1'. | syntax error line 1 at position 30 unexpected ')'."},
            {"DROP TABLE IF EXISTS nosuch_x (TIMESTAMP 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected '1'."},
            {"DROP TABLE IF EXISTS nosuch_x (TABLE(a.b.c.d.e))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'd'."},
            {"DROP TABLE IF EXISTS nosuch_x (TABLE(a.b.c.d))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'd'."},
            {"DROP TABLE IF EXISTS nosuch_x (TABLE(a.b.c.d.e.f))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'd'."},
            {"DESCRIBE TABLE t1 (TABLE(a.b.c.d.e))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'd'."},
            {"DROP TABLE IF EXISTS nosuch_x (TABLE(a.b.c.d.e), INT)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'd'. | syntax error "
                + "line 1 at position 49 unexpected 'INT'."},
            {"DROP TABLE IF EXISTS nosuch_x (INT, TABLE(a.b.c.d.e))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'TABLE'. | syntax "
                + "error line 1 at position 52 unexpected ')'."},
            {"DROP VIEW IF EXISTS nosuch_x (TABLE(a.b.c.d.e))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected 'd'."},
            {"DROP TABLE nosuch_x (TABLE(a.b.c.d.e))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'd'."},
        });
    }
}
