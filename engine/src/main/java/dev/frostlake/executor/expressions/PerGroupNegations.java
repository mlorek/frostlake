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

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * The NOTs of a HAVING whose operand reads an aggregate the HAVING computes over a per-group subquery. The account
 * decorrelates such a NOT into the anti join it stands for, so a group the subquery finds nothing for passes it:
 * {@code HAVING NOT (MAX((SELECT g.v FROM g WHERE g.id = fz.id)) > 0)} keeps the group whose MAX is NULL, and so do
 * NOT over {@code =}, {@code <>}, {@code <}, BETWEEN and an AND (all live-verified). Each such NOT is read as IS NOT
 * TRUE: a NULL operand passes. In a select list the same NOT keeps three-valued logic.
 */
public final class PerGroupNegations extends AstPrinterVisitor {

    private final Set<String> perGroupKeys;
    private final Set<Expression> negations = Collections.newSetFromMap(new IdentityHashMap<Expression, Boolean>());

    private PerGroupNegations(final Collection<String> perGroupKeys) {
        this.perGroupKeys = new HashSet<String>(perGroupKeys);
    }

    /**
     * The NOT nodes of a HAVING condition that read a per-group aggregate, by identity.
     *
     * @param condition    the HAVING condition
     * @param perGroupKeys the printed keys of the aggregates computed over per-group subqueries
     * @return the nodes, empty where none reads one
     */
    public static Set<Expression> of(final Expression condition, final Collection<String> perGroupKeys) {
        if (perGroupKeys.isEmpty()) {
            return Collections.emptySet();
        }
        final PerGroupNegations walk = new PerGroupNegations(perGroupKeys);
        condition.accept(walk);
        return walk.negations;
    }

    @Override
    public String visitUnaryOperation(final UnaryOperationExpression expr) {
        if (expr.getOperator() == UnaryOperator.NOT && readsPerGroup(expr.getOperand())) {
            negations.add(expr);
        }
        return super.visitUnaryOperation(expr);
    }

    @Override
    public String visitSubquery(final SubqueryExpression expr) {
        return "";
    }

    /** Whether an operand holds one of the per-group aggregates. */
    private boolean readsPerGroup(final Expression operand) {
        final boolean[] found = {false};
        operand.accept(new AstPrinterVisitor() {
            @Override
            public String visitFunctionCall(final FunctionCallExpression call) {
                if (perGroupKeys.contains(AstPrinterVisitor.print(call))) {
                    found[0] = true;
                }
                return super.visitFunctionCall(call);
            }

            @Override
            public String visitSubquery(final SubqueryExpression expr) {
                return "";
            }
        });
        return found[0];
    }
}
