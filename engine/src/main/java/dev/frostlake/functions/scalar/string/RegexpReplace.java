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

import dev.frostlake.functions.TextArgumentFunction;
import dev.frostlake.types.StringType;

import java.util.List;
import java.util.regex.Matcher;

/**
 * REGEXP_REPLACE(subject, pattern [, replacement [, position [, occurrence [, parameters]]]]) — replace
 * matches of {@code pattern} with {@code replacement}. The replacement uses Snowflake backreferences
 * ({@code \1}, {@code \2}, …). {@code position} (1-based, default 1) is where matching starts;
 * {@code occurrence} (default 0) replaces ALL matches, or only the Nth when &gt; 0. An invalid pattern is
 * an error (no longer silently returned unchanged).
 */
public class RegexpReplace extends TextArgumentFunction {
    public RegexpReplace() { super("REGEXP_REPLACE", StringType.VARCHAR); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) {
            return null;
        }
        final String subject = args.get(0).toString();
        final String pattern = args.get(1) == null ? "" : args.get(1).toString();
        final String replacement = args.size() > 2 && args.get(2) != null ? args.get(2).toString() : "";
        final int position = args.size() > 3 && args.get(3) != null ? ((Number) args.get(3)).intValue() : 1;
        final int occurrence = args.size() > 4 && args.get(4) != null ? ((Number) args.get(4)).intValue() : 0;
        final String parameters = args.size() > 5 && args.get(5) != null ? args.get(5).toString() : null;

        final int start = Math.max(0, position - 1);
        if (start >= subject.length()) {
            return subject;
        }
        final String prefix = subject.substring(0, start);
        final String region = subject.substring(start);
        final String javaReplacement = RegexpHelper.translateReplacement(replacement);
        final Matcher matcher = RegexpHelper.compile(pattern, parameters).matcher(region);
        final StringBuilder sb = new StringBuilder();
        int count = 0;
        while (matcher.find()) {
            count++;
            if (occurrence == 0 || count == occurrence) {
                matcher.appendReplacement(sb, javaReplacement);
                if (occurrence != 0) {
                    break;
                }
            }
        }
        matcher.appendTail(sb);
        return prefix + sb;
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 6; }
}
