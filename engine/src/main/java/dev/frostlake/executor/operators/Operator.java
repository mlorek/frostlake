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

package dev.frostlake.executor.operators;

import dev.frostlake.storage.Row;

import java.util.List;

/**
 * Base interface for query execution operators.
 * Operators form a pipeline where each operator processes rows and passes them to the next.
 */
public interface Operator {

    /**
     * Execute this operator on the input rows.
     *
     * @param input The input rows from the previous operator
     * @param context The execution context containing table metadata and other information
     * @return The output rows after applying this operator
     */
    List<Row> execute(final List<Row> input, final OperatorContext context);

    /**
     * Get a description of this operator for debugging/logging.
     *
     * @return A human-readable description of the operator
     */
    String getDescription();
}
