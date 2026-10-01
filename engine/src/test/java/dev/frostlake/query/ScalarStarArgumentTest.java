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
import dev.frostlake.storage.Row;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A scalar function whose ONE argument is a star is the call over the columns the star stands for: every
 * relation in scope, or the one its qualifier names, minus its EXCLUDE and outside its ILIKE. The columns are
 * spliced in before anything else sees the call, so the arity rule judges and echoes the spliced list, the
 * result is typed from it, and a grouped query holds each column to the grouping as if it had been written
 * out. With no FROM the star stands for no column; {@code FROM DUAL} has its one NULL column. Every cell is
 * live-verified; the hash values themselves are not compared, only which values hash alike.
 */
public class ScalarStarArgumentTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE ht (s VARCHAR, n INT)");
        engine.execute("INSERT INTO ht VALUES ('abc', 1), ('xyz', 2)");
        engine.execute("CREATE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE TABLE st1 (x VARCHAR(10))");
        engine.execute("INSERT INTO st1 VALUES ('ab')");
        engine.execute("CREATE TABLE st2 (x VARCHAR(10), y VARCHAR(10))");
        engine.execute("INSERT INTO st2 VALUES ('ab', 'cd')");
        engine.execute("CREATE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
    }

    /** Rows joined by " | ", columns by ", ", a BOOLEAN spelled the account's way. */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            for (int c = 0; c < row.getValues().size(); c++) {
                if (c > 0) {
                    out.append(", ");
                }
                final String text = String.valueOf(row.getValue(c));
                out.append("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)
                    ? text.toUpperCase(Locale.ROOT) : text);
            }
        }
        return out.toString();
    }

    /** The declared type of a query's first column, with the parameters that carry meaning. */
    private String typeOf(final String sql) {
        final DataType type = engine.executeQuery(sql).getColumns().get(0).getDataType();
        if (type instanceof NumericType && "NUMBER".equalsIgnoreCase(type.getName())) {
            return "NUMBER(" + ((NumericType) type).getPrecision() + "," + ((NumericType) type).getScale() + ")";
        }
        if (type instanceof StringType) {
            return "VARCHAR(" + ((StringType) type).getMaxLength() + ")";
        }
        return String.valueOf(type == null ? null : type.getName());
    }

    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        assertTrue(String.valueOf(refused.getMessage()).contains(fragment), sql + " -> " + refused.getMessage());
    }

    /** HASH(*) hashes the row's columns, as HASH over them written out does — not one constant for every row. */
    @Test
    public void hashOfAStarHashesTheRowsColumns() {
        assertEquals("TRUE | TRUE", rows("SELECT HASH(*) = HASH(s, n) FROM ht ORDER BY n"));
        assertEquals("TRUE | TRUE", rows("SELECT HASH(ht.*) = HASH(s, n) FROM ht ORDER BY n"));
        assertEquals("TRUE | TRUE", rows("SELECT HASH(* EXCLUDE n) = HASH(s) FROM ht ORDER BY 1"));
        assertEquals("TRUE | TRUE", rows("SELECT HASH(* ILIKE 'n') = HASH(n) FROM ht ORDER BY 1"));
        assertEquals("2", rows("SELECT COUNT(DISTINCT HASH(*)) FROM ht"));
        assertEquals("TRUE", rows("SELECT HASH(*) = HASH('ab') FROM st1"));
        assertEquals("TRUE", rows("SELECT HASH(*) = HASH(1, 2) FROM (SELECT 1 a, 2 b)"));
    }

    /** ARRAY_CONSTRUCT(*) builds the row's array, filtered by the star's own modifiers. */
    @Test
    public void arrayConstructOfAStarHoldsTheRow() {
        assertEquals("[5,true] | [7,false]", rows("SELECT TO_VARCHAR(ARRAY_CONSTRUCT(*)) FROM fz ORDER BY id"));
        assertEquals("[5] | [7]", rows("SELECT TO_VARCHAR(ARRAY_CONSTRUCT(* EXCLUDE b)) FROM fz ORDER BY id"));
        assertEquals("[true] | [false]",
            rows("SELECT TO_VARCHAR(ARRAY_CONSTRUCT(fz.* ILIKE 'b')) FROM fz ORDER BY id"));
        assertEquals("[5,true,5,50]", rows("SELECT TO_VARCHAR(ARRAY_CONSTRUCT(*)) FROM fz a JOIN g ON a.id = g.id"));
        assertEquals("[5,50]", rows("SELECT TO_VARCHAR(ARRAY_CONSTRUCT(g.*)) FROM fz a JOIN g ON a.id = g.id"));
        assertEquals("[]", rows("SELECT TO_VARCHAR(ARRAY_CONSTRUCT(* ILIKE 'nomatch')) FROM st2"));
    }

    /** A one-argument function over a one-column star reads that column; over two it is too many. */
    @Test
    public void aOneArgumentFunctionTakesAOneColumnStar() {
        assertEquals("AB", rows("SELECT UPPER(*) FROM st1"));
        assertEquals("2", rows("SELECT LENGTH(*) FROM st1"));
        assertEquals("AB", rows("SELECT UPPER(st1.*) FROM st1"));
        assertEquals("3", rows("SELECT ABS(*) FROM (SELECT -3 n)"));
        assertEquals("A", rows("SELECT UPPER(*) FROM (SELECT 'a' x)"));
        assertEquals("A", rows("SELECT UPPER(*) FROM (VALUES ('a'))"));
        assertEquals("ab", rows("SELECT TO_VARCHAR(*) FROM st1"));
        assertEquals("AB", rows("SELECT UPPER(* ILIKE 'x') FROM st2"));
        assertEquals("2", rows("SELECT LENGTH(* EXCLUDE y) FROM st2"));
        assertEquals("2", rows("SELECT LENGTH(UPPER(*)) FROM st1"));
        assertEquals("ab, AB", rows("SELECT x, UPPER(*) FROM st1"));
    }

    /** A variadic function takes every column, in relation order and then column order. */
    @Test
    public void aVariadicFunctionTakesEveryColumn() {
        assertEquals("abcd", rows("SELECT CONCAT(*) FROM st2"));
        assertEquals("ab", rows("SELECT CONCAT(*) FROM (SELECT 'a' x, 'b' y)"));
        assertEquals("ab", rows("SELECT CONCAT(*) FROM (VALUES ('a', 'b'))"));
        assertEquals("ababcd", rows("SELECT CONCAT(*) FROM st1 a, st2 b"));
        assertEquals("abcd", rows("SELECT CONCAT(b.*) FROM st1 a, st2 b"));
        assertEquals("ab", rows("SELECT COALESCE(*) FROM st2"));
        assertEquals("cd", rows("SELECT GREATEST(*) FROM st2"));
        assertEquals("5 | 6", rows("SELECT MOD(*) FROM g ORDER BY 1"));
        assertEquals("<abcd>", rows("SELECT CONCAT('<', *, '>') FROM st2"));
    }

    /** The arity rule judges the spliced list and echoes it relation-qualified, even over no row. */
    @Test
    public void theArityRuleJudgesTheSplicedList() {
        assertRefused("SELECT UPPER(*) FROM st2", "error line 1 at position 7");
        assertRefused("SELECT UPPER(*) FROM st2",
            "too many arguments for function [UPPER(ST2.X, ST2.Y)] expected 1, got 2");
        assertRefused("SELECT UPPER(*) FROM st2 WHERE FALSE",
            "too many arguments for function [UPPER(ST2.X, ST2.Y)] expected 1, got 2");
        assertRefused("SELECT ABS(*) FROM fz", "too many arguments for function [ABS(FZ.ID, FZ.B)] expected 1, got 2");
        assertRefused("SELECT UPPER(t.*) FROM st2 t", "too many arguments for function [UPPER(T.X, T.Y)] expected 1, got 2");
        assertRefused("SELECT UPPER(*) FROM st1 a, st1 b",
            "too many arguments for function [UPPER(A.X, B.X)] expected 1, got 2");
        assertRefused("SELECT NVL(* ILIKE 'id') FROM fz", "not enough arguments for function [NVL(FZ.ID)], expected 2, got 1");
        assertRefused("SELECT IFF(*) FROM fz", "not enough arguments for function [IFF(FZ.ID, FZ.B)], expected 3, got 2");
        assertRefused("SELECT CONCAT(* ILIKE 'nomatch') FROM st2",
            "not enough arguments for function [CONCAT()], expected 1, got 0");
    }

    /** No FROM: the star stands for no column. FROM DUAL: for its one NULL column. */
    @Test
    public void withNoFromTheStarStandsForNothing() {
        assertRefused("SELECT HASH(*)", "not enough arguments for function [HASH()], expected 1, got 0");
        assertRefused("SELECT CONCAT(*)", "not enough arguments for function [CONCAT()], expected 1, got 0");
        assertEquals("[]", rows("SELECT TO_VARCHAR(ARRAY_CONSTRUCT(*))"));
        assertEquals("TRUE", rows("SELECT HASH(*) = HASH(NULL) FROM DUAL"));
        assertEquals("1", rows("SELECT ARRAY_SIZE(ARRAY_CONSTRUCT(*)) FROM DUAL"));
        assertEquals("null", rows("SELECT UPPER(*) FROM DUAL"));
        assertEquals("null", rows("SELECT LENGTH(*) FROM DUAL"));
    }

    /** The result is typed from the spliced arguments. */
    @Test
    public void theResultIsTypedFromTheColumns() {
        assertEquals("VARCHAR(30)", typeOf("SELECT UPPER(*) FROM st1"));
        assertEquals("VARCHAR(30)", typeOf("SELECT UPPER(*) FROM st1 WHERE FALSE"));
        assertEquals("VARCHAR(20)", typeOf("SELECT CONCAT(*) FROM st2"));
        assertEquals("VARCHAR(10)", typeOf("SELECT COALESCE(*) FROM st2"));
        assertEquals("NUMBER(18,0)", typeOf("SELECT LENGTH(*) FROM st1"));
        assertEquals("NUMBER(2,0)", typeOf("SELECT ABS(*) FROM (SELECT -3 n)"));
        assertEquals("VARCHAR(134217728)", typeOf("SELECT TO_VARCHAR(*) FROM st1"));
        assertEquals("VARCHAR(30)", typeOf("WITH c AS (SELECT UPPER(*) AS u FROM st1) SELECT u FROM c"));
        assertEquals("AB", rows("SELECT (SELECT UPPER(*) FROM st1)"));
    }

    /** A grouped query holds each spliced column to the grouping as if it had been written out. */
    @Test
    public void aGroupedQueryHoldsEachColumnToTheGrouping() {
        assertRefused("SELECT CONCAT(*), COUNT(*) FROM st2 GROUP BY x", "error line 0 at position -1");
        assertRefused("SELECT CONCAT(*), COUNT(*) FROM st2 GROUP BY x",
            "'ST2.Y' in select clause is neither an aggregate nor in the group by clause.");
        assertRefused("SELECT CONCAT(*), COUNT(*) FROM st2 t GROUP BY x",
            "'T.Y' in select clause is neither an aggregate nor in the group by clause.");
        assertRefused("SELECT CONCAT(t.*), COUNT(*) FROM st2 t GROUP BY t.x",
            "'T.Y' in select clause is neither an aggregate nor in the group by clause.");
        assertRefused("SELECT OBJECT_CONSTRUCT(*), COUNT(*) FROM st2 GROUP BY x",
            "'ST2.Y' in select clause is neither an aggregate nor in the group by clause.");
        assertRefused("SELECT ARRAY_CONSTRUCT(g.*), COUNT(*) FROM st2 JOIN g ON TRUE GROUP BY g.id",
            "'G.V' in select clause is neither an aggregate nor in the group by clause.");
        assertRefused("SELECT CONCAT(*), COUNT(*) FROM st2", "[ST2.X] is not a valid group by expression");
        assertEquals("ab, 1", rows("SELECT CONCAT(* EXCLUDE y), COUNT(*) FROM st2 GROUP BY x"));
        assertEquals("abcd, 1", rows("SELECT CONCAT(*), COUNT(*) FROM st2 GROUP BY x, y"));
        assertEquals("[5,50], 1 | [6,60], 1", rows("SELECT TO_VARCHAR(ARRAY_CONSTRUCT(g.*)), COUNT(*)"
            + " FROM st2 JOIN g ON TRUE GROUP BY g.id, g.v ORDER BY 1"));
        assertEquals("TRUE | TRUE", rows("SELECT HASH(*) = HASH(s, n) FROM ht GROUP BY s, n ORDER BY 1"));
        assertEquals("TRUE | TRUE", rows("SELECT HASH(* EXCLUDE n) = HASH(s) FROM ht GROUP BY s ORDER BY 1"));
    }

    /** Outside the select list a star argument is still refused, lone or not. */
    @Test
    public void outsideTheSelectListTheStarIsRefused() {
        assertRefused("SELECT n FROM ht WHERE HASH(*) = HASH(s, n)",
            "Use of * as a function argument is only allowed in the SELECT clause.");
        assertRefused("SELECT n FROM ht ORDER BY HASH(*) = HASH('abc', 1) DESC, n",
            "Use of * as a function argument is only allowed in the SELECT clause.");
        assertRefused("SELECT HASH(* EXCLUDE nosuch) FROM ht", "column 'NOSUCH' does not exist");
        assertRefused("SELECT HASH(nosuch.*) FROM ht", "Object 'NOSUCH' does not exist or not authorized.");
    }
}
