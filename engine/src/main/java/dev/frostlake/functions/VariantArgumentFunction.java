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
 * A built-in that reads its first argument as a VARIANT and takes there only what converts to one without
 * being asked: a VARIANT, an OBJECT, an ARRAY, a number of either kind, a BOOLEAN and NULL. A text, a
 * DATE, a TIME, a timestamp of any flavour and a BINARY are each refused when the statement compiles —
 * "Invalid argument types for function 'IS_INTEGER': (VARCHAR(16777216))", at the call — whether a
 * literal, a column or an expression is written there, and over no rows as well. The type predicates of
 * a VARIANT and those of its casts that answer NULL for another kind all behave so; the casts that take
 * nothing but a VARIANT say {@link BuiltInFunction#takesVariantOnly} instead (live-verified).
 */
public abstract class VariantArgumentFunction extends StructuredArgumentFunction {
    protected VariantArgumentFunction(final String name, final DataType returnType) {
        super(name, returnType);
    }

    @Override
    public SemiStructuredRejection textRejection(final int position) {
        return position == 0 ? SemiStructuredRejection.ARGUMENT_TYPES : SemiStructuredRejection.NONE;
    }

    @Override
    public SemiStructuredRejection temporalRejection(final int position) {
        return position == 0 ? SemiStructuredRejection.ARGUMENT_TYPES : SemiStructuredRejection.NONE;
    }

    @Override
    public SemiStructuredRejection binaryRejection(final int position) {
        return position == 0 ? SemiStructuredRejection.ARGUMENT_TYPES : SemiStructuredRejection.NONE;
    }
}
