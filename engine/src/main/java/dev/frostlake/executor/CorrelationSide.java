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

/** Where a column reference inside a subquery resolves, as {@link CorrelatedSubqueryRule} reads it. */
enum CorrelationSide {

    /** One of the subquery's own relations, or one of its select aliases. */
    INNER,

    /** The row the subquery is evaluated for, or a row further out. */
    OUTER,

    /**
     * A name of the row the subquery is evaluated for that its relation's statistics pin to one value on every
     * row: a constant, which leaves nothing to correlate (see {@link OuterNameConstancy}).
     */
    CONSTANT,

    /** Neither: a name the rule cannot place, left for the subquery's own execution to report. */
    UNKNOWN
}
