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
import dev.frostlake.types.VariantType;

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
public class RegexpSubstrAll extends BuiltInFunction {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public RegexpSubstrAll() { super("REGEXP_SUBSTR_ALL", VariantType.VARIANT); }

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

        final ArrayNode array = MAPPER.createArrayNode();
        final int start = Math.max(0, position - 1);
        if (start > subject.length()) {
            return array.toString();
        }
        final boolean extract = groupNum != null || RegexpHelper.hasExtract(parameters);
        final Matcher matcher = RegexpHelper.compile(pattern, parameters).matcher(subject);
        if (matcher.find(start)) {
            int index = 1;
            do {
                if (index >= occurrence) {
                    final String value;
                    if (extract && matcher.groupCount() >= 1) {
                        final int group = groupNum != null ? groupNum : 1;
                        value = group <= matcher.groupCount() ? matcher.group(group) : null;
                    } else {
                        value = matcher.group();
                    }
                    if (value == null) {
                        array.addNull();
                    } else {
                        array.add(value);
                    }
                }
                index++;
            } while (matcher.find());
        }
        return array.toString();
    }

    @Override public int getMinArgCount() { return 2; }
    @Override public int getMaxArgCount() { return 6; }
}
