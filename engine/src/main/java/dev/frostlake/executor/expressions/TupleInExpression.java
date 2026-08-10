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

import java.util.List;

/**
 * Row-constructor {@code IN} test: {@code (a, b) IN (SELECT x, y ...)},
 * {@code (a, b) IN ((1, 2), (3, 4))}, or the type-invalid flat spelling {@code (a, b) IN (1, 2)}.
 *
 * <p>The three right-hand shapes carry different live-verified semantics. The SUBQUERY form is
 * TWO-VALUED: a NULL on either side never matches and never yields UNKNOWN — a miss is plain FALSE
 * for {@code IN} and TRUE for {@code NOT IN}. The tuple-ROW list form applies full row-value
 * three-valued logic (a row with a definite pair mismatch is FALSE, an all-equal row TRUE, anything
 * else UNKNOWN). The FLAT scalar list is a compile-time type error ("Invalid argument types for
 * function 'IN'") — it is parsed only so the refusal can name the ROW shape the way Snowflake does.
 */
public final class TupleInExpression implements Expression {
    private final List<Expression> values;            // left-hand tuple
    private final List<List<Expression>> tupleRows;   // right-hand (1, 2), (3, 4) rows (null otherwise)
    private final List<Expression> flatListValues;    // right-hand flat scalar list (null otherwise)
    private final SubqueryExpression subquery;        // right-hand subquery (null otherwise)
    private final boolean not;

    private TupleInExpression(final List<Expression> values, final List<List<Expression>> tupleRows,
                              final List<Expression> flatListValues, final SubqueryExpression subquery,
                              final boolean not) {
        this.values = values;
        this.tupleRows = tupleRows;
        this.flatListValues = flatListValues;
        this.subquery = subquery;
        this.not = not;
    }

    public static TupleInExpression ofTupleRows(final List<Expression> values,
                                                final List<List<Expression>> tupleRows, final boolean not) {
        return new TupleInExpression(values, tupleRows, null, null, not);
    }

    public static TupleInExpression ofFlatList(final List<Expression> values,
                                               final List<Expression> flatListValues, final boolean not) {
        return new TupleInExpression(values, null, flatListValues, null, not);
    }

    public static TupleInExpression ofSubquery(final List<Expression> values,
                                               final SubqueryExpression subquery, final boolean not) {
        return new TupleInExpression(values, null, null, subquery, not);
    }

    public List<Expression> getValues() {
        return values;
    }

    public List<List<Expression>> getTupleRows() {
        return tupleRows;
    }

    public List<Expression> getFlatListValues() {
        return flatListValues;
    }

    public SubqueryExpression getSubquery() {
        return subquery;
    }

    public boolean hasSubquery() {
        return subquery != null;
    }

    public boolean hasTupleRows() {
        return tupleRows != null;
    }

    public boolean hasFlatList() {
        return flatListValues != null;
    }

    public boolean isNot() {
        return not;
    }

    @Override
    public <T> T accept(final ExpressionVisitor<T> visitor) {
        return visitor.visitTupleIn(this);
    }
}
