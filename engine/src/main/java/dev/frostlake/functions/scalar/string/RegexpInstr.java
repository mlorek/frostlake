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
import dev.frostlake.types.IntegerResultWidths;
import dev.frostlake.values.CodePointText;

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
        // A NULL in any argument answers NULL (live-verified).
        if (anyNull(args)) {
            return null;
        }
        final String subject = SharedFunctionHelpers.textOf(args.get(0));
        final String pattern = SharedFunctionHelpers.textOf(args.get(1));
        final int position = RegexpHelper.wholeArgument(args, 2, 1);
        final int occurrence = RegexpHelper.wholeArgument(args, 3, 1);
        final int option = RegexpHelper.wholeArgument(args, 4, 0);
        final String parameters = RegexpHelper.textArgument(args, 5);
        final Integer groupNum = RegexpHelper.groupArgument(args, 6);
        RegexpHelper.requirePosition(position);
        RegexpHelper.requireOccurrence(occurrence);
        RegexpHelper.requireReturnOption(option);
        RegexpHelper.requireGroup(groupNum);
        if (RegexpHelper.matchesNothing(pattern, parameters)) {
            return 0L;
        }

        final int start = RegexpHelper.searchStart(subject, position);
        if (start < 0) {
            return 0L;
        }
        final Matcher matcher = RegexpHelper.compile(pattern, parameters).matcher(subject);
        if (!RegexpHelper.findOccurrence(matcher, start, occurrence)) {
            return 0L;
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
        return (long) (CodePointText.characterIndex(subject, option == 1 ? spanEnd : spanStart) + 1);
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
