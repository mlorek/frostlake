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

package dev.frostlake.functions.scalar.string;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.StringType;

import java.util.List;

public class LPad extends BuiltInFunction {
    public LPad() {
        super("LPAD", StringType.VARCHAR);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        String str = args.get(0).toString();
        int targetLength = ((Number) args.get(1)).intValue();
        String padStr = args.size() > 2 && args.get(2) != null ? args.get(2).toString() : " ";

        if (str.length() >= targetLength) return str;
        if (padStr.isEmpty()) return str;

        int padLength = targetLength - str.length();
        StringBuilder result = new StringBuilder();

        while (result.length() < padLength) {
            result.append(padStr);
        }
        result.setLength(padLength);
        result.append(str);

        return result.toString();
    }

    @Override
    public int getMinArgCount() { return 2; }

    @Override
    public int getMaxArgCount() { return 3; }
}
