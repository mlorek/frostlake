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

package dev.frostlake.udf;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A JavaScript statement's isColumnNullable judges a select list without a FROM item by item, a set operation's
 * column as nullable where any arm's is, whatever the operator, a BY NAME column as nullable, and a derived
 * relation's column as the projection that made it judged its item (all live-verified).
 */
public class JavaScriptSetOperationNullabilityTest extends MeasuredCellsTestSupport {

    @Test
    public void setOperationColumns() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        engine.execute("CREATE OR REPLACE TABLE nn51 (k FLOAT, knn FLOAT NOT NULL)");
        engine.execute("INSERT INTO nn51 VALUES ('inf', 'inf')");
        assertAnswer("Function NUL51 successfully created.",
            "CREATE OR REPLACE PROCEDURE nul51(q VARCHAR) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var rs = "
                + "snowflake.execute({sqlText: Q}); var out = []; for (var i = 1; i <= rs.getColumnCount(); i++) { "
                + "out.push(rs.getColumnName(i) + '=' + (rs.isColumnNullable(i) ? 1 : 0)); } return out.join(' '); $$");
        assertAnswer("C1=0 C2=1 C3=1 C4=1 C5=0 C6=1",
            "CALL nul51('SELECT 1 AS c1, NULL AS c2, NULL::INT AS c3, NULLIF(1, 1) AS c4, ''a'' AS c5, "
                + "TRY_TO_NUMBER(''x'') AS c6')");
        assertAnswer("C1=0", "CALL nul51('SELECT 1 AS c1 UNION ALL SELECT 2')");
        assertAnswer("C1=1", "CALL nul51('SELECT 1 AS c1 UNION ALL SELECT NULL')");
        assertAnswer("C1=0 C2=1",
            "CALL nul51('SELECT knn AS c1, k AS c2 FROM nn51 UNION ALL SELECT knn, knn FROM nn51')");
        assertAnswer("C1=1", "CALL nul51('SELECT knn AS c1 FROM nn51 INTERSECT SELECT k FROM nn51')");
        assertAnswer("C1=1", "CALL nul51('SELECT knn AS c1 FROM nn51 EXCEPT SELECT k FROM nn51')");
        assertAnswer("C1=0", "CALL nul51('SELECT c1 FROM (SELECT 1 AS c1)')");
        assertAnswer("C1=0", "CALL nul51('SELECT c1 FROM (SELECT 1 AS c1 UNION ALL SELECT 2)')");
        assertAnswer("C1=1", "CALL nul51('SELECT c1 FROM (SELECT knn AS c1 FROM nn51 UNION ALL SELECT k FROM nn51)')");
        assertAnswer("C1=1 C2=1", "CALL nul51('SELECT 1 AS c1, 2 AS c2 UNION ALL BY NAME SELECT 3 AS c1')");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
