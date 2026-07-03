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

package dev.frostlake.scripting;

import dev.frostlake.DatabaseEngine;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that procedural expressions which use constructs the lightweight procedural builder
 * doesn't model directly (CASE, BETWEEN, IN, LIKE) are now evaluated through the full query AST (the
 * {@code SqlScalarExpression} bridge) rather than degrading to their source text.
 *
 * <p>These exercise the IF-condition path (handleIfStatement/buildProceduralStatement ->
 * buildExpression -> bridge), which is where the removed text fallback lived: before the migration
 * the condition was a {@code LiteralExpression} of its source text (a non-empty, truthy string), so
 * every IF would have been taken regardless of the actual predicate.
 */
public class ProceduralExpressionAstCompletenessTest {

    private static DatabaseEngine engine;

    @BeforeAll
    public static void setup() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE IF NOT EXISTS test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
    }

    @AfterAll
    public static void teardown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    /** Run "BEGIN IF (cond) THEN RETURN 1; END IF; RETURN 0; END" — the condition goes through buildExpression. */
    private static int ifReturns(final String cond) {
        final Object v = engine.executeQuery(
            "BEGIN\n    IF (" + cond + ") THEN\n        RETURN 1;\n    END IF;\n    RETURN 0;\nEND")
            .getRows().get(0).getValue(0);
        return ((Number) v).intValue();
    }

    @Test
    public void testBetweenTrue() {
        assertEquals(1, ifReturns("5 BETWEEN 1 AND 10"));
    }

    @Test
    public void testBetweenFalse() {
        assertEquals(0, ifReturns("50 BETWEEN 1 AND 10"));
    }

    @Test
    public void testInListTrue() {
        assertEquals(1, ifReturns("2 IN (1, 2, 3)"));
    }

    @Test
    public void testInListFalse() {
        assertEquals(0, ifReturns("9 IN (1, 2, 3)"));
    }

    @Test
    public void testLikeTrue() {
        assertEquals(1, ifReturns("'abc' LIKE 'a%'"));
    }

    @Test
    public void testCaseCondition() {
        assertEquals(1, ifReturns("CASE WHEN 1 = 1 THEN TRUE ELSE FALSE END"));
    }
}
