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

package dev.frostlake.functions.scalar.datetime;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.NumericType;

import java.util.List;

public class Extract extends BuiltInFunction {
    public Extract() { super("EXTRACT", NumericType.INTEGER); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(1) == null) return null;
        if (args.get(1) instanceof java.time.LocalTime) {
            return SharedFunctionHelpers.datePart(args.get(0).toString(),
                ((java.time.LocalTime) args.get(1)).atDate(java.time.LocalDate.of(1970, 1, 1)),
                "EXTRACT");
        }
        return SharedFunctionHelpers.datePart(args.get(0).toString(),
            SharedFunctionHelpers.toLocalDateTime(args.get(1)),
            args.get(1), "EXTRACT");
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 2; }
}
