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

package dev.frostlake.executor;

import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.metastore.model.DefaultValueExpression;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Locale;

/**
 * How the account prints a column DEFAULT that reads the clock (all live-verified). The context function takes its
 * parentheses whether or not they were written: CURRENT_TIMESTAMP and CURRENT_TIMESTAMP() are both
 * {@code CURRENT_TIMESTAMP()}, as CURRENT_DATE, CURRENT_TIME, LOCALTIMESTAMP and LOCALTIME are. A precision is kept
 * ({@code CURRENT_TIMESTAMP(3)}). GETDATE() and SYSTIMESTAMP() print as the CURRENT_TIMESTAMP() they are, and
 * SYSDATE() as the expression it expands to. DESCRIBE's default cell, SHOW COLUMNS, GET_DDL and
 * INFORMATION_SCHEMA.COLUMNS print alike.
 */
public final class ClockDefaultSpelling {

    /** What SYSDATE() expands to. */
    private static final String SYSDATE =
        "CAST(CONVERT_TIMEZONE('UTC', CAST(CURRENT_TIMESTAMP() AS TIMESTAMP_TZ(9))) AS TIMESTAMP_NTZ(9))";

    private ClockDefaultSpelling() {
    }

    /**
     * The account's spelling of a stored default that reads the clock, or null for any other default.
     *
     * @param stored the column's stored default: a clock keyword as the column definition records it, or an
     *               expression
     * @return the spelling, or null
     */
    public static String of(final Object stored) {
        if (stored instanceof String) {
            final String keyword = (String) stored;
            return "CURRENT_TIMESTAMP".equals(keyword) || "CURRENT_DATE".equals(keyword)
                || "CURRENT_TIME".equals(keyword) ? keyword + "()" : null;
        }
        if (!(stored instanceof DefaultValueExpression)) {
            return null;
        }
        final Expression parsed;
        try {
            parsed = ExpressionEvaluator.parse(((DefaultValueExpression) stored).getExpressionText());
        } catch (final RuntimeException unreadable) {
            return null;
        }
        final String name;
        final List<Expression> arguments;
        if (parsed instanceof ColumnReferenceExpression && !((ColumnReferenceExpression) parsed).isQualified()) {
            name = ((ColumnReferenceExpression) parsed).getColumnName().toUpperCase(Locale.ROOT);
            arguments = null;
        } else if (parsed instanceof FunctionCallExpression && !((FunctionCallExpression) parsed).isStar()
                && ((FunctionCallExpression) parsed).getFunctionName() != null) {
            name = ((FunctionCallExpression) parsed).getFunctionName().toUpperCase(Locale.ROOT);
            arguments = ((FunctionCallExpression) parsed).getArguments();
        } else {
            return null;
        }
        final boolean bare = arguments == null || arguments.isEmpty();
        if ("CURRENT_TIMESTAMP".equals(name) || "CURRENT_TIME".equals(name) || "LOCALTIMESTAMP".equals(name)
                || "LOCALTIME".equals(name)) {
            if (bare) {
                return name + "()";
            }
            final String precision = arguments.size() == 1 ? wholeNumber(arguments.get(0)) : null;
            return precision == null ? null : name + "(" + precision + ")";
        }
        if (!bare) {
            return null;
        }
        if ("CURRENT_DATE".equals(name)) {
            return "CURRENT_DATE()";
        }
        if ("GETDATE".equals(name) || "SYSTIMESTAMP".equals(name)) {
            return "CURRENT_TIMESTAMP()";
        }
        return "SYSDATE".equals(name) ? SYSDATE : null;
    }

    /** A whole-number literal's digits, or null for anything else. */
    private static String wholeNumber(final Expression argument) {
        if (!(argument instanceof LiteralExpression)) {
            return null;
        }
        final Object value = ((LiteralExpression) argument).getValue();
        if (value instanceof Integer || value instanceof Long || value instanceof BigInteger) {
            return value.toString();
        }
        return value instanceof BigDecimal && ((BigDecimal) value).scale() <= 0
            ? ((BigDecimal) value).toPlainString() : null;
    }
}
