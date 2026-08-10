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

import dev.frostlake.BaseDatabaseTest;

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
public class ProceduralExpressionAstCompletenessTest extends BaseDatabaseTest {


    /** Run "BEGIN IF (cond) THEN RETURN 1; END IF; RETURN 0; END" — the condition goes through buildExpression. */
    private int ifReturns(final String cond) {
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

    // ---- a DECLARE initializer that is a variant path over a procedural variable ----
    // e.g. DECLARE m STRING := o:result:code; — the initializer goes through evaluateExpression, whose
    // text fallback ran a bare "SELECT o:result:code" that could not see the scripting variable `o`, so
    // the variable silently held the literal text "o:result:code" instead of the extracted value.

    /** CALL a proc that declares `r` from the given initializer over its OBJECT param `o`, and RETURN :r. */
    private String declInit(final String initializer) {
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_di(o OBJECT) RETURNS STRING LANGUAGE SQL AS $$\n"
            + "DECLARE r STRING := " + initializer + ";\n"
            + "BEGIN RETURN :r::STRING; END $$");
        final Object v = engine.executeQuery(
            "CALL p_di(OBJECT_CONSTRUCT('result', OBJECT_CONSTRUCT('code', 'XYZ')))")
            .getRows().get(0).getValue(0);
        return v == null ? null : v.toString();
    }

    @Test
    public void declareInitFromVariantPath() {
        assertEquals("XYZ", declInit("o:result:code"));
    }

    @Test
    public void declareInitFromVariantPathWithCast() {
        assertEquals("XYZ", declInit("o:result:code::string"));
    }

    @Test
    public void declareInitFromConcatWithVariantPath() {
        assertEquals("v=XYZ", declInit("'v=' || o:result:code"));
    }

    @Test
    public void declareInitDynamicRaiseFromStatsPaths() {
        // The ASSERT_CALL shape: an exception handler builds a dynamic RAISE whose message is concatenated
        // from stats variant paths, then EXECUTE IMMEDIATEs it. The paths must be evaluated (not literal).
        engine.execute("""
            CREATE OR REPLACE PROCEDURE assert_like(stats OBJECT) RETURNS OBJECT LANGUAGE SQL AS $$
            DECLARE ex_assert EXCEPTION (-20002, 'SP call failed');
            BEGIN
              IF (stats:result:err_code != 0) THEN RAISE ex_assert; END IF;
              RETURN stats;
            EXCEPTION WHEN OTHER THEN
              DECLARE
                ERR_MSG STRING := stats:result:sql_err_msg;
                ERR_CODE STRING := stats:result:err_code;
                STMT VARCHAR := 'DECLARE E EXCEPTION (-20003, ''Inner: ' || ERR_MSG || ' code=' || ERR_CODE || '''); BEGIN RAISE E; END;';
              BEGIN
                EXECUTE IMMEDIATE :STMT;
              END;
            END $$""");
        final RuntimeException ex = org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
            new org.junit.jupiter.api.function.Executable() {
                @Override
                public void execute() {
                    engine.executeQuery(
                        "CALL assert_like(OBJECT_CONSTRUCT('result', OBJECT_CONSTRUCT('err_code', 5, 'sql_err_msg', 'boom')))");
                }
            });
        final String msg = ex.getMessage();
        org.junit.jupiter.api.Assertions.assertTrue(msg.contains("Inner: boom code=5"), msg);
    }
}
