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

package dev.frostlake.expressions;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An argument-type refusal is anchored on the OPERATOR TOKEN — not the expression's start, not the
 * select item's, and not the statement's. The line is 1-based and the position is the operator's
 * 0-based offset WITHIN THAT LINE, which is exactly what ANTLR's token carries.
 *
 * <p>Every expectation here was read off a live account. The anchor tracks the operator through a
 * longer prefix, a second select item, a parenthesis, an enclosing call, a WHERE clause, a CTAS body
 * and a second line alike.
 */
public class ArgumentTypeRefusalPositionTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE op_t (bn BINARY, s VARCHAR(4), d DATE, ts TIMESTAMP_NTZ, n NUMBER)");
    }

    /** Asserts the refusal's positioned prefix, which is the whole point of this class. */
    private void refusesAt(final String sql, final int line, final int position) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        final String expected = "error line " + line + " at position " + position;
        assertTrue(e.getMessage().replace('\n', ' ').contains(expected),
            "expected [" + expected + "] from: " + sql + "\nbut got: " + e.getMessage());
    }

    /** The operator's own offset, for both refusal families. */
    @Test
    public void theRefusalPointsAtTheOperator() {
        //            0123456789
        refusesAt("SELECT bn || s AS c FROM op_t", 1, 10);
        refusesAt("SELECT d + d AS c FROM op_t", 1, 9);
    }

    /** A longer prefix moves it by exactly that much — it is an offset, not a fixed place. */
    @Test
    public void aLongerPrefixMovesThePosition() {
        refusesAt("SELECT     bn || s AS c FROM op_t", 1, 14);
    }

    /** On a later line the position restarts from that line's own start. */
    @Test
    public void aMultiLineStatementPointsAtTheOperatorsOwnLine() {
        refusesAt("SELECT\n  bn || s AS c\nFROM op_t", 2, 5);
        refusesAt("SELECT\n  d\n  + d AS c\nFROM op_t", 3, 2);
    }

    /** Nesting does not move the anchor off the operator. */
    @Test
    public void theAnchorSurvivesNesting() {
        refusesAt("SELECT UPPER(bn || s) AS c FROM op_t", 1, 16);
        refusesAt("SELECT (bn || s) AS c FROM op_t", 1, 11);
    }

    /** Nor does an earlier select item, nor being in a WHERE rather than a projection. */
    @Test
    public void theAnchorTracksTheOperatorNotTheItem() {
        refusesAt("SELECT n AS a, bn || s AS c FROM op_t", 1, 18);
        refusesAt("SELECT n FROM op_t WHERE bn || s = 'x'", 1, 28);
    }

    /** A CTAS body reports its offset in the WHOLE statement, counting from CREATE. */
    @Test
    public void aCtasBodyIsPositionedInTheWholeStatement() {
        final String sql = "CREATE OR REPLACE TABLE op_c AS SELECT bn || s AS c FROM op_t";
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(e.getMessage().replace('\n', ' ').contains("error line 1 at position 42"),
            "CTAS gave: " + e.getMessage());
    }

    /**
     * The other refusal route — reached when a projection has no FROM, so the VALUE is computed before
     * anything types it — carries the same anchor. The two routes must agree with each other and with
     * live; positioning one alone would have made them disagree.
     */
    @Test
    public void theValueRouteCarriesTheSameAnchor() {
        refusesAt("SELECT ts + n AS c FROM op_t", 1, 10);
        refusesAt("SELECT TO_TIMESTAMP('2026-01-01') + 1 AS c", 1, 34);
    }
    /**
     * A RE-PARSED body reports its offset in the whole statement too, which needs the item's origin to
     * COMPOSE with the body's rather than replace it. A CTAS is projected straight from the statement's
     * own tree, so its offsets were already absolute; a view's definition is re-parsed from its text, so
     * every position inside it is body-relative until composed.
     */
    @Test
    public void aReparsedViewBodyIsPositionedInTheWholeStatement() {
        engine.execute("CREATE TABLE op_u (bn BINARY, s VARCHAR(4))");
        // The expected position is DERIVED from where the operator actually sits, rather than written
        // out — that states the rule (the operator's own offset in the statement) instead of asserting
        // an arithmetic result, and it stays correct when an object name changes length.
        refusesAtTheOperator("CREATE OR REPLACE VIEW op_bv AS SELECT bn || s AS c FROM op_u");
        refusesAtTheOperator(
            "CREATE OR REPLACE MATERIALIZED VIEW op_bm AS SELECT bn || s AS c FROM op_u");
        refusesAtTheOperator(
            "CREATE OR REPLACE VIEW op_bv_with_a_much_longer_name AS SELECT bn || s AS c FROM op_u");
        // A body on its own line moves the LINE as well, which is the half a column-only composition
        // would silently get wrong.
        refusesDdlAt("CREATE OR REPLACE VIEW op_bv2 AS\n  SELECT bn || s AS c FROM op_u", 2, 12);
    }

    /** Asserts a single-line DDL statement points at where its {@code ||} actually is. */
    private void refusesAtTheOperator(final String sql) {
        refusesDdlAt(sql, 1, sql.indexOf("||"));
    }

    /** Asserts a DDL statement's refusal carries this position. */
    private void refusesDdlAt(final String sql, final int line, final int position) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        final String expected = "error line " + line + " at position " + position;
        assertTrue(e.getMessage().replace('\n', ' ').contains(expected),
            "expected [" + expected + "] from: " + sql + "\nbut got: " + e.getMessage());
    }
}
