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

package dev.frostlake.metastore.model;

/**
 * A column {@code DEFAULT} whose value is a non-literal <em>expression</em> (arithmetic, string
 * concatenation, a function call, CASE, …) rather than a stored constant. It holds the raw expression
 * text and is evaluated once per inserted row at INSERT time — not stored verbatim as the column value.
 *
 * <p>This wrapper exists to disambiguate an expression default from a literal string default: both would
 * otherwise be a bare {@code String} on {@code TableColumn.defaultValue}, so a {@code DEFAULT (1+2)} was
 * previously inserted as the text {@code "1+2"} instead of {@code 3}. A literal default stays its plain
 * value; only expression defaults are wrapped. {@link #toString()} returns the expression text so metadata
 * views (SHOW COLUMNS, INFORMATION_SCHEMA, snapshots) render the original expression.
 */
public final class DefaultValueExpression {

    private final String expressionText;

    public DefaultValueExpression(final String expressionText) {
        this.expressionText = expressionText;
    }

    public String getExpressionText() {
        return expressionText;
    }

    @Override
    public String toString() {
        return expressionText;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DefaultValueExpression)) {
            return false;
        }
        final DefaultValueExpression that = (DefaultValueExpression) other;
        return expressionText == null ? that.expressionText == null : expressionText.equals(that.expressionText);
    }

    @Override
    public int hashCode() {
        return expressionText == null ? 0 : expressionText.hashCode();
    }
}
