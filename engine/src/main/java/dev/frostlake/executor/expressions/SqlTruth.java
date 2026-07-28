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

/**
 * The single truthiness rule for a value in predicate position — Snowflake's implicit boolean
 * coercion: a BOOLEAN as itself, a VARCHAR via TO_BOOLEAN's text forms ({@code 'true'}, {@code 't'},
 * {@code 'yes'}, {@code 'y'}, {@code 'on'}, {@code '1'}, case-insensitive), a number as
 * zero/non-zero, NULL and anything else as FALSE. Every consumer that filters on a raw evaluation
 * result (WHERE / HAVING / join conditions / conditional inserts) must use this rather than testing
 * {@code instanceof Boolean} — a bare {@code WHERE is_direct} over a VARCHAR column holding
 * {@code 'true'} is a real loader idiom and silently dropped every row otherwise.
 */
public final class SqlTruth {

    private SqlTruth() {
    }

    public static boolean isTrue(final Object value) {
        return ExpressionArithmetic.isTrue(value);
    }
}
