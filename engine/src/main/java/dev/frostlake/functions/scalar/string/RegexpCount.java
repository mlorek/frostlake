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

import java.util.List;
import java.util.regex.Matcher;

/**
 * REGEXP_COUNT(subject, pattern [, position [, parameters]]) — the number of matches of {@code pattern}
 * at or after {@code position} (1-based, default 1). An invalid pattern is an error.
 */
public class RegexpCount extends TextArgumentFunction {
    public RegexpCount() { super("REGEXP_COUNT", IntegerResultWidths.COUNTER); }

    @Override
    public Object evaluate(final List<Object> args) {
        // A NULL in any argument answers NULL (live-verified).
        if (anyNull(args)) {
            return null;
        }
        final String subject = SharedFunctionHelpers.textOf(args.get(0));
        final String pattern = SharedFunctionHelpers.textOf(args.get(1));
        final int position = RegexpHelper.wholeArgument(args, 2, 1);
        final String parameters = RegexpHelper.textArgument(args, 3);
        RegexpHelper.requirePosition(position);
        if (RegexpHelper.matchesNothing(pattern, parameters)) {
            return 0L;
        }
        final int start = RegexpHelper.searchStart(subject, position);
        if (start < 0) {
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

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 4; }

    /** A BINARY is no text here: the account refuses it by the argument types (live-verified). */
    @Override
    public SemiStructuredRejection binaryRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }
}
