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
 * An INTERSECT or EXCEPT converts a number or text arm toward the leading BOOLEAN, NUMBER or DATE column
 * whichever of the leading arm's rows comes first: a NULL first row changes nothing (all live-verified).
 */
public class NullFirstBooleanSetOperationTest extends MeasuredCellsTestSupport {

    @Test
    public void aNullFirstRowChangesNothing() {
        assertAnswer("true", "SELECT column1 b FROM VALUES (TRUE), (NULL) INTERSECT SELECT 5");
        assertAnswer("true", "SELECT column1 b FROM VALUES (NULL), (TRUE) INTERSECT SELECT 5");
        assertAnswer("null", "SELECT column1 b FROM VALUES (NULL), (TRUE) EXCEPT SELECT 5 ORDER BY 1");
        assertAnswer("null", "SELECT column1 b FROM VALUES (TRUE), (NULL) EXCEPT SELECT 5 ORDER BY 1");
        assertAnswer("REFUSED Boolean value 'maybe' is not recognized",
            "SELECT column1 b FROM VALUES (TRUE), (NULL) INTERSECT SELECT 'maybe'");
        assertAnswer("REFUSED Boolean value 'maybe' is not recognized",
            "SELECT column1 b FROM VALUES (NULL), (TRUE) INTERSECT SELECT 'maybe'");
        assertAnswer("REFUSED Boolean value 'maybe' is not recognized",
            "SELECT column1 b FROM VALUES (NULL), (TRUE) EXCEPT SELECT 'maybe'");
        assertAnswer("false", "SELECT column1 b FROM VALUES (NULL), (FALSE) INTERSECT SELECT 0");
        assertAnswer("null", "SELECT column1 b FROM VALUES (NULL), (TRUE) MINUS SELECT 1 ORDER BY 1");
        assertAnswer("true ;; null", "SELECT column1 b FROM VALUES (NULL), (TRUE) UNION SELECT 5 ORDER BY 1");
        assertAnswer("true", "SELECT column1 b FROM VALUES (NULL), (TRUE) INTERSECT SELECT 'true'");
        assertAnswer("null", "SELECT column1 b FROM VALUES (NULL), (TRUE) EXCEPT SELECT 'yes' ORDER BY 1");
    }

    @Test
    public void chainedAndNestedOperationsConvertAlike() {
        assertAnswer("1", "SELECT column1 n FROM VALUES (NULL), (1) INTERSECT SELECT '1'");
        assertAnswer("null", "SELECT column1 n FROM VALUES (NULL), (1) EXCEPT SELECT '1' ORDER BY 1");
        assertAnswer("2024-01-01",
            "SELECT column1 d FROM VALUES (NULL), ('2024-01-01'::DATE) INTERSECT SELECT '2024-01-01'");
        assertAnswer("REFUSED Numeric value 'x' is not recognized",
            "SELECT column1 n FROM VALUES (NULL), (1) INTERSECT SELECT 'x'");
        assertAnswer("true", "SELECT column1 b FROM VALUES (NULL), (NULL), (TRUE) INTERSECT SELECT 5");
        assertAnswer("REFUSED Unsupported feature 'INTERSECT ALL'.",
            "SELECT column1 b FROM VALUES (NULL), (TRUE) INTERSECT ALL SELECT 5");
        assertAnswer("REFUSED Unsupported feature 'MINUS ALL'.",
            "SELECT column1 b FROM VALUES (NULL), (TRUE) EXCEPT ALL SELECT 5 ORDER BY 1");
        assertAnswer("true", "SELECT column1 b FROM VALUES (NULL), (TRUE) INTERSECT SELECT 5 INTERSECT SELECT TRUE");
        assertAnswer("null",
            "SELECT column1 b FROM VALUES (NULL), (TRUE) EXCEPT SELECT 5 EXCEPT SELECT FALSE ORDER BY 1");
        assertAnswer("true", "SELECT * FROM (SELECT column1 b FROM VALUES (NULL), (TRUE) INTERSECT SELECT 5)");
    }
}
