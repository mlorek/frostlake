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
import dev.frostlake.types.ArrayType;
import dev.frostlake.values.VariantValue;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

import java.util.List;
import java.util.regex.Matcher;

/**
 * REGEXP_SUBSTR_ALL(subject, pattern [, position [, occurrence [, parameters [, group_num]]]]) — returns a
 * VARIANT ARRAY with an element for every match at or after {@code position} (1-based, default 1), starting
 * from the {@code occurrence}-th match (default 1). With the {@code e} flag or an explicit {@code group_num}
 * (which implies {@code e}), each element is the matching capture group (group 1 by default) rather than the
 * whole match. No matches yields an empty array; any NULL argument yields NULL. REGEXP_EXTRACT_ALL is an
 * exact alias.
 */
public class RegexpSubstrAll extends TextArgumentFunction {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public RegexpSubstrAll() { super("REGEXP_SUBSTR_ALL", ArrayType.ARRAY); }

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

        final ArrayNode array = MAPPER.createArrayNode();
        final int start = RegexpHelper.searchStart(subject, position);
        if (start < 0) {
            return VariantValue.ofNode(array);
        }
        final boolean extract = groupNum != null || RegexpHelper.hasExtract(parameters);
        final Matcher matcher = RegexpHelper.compile(pattern, parameters).matcher(subject);
        if (matcher.find(start)) {
            int index = 1;
            do {
                if (index >= occurrence) {
                    final String value = RegexpHelper.matchedText(matcher, extract, groupNum);
                    if (value == null) {
                        array.addNull();
                    } else {
                        array.add(value);
                    }
                }
                index++;
            } while (matcher.find());
        }
        return VariantValue.ofNode(array);
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 6; }

    /** A BINARY is no text here: the account refuses it by the argument types, under either name (live-verified). */
    @Override
    public SemiStructuredRejection binaryRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }
}
