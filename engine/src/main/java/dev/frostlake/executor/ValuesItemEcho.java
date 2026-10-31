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

package dev.frostlake.executor;

import dev.frostlake.executor.expressions.CallEcho;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.ParsedEchoPrinter;
import dev.frostlake.types.DataType;
import dev.frostlake.types.VariantType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * How a refused VALUES item spells the calls the plan holds in another form (live-verified): a conversion to an
 * ARRAY is the cast it is, {@code TO_ARRAY(1)} as {@code CAST(1 AS ARRAY)}; a text made of a semi-structured value
 * is a cast to the widest text, {@code TO_VARCHAR(PARSE_JSON('1'))} as {@code CAST(PARSE_JSON('1') AS
 * VARCHAR(134217728))}; and a call that reads a VARIANT converts an argument of any other type to one,
 * {@code TYPEOF([1])} as {@code TYPEOF(CAST(ARRAY_CONSTRUCT(1) AS VARIANT))} and {@code TO_JSON(1)} as
 * {@code TO_JSON(CAST(1 AS VARIANT))}. What a structured element converts is spelled as {@link StructuredElementEcho}
 * spells it.
 */
final class ValuesItemEcho implements CallEcho {

    /** The calls whose one argument is a VARIANT, any other type converted to one. */
    private static final Set<String> VARIANT_READERS = new HashSet<>(Arrays.asList("TYPEOF", "TO_JSON",
        "STRIP_NULL_VALUE"));

    private final StructuredElementEcho structured = new StructuredElementEcho();

    private final ExpressionEvaluator typer;

    ValuesItemEcho(final ExpressionEvaluator typer) {
        this.typer = typer;
    }

    @Override
    public String spell(final FunctionCallExpression call, final ParsedEchoPrinter printer) {
        final String spelled = structured.spell(call, printer);
        if (spelled != null || call.getFunctionName() == null || call.getArguments().size() != 1
                || call.getNameParts() != null && call.getNameParts().size() > 1) {
            return spelled;
        }
        final String name = call.getFunctionName().toUpperCase(Locale.ROOT);
        final Expression argument = call.getArguments().get(0);
        if ("TO_ARRAY".equals(name)) {
            return "CAST(" + printer.printed(argument) + " AS ARRAY)";
        }
        if (("TO_VARCHAR".equals(name) || "TO_CHAR".equals(name))
                && ValuesSemiStructuredParts.isSemiStructured(typeOf(argument))) {
            return "CAST(" + printer.printed(argument) + " AS VARCHAR(134217728))";
        }
        if (VARIANT_READERS.contains(name) && !(argument instanceof LiteralExpression
                && ((LiteralExpression) argument).getValue() == null) && !(typeOf(argument) instanceof VariantType)) {
            return name + "(CAST(" + printer.printed(argument) + " AS VARIANT))";
        }
        return null;
    }

    private DataType typeOf(final Expression expression) {
        try {
            return typer.inferStaticType(expression);
        } catch (final RuntimeException untyped) {
            return null;
        }
    }
}
