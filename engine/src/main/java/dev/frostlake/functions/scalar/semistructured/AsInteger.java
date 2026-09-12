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
import dev.frostlake.types.IntegerResultWidths;
import dev.frostlake.values.DecimalOriginNode;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;

public class AsInteger extends BuiltInFunction {
    public AsInteger() { super("AS_INTEGER", IntegerResultWidths.WIDEST); }

    @Override
    public Object evaluate(final List<Object> args) {
        // Live-verified: only an INTEGRAL variant number passes through — AS_INTEGER of 2.5 is
        // NULL, never a rounded or truncated value.
        if (args.get(0) == null) return null;
        final JsonNode node = ArrayFunctionHelper.parseNode(args.get(0));
        // A whole DECIMAL (3.00 out of a NUMBER(10,2)) is no INTEGER variant either: NULL on the account.
        if (node instanceof DecimalOriginNode) {
            return null;
        }
        if (node != null && node.isNumber()) {
            final BigDecimal dec = node.decimalValue().stripTrailingZeros();
            return dec.scale() <= 0 ? (Object) dec.longValue() : null;
        }
        final Object v = args.get(0);
        if (v instanceof Number) {
            final BigDecimal dec = new BigDecimal(v.toString()).stripTrailingZeros();
            return dec.scale() <= 0 ? (Object) dec.longValue() : null;
        }
        return null;
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 2; }
}
