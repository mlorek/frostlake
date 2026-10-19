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

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StructuredArrayType;
import dev.frostlake.types.StructuredField;
import dev.frostlake.types.StructuredObjectType;
import dev.frostlake.types.StructuredTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * ARRAY_CAT over two structured arrays (live-verified). The result's element type unifies the two arrays' element
 * types, leaf by leaf: two numbers the type that holds both - FLOAT when either is approximate, otherwise
 * NUMBER(38, s) of the larger scale - an ARRAY's elements and the fields of OBJECTs that name the same fields in the
 * same order unified in turn, and two leaves of one other kind their common type. Both arrays' elements are converted
 * to it, as a cast converts them: {@code ARRAY_CAT([[1]]::ARRAY(ARRAY(INT)), [[2.6]]::ARRAY(ARRAY(DOUBLE)))} is an
 * ARRAY(ARRAY(FLOAT)) holding 1 and 2.6. Arrays that do not unify - a text beside a number, a nesting of another
 * depth, an OBJECT of other fields - are refused, the second named as the plan prints it, against the first's type:
 * {@code Can not convert parameter 'CAST(ARRAY_CONSTRUCT(ARRAY_CONSTRUCT('zz')) AS ARRAY(ARRAY(VARCHAR(134217728))))'
 * of type [ARRAY(ARRAY(VARCHAR(134217728)))] into expected type [ARRAY(ARRAY(NUMBER(38,0)))]}. A semi-structured
 * ARRAY beside a structured first one is {@code Invalid argument types for function 'ARRAY_CAT':
 * (ARRAY(ARRAY(NUMBER(38,0))), ARRAY)} at the call.
 */
final class StructuredConcatenation {

    private StructuredConcatenation() {
    }

    /**
     * The type ARRAY_CAT of two structured arrays answers.
     *
     * @param first  the first argument's type
     * @param second the second argument's type
     * @return the unified structured array type, or null where the two are not both structured arrays or do not
     *         unify
     */
    static DataType unified(final DataType first, final DataType second) {
        if (!(first instanceof StructuredArrayType) || !(second instanceof StructuredArrayType)) {
            return null;
        }
        return unify(first, second);
    }

    /**
     * Refuses ARRAY_CAT over a structured first array and a second one that cannot join it.
     *
     * @param call    the call
     * @param context the evaluator, which types the arguments and places the refusal
     */
    static void require(final FunctionCallExpression call, final ExpressionEvaluatorVisitor context) {
        final List<Expression> args = call.getArguments();
        if (args.size() != 2) {
            return;
        }
        final DataType first = context.inferStaticType(args.get(0));
        if (!(first instanceof StructuredArrayType)) {
            return;
        }
        final DataType second = context.inferStaticType(args.get(1));
        if (!(second instanceof ArrayType)) {
            return;
        }
        if (!StructuredTypes.isStructured(second)) {
            throw context.refusedAt(call, "Invalid argument types for function 'ARRAY_CAT': ("
                + context.argumentTypeText(args.get(0)) + ", " + context.argumentTypeText(args.get(1)) + ")");
        }
        if (unify(first, second) == null) {
            throw new RuntimeException(SqlCompilationError.of("Can not convert parameter '"
                + context.typeInferencer().parameterName(args.get(1)) + "' of type [" + SqlTypeNames.canonical(second)
                + "] into expected type [" + SqlTypeNames.canonical(first) + "]"));
        }
    }

    /** Two types unified, or null where they do not unify. */
    private static DataType unify(final DataType first, final DataType second) {
        if (first instanceof StructuredArrayType || second instanceof StructuredArrayType) {
            if (!(first instanceof StructuredArrayType) || !(second instanceof StructuredArrayType)) {
                return null;
            }
            final StructuredArrayType one = (StructuredArrayType) first;
            final StructuredArrayType two = (StructuredArrayType) second;
            final DataType element = unify(one.getElementType(), two.getElementType());
            return element == null ? null
                : new StructuredArrayType(element, one.isElementNotNull() && two.isElementNotNull());
        }
        if (first instanceof StructuredObjectType || second instanceof StructuredObjectType) {
            return first instanceof StructuredObjectType && second instanceof StructuredObjectType
                ? unifyFields((StructuredObjectType) first, (StructuredObjectType) second) : null;
        }
        if (StructuredTypes.isStructured(first) || StructuredTypes.isStructured(second)) {
            // A MAP joins a MAP of its own type alone.
            return SqlTypeNames.canonical(first).equals(SqlTypeNames.canonical(second)) ? first : null;
        }
        return unifyLeaves(first, second);
    }

    /** Two OBJECTs naming the same fields in the same order, each field unified; null for any other pair. */
    private static DataType unifyFields(final StructuredObjectType first, final StructuredObjectType second) {
        final List<StructuredField> one = first.getFields();
        final List<StructuredField> two = second.getFields();
        if (one.size() != two.size()) {
            return null;
        }
        final List<StructuredField> fields = new ArrayList<>(one.size());
        for (int i = 0; i < one.size(); i++) {
            if (!one.get(i).getName().equals(two.get(i).getName())) {
                return null;
            }
            final DataType type = unify(one.get(i).getDataType(), two.get(i).getDataType());
            if (type == null) {
                return null;
            }
            fields.add(new StructuredField(one.get(i).getName(), type,
                one.get(i).isNotNull() && two.get(i).isNotNull()));
        }
        return new StructuredObjectType(fields);
    }

    /** Two leaves unified: numbers to the type holding both, two leaves of one other kind to their common type. */
    private static DataType unifyLeaves(final DataType first, final DataType second) {
        if (first == null || second == null) {
            return null;
        }
        if (first instanceof NumericType && second instanceof NumericType && isPlainNumber(first)
                && isPlainNumber(second)) {
            if (NumericType.isApproximate(first) || NumericType.isApproximate(second)) {
                return NumericType.DOUBLE;
            }
            return new NumericType("NUMBER", 38,
                Math.max(((NumericType) first).getScale(), ((NumericType) second).getScale()));
        }
        if (first.getClass() != second.getClass()) {
            return null;
        }
        return first.getCommonType(second);
    }

    /** A NUMBER or a FLOAT, which unify by the rule measured; a DECFLOAT is unified by its common type. */
    private static boolean isPlainNumber(final DataType type) {
        return !"DECFLOAT".equals(type.getName());
    }
}
