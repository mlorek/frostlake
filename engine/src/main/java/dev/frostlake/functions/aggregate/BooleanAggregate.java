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
import dev.frostlake.types.DataType;

/**
 * The three boolean aggregates' shared argument rule. The account plans BOOLOR_AGG, BOOLAND_AGG and
 * BOOLXOR_AGG as MAX / MIN over {@code TO_BOOLEAN(x)}, so an argument that conversion refuses is
 * refused while the statement compiles, in the conversion's words and with no position — a FLOAT, a
 * DATE / TIME / TIMESTAMP, a BINARY, an OBJECT or an ARRAY: {@code invalid type [TO_BOOLEAN(AW.F)] for
 * parameter 'TO_BOOLEAN'}. An exact NUMBER reads as {@code x <> 0}, a BOOLEAN as itself, and a VARCHAR
 * or a VARIANT is read at row time — a text that is no TO_BOOLEAN form is {@code Boolean value 'x' is
 * not recognized}, a VARIANT member that is not a boolean {@code Failed to cast variant value 1 to
 * BOOLEAN} (all live-verified).
 */
public abstract class BooleanAggregate extends AggregateFunction {

    protected BooleanAggregate(final String name, final DataType returnType) {
        super(name, returnType);
    }

    @Override
    public SemiStructuredRejection approximateRejection(final int position) {
        return SemiStructuredRejection.BOOLEAN_CONVERSION_PARAMETER;
    }

    @Override
    public SemiStructuredRejection temporalRejection(final int position) {
        return SemiStructuredRejection.BOOLEAN_CONVERSION_PARAMETER;
    }

    @Override
    public SemiStructuredRejection binaryRejection(final int position) {
        return SemiStructuredRejection.BOOLEAN_CONVERSION_PARAMETER;
    }

    @Override
    public SemiStructuredRejection structuredRejection(final int position) {
        return SemiStructuredRejection.BOOLEAN_CONVERSION_PARAMETER;
    }
}
