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

package dev.frostlake.functions.scalar.semistructured;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.types.VariantType;
import dev.frostlake.values.VariantValue;

import java.util.List;

/**
 * TO_VARIANT(expr) — wraps a value as a typed VARIANT. A VARCHAR becomes a variant STRING and is
 * NEVER parsed (live-verified: {@code TO_VARIANT('{"a":1}')} is a string, not an object — it does
 * not equal {@code PARSE_JSON} of the same text); an already semi-structured value passes through;
 * scalars wrap as their typed variant counterpart. NULL stays NULL.
 */
public class ToVariant extends BuiltInFunction {
    public ToVariant() { super("TO_VARIANT", VariantType.VARIANT); }

    @Override
    public Object evaluate(final List<Object> args) {
        final Object value = args.get(0);
        if (value == null || value instanceof VariantValue) {
            return value;
        }
        // Temporal and binary values keep their NATIVE type inside the variant — the AS_*/IS_*
        // extractors and TYPEOF depend on it, exactly as a ::VARIANT cast preserves them.
        if (value instanceof java.time.LocalDate || value instanceof java.time.LocalTime
                || value instanceof java.time.LocalDateTime
                || value instanceof java.time.OffsetDateTime
                || value instanceof java.time.ZonedDateTime
                || value instanceof dev.frostlake.values.BinaryValue) {
            return value;
        }
        if (value instanceof CharSequence) {
            return VariantValue.ofNode(
                ArrayFunctionHelper.MAPPER.getNodeFactory().textNode(value.toString()));
        }
        // Canonicalized, so a whole-valued decimal wraps as an integral variant (TYPEOF = INTEGER).
        return ArrayFunctionHelper.toCanonicalVariant(
            ArrayFunctionHelper.toNode(ArrayFunctionHelper.MAPPER, value));
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }
}
