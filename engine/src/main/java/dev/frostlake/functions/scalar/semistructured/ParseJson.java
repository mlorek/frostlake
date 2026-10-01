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
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.functions.scalar.JsonTypeHelper;
import dev.frostlake.types.VariantType;
import tools.jackson.databind.JsonNode;

import dev.frostlake.values.VariantUndefined;
import dev.frostlake.values.VariantValue;

import java.util.List;

public class ParseJson extends BuiltInFunction {

    public ParseJson() { super("PARSE_JSON", VariantType.VARIANT); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        // The argument is TEXT by the time it arrives: a VARIANT one was coerced to VARCHAR at the call
        // boundary, which is where the declared type is still known — see
        // ExpressionEvaluatorVisitor.coerceVariantArgumentToText. Nothing here may second-guess the
        // content: a quoted string is a JSON STRING, however much its inside looks like JSON.
        final String input = args.get(0).toString().trim();
        // A literal JSON null parses to a JSON null VARIANT — represented as the text "null" so it stays
        // DISTINCT from a SQL NULL (Snowflake: "The JSON null value is distinct from the SQL NULL value").
        if (input.equalsIgnoreCase("null")) return VariantValue.of("null");
        // An EMPTY document is SQL NULL rather than the JSON null: live answers TRUE to IS NULL and SQL NULL
        // to both TYPEOF and IS_NULL_VALUE, where the JSON null answers FALSE, 'NULL_VALUE' and TRUE.
        if (input.isEmpty()) return null;
        // A lone decimal point is the number zero — the degenerate end of the leading-point spelling that
        // reads '.5' as 0.5. No JSON reader accepts it on its own, so it is spelled out for one.
        final String document = input.equals(".") ? "0" : input;
        // Parse leniently (Snowflake tolerates \' and invalid backslash escapes such as a regex \d).
        final JsonNode parsed = JsonTypeHelper.parseLenient(document);
        // A raw line break inside a string is refused, which the lenient reader does not do.
        final String lineBreak = parsed != null ? JsonFaultReader.lineBreakFault(args.get(0).toString()) : null;
        if (lineBreak != null) {
            throw new RuntimeException("Error parsing JSON: " + lineBreak);
        }
        final JsonNode node = parsed;
        // A WHOLE-VALUE `undefined` is SQL NULL — live: PARSE_JSON('undefined') IS NULL is
        // TRUE and its TYPEOF is SQL NULL, while PARSE_JSON('[undefined]') keeps the array element.
        if (VariantUndefined.isUndefined(node)) {
            return null;
        }
        if (node == null) {
            // Snowflake NAMES the fault and where it stands; see JsonFaultReader for the vocabulary.
            // The whole-document echo stands in only where this reader disagrees with the parse — the
            // documents live accepts and Frostlake does not, which are leniency rather than wording.
            // The fault is positioned against the ORIGINAL text, leading whitespace included: live
            // counts what was written, so trimming before the parse must not move the number.
            final String fault = JsonFaultReader.faultOf(args.get(0).toString());
            throw new RuntimeException(fault != null
                ? "Error parsing JSON: " + fault : "Invalid JSON: " + args.get(0));
        }
        return ArrayFunctionHelper.toCanonicalVariant(node);
    }

    /** A VECTOR is refused by its argument type, in every position (live-verified). */
    @Override
    public SemiStructuredRejection vectorRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }

    /**
     * A predicate as PARSE_JSON's first argument is refused by the argument types, where a BOOLEAN
     * value is read as text (live-verified).
     */
    @Override
    public SemiStructuredRejection predicateRejection(final int position) {
        return position == 0 ? SemiStructuredRejection.ARGUMENT_TYPES : SemiStructuredRejection.NONE;
    }
}
