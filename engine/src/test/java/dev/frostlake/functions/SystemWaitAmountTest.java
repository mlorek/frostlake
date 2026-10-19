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
 * SYSTEM$WAIT's amount is a whole number the plan need not compute: a number written with a fraction, and any
 * number that is computed or cast, is refused as no constant (live-verified).
 */
public class SystemWaitAmountTest extends MeasuredCellsTestSupport {

    @Test
    public void theAmount() {
        answer(
            "CREATE OR REPLACE TABLE cb (a INT)");
        answer(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO FZ VALUES (5, TRUE), (7, FALSE)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 0 to function SqlIdentifier{qualifierNames=[], "
                + "identifierName=SYSTEM$WAIT} needs to be constant, found 'CAST(1.5 AS NUMBER(18,0))'",
            "SELECT SYSTEM$WAIT(1.5)");
        assertAnswer(
            "waited 1 seconds",
            "SELECT SYSTEM$WAIT(1.0)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 0 to function SqlIdentifier{qualifierNames=[], "
                + "identifierName=SYSTEM$WAIT} needs to be constant, found 'CAST(0.4 AS NUMBER(18,0))'",
            "SELECT SYSTEM$WAIT(0.4)");
        assertAnswer(
            "waited 0 seconds",
            "SELECT SYSTEM$WAIT('0.4')");
        assertAnswer(
            "waited 0 seconds",
            "SELECT SYSTEM$WAIT(0.4::FLOAT)");
        assertAnswer(
            "waited 1 seconds",
            "SELECT SYSTEM$WAIT(1e0)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 0 to function SqlIdentifier{qualifierNames=[], "
                + "identifierName=SYSTEM$WAIT} needs to be constant, found 'CAST((CAST(2 AS NUMBER(7,6))) / 2 AS "
                + "NUMBER(18,0))'",
            "SELECT SYSTEM$WAIT(2 / 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 0 to function SqlIdentifier{qualifierNames=[], "
                + "identifierName=SYSTEM$WAIT} needs to be constant, found '1 + 0'",
            "SELECT SYSTEM$WAIT(1 + 0)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 0 to function SqlIdentifier{qualifierNames=[], "
                + "identifierName=SYSTEM$WAIT} needs to be constant, found 'CAST(CAST(0 AS NUMBER(10,2)) AS "
                + "NUMBER(18,0))'",
            "SELECT SYSTEM$WAIT(0::NUMBER(10,2))");
        assertAnswer(
            "waited 0 seconds",
            "SELECT SYSTEM$WAIT(0.0)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'SYSTEM$WAIT': (BOOLEAN)",
            "SELECT SYSTEM$WAIT(TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 0 to function SqlIdentifier{qualifierNames=[], "
                + "identifierName=SYSTEM$WAIT} needs to be constant, found 'CAST(0 AS NUMBER(10,0))'",
            "SELECT SYSTEM$WAIT(0::NUMBER(10,0))");
        assertAnswer(
            "waited 0 seconds",
            "SELECT SYSTEM$WAIT(0, 'SEC' || 'ONDS')");
    }
}
