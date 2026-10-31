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

package dev.frostlake.executor.procedural;

public class SqlStatement extends Statement {
    private final String sql;
    private final boolean selectInto;
    private final boolean writtenBinds;
    private final boolean nestedBlock;
    private boolean followsAStage;
    private boolean feedsTheNext;

    public SqlStatement(final String sql) {
        this(sql, false);
    }

    /**
     * A statement of a block, run as text.
     *
     * @param sql the statement's text
     * @param selectInto whether it is the block's own SELECT … INTO, the one context an INTO clause is allowed in
     */
    public SqlStatement(final String sql, final boolean selectInto) {
        this(sql, selectInto, false);
    }

    /**
     * A statement of a block, run as text.
     *
     * @param sql          the statement's text
     * @param selectInto   whether it is the block's own SELECT … INTO
     * @param writtenBinds whether its {@code :names} stay as they are written and are bound from the block when it
     *                     runs, as a CALL's are, which judges each by the type its variable is declared with
     */
    public SqlStatement(final String sql, final boolean selectInto, final boolean writtenBinds) {
        this(sql, selectInto, writtenBinds, false);
    }

    private SqlStatement(final String sql, final boolean selectInto, final boolean writtenBinds,
                         final boolean nestedBlock) {
        super(StatementType.SQL);
        this.sql = sql;
        this.selectInto = selectInto;
        this.writtenBinds = writtenBinds;
        this.nestedBlock = nestedBlock;
    }

    /**
     * A block written inside a block's statement — a loop's body, a branch — that runs from its own text.
     *
     * @param sql the block's text, from its first token
     * @return the statement
     */
    public static SqlStatement ofNestedBlock(final String sql) {
        return new SqlStatement(sql, false, false, true);
    }

    /**
     * Mark the statement a stage of a flow chain.
     *
     * @param follows whether it runs after a stage the arrow joins to it, whose results it reads as {@code $n}
     * @param feeds   whether the arrow joins it to the statement after it, which reads its result
     */
    public void chainedAs(final boolean follows, final boolean feeds) {
        this.followsAStage = follows;
        this.feedsTheNext = feeds;
    }

    /** Whether the statement runs after a flow chain's stage, whose result it reads as {@code $1}. */
    public boolean followsAStage() {
        return followsAStage;
    }

    /** Whether the statement's result feeds the flow chain's next stage. */
    public boolean feedsTheNext() {
        return feedsTheNext;
    }

    /** Whether the statement is a block that runs from its own text, which places what fails in it from there. */
    public boolean isNestedBlock() {
        return nestedBlock;
    }

    public boolean isSelectInto() {
        return selectInto;
    }

    /** Whether the statement runs with its {@code :names} as written, bound from the block when it runs. */
    public boolean keepsWrittenBinds() {
        return writtenBinds;
    }

    public String getSql() {
        return sql;
    }
}
