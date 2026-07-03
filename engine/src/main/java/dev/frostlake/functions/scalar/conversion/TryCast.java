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
import dev.frostlake.types.VariantType;

import java.math.BigDecimal;
import java.util.List;

public class TryCast extends BuiltInFunction {
    public TryCast() { super("TRY_CAST", VariantType.VARIANT); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        if (args.size() < 2 || args.get(1) == null) return args.get(0);
        String targetType = args.get(1).toString().toUpperCase();
        try {
            switch (targetType.replaceAll("\\(.*", "").trim()) {
                case "INTEGER": case "INT": case "BIGINT": return Long.parseLong(args.get(0).toString().trim());
                case "FLOAT": case "DOUBLE": return Double.parseDouble(args.get(0).toString().trim());
                case "NUMBER": case "DECIMAL": return new BigDecimal(args.get(0).toString().trim());
                case "BOOLEAN": { String v = args.get(0).toString().trim().toUpperCase(); return v.equals("TRUE") || v.equals("1"); }
                default: return args.get(0).toString();
            }
        } catch (final Exception e) { return null; }
    }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 2; }
}
