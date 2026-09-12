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
 * Which spelling {@link StrictMessagePrinter} gives an expression. A refusal names its operand from
 * the plan, but the plan is not the same everywhere a refusal is raised: an arity or a conversion
 * sentence sees the plan after its conversions were resolved into casts, while an invalid-type
 * sentence sees the conversions still as the functions they were planned as.
 */
enum StrictPrintMode {

    /**
     * Columns qualified and operators named, and nothing else rewritten: a cast keeps the shape it was
     * written in and a call its own name.
     */
    WRITTEN,

    /**
     * The resolved plan: a cast is {@code CAST(x AS T)} or, when it changes nothing, its bare operand;
     * a one-argument conversion is the cast it stands for; an interval shift is its
     * {@code DATE_ADD…} function; COALESCE is the IFNULL chain; the aggregates are their definitions.
     * The arity sentences, the conversion sentence and the nesting brackets print this.
     */
    PLAN,

    /**
     * The plan an invalid-type sentence quotes: a cast is the conversion function it was planned as —
     * {@code TO_DATE(x)}, {@code TO_VECTOR(x)}, {@code identity(x)} when it changes nothing — an
     * operand's implicit rescale is {@code FIXED_TO_FIXED(x AS NUMBER(p,s)[UNKNOWN])} and its move to
     * FLOAT {@code TO_DOUBLE(x)}, and NULL is upper-cased.
     */
    CONVERSION
}
