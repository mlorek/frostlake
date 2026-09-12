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

package dev.frostlake.functions;

import dev.frostlake.types.DataType;

/**
 * A built-in that reads its arguments as text, and therefore refuses a statically OBJECT- or
 * ARRAY-typed value in EVERY argument position.
 *
 * <p>Every position, not merely the string-shaped ones — that was measured rather than assumed.
 * Live over an OBJECT column, all three of {@code SPLIT_PART(o, ',', 1)},
 * {@code SPLIT_PART(s, o, 1)} and {@code SPLIT_PART(s, ',', o)} are argument-type errors, and so are
 * {@code LPAD(o, 20, '.')}, {@code LPAD(s, o, '.')} (which live reports through {@code SPACE}) and
 * {@code LPAD(s, 20, o)}. A semi-structured value coerces to neither the VARCHAR nor the NUMBER these
 * functions want, so the whole signature refuses it and the per-position distinction collapses.
 *
 * <p>Membership is by measurement, so two neighbours in the same package deliberately stay out:
 * {@code SEARCH} ACCEPTS semi-structured data ({@code SEARCH(o, 'k')} returned TRUE live), and
 * {@code INSTR} / {@code CHAR_LENGTH} / {@code CHARACTER_LENGTH} are names Snowflake does not know at
 * all ("Unknown function"), so there is no live behaviour to copy. Extending this class is what
 * declares the rule; nothing infers it from the package.
 *
 * <p>VARIANT is never refused, even when it holds an object — {@code UPPER(vo)} returned
 * {@code {"X":1}} live — and neither is an explicit conversion: {@code UPPER(o::VARCHAR)},
 * {@code UPPER(TO_VARCHAR(o))} and {@code UPPER(TO_JSON(o))} all work. The rule reads the DECLARED
 * type, so {@code UPPER(v::OBJECT)} — the same value, cast — IS refused.
 */
public abstract class TextArgumentFunction extends BuiltInFunction {

    protected TextArgumentFunction(final String name, final DataType returnType) {
        super(name, returnType);
    }

    @Override
    public SemiStructuredRejection semiStructuredRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }

    /** A VECTOR is no text either, and is refused in every position (live-verified). */
    @Override
    public SemiStructuredRejection vectorRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }
}
