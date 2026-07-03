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
import dev.frostlake.types.NumericType;

import java.util.List;

public class CharIndex extends BuiltInFunction {
    public CharIndex() { super("CHARINDEX", NumericType.INTEGER); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null) return 0L;
        String needle = args.get(0).toString();
        String haystack = args.get(1).toString();
        int startPos = args.size() > 2 && args.get(2) != null ? ((Number) args.get(2)).intValue() - 1 : 0;
        startPos = Math.max(0, startPos);
        int idx = haystack.indexOf(needle, startPos);
        return idx < 0 ? 0L : (long) (idx + 1);
    }

    @Override public int getMinArgCount() { return 2; }
    @Override public int getMaxArgCount() { return 3; }
}
