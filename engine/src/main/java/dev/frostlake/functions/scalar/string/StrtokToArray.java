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
import dev.frostlake.values.VariantValue;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

import java.util.List;
import java.util.regex.Pattern;

/**
 * STRTOK_TO_ARRAY(string [, delimiters]) — tokenizes {@code string} on any character in {@code delimiters}
 * (each character is its own delimiter; the default is a single space) and returns a VARIANT ARRAY of the
 * non-empty tokens. Consecutive delimiters produce no empty elements. When tokenization yields nothing the
 * result is an empty array; a NULL string or NULL delimiter argument yields NULL.
 */
public class StrtokToArray extends TextArgumentFunction {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public StrtokToArray() { super("STRTOK_TO_ARRAY", ArrayType.ARRAY); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        if (args.size() > 1 && args.get(1) == null) return null;
        final String str = args.get(0).toString();
        final String delimiters = args.size() > 1 ? args.get(1).toString() : " ";
        final ArrayNode array = MAPPER.createArrayNode();
        if (delimiters.isEmpty()) {
            if (!str.isEmpty()) array.add(str);
            return VariantValue.ofNode(array);
        }
        final String regex = "[" + Pattern.quote(delimiters) + "]+";
        for (final String token : str.split(regex, -1)) {
            if (!token.isEmpty()) array.add(token);
        }
        return VariantValue.ofNode(array);
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 2; }
}
