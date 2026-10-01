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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A WHERE may read a select alias, but not one whose item holds an aggregate — an aggregate called as a window
 * included — nor one whose item is a window call. Live refuses the first as {@code aggregate function alias 'C'
 * cannot be used in the WHERE clause} at the reference, and the second with the window sentence naming the call,
 * unpositioned; a column of the name still resolves, and a bad name earlier in the predicate still speaks first.
 * Every cell is live-verified.
 */
public class WhereAggregateAliasTest extends BaseDatabaseTest {

    @BeforeEach
    public void createTable() {
        engine.execute("CREATE TABLE t (x NUMBER, y NUMBER)");
        engine.execute("INSERT INTO t VALUES (1, 10), (2, 20)");
    }

    @Test
    public void anAggregateOrWindowAliasIsRefusedInWhere() {
        for (final String[] cell : new String[][] {
            {"SELECT COUNT(*) AS c FROM t WHERE c = 1", "SQL compilation error: error line 1 at position 34\naggregate function alias 'C' cannot be used in the WHERE clause"},
            {"SELECT COUNT(*) AS c WHERE c = 1", "SQL compilation error: error line 1 at position 27\naggregate function alias 'C' cannot be used in the WHERE clause"},
            {"SELECT SUM(1) AS s WHERE s = 1", "SQL compilation error: error line 1 at position 25\naggregate function alias 'S' cannot be used in the WHERE clause"},
            {"SELECT ROW_NUMBER() OVER (ORDER BY 1) AS r WHERE r = 1", "SQL compilation error:\nWindow function [ROW_NUMBER() OVER (ORDER BY 1 ASC NULLS LAST)] appears outside of SELECT, QUALIFY, and ORDER BY clauses."},
            {"SELECT SUM(x) AS s FROM t WHERE s = 1", "SQL compilation error: error line 1 at position 32\naggregate function alias 'S' cannot be used in the WHERE clause"},
            {"SELECT x, COUNT(*) OVER () AS w FROM t WHERE w = 1", "SQL compilation error: error line 1 at position 45\naggregate function alias 'W' cannot be used in the WHERE clause"},
            {"SELECT COUNT(*) + 1 AS c FROM t WHERE c = 1", "SQL compilation error: error line 1 at position 38\naggregate function alias 'C' cannot be used in the WHERE clause"},
            {"SELECT SUM(x) OVER () AS w FROM t WHERE w > 0", "SQL compilation error: error line 1 at position 40\naggregate function alias 'W' cannot be used in the WHERE clause"},
            {"SELECT COUNT(*) AS c FROM t WHERE c + 1 = 2", "SQL compilation error: error line 1 at position 34\naggregate function alias 'C' cannot be used in the WHERE clause"},
            {"SELECT COUNT(*) AS c FROM t WHERE UPPER(c) = 'x'", "SQL compilation error: error line 1 at position 40\naggregate function alias 'C' cannot be used in the WHERE clause"},
            {"SELECT COUNT(*) AS c FROM t WHERE missing = 1 AND c = 1", "SQL compilation error: error line 1 at position 34\ninvalid identifier 'MISSING'"},
            {"SELECT COUNT(*) AS c FROM t WHERE c = 1 AND missing = 1", "SQL compilation error: error line 1 at position 34\naggregate function alias 'C' cannot be used in the WHERE clause"},
            {"SELECT MAX(x) AS m FROM t WHERE m > 0 GROUP BY y", "SQL compilation error: error line 1 at position 32\naggregate function alias 'M' cannot be used in the WHERE clause"},
            {"SELECT x, RANK() OVER (ORDER BY y) AS rk FROM t WHERE rk = 1", "SQL compilation error:\nWindow function [RANK() OVER (ORDER BY T.Y ASC NULLS LAST)] appears outside of SELECT, QUALIFY, and ORDER BY clauses."},
            {"SELECT x, LAG(x) OVER (ORDER BY y) AS l FROM t WHERE l IS NULL", "SQL compilation error:\nWindow function [LAG(T.X) OVER (ORDER BY T.Y ASC NULLS LAST)] appears outside of SELECT, QUALIFY, and ORDER BY clauses."},
            {"SELECT COUNT(*) AS \"c\" FROM t WHERE \"c\" = 1", "SQL compilation error: error line 1 at position 36\naggregate function alias '\"c\"' cannot be used in the WHERE clause"},
            {"SELECT COUNT(*) AS c FROM t t1 WHERE t1.c = 1", "SQL compilation error: error line 1 at position 37\ninvalid identifier 'T1.C'"},
            {"SELECT COUNT(*) AS c, SUM(x) AS s FROM t WHERE s = 1 AND c = 1", "SQL compilation error: error line 1 at position 47\naggregate function alias 'S' cannot be used in the WHERE clause"},
            {"SELECT ROW_NUMBER() OVER (PARTITION BY y ORDER BY x DESC) AS r FROM t WHERE r = 1", "SQL compilation error:\nWindow function [ROW_NUMBER() OVER (PARTITION BY T.Y ORDER BY T.X DESC NULLS FIRST)] appears outside of SELECT, QUALIFY, and ORDER BY clauses."},
            {"SELECT COUNT(DISTINCT x) AS c FROM t WHERE c = 1", "SQL compilation error: error line 1 at position 43\naggregate function alias 'C' cannot be used in the WHERE clause"},
            {"SELECT x AS a, COUNT(*) OVER () AS c FROM t WHERE a = 1 AND c = 1", "SQL compilation error: error line 1 at position 60\naggregate function alias 'C' cannot be used in the WHERE clause"},
        }) {
            final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.executeQuery(cell[0]);
                }
            }, cell[0]);
            assertTrue(String.valueOf(refused.getMessage()).contains(cell[1]),
                cell[0] + " should be refused with [" + cell[1] + "] but read: " + refused.getMessage());
        }
    }

    @Test
    public void aPlainAliasOrAColumnOfTheNameStillReads() {
        for (final String[] cell : new String[][] {
            {"SELECT x AS c FROM t WHERE c = 1", "1"},
            {"SELECT COUNT(*) AS x FROM t WHERE x = 1", "1"},
        }) {
            final StringBuilder answer = new StringBuilder();
            for (final Row row : engine.executeQuery(cell[0]).getRows()) {
                if (answer.length() > 0) {
                    answer.append(" | ");
                }
                answer.append(row.getValue(0));
            }
            assertEquals(cell[1], answer.toString(), cell[0]);
        }
    }
}
