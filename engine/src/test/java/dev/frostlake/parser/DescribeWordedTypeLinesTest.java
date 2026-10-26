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
 * A TIMESTAMP whose time-zone words break off in a DESCRIBE's argument list (all live-verified).
 */
public class DescribeWordedTypeLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void describedWordedTypes() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected ')'. | syntax "
                + "error line 1 at position 29 unexpected 'WITH'.",
            "DESCRIBE TABLE t1 (TIMESTAMP WITH)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected ')'. | syntax "
                + "error line 1 at position 29 unexpected 'WITH'. | syntax error line 1 at position 38 unexpected ')'.",
            "DESCRIBE TABLE t1 (TIMESTAMP WITH TIME)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected ')'. | syntax "
                + "error line 1 at position 29 unexpected 'WITH'. | syntax error line 1 at position 39 unexpected ')'.",
            "DESCRIBE TABLE t1 (TIMESTAMP WITH LOCAL)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected ')'.",
            "DESCRIBE TABLE t1 (TIMESTAMP WITH LOCAL TIME)");
        assertAnswer("A|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (TIMESTAMP WITH TIME ZONE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'WITHOUT'.",
            "DESCRIBE TABLE t1 (TIMESTAMP WITHOUT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ')'.",
            "DESCRIBE TABLE t1 (TIMESTAMP WITHOUT TIME)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'WITH'.",
            "DESCRIBE TABLE t1 (TIME WITH)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected ')'. | syntax "
                + "error line 1 at position 34 unexpected 'WITH'.",
            "DESCRIBE TABLE t1 (INT, TIMESTAMP WITH)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected ','. | syntax "
                + "error line 1 at position 29 unexpected 'WITH'. | syntax error line 1 at position 38 unexpected ')'.",
            "DESCRIBE TABLE t1 (TIMESTAMP WITH, INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ')'. | syntax "
                + "error line 1 at position 25 unexpected 'WITH'.",
            "DESC TABLE t1 (TIMESTAMP WITH)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'WITH'. | syntax "
                + "error line 1 at position 38 unexpected ')'.",
            "DESCRIBE TABLE t1 (TIMESTAMP WITH ZONE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'WITH'.",
            "DESCRIBE TABLE t1 (TIMESTAMP(3) WITH)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected ')'. | syntax "
                + "error line 1 at position 29 unexpected 'WITH'. | syntax error line 1 at position 38 unexpected ')'.",
            "DESCRIBE TABLE t1 (TIMESTAMP WITH TIME) TYPE = COLUMNS");
        assertAnswer("A|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null", "DESCRIBE TABLE t1 (TIMESTAMP)");
        assertAnswer("A|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (DOUBLE PRECISION)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected ')'. | syntax "
                + "error line 1 at position 28 unexpected 'WITH'.",
            "DESCRIBE VIEW t1 (TIMESTAMP WITH)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ')'. | syntax "
                + "error line 1 at position 37 unexpected 'WITH'.",
            "DESCRIBE FUNCTION nosuchfn(TIMESTAMP WITH)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'x'.",
            "DESCRIBE TABLE t1 (TIMESTAMP WITH TIME ZONE x)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'ZONE'. | syntax "
                + "error line 1 at position 29 unexpected 'WITHOUT'. | syntax error line 1 at position 41 unexpected "
                + "')'.",
            "DESCRIBE TABLE t1 (TIMESTAMP WITHOUT ZONE)");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
