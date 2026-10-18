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
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.Row;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * The state of the window computation a thread is running (see {@link WindowFunctionEvaluator}): one per thread, as
 * the sessions of an engine share the evaluator and run read-only statements at once.
 */
final class WindowComputationState {

    /**
     * SELECT-list alias -> its defining expression text: a window's PARTITION BY / ORDER BY may reference a SELECT
     * alias, and such a key that is no base column resolves to this expression. Empty outside a window computation.
     */
    Map<String, String> windowSelectAliases = new HashMap<>();

    /** The keys of the computation, each with the SELECT aliases it reads written out, per relation. */
    Map<Table, Map<String, String>> windowKeysReadThroughAliases = new IdentityHashMap<>();

    /** The rows the computation was handed, each by its place among them, which a SEQ call reads. */
    Map<Row, Long> windowRowOrdinals = new IdentityHashMap<>();

    /** Each SELECT item's canonical AST print, mapped to its projected column index, over already-projected rows. */
    Map<String, Integer> windowSelectItemCanonicalIndex = new HashMap<>();

    /** The canonical-index scopes saved by nested computations. */
    final Deque<Map<String, Integer>> savedCanonicalScopes = new ArrayDeque<>();

    /** How to compute an expression over a grouped output row's source group, or null. */
    GroupedExpressionValues groupedValues;

    /** Each RATIO_TO_REPORT call's declared scale, so the width is inferred once and not once per row. */
    final Map<FrostlakeParser.FunctionCallExprContext, Integer> ratioDeclaredScales = new IdentityHashMap<>();
}
