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
 * A built-in that reads its argument as a VARIANT and therefore refuses a STRUCTURED one — an
 * {@code OBJECT(x VARCHAR)}, an {@code ARRAY(INT)} or a {@code MAP(VARCHAR, INT)} — in EVERY argument
 * position, while taking the plain semi-structured OBJECT and ARRAY happily. The structured twin of
 * {@link TextArgumentFunction}, and the same live message shape ("Invalid argument types for function
 * 'TO_JSON': (OBJECT(x VARCHAR(16777216)))", SQLSTATE 42P13) — only the type name is the whole
 * parameterised type rather than the bare family.
 *
 * <p>This is a DIVERGENCE, not a generalisation: live over one table carrying both,
 * {@code TO_JSON(o)} returns {@code {"k":"v1"}} and {@code TYPEOF(o)} returns {@code OBJECT} while
 * {@code TO_JSON(so)} and {@code TYPEOF(so)} are compile errors. Snowflake will not implicitly convert
 * a structured value to the VARIANT these functions read, and there is no run-time answer to fall back
 * on, so the whole signature refuses it. An explicit conversion is legal and is how a caller opts in:
 * {@code TO_JSON(so::VARIANT)}, {@code TO_JSON(so::OBJECT)} and {@code ARRAY_AGG(TO_VARIANT(so))} all
 * work live.
 *
 * <p>Membership is by measurement, so close neighbours deliberately stay out. {@code PARSE_JSON},
 * {@code CHECK_JSON} and {@code CHECK_XML} refuse a plain OBJECT too, so they are not a DIVERGENCE and
 * belong to the semi-structured rule if they are ever declared. {@code AS_INTEGER},
 * {@code AS_DECIMAL}, {@code AS_NUMBER} and the {@code AS_TIMESTAMP_*} trio refuse both kinds AND use
 * a different sentence entirely ("invalid type [ARRAY] for parameter 'AS_INTEGER(variantValue...)'"),
 * which is why the {@code AS_*} family is split down the middle here. And the ACCESSORS are untouched:
 * {@code GET}, {@code GET_PATH}, {@code OBJECT_KEYS}, {@code OBJECT_INSERT}, {@code ARRAY_SIZE},
 * {@code ARRAY_APPEND}, {@code MAP_KEYS} and {@code FLATTEN} all read a structured value live.
 */
public abstract class StructuredArgumentFunction extends BuiltInFunction {

    protected StructuredArgumentFunction(final String name, final DataType returnType) {
        super(name, returnType);
    }

    @Override
    public SemiStructuredRejection structuredRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }
}
