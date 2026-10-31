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
 * A literal or a '.' after a TIMESTAMP inside a type's parentheses in a DESCRIBE's signature is named once, and
 * then the token that ends the signature's item, the ',' or the ')' at the list's own depth (all live-verified).
 */
public class DescribeSignatureNestedTimestampTest extends MeasuredCellsTestSupport {

    @Test
    public void aLiteralAfterANestedTimestamp() {
        engine.execute("CREATE OR REPLACE TABLE tc750 (a INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'. | syntax "
                + "error line 1 at position 32 unexpected '1'. | syntax error line 1 at position 33 unexpected ')'.",
            "DESCRIBE TABLE tc750 (TIMESTAMP 1)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '1'. | syntax "
                + "error line 1 at position 40 unexpected ')'.",
            "DESCRIBE TABLE tc750 (ARRAY(TIMESTAMP 1))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected '1'. | syntax "
                + "error line 1 at position 45 unexpected ')'.",
            "DESCRIBE TABLE tc750 (INT, ARRAY(TIMESTAMP 1))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected '1'. | syntax "
                + "error line 1 at position 43 unexpected ')'.",
            "DESCRIBE TABLE tc750 (OBJECT(a TIMESTAMP 1))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '1'. | syntax "
                + "error line 1 at position 40 unexpected ')'.",
            "DESCRIBE TABLE tc750 (TABLE(TIMESTAMP 1))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '1'. | syntax "
                + "error line 1 at position 42 unexpected ')'.",
            "DESCRIBE TABLE tc750 (ARRAY(TIMESTAMP 1) x)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected '1'. | syntax "
                + "error line 1 at position 47 unexpected ')'.",
            "DESCRIBE TABLE tc750 (MAP(VARCHAR, TIMESTAMP 1))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '1'. | syntax "
                + "error line 1 at position 40 unexpected ','.",
            "DESCRIBE TABLE tc750 (ARRAY(TIMESTAMP 1), INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected '1'. | syntax "
                + "error line 1 at position 47 unexpected ')'.",
            "DESCRIBE TABLE tc750 (ARRAY(ARRAY(TIMESTAMP 1)))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected '1'. | syntax "
                + "error line 1 at position 50 unexpected ')'.",
            "DESCRIBE TABLE tc750 (OBJECT(a INT, b TIMESTAMP 1))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected ''x''. | syntax "
                + "error line 1 at position 42 unexpected ')'.",
            "DESCRIBE TABLE tc750 (ARRAY(TIMESTAMP 'x'))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '.'. | syntax "
                + "error line 1 at position 39 unexpected ')'.",
            "DESCRIBE TABLE tc750 (ARRAY(TIMESTAMP.))");
    }
}
