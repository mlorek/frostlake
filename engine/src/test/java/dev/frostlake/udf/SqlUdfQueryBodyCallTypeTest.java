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
package dev.frostlake.udf;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A call of a SQL function whose body is a QUERY is typed by what the call is inlined as, not by the RETURNS
 * clause: the query's one item where it reads no relation — {@code SELECT a + 1} as {@code a + 1} — and otherwise
 * the scalar subquery it is, so {@code SELECT MAX(x) FROM other} is typed as the column's MAX. A body answering
 * more than one row is refused as that subquery is (all live-verified).
 */
public class SqlUdfQueryBodyCallTypeTest extends BaseDatabaseTest {

    /** A call, and what SYSTEM$TYPEOF answers for it. */
    private static final String CONSTANT_CALLS = """
        sq_p(1) => NUMBER(2,0)[SB1]
        sq_p(1.5) => NUMBER(4,1)[SB1]
        sq_p('5') => NUMBER(38,0)[SB1]
        sq_p(NULL) => NUMBER(38,0)[SB1]
        sq_p(1::FLOAT) => NUMBER(38,0)[SB1]
        inc_p(1) => NUMBER(2,0)[SB1]
        inc_p(1.5) => NUMBER(4,1)[SB1]
        inc_p('5') => NUMBER(38,0)[SB1]
        sq_d(1) => NUMBER(2,0)[SB1]
        sq_d(1.5) => NUMBER(4,1)[SB1]
        sq_s('ab') => VARCHAR(3)[LOB]
        ex_s('ab') => VARCHAR(3)[LOB]
        sq_s5('ab') => VARCHAR(3)[LOB]
        sq_max() => NUMBER(10,2)[SB2]
        sq_cnt() => NUMBER(18,0)[SB1]
        sq_w() => VARCHAR(9)[LOB]
        sq_sub() => NUMBER(10,2)[SB2]
        ex_sub() => NUMBER(10,2)[SB2]
        sq_where(1) => NUMBER(2,0)[SB1]
        sq_lit() => NUMBER(1,0)[SB1]
        sq_cast(1) => NUMBER(12,3)[SB2]
        sq_bool(2) => BOOLEAN[SB1]
        sq_date() => DATE[SB4]
        sq_var(1) => VARIANT[LOB]
        sq_alias(1) => NUMBER(2,0)[SB1]
        sq_dist(1) => NUMBER(2,0)[SB1]
        sq_rows() => NUMBER(10,2)[SB2]
        sq_p(sq_p(1)) => NUMBER(3,0)[SB1]
        sq_p(1) + 1 => NUMBER(3,0)[SB1]
        sq_s('ab') || 'c' => VARCHAR(4)[LOB]
        UPPER(sq_s('ab')) => VARCHAR(9)[LOB]
        """;

    /** A call over a column of {@code t}, and what SYSTEM$TYPEOF answers for it on every row. */
    private static final String COLUMN_CALLS = """
        sq_p(n) => NUMBER(6,2)[SB2] ;; NUMBER(6,2)[SB2]
        sq_p(big) => NUMBER(38,0)[SB8] ;; NUMBER(38,0)[SB8]
        inc_p(n) => NUMBER(6,2)[SB2] ;; NUMBER(6,2)[SB2]
        inc_p(big) => NUMBER(38,0)[SB8] ;; NUMBER(38,0)[SB8]
        sq_d(n) => NUMBER(6,2)[SB2] ;; NUMBER(6,2)[SB2]
        sq_s(s) => VARCHAR(8)[LOB] ;; VARCHAR(8)[LOB]
        ex_s(s) => VARCHAR(8)[LOB] ;; VARCHAR(8)[LOB]
        sq_s5(s) => VARCHAR(8)[LOB] ;; VARCHAR(8)[LOB]
        sq_where(n) => NUMBER(6,2)[SB2] ;; NUMBER(6,2)[SB2]
        sq_cast(n) => NUMBER(12,3)[SB2] ;; NUMBER(12,3)[SB2]
        sq_var(n) => VARIANT[LOB] ;; VARIANT[LOB]
        """;

    /** A call and its value. */
    private static final String VALUES = """
        sq_p(1) => 2
        sq_p(1.5) => 2.5
        sq_p('5') => 6
        sq_p(NULL) => null
        sq_p(1::FLOAT) => 2
        inc_p(1) => 2
        inc_p(1.5) => 2.5
        inc_p('5') => 6
        sq_d(1) => 2
        sq_d(1.5) => 2.5
        sq_s('ab') => abx
        ex_s('ab') => abx
        sq_s5('ab') => abx
        sq_max() => 2.50
        sq_maxa(1) => 3.50
        sq_maxa(1.5) => 4.00
        sq_cnt() => 2
        sq_w() => qq
        sq_sub() => 2.50
        ex_sub() => 2.50
        sq_where(1) => 2
        sq_lit() => 7
        sq_cast(1) => 2.000
        sq_bool(2) => true
        sq_var(1) => 1
        sq_alias(1) => 2
        sq_dist(1) => 2
        sq_p(sq_p(1)) => 3
        sq_p(1) + 1 => 3
        sq_s('ab') || 'c' => abxc
        UPPER(sq_s('ab')) => ABX
        """;

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE t (n NUMBER(5,2), big NUMBER(38,0), s VARCHAR(7))");
        engine.execute("INSERT INTO t VALUES (1.5, 5, 'ab'), (2.5, 3000000000, 'cde')");
        engine.execute("CREATE OR REPLACE TABLE other (x NUMBER(10,2), w VARCHAR(9))");
        engine.execute("INSERT INTO other VALUES (1.25, 'p'), (2.5, 'qq')");
        final String[] functions = {
            "sq_p(a NUMBER) RETURNS NUMBER AS 'SELECT a + 1'",
            "inc_p(a NUMBER) RETURNS NUMBER AS 'a + 1'",
            "sq_d(a NUMBER) RETURNS NUMBER(10,2) AS 'SELECT a + 1'",
            "sq_s(a VARCHAR) RETURNS VARCHAR AS 'SELECT a || ''x'''",
            "ex_s(a VARCHAR) RETURNS VARCHAR AS 'a || ''x'''",
            "sq_s5(a VARCHAR) RETURNS VARCHAR(5) AS 'SELECT a || ''x'''",
            "sq_max() RETURNS NUMBER AS 'SELECT MAX(x) FROM other'",
            "sq_maxa(a NUMBER) RETURNS NUMBER AS 'SELECT MAX(x) + a FROM other'",
            "sq_cnt() RETURNS NUMBER AS 'SELECT COUNT(*) FROM other'",
            "sq_w() RETURNS VARCHAR AS 'SELECT MAX(w) FROM other'",
            "sq_sub() RETURNS NUMBER AS 'SELECT (SELECT MAX(x) FROM other)'",
            "ex_sub() RETURNS NUMBER AS '(SELECT MAX(x) FROM other)'",
            "sq_where(a NUMBER) RETURNS NUMBER AS 'SELECT a + 1 WHERE a > 0'",
            "sq_lit() RETURNS NUMBER AS 'SELECT 7'",
            "sq_cast(a NUMBER) RETURNS NUMBER AS 'SELECT (a + 1)::NUMBER(12,3)'",
            "sq_bool(a NUMBER) RETURNS BOOLEAN AS 'SELECT a > 1'",
            "sq_date() RETURNS DATE AS 'SELECT CURRENT_DATE()'",
            "sq_var(a NUMBER) RETURNS VARIANT AS 'SELECT TO_VARIANT(a)'",
            "sq_alias(a NUMBER) RETURNS NUMBER AS 'SELECT a + 1 AS z'",
            "sq_dist(a NUMBER) RETURNS NUMBER AS 'SELECT DISTINCT a + 1'",
            "sq_rows() RETURNS NUMBER AS 'SELECT x FROM other'",
        };
        for (final String function : functions) {
            engine.execute("CREATE OR REPLACE FUNCTION " + function);
        }
    }

    /** Every row's first cell, the rows joined as the cells were recorded. */
    private String cells(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        while (rs.next()) {
            out.append(out.length() == 0 ? "" : " ;; ").append(rs.getValue(0));
        }
        return out.toString();
    }

    @Test
    public void aQueryBodyTypesItsCallAsTheInlinedQuery() {
        for (final String line : CONSTANT_CALLS.split("\n")) {
            if (!line.isBlank()) {
                final String[] cell = line.trim().split(" => ", 2);
                assertEquals(cell[1], cells("SELECT SYSTEM$TYPEOF(" + cell[0] + ")"), cell[0]);
            }
        }
    }

    @Test
    public void aColumnArgumentBringsItsOwnType() {
        for (final String line : COLUMN_CALLS.split("\n")) {
            if (!line.isBlank()) {
                final String[] cell = line.trim().split(" => ", 2);
                assertEquals(cell[1], cells("SELECT SYSTEM$TYPEOF(" + cell[0] + ") FROM t ORDER BY n"), cell[0]);
            }
        }
    }

    @Test
    public void theCallAnswersTheBodysValue() {
        for (final String line : VALUES.split("\n")) {
            if (!line.isBlank()) {
                final String[] cell = line.trim().split(" => ", 2);
                assertEquals(cell[1], cells("SELECT " + cell[0]), cell[0]);
            }
        }
    }

    @Test
    public void aTableCreatedFromTheCallsDeclaresTheirTypes() {
        engine.execute("CREATE OR REPLACE TABLE ctq AS SELECT sq_p(1) AS a, sq_p(n) AS b, sq_s(s) AS c, sq_max() AS d,"
            + " sq_d(1) AS e FROM t");
        final ResultSet described = engine.executeQuery("DESCRIBE TABLE ctq");
        final StringBuilder types = new StringBuilder();
        while (described.next()) {
            types.append(types.length() == 0 ? "" : ", ").append(described.getValue(0)).append(' ')
                .append(described.getValue(1));
        }
        assertEquals("A NUMBER(2,0), B NUMBER(6,2), C VARCHAR(8), D NUMBER(10,2), E NUMBER(2,0)", types.toString());
    }

    @Test
    public void aBodyAnsweringSeveralRowsIsRefusedAsTheSubqueryItIs() {
        assertEquals("Single-row subquery returns more than one row.", assertThrows(RuntimeException.class,
            new Executable() {
                @Override
                public void execute() {
                    engine.execute("SELECT sq_rows()");
                }
            }).getMessage());
        // Its type is still the item's, since nothing is computed to tell it.
        assertEquals("NUMBER(10,2)[SB2]", cells("SELECT SYSTEM$TYPEOF(sq_rows())"));
    }
}
