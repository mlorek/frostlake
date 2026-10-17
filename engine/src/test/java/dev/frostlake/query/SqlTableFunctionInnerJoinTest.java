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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A SQL table function joined with an ON condition, an inner join, keeps the pairs the condition holds, its body read
 * for each row of the left side as a correlated subquery, as the account answers it (live-verified with one left row);
 * a RIGHT join is refused as the other outer joins are. Engine only: the account may also abort such an inner join
 * with an internal error, which is not mirrored.
 */
public class SqlTableFunctionInnerJoinTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE tfj");
        engine.execute("USE SCHEMA tfj.PUBLIC");
        engine.execute("CREATE FUNCTION ptf(p NUMBER) RETURNS TABLE (x VARCHAR) AS $$ SELECT TO_VARCHAR(p) $$");
        engine.execute("CREATE FUNCTION ptf2(p NUMBER) RETURNS TABLE (x NUMBER)"
            + " AS $$ SELECT p UNION ALL SELECT p + 1 $$");
        engine.execute("CREATE TABLE tt (d NUMBER(10,2), n NUMBER(38,0))");
        engine.execute("INSERT INTO tt VALUES (1.5, 2), (2.5, 3)");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.append(out.length() > 0 ? " ;; " : "");
            for (int i = 0; i < row.getValues().size(); i++) {
                out.append(i > 0 ? "|" : "").append(row.getValue(i));
            }
        }
        return out.toString();
    }

    @Test
    public void anInnerJoinKeepsThePairsItsConditionHolds() {
        assertEquals("2 ;; 3", rows("SELECT x FROM tt JOIN LATERAL ptf(tt.n) ON TRUE ORDER BY 1"));
        assertEquals("2 ;; 3", rows("SELECT x FROM tt JOIN TABLE(ptf(tt.n)) ON TRUE ORDER BY 1"));
        assertEquals("2", rows("SELECT f.x FROM tt JOIN LATERAL ptf(tt.n) f ON f.x = '2'"));
        assertEquals("2|2 ;; 3|3",
            rows("SELECT tt.n, f.x FROM tt INNER JOIN TABLE(ptf(tt.n)) f ON f.x = TO_VARCHAR(tt.n) ORDER BY 1"));
        assertEquals("3", rows("SELECT COUNT(*) FROM tt JOIN TABLE(ptf2(tt.n)) ON x > 2"));
        assertEquals("2|3 ;; 3|4", rows("SELECT tt.n, x FROM tt JOIN TABLE(ptf2(tt.n)) ON x > tt.n ORDER BY 1"));
        assertEquals("", rows("SELECT x FROM tt JOIN TABLE(ptf(tt.n)) ON x = '9'"));
        assertEquals("3 ;; 4", rows("SELECT x FROM tt JOIN TABLE(ptf(tt.n + 1)) ON TRUE ORDER BY 1"));
    }

    @Test
    public void aRightJoinIsRefusedAsAnOuterJoin() {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT x FROM tt RIGHT JOIN TABLE(ptf(tt.n)) ON TRUE");
            }
        });
        assertEquals("SQL compilation error:\nUnsupported subquery type cannot be evaluated", refused.getMessage());
    }
}
