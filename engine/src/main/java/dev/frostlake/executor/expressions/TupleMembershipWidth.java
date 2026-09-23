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

import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.RelationShapeOnly;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.types.DataType;
import dev.frostlake.types.SqlTypeNames;

import java.util.HashMap;
import java.util.List;

/**
 * A tuple compared by IN with a subquery of another width, both wider than one column, is refused as the
 * conversion of the subquery's ROW into the tuple's, unpositioned, the subquery re-printed from its plan
 * inside {@code ANY(…)} — {@code ALL(…)} for NOT IN — (see {@link SubqueryPlanPrint}):
 *
 * <pre>
 *   (a, a) IN (SELECT 1, 2, 3 FROM ft)
 *       Can not convert parameter 'ANY(SELECT 1 AS "1", 2 AS "2", 3 AS "3" FROM FT AS FT)' of type
 *       [ROW(NUMBER(1,0), NUMBER(1,0), NUMBER(1,0))] into expected type [ROW(NUMBER(38,0), NUMBER(38,0))]
 * </pre>
 *
 * <p>A subquery whose re-print is not modelled is quoted as written.
 */
final class TupleMembershipWidth {

    private TupleMembershipWidth() {
    }

    /**
     * Refuse {@code tuple} when its subquery selects another number of columns, more than one.
     *
     * @param expected the tuple's own ROW type, as an argument-type list names it
     */
    static void reject(final ExpressionEvaluatorVisitor visitor, final TupleInExpression tuple, final String expected) {
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        if (queryExecutor == null) {
            return;
        }
        final List<ResultSetColumn> columns;
        final boolean previous = RelationShapeOnly.begin();
        LateConstantRefusal.beginProbe();
        try {
            final List<ResultSet> results = queryExecutor.executeWithLateralContext(
                tuple.getSubquery().getSubquery(), new HashMap<String, Object>());
            if (results.isEmpty() || results.get(0) == null) {
                return;
            }
            columns = results.get(0).getColumns();
        } catch (final RuntimeException undetermined) {
            return;
        } finally {
            LateConstantRefusal.endProbe();
            RelationShapeOnly.end(previous);
        }
        if (columns.size() < 2 || columns.size() == tuple.getValues().size()) {
            return;
        }
        final StringBuilder row = new StringBuilder("ROW(");
        for (int i = 0; i < columns.size(); i++) {
            final DataType type = columns.get(i).getStaticType();
            row.append(i > 0 ? ", " : "").append(type == null ? "NULL" : SqlTypeNames.canonical(type));
        }
        row.append(')');
        final String printed = new SubqueryPlanPrint(visitor, StrictPrintMode.PLAN).select(tuple.getSubquery());
        final String select = printed != null ? printed : tuple.getSubquery().getSubquery();
        throw new RuntimeException(SqlCompilationError.of("Can not convert parameter '" + (tuple.isNot() ? "ALL(" : "ANY(")
            + select + ")' of type [" + row + "] into expected type [" + expected + "]"));
    }
}
