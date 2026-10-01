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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The calls an untyped NULL argument folds to an untyped NULL. A date arithmetic or truncation call with the
 * bare word NULL among its values has no type at all: {@code DATEADD(day, NULL, d)}, {@code DATEDIFF(day, d,
 * NULL)} and {@code DATE_TRUNC('day', NULL)} read NULL[LOB] in SYSTEM$TYPEOF, project a VARCHAR(0) column as
 * a bare NULL does, and convert under any cast (live-verified). A NULL unit folds the call as well —
 * {@code DATEADD(NULL, 1, ts)}, {@code TIMEADD(NULL, 1, t)}, {@code DATEDIFF(NULL, d, d)} and
 * {@code DATE_TRUNC(NULL, d)} — and ADD_MONTHS folds for a NULL in either place. So do the date parts,
 * {@code YEAR(NULL)}, {@code HOUR(NULL)}, {@code EXTRACT(year FROM NULL)}, {@code DATE_PART(year, NULL)},
 * and {@code LAST_DAY(NULL)} and {@code TRUNC(NULL)}, which no one type of theirs takes.
 *
 * <p>A conditional that can answer nothing but an untyped NULL folds the same way: IFF, NVL2, COALESCE, NVL,
 * IFNULL, GREATEST, LEAST, DECODE and CASE whose every value branch is one — {@code IFF(TRUE, NULL, NULL)},
 * {@code CASE WHEN a > 1 THEN NULL END}, {@code DECODE(1, 1, NULL, NULL)} — and a NULLIF whose first argument
 * is one, {@code NULLIF(NULL, 1)}, all read NULL[LOB] and read through a derived column, a concatenation or
 * arithmetic as the bare word does (live-verified). A typed NULL branch keeps its type:
 * {@code IFF(TRUE, NULL::INT, NULL)} is NUMBER(38,0).
 */
public final class UntypedNullFold {

    private static final Set<String> FOLDING = new HashSet<>(Arrays.asList(
        "DATEADD", "TIMEADD", "TIMESTAMPADD", "DATEDIFF", "TIMEDIFF", "TIMESTAMPDIFF", "DATE_TRUNC", "ADD_MONTHS",
        "YEAR", "MONTH", "DAY", "DAYOFMONTH", "HOUR", "MINUTE", "SECOND", "WEEK", "WEEKOFYEAR", "QUARTER",
        "DAYOFWEEK", "DAYOFYEAR", "YEAROFWEEK", "EXTRACT", "DATE_PART", "LAST_DAY"));

    /** The conditionals that answer one of their value branches, and fold when every one is an untyped NULL. */
    private static final Set<String> PICKING = new HashSet<>(Arrays.asList(
        "IFF", "NVL2", "COALESCE", "NVL", "IFNULL", "GREATEST", "LEAST", "DECODE"));

    private UntypedNullFold() {
    }

    /**
     * @param expr the expression
     * @return whether it is such a call over an untyped NULL value
     */
    public static boolean foldsToUntypedNull(final Expression expr) {
        if (expr instanceof CaseExpression) {
            final CaseExpression conditional = (CaseExpression) expr;
            for (final WhenClause when : conditional.getWhenClauses()) {
                if (!isUntypedNull(when.getResult())) {
                    return false;
                }
            }
            return conditional.getElseExpression() == null || isUntypedNull(conditional.getElseExpression());
        }
        if (!(expr instanceof FunctionCallExpression)) {
            return false;
        }
        final FunctionCallExpression call = (FunctionCallExpression) expr;
        if (call.getFunctionName() == null || call.getNameExpression() != null) {
            return false;
        }
        final String name = call.getFunctionName().toUpperCase(Locale.ROOT);
        if (name.equals("NULLIF")) {
            return call.getArguments().size() == 2 && isUntypedNull(call.getArguments().get(0));
        }
        if (name.equals("TRUNC")) {
            // Over a NULL value TRUNC is neither the numeric nor the date truncation: TRUNC(NULL) is NULL[LOB].
            final Expression value = call.getArguments().isEmpty() ? null : call.getArguments().get(0);
            return value instanceof LiteralExpression && ((LiteralExpression) value).getType() == LiteralType.NULL;
        }
        if (PICKING.contains(name)) {
            final List<Expression> branches = TypeInferencer.conditionalBranches(name, call.getArguments());
            for (final Expression branch : branches) {
                if (!isUntypedNull(branch)) {
                    return false;
                }
            }
            return !branches.isEmpty();
        }
        if (!FOLDING.contains(name)) {
            return false;
        }
        for (int i = 0; i < call.getArguments().size(); i++) {
            final Expression argument = call.getArguments().get(i);
            if (argument instanceof LiteralExpression && ((LiteralExpression) argument).getType() == LiteralType.NULL) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param name a call's name, upper-cased
     * @return whether it is a conditional that folds by answering an untyped NULL branch, where every other
     *     folding call is the NULL itself
     */
    public static boolean picksABranch(final String name) {
        return PICKING.contains(name) || name.equals("NULLIF");
    }

    /**
     * @param expr the expression
     * @return whether it has no type at all: the bare word NULL, or a call that folds to one
     */
    public static boolean isUntypedNull(final Expression expr) {
        return expr instanceof LiteralExpression && ((LiteralExpression) expr).getType() == LiteralType.NULL
            || foldsToUntypedNull(expr);
    }
}
