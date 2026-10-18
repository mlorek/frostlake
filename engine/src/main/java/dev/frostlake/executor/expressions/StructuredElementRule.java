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

package dev.frostlake.executor.expressions;

import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.MapType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.StructuredArrayType;
import dev.frostlake.types.StructuredTypes;

import java.util.List;

/**
 * The element ARRAY_APPEND, ARRAY_PREPEND and ARRAY_INSERT add to a structured ARRAY takes the array's element type
 * (live-verified): it is cast to it as the call reads it, and the call answers the array's structured type.
 *
 * <ul>
 *   <li>A static type that does not bind to the element type is refused while the statement compiles. Examples are a
 *       DATE, a BOOLEAN or an ARRAY into an {@code ARRAY(NUMBER)}, refused as {@code Invalid argument types for
 *       function 'ARRAY_APPEND': (ARRAY(NUMBER(10,2)), DATE)} at the call. The pairs are a user-defined function's
 *       parameter pairs (see {@link UdfArgumentTypes}).</li>
 *   <li>Any other value is cast as it arrives. A NUMBER element takes the array's scale but not its precision:
 *       {@code 2.7} into {@code ARRAY(INT)} is 3, {@code 1.25} into {@code ARRAY(NUMBER(3,1))} is 1.3, and
 *       {@code 1000} into {@code ARRAY(NUMBER(3,0))} stays 1000. {@code '2.5'} into {@code ARRAY(DOUBLE)} is 2.5,
 *       {@code 5} into {@code ARRAY(VARCHAR)} is {@code '5'}, and a text that reads as no number is refused as the
 *       cast refuses it ({@code Numeric value 'zz' is not recognized}).</li>
 *   <li>A NULL is added as it is added to any array, as {@code undefined}.</li>
 *   <li>An element type that is itself structured, an {@code ARRAY(ARRAY(INT))}'s or an {@code ARRAY(OBJECT(a INT))}'s,
 *       takes only a structured value of its own kind, converted as a cast converts it: {@code [2.6]::ARRAY(DOUBLE)}
 *       is added as {@code [3]}. A semi-structured {@code ['zz']} or {@code {'a':'zz'}}, a VARIANT or a scalar is
 *       refused while the statement compiles, in the same sentence.</li>
 * </ul>
 */
final class StructuredElementRule {

    private StructuredElementRule() {
    }

    /**
     * The argument a call adds as an element: the second of ARRAY_APPEND and ARRAY_PREPEND, the third of ARRAY_INSERT.
     *
     * @param funcName the call's name, upper-cased
     * @return the argument's position, or -1 for any other call
     */
    static int elementPosition(final String funcName) {
        if ("ARRAY_APPEND".equals(funcName) || "ARRAY_PREPEND".equals(funcName)) {
            return 1;
        }
        return "ARRAY_INSERT".equals(funcName) ? 2 : -1;
    }

    /**
     * Refuses an element whose static type does not bind to the structured array's element type.
     *
     * @param funcName the call's name, upper-cased
     * @param call     the call
     * @param context  the evaluator, which types the arguments and places the refusal
     */
    static void require(final String funcName, final FunctionCallExpression call,
                        final ExpressionEvaluatorVisitor context) {
        final int position = elementPosition(funcName);
        final List<Expression> args = call.getArguments();
        if (position < 0 || args.size() <= position || isNull(args.get(position))) {
            return;
        }
        final DataType array = context.inferStaticType(args.get(0));
        if (!(array instanceof StructuredArrayType)) {
            return;
        }
        final DataType element = context.inferStaticType(args.get(position));
        final DataType elementType = ((StructuredArrayType) array).getElementType();
        if (element == null || !(StructuredTypes.isStructured(elementType) ? !sameStructuredKind(elementType, element)
                : UdfArgumentTypes.refuses(elementType, element))) {
            return;
        }
        final StringBuilder types = new StringBuilder();
        for (final Expression arg : args) {
            types.append(types.length() > 0 ? ", " : "").append(context.argumentTypeText(arg));
        }
        throw context.refusedAt(call, "Invalid argument types for function '" + funcName + "': (" + types + ")");
    }

    /**
     * The type an element is cast to on its way into an array of {@code element}s, as a cast names it.
     *
     * @param element the array's element type
     * @return the cast's target, or null when the element is added as it is
     */
    static String castTarget(final DataType element) {
        if (element == null || StructuredTypes.isStructured(element)) {
            return null;
        }
        if (element instanceof NumericType) {
            return NumericType.isApproximate(element) ? "FLOAT"
                : "NUMBER(38," + ((NumericType) element).getScale() + ")";
        }
        if (element instanceof StringType) {
            return "VARCHAR";
        }
        if (element instanceof DateTimeType || element instanceof BooleanType || element instanceof BinaryType) {
            return element.getName();
        }
        return null;
    }

    /**
     * Whether a value's static type is a structured type of the element type's own kind: an ARRAY(…) for an
     * ARRAY(…) element, an OBJECT(…) for an OBJECT(…) one, a MAP for a MAP.
     */
    private static boolean sameStructuredKind(final DataType elementType, final DataType given) {
        if (!StructuredTypes.isStructured(given)) {
            return false;
        }
        if (elementType instanceof StructuredArrayType) {
            return given instanceof StructuredArrayType;
        }
        if (elementType instanceof MapType) {
            return given instanceof MapType;
        }
        return !(given instanceof StructuredArrayType) && !(given instanceof MapType);
    }

    /** Whether an argument is the bare word NULL. */
    private static boolean isNull(final Expression arg) {
        return arg instanceof LiteralExpression && ((LiteralExpression) arg).getValue() == null;
    }
}
