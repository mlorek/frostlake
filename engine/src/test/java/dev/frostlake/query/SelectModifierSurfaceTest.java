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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SELECT row-limiting and star-modifier surface, measured statement by statement against a
 * real account (40-cell dual probe, verdicts fully agreeing): every part of the ANSI FETCH is
 * optional but the count; {@code OFFSET n [ROWS]} may prefix a FETCH but never stand alone;
 * {@code LIMIT ''} and {@code OFFSET ''} are the documented empty-string defaults while any other
 * string refuses at the literal; the star modifiers come in ONE order — ILIKE-or-EXCLUDE, then
 * REPLACE, then RENAME, each at most once; and TOP beside a LIMIT or FETCH refuses with
 * {@code Duplicate LIMIT: <keyword>.}.
 */
public class SelectModifierSurfaceTest extends BaseDatabaseTest {

    @BeforeEach
    public void createFixture() {
        engine.execute("CREATE TABLE sm_t (a INTEGER, b INTEGER, c VARCHAR)");
        engine.execute("INSERT INTO sm_t VALUES (1, 10, 'x'), (2, 20, 'y'), (3, 30, 'z')");
    }

    private int rows(final String sql) {
        return engine.executeQuery(sql).getRowCount();
    }

    private RuntimeException refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
    }

    @Test
    public void everyFetchSpellingWorks() {
        assertEquals(2, rows("SELECT a FROM sm_t FETCH 2"));
        assertEquals(2, rows("SELECT a FROM sm_t FETCH FIRST 2"));
        assertEquals(2, rows("SELECT a FROM sm_t FETCH NEXT 2 ROWS ONLY"));
        assertEquals(2, rows("SELECT a FROM sm_t FETCH FIRST 2 ROW ONLY"));
    }

    @Test
    public void offsetRowsPrefixesFetch() {
        final ResultSet rs = engine.executeQuery(
            "SELECT a FROM sm_t ORDER BY a OFFSET 1 ROWS FETCH NEXT 2 ROWS ONLY");
        assertEquals(2, rs.getRowCount());
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(1, rows("SELECT a FROM sm_t ORDER BY a OFFSET 2 FETCH 5"));
    }

    @Test
    public void bareOffsetWithoutFetchStaysASyntaxError() {
        assertTrue(refusal("SELECT a FROM sm_t OFFSET 1").getMessage().contains("syntax error"));
        assertTrue(refusal("SELECT a FROM sm_t OFFSET 1 ROWS").getMessage().contains("syntax error"));
    }

    @Test
    public void emptyStringLimitAndOffsetAreTheDocumentedDefaults() {
        assertEquals(3, rows("SELECT a FROM sm_t LIMIT ''"));
        assertEquals(3, rows("SELECT a FROM sm_t LIMIT '' OFFSET ''"));
        assertEquals(2, rows("SELECT a FROM sm_t ORDER BY a LIMIT 2 OFFSET ''"));
    }

    @Test
    public void nonEmptyStringLimitRefusesAtTheLiteral() {
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 25 unexpected ''3''.",
            refusal("SELECT a FROM sm_t LIMIT '3'").getMessage());
        assertTrue(refusal("SELECT a FROM sm_t LIMIT 'abc'").getMessage()
            .contains("unexpected ''abc''."));
    }

    @Test
    public void starModifiersKeepTheirFixedOrder() {
        assertEquals(3, rows("SELECT * EXCLUDE c FROM sm_t"));
        assertEquals(3, rows("SELECT * EXCLUDE c RENAME a AS x FROM sm_t"));
        assertEquals(3, rows("SELECT * REPLACE (a + 1 AS a) RENAME b AS y FROM sm_t"));

        assertTrue(refusal("SELECT * RENAME a AS x EXCLUDE c FROM sm_t").getMessage()
            .contains("unexpected 'EXCLUDE'"));
        assertTrue(refusal("SELECT * RENAME b AS y REPLACE (a + 1 AS a) FROM sm_t").getMessage()
            .contains("unexpected 'REPLACE'"));
        assertTrue(refusal("SELECT * EXCLUDE a EXCLUDE b FROM sm_t").getMessage()
            .contains("unexpected 'EXCLUDE'"));
    }

    @Test
    public void ilikeAndExcludeAreMutuallyExclusive() {
        assertEquals(3, rows("SELECT * ILIKE 'a%' FROM sm_t"));
        assertTrue(refusal("SELECT * ILIKE 'a%' EXCLUDE b FROM sm_t").getMessage()
            .contains("unexpected 'EXCLUDE'"));
        assertTrue(refusal("SELECT * EXCLUDE a ILIKE 'b%' FROM sm_t").getMessage()
            .contains("unexpected 'ILIKE'"));
    }

    @Test
    public void topBesideLimitOrFetchRefusesAsDuplicate() {
        assertEquals(2, rows("SELECT TOP 2 a FROM sm_t"));
        assertEquals("SQL compilation error:\nDuplicate LIMIT: LIMIT.",
            refusal("SELECT TOP 2 a FROM sm_t LIMIT 1").getMessage());
        assertEquals("SQL compilation error:\nDuplicate LIMIT: FETCH.",
            refusal("SELECT TOP 2 a FROM sm_t FETCH FIRST 1 ROWS ONLY").getMessage());
    }
}
