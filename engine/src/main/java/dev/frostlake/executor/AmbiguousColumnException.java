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
 * A bare column name carried by both sides of an ON-joined relation — live's "SQL compilation
 * error: ambiguous column name 'T'". The join FORM is the rule (measured cell by cell): an ON join
 * makes every same-named bare reference ambiguous, inner and left alike, while a USING or NATURAL
 * join resolves EVERY same-named pair to the LEFT side — keys and non-keys both — which is why
 * {@code SELECT d FROM a JOIN b USING (k)} answers a.d where the ON spelling of the same join is
 * rejected. Typed so the lenient catch-and-retry fallbacks (the expression visitor's multi-table
 * probe, WhereOperator's strategy chain) can rethrow it: ambiguity is definitive.
 */
public class AmbiguousColumnException extends RuntimeException {

    /**
     * Builds the SQL compilation error for a bare column name both sides of an ON join carry.
     *
     * @param columnName the ambiguous bare column name, quoted verbatim in the error message
     */
    public AmbiguousColumnException(final String columnName) {
        super(SqlCompilationError.of("ambiguous column name '" + columnName + "'"));
    }
}
