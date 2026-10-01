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

import dev.frostlake.metastore.model.Table;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * The relations a LATERAL item's arguments read, while the item runs for one row of them. The arguments are
 * evaluated over the row's VALUES, looked up by name; these relations give the names their declared TYPES, which the
 * account reads there as anywhere: {@code FLATTEN(input => ARRAY_CONSTRUCT(u))} over a UUID column holds a UUID
 * member, a DATE column a DATE one (live-verified), where values alone would build text members.
 *
 * <p>Per thread and stacked: SELECTs run under the engine's read lock, so several evaluate at once, and a LATERAL
 * item's argument may hold a query with a LATERAL item of its own.
 */
final class LateralArgumentTypes {

    private final ThreadLocal<Deque<Map<String, Table>>> aliasStack = new ThreadLocal<Deque<Map<String, Table>>>() {
        @Override
        protected Deque<Map<String, Table>> initialValue() {
            return new ArrayDeque<>();
        }
    };

    private final ThreadLocal<Deque<List<Table>>> tablesStack = new ThreadLocal<Deque<List<Table>>>() {
        @Override
        protected Deque<List<Table>> initialValue() {
            return new ArrayDeque<>();
        }
    };

    /**
     * Offers the relations one LATERAL item reads while it runs; every call is matched by {@link #end}.
     *
     * @param aliasToTable the FROM-clause keys in scope
     * @param tables       every relation in scope, in combined-row order
     */
    void begin(final Map<String, Table> aliasToTable, final List<Table> tables) {
        aliasStack.get().push(aliasToTable);
        tablesStack.get().push(tables);
    }

    /** Withdraws the relations the matching {@link #begin} offered. */
    void end() {
        aliasStack.get().pop();
        tablesStack.get().pop();
    }

    /**
     * Gives an argument's evaluator the relations the running LATERAL item reads as its declared-type base; an
     * evaluator outside any LATERAL item is left as it is.
     *
     * @param evaluator the evaluator of one argument
     */
    void typeArgumentsOf(final ExpressionEvaluator evaluator) {
        final List<Table> tables = tablesStack.get().peek();
        if (tables == null || tables.isEmpty()) {
            return;
        }
        evaluator.setDeclaredTypeBase(tables.get(0), aliasStack.get().peek(), tables);
    }
}
