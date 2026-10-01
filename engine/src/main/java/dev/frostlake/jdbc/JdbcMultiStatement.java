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

package dev.frostlake.jdbc;

import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.StatementCount;

import java.sql.SQLException;

/**
 * The driver-side multi-statement gate, matching the real driver (live-verified): one
 * {@code execute()} refuses {@code ;}-separated packs unless MULTI_STATEMENT_COUNT says otherwise —
 * {@code Actual statement count N did not match the desired statement count D.} (state 0A000,
 * code 8), matched EXACTLY in both directions, with 0 meaning any count. The count is read from a
 * per-statement parameter first, then the connection's default, which starts at 1 (or the
 * MULTI_STATEMENT_COUNT connection property) and follows {@code ALTER SESSION SET
 * MULTI_STATEMENT_COUNT} / {@code UNSET} statements executed through the connection.
 *
 * <p>Counting is scanner-based like the drivers' placeholder handling — string literals (both
 * {@code ''} and {@code \'} escapes), quoted identifiers, {@code --}/{@code //}/{@code /* *&#47;}
 * comments and {@code $$…$$} bodies are opaque; a trailing {@code ;} does not add a statement; a
 * scripting region — an unquoted block or DECLARE-headed body — counts as one statement, as the
 * account counts it (see {@link #countStatements}).
 */
final class JdbcMultiStatement {

    private JdbcMultiStatement() {
    }

    /** The count an exact ALTER SESSION SET/UNSET MULTI_STATEMENT_COUNT assigns, as {@link StatementCount} reads it. */
    static Integer sessionCountAssignment(final String sql) {
        return StatementCount.assignedCount(sql);
    }

    /** Top-level statement count, as {@link StatementCount} counts it for every client. */
    static int countStatements(final String sql) {
        return StatementCount.countStatements(sql);
    }

    /** The live refusal, byte-for-byte. */
    static SQLException countMismatch(final int actual, final int desired) {
        return new SQLException("Actual statement count " + actual
            + " did not match the desired statement count " + desired + ".", "0A000", 8);
    }

    /**
     * The refusal for {@code sql} holding {@code actual} statements where {@code desired} were asked for. The
     * account compiles the text before it counts it (live-verified), so a text that will not parse earns
     * its syntax error instead, in whichever statement the fault lies and whatever the counts; any other
     * fault waits for its statement to run, so the count refusal wins over it.
     */
    static SQLException countMismatch(final String sql, final int actual, final int desired) {
        try {
            QueryExecutor.requireParses(sql);
        } catch (final RuntimeException syntaxError) {
            return new SQLException(syntaxError.getMessage(), syntaxError);
        }
        return countMismatch(actual, desired);
    }

    private static int skipLine(final String s, final int start) {
        final int j = s.indexOf('\n', start);
        return j < 0 ? s.length() : j + 1;
    }
}
