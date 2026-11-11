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
 * The syntax-error lines of a PIVOT whose aggregate is no call of one column name (live-verified): an aggregate of
 * two arguments is refused at its comma and then at its call's bracket, an operator inside the call twice and then
 * at the pivot column, an operator after the call at it and at the pivot column, CAST and TRY_CAST at their bracket
 * and at the pivot column, and IDENTIFIER at the name, at the name, comma and bracket, or at the FOR after a text.
 */
public class PivotAggregateShapeLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void aPivotAggregateThatIsNoCallOfOneName() {
        engine.execute("CREATE OR REPLACE TABLE flags906 (student VARCHAR, subject VARCHAR, ok BOOLEAN, score INT)");
        engine.execute("INSERT INTO flags906 VALUES ('ann', 'Math', TRUE, 90), ('bob', 'Art', FALSE, 70)");
        assertCells(new String[][] {
            {"SELECT * FROM flags906 PIVOT (OBJECT_AGG(student, score) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected ','. | syntax "
                + "error line 1 at position 55 unexpected ')'."},
            {"SELECT * FROM flags906 PIVOT (IDENTIFIER(student) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'student'."},
            {"SELECT * FROM flags906 PIVOT (TRY_CAST(score) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '('. | syntax "
                + "error line 1 at position 50 unexpected 'subject'."},
            {"SELECT * FROM (SELECT student, subject, score FROM flags906) PIVOT (SUM(score + 1) FOR subject IN "
                + "('Math', 'Art'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 78 unexpected '+'. | syntax "
                + "error line 1 at position 78 unexpected '+'. | syntax error line 1 at position 87 unexpected "
                + "'subject'."},
            {"SELECT * FROM flags906 PIVOT (SUM(score + 1) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '+'. | syntax "
                + "error line 1 at position 40 unexpected '+'. | syntax error line 1 at position 49 unexpected "
                + "'subject'."},
            {"SELECT * FROM flags906 PIVOT (MAX(student, score) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ','. | syntax "
                + "error line 1 at position 48 unexpected ')'."},
            {"SELECT * FROM flags906 PIVOT (COUNT(student, score) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected ','. | syntax "
                + "error line 1 at position 50 unexpected ')'."},
            {"SELECT * FROM flags906 PIVOT (IDENTIFIER('student') FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'FOR'."},
            {"SELECT * FROM flags906 PIVOT (TRY_CAST(score AS INT) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '('. | syntax "
                + "error line 1 at position 57 unexpected 'subject'."},
            {"SELECT * FROM flags906 PIVOT (SUM(score * 2) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '*'. | syntax "
                + "error line 1 at position 40 unexpected '*'. | syntax error line 1 at position 49 unexpected "
                + "'subject'."},
            {"SELECT * FROM flags906 PIVOT (SUM(score) + 1 FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected '+'. | syntax "
                + "error line 1 at position 49 unexpected 'subject'."},
            {"SELECT * FROM flags906 PIVOT (SUM(DISTINCT score) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'DISTINCT'."},
            {"SELECT * FROM flags906 PIVOT (AVG(score - 1) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '-'. | syntax "
                + "error line 1 at position 40 unexpected '-'. | syntax error line 1 at position 49 unexpected "
                + "'subject'."},
            {"SELECT * FROM flags906 PIVOT (IDENTIFIER(student, x) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'student'. | "
                + "syntax error line 1 at position 48 unexpected ','. | syntax error line 1 at position 51 "
                + "unexpected ')'."},
            {"SELECT * FROM flags906 PIVOT (CAST(score) FOR subject IN ('Math', 'Art'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '('. | syntax "
                + "error line 1 at position 46 unexpected 'subject'."},
        });
    }
}
