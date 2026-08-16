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

import dev.frostlake.types.BooleanType;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Locale;

public class BoolOrAgg extends BooleanAggregate {
    public BoolOrAgg() { super("BOOLOR_AGG", new BooleanType()); }

    @Override
    public Accumulator createAccumulator() { return new BoolOrAccumulator(); }

    @Override
    public Object evaluate(final List<Object> args) { return null; }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }

static boolean isTruthy(final Object v) {
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof Number) return ((Number) v).doubleValue() != 0;
        if (v instanceof VariantValue) {
            // A VARIANT member is read as a boolean only when it IS one: BOOLOR_AGG over a VARIANT
            // holding 1 is "Failed to cast variant value 1 to BOOLEAN" on the account, at row time.
            final JsonNode member = ((VariantValue) v).node();
            if (member != null && member.isBoolean()) {
                return member.booleanValue();
            }
            throw new RuntimeException("Failed to cast variant value " + ((VariantValue) v).text() + " to BOOLEAN");
        }
        if (v instanceof String) {
            // A text reads through TO_BOOLEAN's forms and nothing else: 'x' is "Boolean value 'x' is
            // not recognized" (live-verified, row time, no prefix).
            final String s = ((String) v).trim().toLowerCase(Locale.ROOT);
            if (s.equals("true") || s.equals("t") || s.equals("yes") || s.equals("y") || s.equals("on") || s.equals("1")) {
                return true;
            }
            if (s.equals("false") || s.equals("f") || s.equals("no") || s.equals("n") || s.equals("off") || s.equals("0")) {
                return false;
            }
            throw new RuntimeException("Boolean value '" + v + "' is not recognized");
        }
        return false;
    }
}
