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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@code LET name RESULTSET} — the LET spelling of a resultset declaration, accepted in every
 * combination a real account takes (live-verified): bound by {@code :=} or {@code DEFAULT} to a
 * parenthesized SELECT, EXECUTE IMMEDIATE or CALL, or declared BARE with no initializer at all.
 * DECLARE takes the same combinations. The initializer must be one of those statements: a scalar
 * ({@code LET r RESULTSET := 1}) is a syntax error at the scalar's own offset.
 */
public class LetResultSetTest extends BaseDatabaseTest {

    private String firstCell(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void letBoundToSelect() {
        assertEquals("1", firstCell("""
            BEGIN
              LET r RESULTSET := (SELECT 1 AS x);
              RETURN TABLE(r);
            END;"""));
    }

    @Test
    public void letDefaultSelect() {
        assertEquals("2", firstCell("""
            BEGIN
              LET r RESULTSET DEFAULT (SELECT 2 AS y);
              RETURN TABLE(r);
            END;"""));
    }

    @Test
    public void letBoundToExecuteImmediate() {
        assertEquals("3", firstCell("""
            BEGIN
              LET r RESULTSET := (EXECUTE IMMEDIATE 'SELECT 3 AS z');
              RETURN TABLE(r);
            END;"""));
    }

    @Test
    public void letBoundToCall() {
        engine.execute("""
            CREATE OR REPLACE PROCEDURE lrs_bok()
            RETURNS VARCHAR LANGUAGE SQL AS $$
            BEGIN
              RETURN 'fine';
            END;
            $$""");
        assertEquals("fine", firstCell("""
            BEGIN
              LET r RESULTSET := (CALL lrs_bok());
              RETURN TABLE(r);
            END;"""));
    }

    @Test
    public void bareLetDeclaresAnUnboundResultset() {
        assertEquals("x", firstCell("""
            BEGIN
              LET r RESULTSET;
              RETURN 'x';
            END;"""));
    }

    @Test
    public void letResultsetReassigned() {
        assertEquals("4", firstCell("""
            BEGIN
              LET r RESULTSET := (SELECT 1 AS x);
              r := (SELECT 4 AS w);
              RETURN TABLE(r);
            END;"""));
    }

    @Test
    public void declareTakesAssignAndCallAndExecuteImmediate() {
        assertEquals("5", firstCell("""
            DECLARE
              r RESULTSET := (SELECT 5 AS v);
            BEGIN
              RETURN TABLE(r);
            END;"""));
        assertEquals("6", firstCell("""
            DECLARE
              r RESULTSET := (EXECUTE IMMEDIATE 'SELECT 6 AS u');
            BEGIN
              RETURN TABLE(r);
            END;"""));
        engine.execute("""
            CREATE OR REPLACE PROCEDURE lrs_bok2()
            RETURNS VARCHAR LANGUAGE SQL AS $$
            BEGIN
              RETURN 'fine';
            END;
            $$""");
        assertEquals("fine", firstCell("""
            DECLARE
              r RESULTSET DEFAULT (CALL lrs_bok2());
            BEGIN
              RETURN TABLE(r);
            END;"""));
    }

    @Test
    public void scalarInitializerIsASyntaxError() {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("""
                    BEGIN
                      LET r RESULTSET := 1;
                      RETURN 'x';
                    END;""");
            }
        });
        // One line, exactly — the block's END is never named after the fault (live-verified).
        assertEquals("SQL compilation error:\nsyntax error line 2 at position 21 unexpected '1'.",
            e.getMessage());
    }
}
