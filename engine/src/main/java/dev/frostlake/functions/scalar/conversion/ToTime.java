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

package dev.frostlake.functions.scalar.conversion;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.functions.scalar.SnowflakeDateParser;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.values.VariantValue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * TO_TIME(expr [, format]). With a format the input is read against that model only — a mismatch is
 * "Can't parse '&lt;input&gt;' as time with format '&lt;format&gt;'", the input and the model echoed
 * verbatim — and any date the model reads is dropped; a NULL format is a NULL answer. Without one
 * (or under AUTO) the input is read flexibly.
 */
public class ToTime extends BuiltInFunction {
    public ToTime() { this("TO_TIME"); }

    /**
     * A conversion to TIME registered under another name, for the TIME synonym.
     *
     * @param name the name it is called by
     */
    protected ToTime(final String name) { super(name, DateTimeType.TIME); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        if (args.size() > 1 && args.get(1) == null) return null;
        final Object v = args.get(0);
        if (v instanceof VariantValue && ((VariantValue) v).node() != null
                && ((VariantValue) v).node().isNumber()) {
            // A VARIANT number is no time: live fails the variant's cast, "Failed to cast variant value
            // 1579046400 to TIME", for TO_TIME and the TIME() alias alike (live-verified).
            throw new RuntimeException("Failed to cast variant value " + ((VariantValue) v).text() + " to TIME");
        }
        if (v instanceof Number) {
            // Only the TIME() alias brings a NUMBER here — TO_TIME(<number>) is refused while the
            // statement compiles — and the account converts it through a VARIANT, whose failure names
            // the JSON number: "Failed to cast variant value 1.5 to TIME" for a NUMBER(10,2) 1.50 and
            // "... 86400 to TIME" for a whole number (live-verified).
            throw new RuntimeException("Failed to cast variant value " + jsonNumberText((Number) v)
                + " to TIME");
        }
        if (args.size() > 1 && SharedFunctionHelpers.isExplicitFormat(args.get(1).toString())) {
            return LocalTime.from(SnowflakeDateParser.parse(v.toString(), args.get(1).toString(), "time"));
        }
        if (v instanceof LocalTime) return v;
        if (v instanceof LocalDateTime) return ((LocalDateTime) v).toLocalTime();
        final String s = v.toString().trim();
        try { return LocalTime.parse(s); } catch (final Exception ignored) {}
        try { return LocalTime.parse(s, DateTimeFormatter.ofPattern("HH:mm")); } catch (final Exception ignored) {}
        throw new RuntimeException("Time '" + s + "' is not recognized");
    }

    /**
     * A number as a VARIANT prints it: an exact number without trailing zeros or a point on a whole
     * value, a double in the fifteen-decimal scientific form — {@code 1.500000000000000e+00} for a
     * FLOAT 1.5 (live-verified).
     */
    private static String jsonNumberText(final Number value) {
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).stripTrailingZeros().toPlainString();
        }
        if (value instanceof Double || value instanceof Float) {
            return String.format(Locale.ROOT, "%.15e", value.doubleValue());
        }
        return value.toString();
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 2; }
}
