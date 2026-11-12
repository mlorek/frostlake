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
 * Inside a subquery, a column qualified with the subquery's own table's name reads that table, though the query
 * around it reads the same table under an alias: the innermost scope that names a relation owns the name. The
 * subquery's {@code n.g} read the outer row, so EXISTS, IN and scalar subqueries answered for the wrong rows.
 */
public class SubquerySelfNameScopeTest extends MeasuredCellsTestSupport {

    @Test
    public void theSubquerysOwnTableAnswersItsQualifiedNames() {
        engine.execute("CREATE OR REPLACE TABLE ssn (g INT, i INT)");
        engine.execute("INSERT INTO ssn VALUES (1, 1), (1, 2), (2, 6), (3, 7), (3, 1)");
        assertAnswer("2 ;; 3", "SELECT g FROM ssn o GROUP BY g "
            + "HAVING EXISTS (SELECT 1 FROM ssn WHERE ssn.g = o.g AND ssn.i > 5) ORDER BY g");
        assertAnswer("2|6 ;; 3|1 ;; 3|7", "SELECT o.g, o.i FROM ssn o "
            + "WHERE EXISTS (SELECT 1 FROM ssn WHERE ssn.g = o.g AND ssn.i > 5) ORDER BY 1, 2");
        assertAnswer("2|6 ;; 3|1 ;; 3|7", "SELECT o.g, o.i FROM ssn o "
            + "WHERE o.g IN (SELECT ssn.g FROM ssn WHERE ssn.i > 5) ORDER BY 1, 2");
        assertAnswer("1|1|2 ;; 1|2|2 ;; 2|6|6 ;; 3|1|7 ;; 3|7|7", "SELECT o.g, o.i, "
            + "(SELECT MAX(ssn.i) FROM ssn WHERE ssn.g = o.g) mx FROM ssn o ORDER BY 1, 2");
        assertAnswer("2 ;; 3", "SELECT g FROM ssn o GROUP BY g "
            + "HAVING g IN (SELECT ssn.g FROM ssn WHERE ssn.g = o.g AND ssn.i > 5) ORDER BY g");
        // With an inner alias or bare names the answers are the same.
        assertAnswer("2 ;; 3", "SELECT g FROM ssn o GROUP BY g "
            + "HAVING EXISTS (SELECT 1 FROM ssn x WHERE x.g = o.g AND x.i > 5) ORDER BY g");
    }
}
