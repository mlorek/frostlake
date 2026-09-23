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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A condition must be a BOOLEAN, and that holds for a name belonging to the query AROUND a subquery
 * just as it does for one of the subquery's own tables.
 *
 * <p>Such a name has no type inside the subquery, and an untyped condition used to pass the rule
 * unexamined — {@code (SELECT 1 FROM t2 WHERE n)} over a NUMBER column of the outer query ran and
 * returned rows where Snowflake refuses it while compiling. The enclosing query's scope types it, and
 * the refusal names it the way the plan spells a correlated reference: {@code CORRELATION(RT.N)}.
 */
public class OuterColumnPredicateTypeTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE rt (n INT, n52 NUMBER(5,2), g VARCHAR, bo BOOLEAN)");
        engine.execute("CREATE OR REPLACE TABLE t2 (a INT, b INT)");
    }

    /** The message of the refusal the statement raises. */
    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                final ResultSet rs = engine.executeQuery(sql);
                while (rs.next()) {
                    continue;
                }
            }
        }).getMessage().replace("\n", " ");
    }

    /** The number of rows the statement answers with. */
    private int rows(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        int rows = 0;
        while (rs.next()) {
            rows++;
        }
        return rows;
    }

    /** A bare outer column as the whole condition is refused, named as a correlation. */
    @Test
    public void anOuterColumnIsNotACondition() {
        assertTrue(refusal("SELECT (SELECT 1 FROM t2 WHERE n) FROM rt").contains(
            "Invalid data type [NUMBER(38,0)] for predicate [CORRELATION(RT.N)]"));
        assertTrue(refusal("SELECT (SELECT 1 FROM t2 WHERE rt.n) FROM rt").contains(
            "Invalid data type [NUMBER(38,0)] for predicate [CORRELATION(RT.N)]"),
            "qualified or bare, it is the same name");
    }

    /** The refusal names the column's OWN type, read through the enclosing query's scope. */
    @Test
    public void theTypeNamedIsTheOuterColumnsOwn() {
        assertTrue(refusal("SELECT (SELECT 1 FROM t2 WHERE g) FROM rt").contains(
            "Invalid data type [VARCHAR(16777216)] for predicate [CORRELATION(RT.G)]"));
        assertTrue(refusal("SELECT (SELECT 1 FROM t2 WHERE n52) FROM rt").contains(
            "Invalid data type [NUMBER(5,2)] for predicate [CORRELATION(RT.N52)]"),
            "the declared width, not a family default");
    }

    /** An expression over an outer column is refused too, and echoed as written. */
    @Test
    public void anExpressionOverOneIsRefusedAsWritten() {
        assertTrue(refusal("SELECT (SELECT 1 FROM t2 WHERE n + 1) FROM rt").contains(
            "Invalid data type [NUMBER(38,0)] for predicate [CORRELATION(RT.N) + 1]"));
    }

    /** Every clause that takes a condition applies the rule: WHERE, HAVING, QUALIFY, JOIN ON. */
    @Test
    public void everyConditionClauseAppliesIt() {
        assertTrue(refusal("SELECT (SELECT 1 FROM t2 GROUP BY a HAVING n) FROM rt").contains(
            "Invalid data type [NUMBER(38,0)] for predicate [CORRELATION(RT.N)]"));
        assertTrue(refusal("SELECT (SELECT a FROM t2 QUALIFY n) FROM rt").contains(
            "Invalid data type [NUMBER(38,0)] for predicate [CORRELATION(RT.N)]"));
        assertTrue(refusal("SELECT * FROM rt WHERE EXISTS (SELECT 1 FROM t2 JOIN t2 u ON n)").contains(
            "Invalid data type [NUMBER(38,0)] for predicate [CORRELATION(RT.N)]"));
    }

    /** It reaches a subquery under IN and under EXISTS alike. */
    @Test
    public void itReachesEveryKindOfSubquery() {
        assertTrue(refusal("SELECT * FROM rt WHERE n IN (SELECT a FROM t2 WHERE g)").contains(
            "Invalid data type [VARCHAR(16777216)] for predicate [CORRELATION(RT.G)]"));
        assertTrue(refusal("SELECT * FROM rt WHERE EXISTS (SELECT 1 FROM t2 WHERE n52)").contains(
            "Invalid data type [NUMBER(5,2)] for predicate [CORRELATION(RT.N52)]"));
    }

    /** A BOOLEAN outer column is a condition, and so is a comparison over any outer column. */
    @Test
    public void aBooleanOuterColumnIsAccepted() {
        assertEquals(0, rows("SELECT (SELECT 1 FROM t2 WHERE bo) FROM rt"));
        assertEquals(0, rows("SELECT (SELECT 1 FROM t2 WHERE n = 1) FROM rt"));
    }

    /** A column of the subquery's own table is refused the same way, named without CORRELATION. */
    @Test
    public void anInnerColumnStillReadsAsItsOwnRelation() {
        assertTrue(refusal("SELECT (SELECT 1 FROM t2 WHERE a) FROM rt").contains(
            "Invalid data type [NUMBER(38,0)] for predicate [T2.A]"));
    }
}
