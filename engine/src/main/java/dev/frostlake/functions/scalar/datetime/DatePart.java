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

import java.time.LocalTime;
import java.util.List;

public class DatePart extends BuiltInFunction {
    public DatePart() { super("DATE_PART", NumericType.INTEGER); }

    /**
     * DATE_PART spells its own bad-unit refusal, and not the sentence its neighbours use: live answers
     * "invalid value [ZZ] for parameter 'DATE_PART date/time part'" where DATEADD and DATE_TRUNC say
     * "['ZZ'] is not a valid date/time component for function …". A unit that is not an identifier or a
     * string reaches here as null and is reported as the word "null", which is live's own wording.
     */
    private static RuntimeException invalidPart(final Object raw) {
        return new RuntimeException("SQL compilation error:\ninvalid value [" + raw
            + "] for parameter 'DATE_PART date/time part'");
    }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) {
            throw invalidPart("null");
        }
        if (args.get(1) == null) return null;
        // A TIME reads on the clock alone; the day-or-larger units are refused at plan time, so this
        // never has to decide them from a value.
        try {
            if (args.get(1) instanceof LocalTime) {
                return SharedFunctionHelpers.datePart(args.get(0).toString(), (LocalTime) args.get(1));
            }
            return SharedFunctionHelpers.datePart(args.get(0).toString(),
                SharedFunctionHelpers.toLocalDateTime(args.get(1)),
                args.get(1), "DATE_PART");
        } catch (final RuntimeException unknownPart) {
            if (String.valueOf(unknownPart.getMessage()).startsWith("Unsupported date part: ")) {
                throw invalidPart(args.get(0));
            }
            throw unknownPart;
        }
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 2; }
}
