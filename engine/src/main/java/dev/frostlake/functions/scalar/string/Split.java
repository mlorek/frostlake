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
import dev.frostlake.types.ArrayType;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

import java.util.List;
import java.util.regex.Pattern;

/**
 * SPLIT(string, delimiter) — splits {@code string} on the literal {@code delimiter} into an ARRAY of
 * substrings (rendered as a JSON array, the engine's VARIANT representation). Trailing empty parts are kept.
 * An empty delimiter yields the whole string as a single element; either NULL argument yields NULL.
 */
public class Split extends TextArgumentFunction {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public Split() { super("SPLIT", ArrayType.ARRAY); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null) {
            return null;
        }
        final String str = args.get(0).toString();
        final String delim = args.get(1).toString();
        final ArrayNode array = MAPPER.createArrayNode();
        if (delim.isEmpty()) {
            array.add(str);
        } else {
            for (final String part : str.split(Pattern.quote(delim), -1)) {
                array.add(part);
            }
        }
        return array.toString();
    }

    @Override public int getMinArgCount() { return 2; }
    @Override public int getMaxArgCount() { return 2; }
}
