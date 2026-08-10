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

/**
 * The generated NAME of an unaliased select item, all live-verified. A (possibly parenthesized,
 * possibly qualified) column reference is named after the COLUMN — {@code (amount)},
 * {@code ( amount )}, {@code ((amount))} and {@code s.amount} all project {@code AMOUNT}, and a
 * quoted reference keeps the column's own case. EVERY other expression is named by its source text
 * folded to upper case, bluntly: whitespace and newlines survive exactly as written, string
 * literals fold ({@code upper('a')} is named {@code UPPER('A')}), quoted identifiers INSIDE the
 * text fold too ({@code "lc" || 'x'} is named {@code "LC" || 'X'}), nothing is truncated, and a
 * FROM-less literal is named after itself ({@code SELECT 1} projects a column named {@code 1}).
 * Only an alias escapes the fold, and only when QUOTED — an unquoted alias is an identifier and
 * upper-cases like one. A set operation takes its names from the first branch.
 */
public class DerivedColumnNameTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE TABLE sales (region VARCHAR, product VARCHAR, amount NUMBER)");
        engine.execute("INSERT INTO sales VALUES ('east','a',10),('west','b',20)");
        engine.execute("CREATE TABLE q188 (\"lc\" VARCHAR)");
        engine.execute("INSERT INTO q188 VALUES ('x')");
    }

    private String name(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getColumns().get(0).getName();
    }

    // ── the text fold ────────────────────────────────────────────────────────

    @Test
    public void theSourceTextIsPreservedVerbatimAndUpperCased() {
        assertEquals("AMOUNT    +    1", name("SELECT amount    +    1 FROM sales"));
        assertEquals("AMOUNT+1", name("SELECT amount+1 FROM sales"));
        assertEquals("AMOUNT::INT", name("SELECT amount::int FROM sales"));
    }

    @Test
    public void aNewlineInsideTheExpressionSurvives() {
        assertEquals("AMOUNT +\n1", name("SELECT amount +\n1 FROM sales"));
    }

    @Test
    public void literalsAndQuotedIdentifiersInsideTheTextFoldToo() {
        assertEquals("UPPER('A')", name("SELECT upper('a')"));
        assertEquals("\"LC\" || 'X'", name("SELECT \"lc\" || 'x' FROM q188"));
    }

    @Test
    public void nothingIsTruncated() {
        assertEquals("UPPER('ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789')",
            name("SELECT upper('abcdefghijklmnopqrstuvwxyz0123456789') FROM sales"));
    }

    @Test
    public void aFromLessLiteralIsNamedAfterItself() {
        assertEquals("1", name("SELECT 1"));
        assertEquals("'X'", name("SELECT 'x'"));
    }

    @Test
    public void aggregateItemsFoldLikeAnyOtherText() {
        final ResultSet rs = engine.executeQuery(
            "SELECT count(*), sum(amount), MAX(amount), MIN(amount) FROM sales");
        assertEquals("COUNT(*)", rs.getColumns().get(0).getName());
        assertEquals("SUM(AMOUNT)", rs.getColumns().get(1).getName());
        assertEquals("MAX(AMOUNT)", rs.getColumns().get(2).getName());
        assertEquals("MIN(AMOUNT)", rs.getColumns().get(3).getName());
    }

    // ── column references escape the fold ────────────────────────────────────

    @Test
    public void aColumnReferenceIsNamedAfterTheColumnThroughParensAndQualifiers() {
        assertEquals("AMOUNT", name("SELECT (amount) FROM sales"));
        assertEquals("AMOUNT", name("SELECT ((amount)) FROM sales"));
        assertEquals("AMOUNT", name("SELECT ( amount ) FROM sales"));
        assertEquals("AMOUNT", name("SELECT s.amount FROM sales s"));
        assertEquals("AMOUNT", name("SELECT (s.amount) FROM sales s"));
    }

    @Test
    public void aQuotedColumnReferenceKeepsTheColumnsOwnCase() {
        assertEquals("lc", name("SELECT \"lc\" FROM q188"));
    }

    @Test
    public void aParenthesizedExpressionKeepsItsParensInTheName() {
        assertEquals("(AMOUNT + 1)", name("SELECT (amount + 1) FROM sales"));
    }

    // ── aliases ──────────────────────────────────────────────────────────────

    @Test
    public void anUnquotedAliasFoldsAndAQuotedOneIsVerbatim() {
        assertEquals("CNT", name("SELECT count(*) AS cnt FROM sales"));
        assertEquals("lower", name("SELECT count(*) AS \"lower\" FROM sales"));
    }

    // ── set operations ───────────────────────────────────────────────────────

    @Test
    public void aSetOperationIsNamedByItsFirstBranch() {
        assertEquals("AMOUNT+1",
            name("SELECT amount+1 FROM sales UNION ALL SELECT amount+2 FROM sales"));
    }

    // ── positional references ────────────────────────────────────────────────

    @Test
    public void aPositionalItemIsNamedInItsDollarForm() {
        // $N resolves by POSITION — over a plain table and over VALUES alike — and the output
        // column is named $N, not the COLUMN<N> name it resolves through.
        final ResultSet overTable = engine.executeQuery("SELECT $2 FROM sales ORDER BY 1");
        assertEquals("$2", overTable.getColumns().get(0).getName());
        assertEquals("a", overTable.getRows().get(0).getValue(0));
        assertEquals("b", overTable.getRows().get(1).getValue(0));
        final ResultSet overValues = engine.executeQuery("SELECT $1 FROM (VALUES (7,8))");
        assertEquals("$1", overValues.getColumns().get(0).getName());
        assertEquals(7L, ((Number) overValues.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void anOutOfRangePositionalIsRefusedInItsDollarForm() {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT $3 FROM (VALUES (7,8))");
            }
        });
        assertEquals("SQL compilation error: error line 1 at position 7\ninvalid identifier '$3'",
            e.getMessage());
    }
}
