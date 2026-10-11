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
        super(StatementType.SQL);
        this.sql = sql;
        this.selectInto = selectInto;
        this.writtenBinds = writtenBinds;
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
