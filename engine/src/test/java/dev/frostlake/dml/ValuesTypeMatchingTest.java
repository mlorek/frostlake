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

package dev.frostlake.dml;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.ExecutionResult;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An INSERT ... VALUES cell is type-matched against its column by its STATIC type, at compile time, exactly
 * as an INSERT ... SELECT item is. A predicate into a NUMBER column is refused with
 * {@code Expression type does not match column data type, expecting NUMBER(38,0) but got BOOLEAN for
 * column N}: a comparison, NOT, AND/OR, LIKE, IS NULL, EXISTS, IN, BETWEEN, a boolean function or
 * conditional, and a scalar subquery over one. A DATE into a NUMBER is refused the same way, and so is a
 * number into a DATE. Nothing is written. NOT, AND and OR parse in every VALUES list, as a select item's
 * do. Every cell is live-verified.
 */
public class ValuesTypeMatchingTest extends BaseDatabaseTest {

    private static final String BOOLEAN_INTO_N =
        "Expression type does not match column data type, expecting NUMBER(38,0) but got BOOLEAN for column N";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE ti (v VARCHAR, n NUMBER, b BOOLEAN, d DATE)");
    }

    private void assertRefused(final String sql, final String sentence) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                final ExecutionResult result = engine.execute(sql);
                if (!result.isSuccess()) {
                    throw new RuntimeException(result.getErrorMessage());
                }
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(sentence), sql + " -> " + refused.getMessage());
    }

    private long count() {
        return ((Number) engine.executeQuery("SELECT COUNT(*) FROM ti").getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void aPredicateIntoANumberColumnIsRefusedAtCompileTime() {
        assertRefused("INSERT INTO ti (n) VALUES (1 = 1)", BOOLEAN_INTO_N);
        assertRefused("INSERT INTO ti (n) VALUES (NOT TRUE)", BOOLEAN_INTO_N);
        assertRefused("INSERT INTO ti (n) VALUES (1 < 2)", BOOLEAN_INTO_N);
        assertRefused("INSERT INTO ti (n) VALUES (1 <> 1)", BOOLEAN_INTO_N);
        assertRefused("INSERT INTO ti (n) VALUES ('a' LIKE 'a')", BOOLEAN_INTO_N);
        assertRefused("INSERT INTO ti (n) VALUES (TRUE AND FALSE)", BOOLEAN_INTO_N);
        assertRefused("INSERT INTO ti (n) VALUES (1 = 1 OR NULL)", BOOLEAN_INTO_N);
        assertRefused("INSERT INTO ti (n) VALUES (NOT (1 = 1))", BOOLEAN_INTO_N);
        assertRefused("INSERT INTO ti (n) VALUES (1 IS NULL)", BOOLEAN_INTO_N);
        assertRefused("INSERT INTO ti (n) VALUES (EXISTS (SELECT 1))", BOOLEAN_INTO_N);
        assertRefused("INSERT INTO ti (n) VALUES (1 IN (1, 2))", BOOLEAN_INTO_N);
        assertRefused("INSERT INTO ti (n) VALUES (1 BETWEEN 0 AND 2)", BOOLEAN_INTO_N);
        assertRefused("INSERT INTO ti (n) VALUES (NULL = NULL)", BOOLEAN_INTO_N);
        assertRefused("INSERT INTO ti (n) VALUES (IFF(TRUE, 1 = 1, FALSE))", BOOLEAN_INTO_N);
        assertRefused("INSERT INTO ti (n) VALUES (TO_BOOLEAN('t'))", BOOLEAN_INTO_N);
        assertRefused("INSERT INTO ti (n) VALUES (BOOLAND(1, 1))", BOOLEAN_INTO_N);
        assertRefused("INSERT INTO ti (n) VALUES ((SELECT 1 = 1))", BOOLEAN_INTO_N);
        assertEquals(0L, count());
    }

    @Test
    public void theLiteralTheSelectFormAndEveryRowAgree() {
        assertRefused("INSERT INTO ti (n) VALUES (TRUE)", BOOLEAN_INTO_N);
        assertRefused("INSERT INTO ti (n) SELECT 1 = 1", BOOLEAN_INTO_N);
        assertRefused("INSERT INTO ti (n) VALUES (1 = 1), (1 = 2)", BOOLEAN_INTO_N);
        assertRefused("INSERT INTO ti (n) VALUES (NULL), (TRUE)", BOOLEAN_INTO_N);
        assertRefused("INSERT INTO ti (n, b) VALUES (1 = 1, 1 = 1)", BOOLEAN_INTO_N);
        assertRefused("INSERT INTO ti (v, n) VALUES ('x', 1 = 1)", BOOLEAN_INTO_N);
        assertRefused("INSERT INTO ti VALUES ('z', 1 = 1, TRUE, '2024-01-01')", BOOLEAN_INTO_N);
        assertEquals(0L, count());
    }

    @Test
    public void logicalOperatorsParseInEveryValuesList() {
        engine.execute("INSERT INTO ti (b) VALUES (NOT TRUE)");
        engine.execute("INSERT INTO ti (b) VALUES (TRUE AND FALSE)");
        engine.execute("MERGE INTO ti USING (SELECT 1 AS k) s ON FALSE"
            + " WHEN NOT MATCHED THEN INSERT (b) VALUES (NOT TRUE)");
        assertEquals(3L, count());
        assertEquals("false", String.valueOf(
            engine.executeQuery("SELECT column1 FROM (VALUES (NOT TRUE))").getRows().get(0).getValue(0)));
    }

    @Test
    public void otherFamiliesMatchTheSameWay() {
        assertRefused("INSERT INTO ti (n) VALUES ('2024-01-01'::DATE)",
            "Expression type does not match column data type, expecting NUMBER(38,0) but got DATE for column N");
        assertRefused("INSERT INTO ti (n) VALUES (CURRENT_DATE)",
            "Expression type does not match column data type, expecting NUMBER(38,0) but got DATE for column N");
        assertRefused("INSERT INTO ti (d) VALUES (5)",
            "Expression type does not match column data type, expecting DATE but got NUMBER(1,0) for column D");
        // A conditional that answers a number is a number, whatever its condition.
        engine.execute("INSERT INTO ti (n) VALUES (IFF(1 = 1, 1, 0))");
        assertEquals(1L, count());
    }
}
