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
import java.util.regex.Matcher;

/**
 * REGEXP_COUNT(subject, pattern [, position [, parameters]]) — the number of matches of {@code pattern}
 * at or after {@code position} (1-based, default 1). An invalid pattern is an error.
 */
public class RegexpCount extends BuiltInFunction {
    public RegexpCount() { super("REGEXP_COUNT", NumericType.INTEGER); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null) {
            return 0L;
        }
        final String subject = args.get(0).toString();
        final String pattern = args.get(1).toString();
        final int position = args.size() > 2 && args.get(2) != null ? ((Number) args.get(2)).intValue() : 1;
        final String parameters = args.size() > 3 && args.get(3) != null ? args.get(3).toString() : null;

        final int start = Math.max(0, position - 1);
        if (start > subject.length()) {
            return 0L;
        }
        final Matcher matcher = RegexpHelper.compile(pattern, parameters).matcher(subject);
        long count = 0;
        if (matcher.find(start)) {
            count = 1;
            while (matcher.find()) {
                count++;
            }
        }
        return count;
    }

    @Override public int getMinArgCount() { return 2; }
    @Override public int getMaxArgCount() { return 4; }
}
