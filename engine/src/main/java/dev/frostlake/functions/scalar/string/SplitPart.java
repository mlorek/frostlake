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

import dev.frostlake.executor.expressions.CollationSpec;
import dev.frostlake.functions.CollationMatching;
import dev.frostlake.functions.TextArgumentFunction;
import dev.frostlake.types.StringType;

import java.util.List;
import java.util.regex.Pattern;

public class SplitPart extends TextArgumentFunction {
    public SplitPart() { super("SPLIT_PART", StringType.VARCHAR); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        final String str = args.get(0).toString();
        final String delim = args.get(1) == null ? "" : args.get(1).toString();
        final int part = ((Number) args.get(2)).intValue();
        if (delim.isEmpty()) return part == 1 ? str : "";
        final String[] parts = str.split(Pattern.quote(delim), -1);
        final int idx = part > 0 ? part - 1 : parts.length + part;
        if (idx < 0 || idx >= parts.length) return "";
        return parts[idx];
    }

    /** Under a collation the text splits on every separator the collation matches. */
    @Override
    public Object evaluate(final List<Object> args, final CollationSpec collation) {
        if (collation == null || args.get(0) == null || args.get(1) == null) {
            return evaluate(args);
        }
        final List<String> parts = CollationMatching.splitUnder(collation, args.get(0).toString(),
            args.get(1).toString());
        final int part = ((Number) args.get(2)).intValue();
        final int idx = part > 0 ? part - 1 : parts.size() + part;
        if (idx < 0 || idx >= parts.size()) {
            return "";
        }
        return parts.get(idx);
    }

    @Override
    public int getMinArgCount() { return 3; }
    @Override
    public int getMaxArgCount() { return 3; }
}
