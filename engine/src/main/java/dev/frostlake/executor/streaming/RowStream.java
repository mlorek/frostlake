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

package dev.frostlake.executor.streaming;

import dev.frostlake.storage.Row;

/**
 * Pull-based (volcano-model) stream of rows: each {@link #next()} call produces one row on demand
 * and returns {@code null} when exhausted, so a downstream operator (e.g. LIMIT) can stop pulling
 * without the upstream having to materialize its full output.
 *
 * <p>This is the streaming counterpart to the materializing
 * {@link dev.frostlake.executor.operators.Operator} pipeline (which passes whole {@code List<Row>}s
 * between stages). It is the foundation for lazy execution / LIMIT short-circuit; production query
 * execution is not yet routed through it — that wiring is a deliberate follow-up step.
 */
public interface RowStream {

    /** The next row, or {@code null} when the stream is exhausted. */
    Row next();

    /** Release any resources held by this stream and its sources. */
    void close();
}
