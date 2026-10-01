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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A set operation's own ORDER BY and LIMIT, where a refusal re-prints the subquery from the plan: they follow
 * the combination — {@code  ORDER BY 1 ASC NULLS LAST} after two spaces, {@code LIMIT 1 OFFSET 0} after one —
 * an ordinal as the output name it counts to (live-verified).
 */
public class SetOperationTailEchoTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
    }

    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            return rs.next() ? String.valueOf(rs.getValue(0)) : "no row";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** ORDER BY, LIMIT, both, DESC and a name as the key. */
    @Test
    public void aSetOperationKeepsItsOwnOrderByAndLimit() {
        final String[][] cells = {
            {"SELECT id FROM fz GROUP BY id ORDER BY (SELECT 1 UNION ALL SELECT 2 ORDER BY 1)",
                "SQL compilation error:|[(SELECT SET_COMBINE(WB$0.\"1\", WB$1.\"2\") AS \"1\" FROM (SELECT OB$0.\"1\" AS \"1\" FROM (SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL) OB$0) WB$0 UNION ALL (SELECT OB$1.\"2\" AS \"2\" FROM (SELECT 2 AS \"2\" FROM (VALUES (null)) DUAL) OB$1) WB$1  ORDER BY 1 ASC NULLS LAST)] is not a valid order by expression"},
            {"SELECT id FROM fz GROUP BY id ORDER BY (SELECT 1 UNION ALL SELECT 2 LIMIT 1)",
                "SQL compilation error:|[(SELECT SET_COMBINE(WB$0.\"1\", WB$1.\"2\") AS \"1\" FROM (SELECT OB$0.\"1\" AS \"1\" FROM (SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL) OB$0) WB$0 UNION ALL (SELECT OB$1.\"2\" AS \"2\" FROM (SELECT 2 AS \"2\" FROM (VALUES (null)) DUAL) OB$1) WB$1 LIMIT 1 OFFSET 0)] is not a valid order by expression"},
            {"SELECT id FROM fz GROUP BY id ORDER BY (SELECT 1 UNION ALL SELECT 2 ORDER BY 1 DESC)",
                "SQL compilation error:|[(SELECT SET_COMBINE(WB$0.\"1\", WB$1.\"2\") AS \"1\" FROM (SELECT OB$0.\"1\" AS \"1\" FROM (SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL) OB$0) WB$0 UNION ALL (SELECT OB$1.\"2\" AS \"2\" FROM (SELECT 2 AS \"2\" FROM (VALUES (null)) DUAL) OB$1) WB$1  ORDER BY 1 DESC NULLS FIRST)] is not a valid order by expression"},
            {"SELECT id FROM fz GROUP BY id ORDER BY (SELECT 1 AS x UNION ALL SELECT 2 ORDER BY x)",
                "SQL compilation error:|[(SELECT SET_COMBINE(WB$0.X, WB$1.\"2\") AS \"X\" FROM (SELECT OB$0.X AS \"X\" FROM (SELECT 1 AS \"X\" FROM (VALUES (null)) DUAL) OB$0) WB$0 UNION ALL (SELECT OB$1.\"2\" AS \"2\" FROM (SELECT 2 AS \"2\" FROM (VALUES (null)) DUAL) OB$1) WB$1  ORDER BY X ASC NULLS LAST)] is not a valid order by expression"},
            {"SELECT id FROM fz GROUP BY id ORDER BY (SELECT 1 UNION ALL SELECT 2 ORDER BY 1 LIMIT 1)",
                "SQL compilation error:|[(SELECT SET_COMBINE(WB$0.\"1\", WB$1.\"2\") AS \"1\" FROM (SELECT OB$0.\"1\" AS \"1\" FROM (SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL) OB$0) WB$0 UNION ALL (SELECT OB$1.\"2\" AS \"2\" FROM (SELECT 2 AS \"2\" FROM (VALUES (null)) DUAL) OB$1) WB$1  ORDER BY 1 ASC NULLS LAST LIMIT 1 OFFSET 0)] is not a valid order by expression"},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }
}
