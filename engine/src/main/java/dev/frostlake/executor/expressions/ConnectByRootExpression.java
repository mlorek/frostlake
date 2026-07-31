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

package dev.frostlake.executor.expressions;

/**
 * {@code CONNECT_BY_ROOT <column>}: the column's value on the ROOT row of the hierarchy branch that
 * produced the current row.
 *
 * <p>The CONNECT BY expansion materializes the root row's values as extra {@link #ROOT_PREFIX}-prefixed
 * columns (hidden from {@code SELECT *}), so this is modelled as an ordinary
 * {@link ColumnReferenceExpression} to that prefixed name. Used in a query WITHOUT a CONNECT BY clause
 * the prefixed column is absent and Snowflake simply returns the column's own value (live-verified:
 * {@code SELECT CONNECT_BY_ROOT nm FROM h} returns every row's {@code nm}) — {@link #baseColumnReference()}
 * is the fallback the evaluator uses for that case.
 */
public class ConnectByRootExpression extends ColumnReferenceExpression {

    /** Prefix of the synthesized root-row column names produced by the CONNECT BY expansion. */
    public static final String ROOT_PREFIX = "CONNECT_BY_ROOT$";

    private final String baseTableName;
    private final String baseColumnName;

    public ConnectByRootExpression(final String baseTableName, final String baseColumnName) {
        super(ROOT_PREFIX + baseColumnName);
        this.baseTableName = baseTableName;
        this.baseColumnName = baseColumnName;
    }

    /** The plain column reference as written after {@code CONNECT_BY_ROOT}. */
    public ColumnReferenceExpression baseColumnReference() {
        return baseTableName == null
            ? new ColumnReferenceExpression(baseColumnName)
            : new ColumnReferenceExpression(baseTableName, baseColumnName);
    }

    @Override
    public String toString() {
        return "CONNECT_BY_ROOT " + (baseTableName == null ? baseColumnName : baseTableName + "." + baseColumnName);
    }
}
