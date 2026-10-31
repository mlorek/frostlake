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
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.StringType;

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
        // A NULL in any argument answers NULL (live-verified).
        if (anyNull(args)) {
            return null;
        }
        final String subject = SharedFunctionHelpers.textOf(args.get(0));
        final String pattern = SharedFunctionHelpers.textOf(args.get(1));
        final int position = RegexpHelper.wholeArgument(args, 2, 1);
        final int occurrence = RegexpHelper.wholeArgument(args, 3, 1);
        final String parameters = RegexpHelper.textArgument(args, 4);
        final Integer groupNum = RegexpHelper.groupArgument(args, 5);
        RegexpHelper.requirePosition(position);
        RegexpHelper.requireOccurrence(occurrence);
        RegexpHelper.requireGroup(groupNum);
        if (RegexpHelper.matchesNothing(pattern, parameters)) {
            return null;
        }
        final int start = RegexpHelper.searchStart(subject, position);
        if (start < 0) {
            return null;
        }
        final Matcher matcher = RegexpHelper.compile(pattern, parameters).matcher(subject);
        if (!RegexpHelper.findOccurrence(matcher, start, occurrence)) {
            return null;
        }
        return RegexpHelper.matchedText(matcher, groupNum != null || RegexpHelper.hasExtract(parameters), groupNum);
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
