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

import dev.frostlake.executor.expressions.AstPrinterVisitor;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.SubqueryExpression;
import dev.frostlake.functions.FunctionRegistry;

import java.util.Locale;

/**
 * The walk {@link GroupedSeqItems} takes over a grouped select's item: whether a SEQ1, SEQ2, SEQ4 or SEQ8 call stands
 * outside every aggregate call, and whether the item holds a query. A window call's parts are its own and are not
 * walked, and neither is a query's.
 */
final class GroupedSeqWalk extends AstPrinterVisitor {

    private final FunctionRegistry registry;
    private boolean numbered;
    private boolean holdsQuery;

    /**
     * @param registry the registry that tells an aggregate call
     */
    GroupedSeqWalk(final FunctionRegistry registry) {
        this.registry = registry;
    }

    /** Whether a SEQ call stands outside every aggregate. */
    boolean numbered() {
        return numbered;
    }

    /** Whether the item holds a query. */
    boolean holdsQuery() {
        return holdsQuery;
    }

    @Override
    public String visitFunctionCall(final FunctionCallExpression expr) {
        final String name = expr.getFunctionName() == null ? "" : expr.getFunctionName().toUpperCase(Locale.ROOT);
        if ("SEQ1".equals(name) || "SEQ2".equals(name) || "SEQ4".equals(name) || "SEQ8".equals(name)) {
            numbered = true;
        }
        // An aggregate's arguments are computed over the group's rows, which number the scan the grouping read.
        if (registry != null && registry.hasAggregateFunction(name)) {
            return "";
        }
        return super.visitFunctionCall(expr);
    }

    @Override
    public String visitSubquery(final SubqueryExpression expr) {
        holdsQuery = true;
        return "";
    }
}
