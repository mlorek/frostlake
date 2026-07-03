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

/**
 * Precomputed plan for one GROUP BY select item: the aggregate kind, the resolved column index, and the
 * DISTINCT flag are computed ONCE (parsing + column resolution) and then applied to every group's rows
 * by {@code QueryExecutor.applyAggregatePlan} — so the per-group cost is just the scan, not a re-parse
 * and re-resolve. Plans cover the common aggregates (COUNT/SUM/AVG/MIN/MAX, incl. DISTINCT) and plain
 * single-table column references; anything else (generic-accumulator aggregates, multi-table qualified
 * names, expressions) falls back to the general per-group path. See {@code QueryExecutor}.
 */
final class AggregatePlan {

    final AggregateKind kind;
    final int colIndex;
    final boolean distinct;

    AggregatePlan(final AggregateKind kind, final int colIndex, final boolean distinct) {
        this.kind = kind;
        this.colIndex = colIndex;
        this.distinct = distinct;
    }
}
