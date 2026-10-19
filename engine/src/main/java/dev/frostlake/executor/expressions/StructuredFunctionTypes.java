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

import dev.frostlake.executor.commands.DataTypeParser;
import dev.frostlake.types.DataType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.StructuredArrayType;
import dev.frostlake.types.StructuredField;
import dev.frostlake.types.StructuredLeafWidths;
import dev.frostlake.types.StructuredObjectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The structured type the array and object functions answer over a structured value, where they would otherwise
 * answer a plain ARRAY or OBJECT (live-verified):
 *
 * <pre>
 *   ARRAY_CAT(ARRAY(NUMBER), ARRAY(NUMBER(2,1)))           ARRAY(NUMBER(38,1))    the elements' common type
 *   ARRAY_APPEND, ARRAY_PREPEND, ARRAY_INSERT,             the first array's type
 *   ARRAY_SLICE, ARRAY_COMPACT, ARRAY_EXCEPT
 *   OBJECT_DELETE(o, 'city')                               o without the field
 *   OBJECT_INSERT(o, 'zip', v, FALSE)                      o and the new field, typed as v
 *   OBJECT_PICK(o, 'state', 'city')                        the picked fields, in the order picked
 *   OBJECT_KEYS(o)                                         ARRAY(VARCHAR)
 * </pre>
 *
 * <p>The account also marks the elements of an ARRAY_COMPACT and of an OBJECT_KEYS NOT NULL, which a structured
 * ARRAY type here cannot carry yet.
 */
final class StructuredFunctionTypes {

    private StructuredFunctionTypes() {
    }

    /**
     * The structured type a call answers, or null for any other call and for a call over a plain value.
     *
     * @param inferencer the inferencer of the arguments' types
     * @param funcName   the function's name, upper-cased
     * @param args       the call's arguments
     * @return the type, or null
     */
    static DataType of(final TypeInferencer inferencer, final String funcName, final List<Expression> args) {
        if (args.isEmpty()) {
            return null;
        }
        final DataType first = inferencer.infer(args.get(0));
        switch (funcName) {
            case "ARRAY_CAT":
                return args.size() == 2 ? concatenated(first, inferencer.infer(args.get(1))) : null;
            case "ARRAY_APPEND":
            case "ARRAY_PREPEND":
            case "ARRAY_INSERT":
            case "ARRAY_SLICE":
            case "ARRAY_EXCEPT":
                return first instanceof StructuredArrayType ? first : null;
            case "ARRAY_COMPACT":
                // What it keeps is never NULL: ARRAY(NUMBER(38,0) NOT NULL) over an ARRAY(INT) (live-verified).
                return first instanceof StructuredArrayType
                    ? new StructuredArrayType(((StructuredArrayType) first).getElementType(), true) : null;
            case "OBJECT_DELETE":
                return first instanceof StructuredObjectType ? deleted((StructuredObjectType) first, args) : null;
            case "OBJECT_PICK":
                return first instanceof StructuredObjectType ? picked((StructuredObjectType) first, args) : null;
            case "OBJECT_INSERT":
                return first instanceof StructuredObjectType && args.size() >= 3
                    ? inserted(inferencer, (StructuredObjectType) first, args) : null;
            case "OBJECT_KEYS":
                // A key is never NULL: ARRAY(VARCHAR NOT NULL) (live-verified).
                return first instanceof StructuredObjectType
                    ? new StructuredArrayType(new StringType("VARCHAR", DataTypeParser.CAST_STRING_DEFAULT, true),
                        true)
                    : null;
            default:
                return null;
        }
    }

    private static DataType concatenated(final DataType first, final DataType second) {
        return StructuredConcatenation.unified(first, second);
    }

    private static DataType deleted(final StructuredObjectType object, final List<Expression> args) {
        final List<String> removed = new ArrayList<>();
        for (int i = 1; i < args.size(); i++) {
            final Object key = TypeInferencer.constantKey(args.get(i));
            if (!(key instanceof String)) {
                return null;
            }
            removed.add((String) key);
        }
        final List<StructuredField> kept = new ArrayList<>();
        for (final StructuredField field : object.getFields()) {
            if (!removed.contains(field.getName())) {
                kept.add(field);
            }
        }
        return new StructuredObjectType(kept);
    }

    private static DataType picked(final StructuredObjectType object, final List<Expression> args) {
        final List<StructuredField> chosen = new ArrayList<>();
        for (int i = 1; i < args.size(); i++) {
            final Object key = TypeInferencer.constantKey(args.get(i));
            final StructuredField field = key instanceof String ? object.field((String) key) : null;
            if (field == null) {
                return null;
            }
            chosen.add(field);
        }
        return new StructuredObjectType(chosen);
    }

    private static DataType inserted(final TypeInferencer inferencer, final StructuredObjectType object,
                                     final List<Expression> args) {
        final Object key = TypeInferencer.constantKey(args.get(1));
        if (!(key instanceof String)) {
            return null;
        }
        final DataType valueType = inferencer.infer(args.get(2));
        if (valueType == null) {
            return null;
        }
        // A value written as a constant is never NULL, which its field says, and a string written with no width of
        // its own is an unsized leaf (live: zip_code VARCHAR NOT NULL for 94402::VARCHAR).
        final DataType leaf = valueType instanceof StringType
            && ((StringType) valueType).getMaxLength() == DataTypeParser.CAST_STRING_DEFAULT
            ? new StringType("VARCHAR", DataTypeParser.CAST_STRING_DEFAULT, true)
            : StructuredLeafWidths.unsizedCastLeaf(valueType, DataTypeParser.CAST_STRING_DEFAULT);
        final StructuredField added = new StructuredField((String) key, leaf, isNonNullConstant(args.get(2)));
        final List<StructuredField> fields = new ArrayList<>();
        boolean replaced = false;
        for (final StructuredField field : object.getFields()) {
            if (field.getName().equals(key)) {
                fields.add(added);
                replaced = true;
            } else {
                fields.add(field);
            }
        }
        if (!replaced) {
            fields.add(added);
        }
        return new StructuredObjectType(fields);
    }

    /** Whether a value is written as a constant that is not NULL: a literal, or a cast of one. */
    private static boolean isNonNullConstant(final Expression value) {
        if (value instanceof CastExpression) {
            return isNonNullConstant(((CastExpression) value).getExpression());
        }
        return value instanceof LiteralExpression && ((LiteralExpression) value).getValue() != null
            && !"NULL".equals(String.valueOf(((LiteralExpression) value).getValue()).toUpperCase(Locale.ROOT));
    }
}
