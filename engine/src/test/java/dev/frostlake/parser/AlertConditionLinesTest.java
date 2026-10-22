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
 * The syntax-error lines of an alert's condition: a statement that is no query is refused at its first word, and
 * a word after a query's closing parenthesis alone (all live-verified).
 */
public class AlertConditionLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void conditions() {
        engine.execute("CREATE OR REPLACE TABLE t1 (x INT)");
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 72 unexpected 'x'.",
            "CREATE OR REPLACE ALERT al SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1) x)) THEN SELECT 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'CREATE'. | "
                + "syntax error line 1 at position 86 unexpected ')'.",
            "CREATE OR REPLACE ALERT al SCHEDULE = '60 MINUTE' IF (EXISTS (CREATE TABLE zz (a INT))) THEN SELECT 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 72 unexpected 'x'.",
            "CREATE OR REPLACE ALERT al SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1) x y)) THEN SELECT 1");
    }
}
