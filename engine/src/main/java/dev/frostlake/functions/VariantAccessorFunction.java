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
 * A built-in that NAVIGATES a semi-structured value — reads a key, an index, a size, or splices a new
 * element in — and therefore refuses a GEOSPATIAL one in EVERY argument position, while taking a plain
 * OBJECT or ARRAY and a STRUCTURED one alike.
 *
 * <p>This family is the reason {@link BuiltInFunction#geoRejection} exists as a question of its own.
 * The accessors are precisely the surface both semi-structured declarations leave alone — they read a
 * structured value quite happily, so they are not {@link StructuredArgumentFunction}s, and they read a
 * plain OBJECT, so they declare no {@code semiStructuredRejection} either — which makes the inherited
 * union default answer NONE for them. Live disagrees, and only for geo. Measured over one
 * table carrying an OBJECT column and a GEOGRAPHY column: {@code GET(o, 'k')} returns {@code "v"} while
 * {@code GET(g, 'type')} is "Invalid argument types for function 'GET': (GEOGRAPHY, VARCHAR(4))"
 * (SQLSTATE 42P13), and {@code GET_PATH}, {@code OBJECT_KEYS}, {@code OBJECT_INSERT},
 * {@code ARRAY_SIZE}, {@code ARRAY_APPEND} and {@code ARRAY_TO_STRING} each split the same way.
 *
 * <p>Every position, not merely the semi-structured-shaped one: live refuses
 * {@code OBJECT_INSERT(o, 'a', g)} and {@code ARRAY_APPEND(ARRAY_CONSTRUCT(1), g)} — a geo value in the
 * VALUE slot of an otherwise valid call — with the same sentence, naming the offending position in the
 * argument-type list.
 *
 * <p>A geo value is not reachable by any of them at all: it is not a VARIANT and does not become one
 * ({@code TO_VARIANT(g)} and {@code g::VARIANT} are both compile errors live), so unlike the structured
 * types there is no explicit conversion that opts back in. {@code ST_ASGEOJSON(g)} returns an OBJECT
 * that these functions then read.
 *
 * <p>Membership is by measurement, so close neighbours stay out. {@code GET_IGNORE_CASE},
 * {@code ARRAY_SLICE}, {@code MAP_KEYS} and {@code FLATTEN} have not been measured over a geo value.
 * {@code PARSE_JSON} deliberately stays out for the opposite reason: live refuses a plain OBJECT there
 * too, so it is not a geo DIVERGENCE — it belongs to the semi-structured rule whenever that is declared
 * for it.
 */
public abstract class VariantAccessorFunction extends BuiltInFunction {

    protected VariantAccessorFunction(final String name, final DataType returnType) {
        super(name, returnType);
    }

    @Override
    public SemiStructuredRejection geoRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }
}
