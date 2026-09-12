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

package dev.frostlake.functions.aggregate;

import dev.frostlake.functions.AggregateFunction;
import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.types.ObjectType;

import java.util.List;

/**
 * OBJECT_AGG(key, value) — aggregates key/value pairs into a single OBJECT. Pairs with a NULL key or
 * NULL value are omitted; the result is canonical JSON text (keys sorted), the engine's OBJECT form.
 */
public class ObjectAgg extends AggregateFunction {
    public ObjectAgg() { super("OBJECT_AGG", ObjectType.OBJECT); }

    /**
     * The KEY is never semi-structured, plain or not: live, {@code OBJECT_AGG(o, n)} is
     * "Invalid argument types for function 'OBJECT_AGG': (OBJECT, NUMBER(38,0))" and
     * {@code OBJECT_AGG(so, n)} / {@code OBJECT_AGG(sa, n)} are the same sentence with the structured
     * type named. Position 1, the VALUE, is where the two kinds diverge — see
     * {@link #structuredRejection}.
     */
    @Override
    public SemiStructuredRejection semiStructuredRejection(final int position) {
        return position == 0 ? SemiStructuredRejection.ARGUMENT_TYPES : SemiStructuredRejection.NONE;
    }

    /**
     * The VALUE takes a plain OBJECT or ARRAY and refuses a STRUCTURED one: live,
     * {@code OBJECT_AGG(s, o)} builds {@code {"aa":{"k":"v1"},…}} while {@code OBJECT_AGG(s, so)} is
     * "Invalid argument types for function 'OBJECT_AGG': (VARCHAR(16777216), OBJECT(x
     * VARCHAR(16777216)))" — the message lists BOTH arguments, so the offending position is visible.
     */
    @Override
    public SemiStructuredRejection structuredRejection(final int position) {
        return position == 1 ? SemiStructuredRejection.ARGUMENT_TYPES : SemiStructuredRejection.NONE;
    }

    /**
     * A FILE is refused in BOTH halves, which is where it parts company with the plain OBJECT the
     * VALUE position accepts: live, {@code OBJECT_AGG(f, 1)} is "Invalid argument types for
     * function 'OBJECT_AGG': (FILE, NUMBER(1,0))" and {@code OBJECT_AGG(s, f)} is the same sentence
     * naming "(VARCHAR(16777216), FILE)", while {@code OBJECT_AGG(s, o)} builds its object happily.
     * Without this the inherited default would read {@link #semiStructuredRejection} and let the value
     * half through.
     */
    @Override
    public SemiStructuredRejection fileRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }

    /**
     * The VALUE takes a NUMBER, a BOOLEAN, a FLOAT or a VARIANT (cast to VARIANT in the plan) and
     * refuses a VARCHAR, a temporal or a BINARY with the argument-type list — OBJECT_AGG(t, t) is
     * "Invalid argument types for function 'OBJECT_AGG': (VARCHAR(10), VARCHAR(10))", OBJECT_AGG(t, d)
     * "(VARCHAR(10), DATE)" — while the KEY takes any of them, cast to text (live-verified).
     */
    @Override
    public SemiStructuredRejection textRejection(final int position) {
        return position == 1 ? SemiStructuredRejection.ARGUMENT_TYPES : SemiStructuredRejection.NONE;
    }

    @Override
    public SemiStructuredRejection temporalRejection(final int position) {
        return position == 1 ? SemiStructuredRejection.ARGUMENT_TYPES : SemiStructuredRejection.NONE;
    }

    @Override
    public SemiStructuredRejection binaryRejection(final int position) {
        return position == 1 ? SemiStructuredRejection.ARGUMENT_TYPES : SemiStructuredRejection.NONE;
    }

    @Override
    public Accumulator createAccumulator() { return new ObjectAggAccumulator(); }

    @Override
    public Object evaluate(final List<Object> args) { return null; }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 2; }
}
