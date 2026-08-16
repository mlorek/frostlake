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
import dev.frostlake.types.NumericType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * AS_DECIMAL(v [, precision [, scale]]) — the variant's numeric value as a fixed-point number
 * rounded (HALF_UP) to the given scale, else NULL. Defaults are NUMBER(38,0), so
 * {@code AS_DECIMAL(PARSE_JSON('2.5'))} is 3 (live-verified); a rounded value wider than the
 * precision raises Snowflake's out-of-range error. AS_NUMBER is the same function.
 */
public class AsDecimal extends BuiltInFunction {
    public AsDecimal() { super("AS_DECIMAL", NumericType.NUMBER); }

    @Override
    public Object evaluate(final List<Object> args) {
        final JsonNode node = ArrayFunctionHelper.parseNode(args.get(0));
        final BigDecimal raw;
        if (node != null && node.isNumber()) {
            raw = node.decimalValue();
        } else if (args.get(0) instanceof Number) {
            raw = new BigDecimal(args.get(0).toString());
        } else {
            return null;
        }
        final int precision = args.size() > 1 && args.get(1) != null
            ? ((Number) args.get(1)).intValue() : 38;
        final int scale = args.size() > 2 && args.get(2) != null
            ? ((Number) args.get(2)).intValue() : 0;
        final BigDecimal rounded = raw.setScale(scale, RoundingMode.HALF_UP);
        if (rounded.precision() > precision) {
            // Live-verified wording.
            throw new RuntimeException(
                "Number out of representable range: type FIXED, value " + rounded.toPlainString());
        }
        if (scale == 0) {
            try {
                return rounded.longValueExact();
            } catch (final ArithmeticException beyondLong) {
                return rounded;
            }
        }
        return rounded;
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 3; }
}
