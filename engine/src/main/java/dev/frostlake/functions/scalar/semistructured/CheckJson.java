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
import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.functions.scalar.JsonTypeHelper;
import dev.frostlake.types.StringType;

import java.util.List;

/**
 * CHECK_JSON(str) — NULL when PARSE_JSON reads the text, otherwise the fault PARSE_JSON would name. It reads
 * with PARSE_JSON's own leniency: single quotes, a leading zero or plus, a trailing point or comma, an array
 * hole, NaN and Infinity, an invalid escape and an unquoted key are all valid (live-verified).
 */
public class CheckJson extends BuiltInFunction {

    public CheckJson() { super("CHECK_JSON", StringType.VARCHAR); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        final String original = args.get(0).toString();
        final String input = original.trim();
        // The JSON null, an empty document and a lone point read on PARSE_JSON, so they are valid here.
        if (input.isEmpty() || input.equalsIgnoreCase("null") || input.equals(".")) {
            return null;
        }
        // The fault is positioned against the ORIGINAL text, leading whitespace included, as PARSE_JSON
        // reports it — trimming for the parse must not move the number. The sentence is PARSE_JSON's without
        // its "Error parsing JSON: " prefix: CHECK_JSON('cdefg') is `unknown keyword "cdefg", pos 6`.
        if (JsonTypeHelper.parseLenient(input) != null) {
            return JsonFaultReader.lineBreakFault(original);
        }
        final String fault = JsonFaultReader.faultOf(original);
        return fault != null ? fault : "Invalid JSON: " + original;
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }

    /**
     * A predicate as CHECK_JSON's first argument is refused by the argument types, where a BOOLEAN
     * value is read as text (live-verified).
     */
    @Override
    public SemiStructuredRejection predicateRejection(final int position) {
        return position == 0 ? SemiStructuredRejection.ARGUMENT_TYPES : SemiStructuredRejection.NONE;
    }
}
