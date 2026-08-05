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
 * A dotted column qualifier naming no FROM-clause key — live's "SQL compilation error: invalid
 * identifier 'R.C'": an alias REPLACES the table name, in the SELECT list, WHERE, GROUP BY and
 * ORDER BY alike (each measured). Typed so the lenient retry chains (WhereOperator's fallback
 * evaluator, GroupByOperator's key builder, OrderByExecutor's comparator) can rethrow it instead
 * of quietly re-resolving through a keyless evaluator: an invalid qualifier is definitive.
 */
public class InvalidQualifierException extends RuntimeException {

    public InvalidQualifierException(final String qualifiedName) {
        super(SqlCompilationError.invalidIdentifier(qualifiedName));
    }
}
