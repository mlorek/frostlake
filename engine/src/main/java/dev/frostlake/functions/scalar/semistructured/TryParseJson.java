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

package dev.frostlake.functions.scalar.semistructured;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.scalar.JsonTypeHelper;
import dev.frostlake.types.VariantType;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.JsonNode;

import java.util.List;

public class TryParseJson extends BuiltInFunction {

    public TryParseJson() { super("TRY_PARSE_JSON", VariantType.VARIANT); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        // The argument is TEXT by the time it arrives, a VARIANT one having been coerced to VARCHAR at
        // the call boundary — see ExpressionEvaluatorVisitor.coerceVariantArgumentToText. A quoted
        // string is a JSON STRING here, whatever its content looks like.
        final String input = args.get(0).toString().trim();
        // 'null' parses to the VARIANT JSON null, exactly as PARSE_JSON does — live:
        // TYPEOF(TRY_PARSE_JSON('null')) = 'NULL_VALUE', not SQL NULL.
        if (input.equalsIgnoreCase("null")) return VariantValue.of("null");
        // Parse leniently (Snowflake tolerates \' and invalid backslash escapes such as a regex \d);
        // TRY_ variant returns NULL when it still cannot be parsed.
        final JsonNode node = JsonTypeHelper.parseLenient(input);
        // A raw line break inside a string does not read, as PARSE_JSON refuses it.
        return node == null || JsonFaultReader.lineBreakFault(input) != null ? null : VariantValue.ofNode(node);
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }
}
