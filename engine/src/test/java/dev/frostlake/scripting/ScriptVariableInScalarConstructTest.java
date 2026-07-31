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
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A Snowflake Scripting variable referenced inside a scalar construct the procedural builder doesn't
 * model with a dedicated node (a {@code ::} / CAST cast, CASE, IN, BETWEEN, …) must resolve to the
 * variable's value. Previously such constructs were evaluated as a standalone {@code SELECT <text>} that
 * could not see procedural variables, so e.g. {@code w::VARCHAR} silently returned NULL; they now flow
 * through the shared AST evaluator with the current variables supplied as a resolution context.
 */
public class ScriptVariableInScalarConstructTest extends BaseDatabaseTest {

    private Object ret(final String block) {
        final ResultSet rs = engine.executeQuery(block);
        return rs.getRows().isEmpty() ? null : rs.getRows().get(0).getValue(0);
    }

    @Test
    public void doubleColonCastOnVariable() {
        assertEquals("168", String.valueOf(ret("""
            BEGIN
              LET w INTEGER := 168;
              RETURN w::VARCHAR;
            END;""")));
    }

    @Test
    public void castFunctionOnVariable() {
        assertEquals("168", String.valueOf(ret("""
            BEGIN
              LET w INTEGER := 168;
              RETURN CAST(w AS VARCHAR);
            END;""")));
    }

    @Test
    public void castVariableInsideArithmetic() {
        assertEquals(169L, ((Number) ret("""
            BEGIN
              LET w VARCHAR := '168';
              RETURN w::NUMBER + 1;
            END;""")).longValue());
    }

    @Test
    public void variableInsideCaseExpression() {
        assertEquals(168L, ((Number) ret("""
            BEGIN
              LET w INTEGER := 168;
              RETURN CASE WHEN 1 = 1 THEN w ELSE 99 END;
            END;""")).longValue());
    }

    @Test
    public void variableInsideInPredicate() {
        assertEquals(Boolean.TRUE, ret("""
            BEGIN
              LET w INTEGER := 168;
              RETURN w IN (1, 168, 200);
            END;"""));
    }

    @Test
    public void variableInsideBetweenPredicate() {
        assertEquals(Boolean.TRUE, ret("""
            BEGIN
              LET w INTEGER := 168;
              RETURN w BETWEEN 1 AND 200;
            END;"""));
    }

    @Test
    public void literalCastStillWorks() {
        // Regression guard: a cast with no variable was already fine and must stay so.
        assertEquals("168", String.valueOf(ret("""
            BEGIN
              RETURN 168::VARCHAR;
            END;""")));
    }

    @Test
    public void castConcatWithVariableFromDocExample() {
        // The reported doc pattern: w::VARCHAR || ', ' || dt::VARCHAR.
        assertEquals("168, 2020-09-30", String.valueOf(ret("""
            BEGIN
              LET w INTEGER := 168;
              LET dt DATE := '2020-09-30';
              RETURN w::VARCHAR || ', ' || dt::VARCHAR;
            END;""")));
    }

    @Test
    public void semiStructuredCastOnVariableSurvivesIntoAFunctionCall() {
        // Snowflake BINDS the variable's value into the statement and then compiles the surrounding
        // expression, so a written ::VARIANT still applies. Live-verified on a real account
        //: this exact block answers 'available', TYPEOF(v::VARIANT) answers VARCHAR, and
        // dropping the cast errors "Invalid argument types for function 'ARRAY_CONTAINS': (VARCHAR(1),
        // VARIANT)" — reporting the BOUND value's width. Rebuilding the call from argument VALUES alone
        // dropped the cast, so the strict semi-structured families rejected the bare VARCHAR literal
        // that substitution had created.
        assertEquals("available", String.valueOf(ret("""
            DECLARE
              v_target_role VARCHAR := 'ROLE_CALLER';
              role_array VARIANT;
            BEGIN
              SELECT PARSE_JSON('["ROLE_CALLER","OTHER"]') INTO role_array;
              IF (NOT ARRAY_CONTAINS(v_target_role::VARIANT, role_array)) THEN
                RETURN 'NOT available';
              END IF;
              RETURN 'available';
            END;""")));
    }

    @Test
    public void variantCastOnVariableKeepsTypeofWorking() {
        assertEquals("VARCHAR", String.valueOf(ret("""
            BEGIN
              LET v VARCHAR := 'abc';
              RETURN TYPEOF(v::VARIANT);
            END;""")));
    }

    @Test
    public void uncastVariableStaysRejectedByTheStrictFamily() {
        // The rule itself must keep firing: live rejects ARRAY_CONTAINS over a bare VARCHAR needle in
        // every source position tried (literal, column, scripting variable and SQL-UDF parameter).
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                ret("""
                    BEGIN
                      LET v VARCHAR := 'abc';
                      RETURN ARRAY_CONTAINS(v, PARSE_JSON('["abc"]'));
                    END;""");
            }
        });
    }
}
