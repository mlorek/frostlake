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
 * value; only expression defaults are wrapped. {@link #toString()} returns the expression text, which INSERT
 * evaluates and a snapshot keeps; the metadata views print {@link #getToldText()}, the default as the account tells
 * it back.
 */
public class DefaultValueExpression {

    private final String expressionText;

    private final String toldText;

    public DefaultValueExpression(final String expressionText) {
        this(expressionText, null);
    }

    /**
     * @param expressionText the default as written, which INSERT evaluates
     * @param toldText       the default as the account tells it back, or null when that is the text as written
     */
    public DefaultValueExpression(final String expressionText, final String toldText) {
        this.expressionText = expressionText;
        this.toldText = toldText;
    }

    public String getExpressionText() {
        return expressionText;
    }

    /**
     * The default as DESCRIBE, SHOW COLUMNS, GET_DDL and INFORMATION_SCHEMA print it: printed from the plan it
     * compiled to where that was worked out, else as written.
     *
     * @return the text
     */
    public String getToldText() {
        return toldText != null ? toldText : toString();
    }

    /** @return the told text when one was worked out, else null */
    public String toldTextOrNull() {
        return toldText;
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
