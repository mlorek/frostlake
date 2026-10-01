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
import dev.frostlake.types.StringType;
import dev.frostlake.values.CodePointText;

import java.util.List;
import java.util.regex.Matcher;

/**
 * REGEXP_SUBSTR(subject, pattern [, position [, occurrence [, parameters [, group_num]]]]) — the
 * {@code occurrence}-th match (default 1) at or after {@code position} (1-based, default 1). With the
 * {@code e} flag or an explicit {@code group_num} (which implies {@code e}), the matching capture group is
 * returned instead of the whole match — group 1 by default; a pattern with no groups falls back to the
 * whole match. Returns NULL when there is no such match.
 */
public class RegexpSubstr extends TextArgumentFunction {
    public RegexpSubstr() { super("REGEXP_SUBSTR", StringType.VARCHAR); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null) {
            return null;
        }
        final String subject = args.get(0).toString();
        final String pattern = args.get(1).toString();
        final int position = args.size() > 2 && args.get(2) != null ? ((Number) args.get(2)).intValue() : 1;
        final int occurrence = args.size() > 3 && args.get(3) != null ? ((Number) args.get(3)).intValue() : 1;
        final String parameters = args.size() > 4 && args.get(4) != null ? args.get(4).toString() : null;
        final Integer groupNum = args.size() > 5 && args.get(5) != null ? ((Number) args.get(5)).intValue() : null;

        // The position counts characters, a supplementary one included (live-verified).
        final int start = Math.max(0, position - 1);
        if (start > CodePointText.length(subject)) {
            return null;
        }
        final Matcher matcher = RegexpHelper.compile(pattern, parameters).matcher(subject);
        if (!matcher.find(CodePointText.offset(subject, start))) {
            return null;
        }
        int found = 1;
        while (found < occurrence) {
            if (!matcher.find()) {
                return null;
            }
            found++;
        }
        final boolean extract = groupNum != null || RegexpHelper.hasExtract(parameters);
        if (extract && matcher.groupCount() >= 1) {
            final int group = groupNum != null ? groupNum : 1;
            return group <= matcher.groupCount() ? matcher.group(group) : null;
        }
        return matcher.group();
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 6; }

    /** A BINARY is no text here: the account refuses it by the argument types (live-verified). */
    @Override
    public SemiStructuredRejection binaryRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }
}
