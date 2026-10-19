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
import dev.frostlake.executor.expressions.ExpressionEvaluatorVisitor;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.ParsedEchoPrinter;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;

import java.math.BigDecimal;
import java.util.List;

/**
 * A call of a function a handler answers — Java, JavaScript, Python, Scala — as the plan names it:
 * {@code EXTENSION_FUNC_V} over its arguments, a whole number given for a FLOAT parameter written as the FLOAT
 * it becomes, {@code jdbl(1)} reading {@code EXTENSION_FUNC_V(1.0)}, and a NULL as {@code null} (live-verified).
 */
final class HandlerCallEcho implements CallEcho {

    /** The plan's name for a call a handler answers. */
    private static final String PLAN_NAME = "EXTENSION_FUNC_V";

    private final ExpressionEvaluatorVisitor scope;
    /** Whether the print in progress has spelled a handler's call. */
    private boolean spelled;

    /**
     * @param scope the visitor that resolves a call to the function it names
     */
    HandlerCallEcho(final ExpressionEvaluatorVisitor scope) {
        this.scope = scope;
    }

    /**
     * The expression as the plan prints it when a handler's function is called anywhere in it, or null.
     *
     * @param node the expression
     * @return its text, or null when it calls no handler
     */
    String echoedIfHandled(final Expression node) {
        spelled = false;
        final String text = ParsedEchoPrinter.print(node, this);
        return spelled ? text : null;
    }

    @Override
    public String spell(final FunctionCallExpression call, final ParsedEchoPrinter printer) {
        final Function handled = scope.handlerFunctionCalled(call);
        if (handled == null) {
            return null;
        }
        spelled = true;
        final List<Parameter> parameters = handled.getParameters();
        final List<Expression> arguments = call.getArguments();
        final StringBuilder text = new StringBuilder(PLAN_NAME).append('(');
        for (int i = 0; i < arguments.size(); i++) {
            final DataType parameter = i < parameters.size() ? parameters.get(i).getDataType() : null;
            text.append(i > 0 ? ", " : "").append(argument(arguments.get(i), parameter, printer));
        }
        return text.append(')').toString();
    }

    /** An argument as the call receives it: a whole number for a FLOAT parameter as that FLOAT. */
    private static String argument(final Expression argument, final DataType parameter,
                                   final ParsedEchoPrinter printer) {
        if (argument instanceof LiteralExpression && parameter != null && NumericType.isApproximate(parameter)) {
            final Object value = ((LiteralExpression) argument).getValue();
            if (value instanceof Number && !(value instanceof Double) && !(value instanceof Float)) {
                final BigDecimal number = new BigDecimal(value.toString());
                if (number.scale() <= 0) {
                    return number.toBigInteger() + ".0";
                }
            }
        }
        return printer.printed(argument);
    }
}
