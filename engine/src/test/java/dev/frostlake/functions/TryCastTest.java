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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TRY_CAST(expr AS type) converts like CAST but returns NULL for a value that cannot be converted, rather
 * than raising an error. A bare NUMBER target follows the NUMBER(38,0) default (rounds to a whole number).
 */
public class TryCastTest extends BaseDatabaseTest {

    private Object val(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    private double num(final String sql) {
        return ((Number) val(sql)).doubleValue();
    }

    @Test
    public void convertsValidValues() {
        assertEquals(123L, ((Number) val("SELECT TRY_CAST('123' AS INTEGER)")).longValue());
        // TRY_CAST converts only FROM a string: a numeric source errors "Function TRY_CAST cannot
        // be used with arguments of types NUMBER(3,0) and VARCHAR(134217728)" (live-verified).
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                val("SELECT TRY_CAST(123 AS VARCHAR)");
            }
        });
        assertEquals(1.5, num("SELECT TRY_CAST('1.5' AS FLOAT)"), 1e-9);
    }

    @Test
    public void returnsNullOnFailure() {
        assertNull(val("SELECT TRY_CAST('abc' AS INTEGER)"));
        assertNull(val("SELECT TRY_CAST('not_a_number' AS NUMBER)"));
    }

    @Test
    public void nullInputStaysNull() {
        assertNull(val("SELECT TRY_CAST(NULL AS INTEGER)"));
    }

    @Test
    public void bareNumberRoundsToScaleZero() {
        assertEquals(406.0, num("SELECT TRY_CAST('405.958' AS NUMBER)"), 1e-9);
        assertEquals(405.96, num("SELECT TRY_CAST('405.958' AS NUMBER(10,2))"), 1e-9);
    }

    @Test
    public void usableInWhereAndProjection() {
        // A realistic pattern: TRY_CAST guards a split_part that may not be numeric.
        final Object v = val("""
            SELECT TRY_CAST(SPLIT_PART('sev:42', ':', 2) AS INTEGER)
            """);
        assertEquals(42L, ((Number) v).longValue());
    }

    @Test
    public void functionCallShapesAreSyntaxErrors() {
        // TRY_CAST is pure grammar: the two-argument function form and the one-argument form are
        // both syntax errors (live-verified: "unexpected ','" / "unexpected ')'"), never calls.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                val("SELECT TRY_CAST('1.5', 'NUMBER(10,2)')");
            }
        });
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                val("SELECT TRY_CAST('1.5')");
            }
        });
    }

    @Test
    public void tryCastRemainsAnIdentifierInNamePositions() {
        // Live accepts try_cast as an alias, a column name and a table name — only the
        // expression/call position is reserved for the cast construct.
        assertEquals(1L, ((Number) val("SELECT 1 AS try_cast")).longValue());
        assertEquals(1L, ((Number) val("SELECT 1 try_cast")).longValue());
        engine.executeQuery("CREATE TABLE try_cast_names (try_cast INT)");
        engine.executeQuery("CREATE TABLE try_cast (a INT)");
        engine.executeQuery("INSERT INTO try_cast VALUES (7)");
        assertEquals(7L, ((Number) val("SELECT a FROM try_cast")).longValue());
    }

    @Test
    public void bareExpressionPositionIsTheConstruct() {
        // In EXPRESSION position TRY_CAST always begins the cast construct, never a column
        // reference — live refuses the bare reference with a syntax error at the NEXT token in
        // every expression clause (live-verified: SELECT "unexpected 'FROM'", FROM-less
        // "unexpected '<EOF>'", WHERE "unexpected '='", GROUP BY / ORDER BY "unexpected '<EOF>'",
        // mid-arithmetic "unexpected '+'").
        engine.executeQuery("CREATE TABLE try_cast_expr (id INTEGER, try_cast INTEGER)");
        engine.executeQuery("INSERT INTO try_cast_expr VALUES (1, 7)");
        assertSyntaxRefused("SELECT try_cast FROM try_cast_expr");
        assertSyntaxRefused("SELECT TRY_CAST");
        assertSyntaxRefused("SELECT id FROM try_cast_expr WHERE try_cast = 7");
        assertSyntaxRefused("SELECT COUNT(*) FROM try_cast_expr GROUP BY try_cast");
        assertSyntaxRefused("SELECT id FROM try_cast_expr ORDER BY try_cast");
        assertSyntaxRefused("SELECT try_cast + 1 FROM try_cast_expr");
    }

    @Test
    public void qualifiedAndQuotedReferencesReachTheColumn() {
        // The two live-legal escapes for a column literally named try_cast: qualify it (table or
        // alias) or quote it; SET targets and INSERT column lists are name positions and need
        // neither (all live-verified).
        engine.executeQuery("CREATE TABLE try_cast_q (id INTEGER, try_cast INTEGER)");
        engine.executeQuery("INSERT INTO try_cast_q VALUES (1, 7)");
        assertEquals(7L, ((Number) val("SELECT try_cast_q.try_cast FROM try_cast_q")).longValue());
        assertEquals(7L, ((Number) val("SELECT x.try_cast FROM try_cast_q x")).longValue());
        assertEquals(7L, ((Number) val("SELECT \"TRY_CAST\" FROM try_cast_q")).longValue());
        engine.executeQuery("UPDATE try_cast_q SET try_cast = 8");
        assertEquals(8L, ((Number) val("SELECT x.try_cast FROM try_cast_q x")).longValue());
        engine.executeQuery("INSERT INTO try_cast_q (id, try_cast) VALUES (2, 9)");
        assertEquals(2L, ((Number) val("SELECT COUNT(*) FROM try_cast_q")).longValue());
    }

    private void assertSyntaxRefused(final String sql) {
        final RuntimeException refusal = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                val(sql);
            }
        }, sql);
        assertTrue(refusal.getMessage().toLowerCase().contains("syntax"),
            sql + " should be refused as a SYNTAX error, was: " + refusal.getMessage());
    }
}
