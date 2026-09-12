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

import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.functions.TextArgumentFunction;
import dev.frostlake.types.IntegerResultWidths;

import java.util.List;
import java.util.regex.Matcher;

/**
 * REGEXP_INSTR(subject, pattern [, position [, occurrence [, option [, parameters [, group_num]]]]]) — the
 * 1-based position of the {@code occurrence}-th match (default 1) at or after {@code position} (default 1),
 * or 0 when there is none. {@code option} 0 (default) reports the match's first character; 1 reports the
 * character just after the match. With the {@code e} flag or an explicit {@code group_num}, the position of
 * that capture group is reported instead of the whole match.
 */
public class RegexpInstr extends TextArgumentFunction {
    public RegexpInstr() { super("REGEXP_INSTR", IntegerResultWidths.COUNTER); }

    @Override
    public Object evaluate(final List<Object> args) {
        // Snowflake: NULL in, NULL out — a NULL haystack or needle yields NULL, not 0/false
        for (int i = 0; i < args.size(); i++) {
            if (i < args.size() && args.get(i) == null) {
                return null;
            }
        }
        if (args.get(0) == null || args.get(1) == null) {
            return 0L;
        }
        final String subject = args.get(0).toString();
        final String pattern = args.get(1).toString();
        final int position = args.size() > 2 && args.get(2) != null ? ((Number) args.get(2)).intValue() : 1;
        final int occurrence = args.size() > 3 && args.get(3) != null ? ((Number) args.get(3)).intValue() : 1;
        final int option = args.size() > 4 && args.get(4) != null ? ((Number) args.get(4)).intValue() : 0;
        final String parameters = args.size() > 5 && args.get(5) != null ? args.get(5).toString() : null;
        final Integer groupNum = args.size() > 6 && args.get(6) != null ? ((Number) args.get(6)).intValue() : null;

        final int start = Math.max(0, position - 1);
        if (start > subject.length()) {
            return 0L;
        }
        final Matcher matcher = RegexpHelper.compile(pattern, parameters).matcher(subject);
        if (!matcher.find(start)) {
            return 0L;
        }
        int found = 1;
        while (found < occurrence) {
            if (!matcher.find()) {
                return 0L;
            }
            found++;
        }
        int spanStart = matcher.start();
        int spanEnd = matcher.end();
        final boolean extract = groupNum != null || RegexpHelper.hasExtract(parameters);
        if (extract && matcher.groupCount() >= 1) {
            final int group = groupNum != null ? groupNum : 1;
            if (group <= matcher.groupCount() && matcher.start(group) >= 0) {
                spanStart = matcher.start(group);
                spanEnd = matcher.end(group);
            }
        }
        return (long) ((option == 1 ? spanEnd : spanStart) + 1);
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 7; }

    /** A BINARY is no text here: the account refuses it by the argument types (live-verified). */
    @Override
    public SemiStructuredRejection binaryRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }
}
