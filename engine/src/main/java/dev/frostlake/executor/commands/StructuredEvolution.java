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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.types.DataType;
import dev.frostlake.types.IcebergNumericType;
import dev.frostlake.types.IcebergTypes;
import dev.frostlake.types.MapType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringType;
import dev.frostlake.types.StructuredArrayType;
import dev.frostlake.types.StructuredField;
import dev.frostlake.types.StructuredObjectType;

import java.util.List;

/**
 * ALTER TABLE or ALTER ICEBERG TABLE … ALTER COLUMN … SET DATA TYPE from a structured type to another of its kind,
 * judged member by member, each named by its path, {@code O.a}, {@code O.element}, {@code O.key} and {@code O.value}
 * (live-verified). An OBJECT may add keys, drop them and order them anew, its fields matched by name, and a field may
 * widen its type; RENAME FIELDS renames the keys by their place instead, nested ones too, and may neither drop a key
 * nor add one ("RENAME FIELDS DDL on structured field O should not drop fields") nor change a member's type: "RENAME
 * FIELDS DDL on structured type column should not change the type of field O.a". An ARRAY's element and a MAP's key and
 * value evolve as a field does, and no member may become NOT NULL: "DDL operation failed because it would result in a
 * non-nullable structured type field 'O.a' contained within a nullable object". On an Iceberg table a member widens as
 * the format promotes a column - an int to a long, a float to a double, a decimal to a wider one of its scale - any
 * other change refused in the format's names, {@code cannot change column O.a from type int to string}. On another
 * table a NUMBER widens its precision at its scale and a VARCHAR its length: a narrower precision is "Unsupported
 * feature 'Decreasing the precision of structured field O.value is currently not supported'.", a scale changed and a
 * VARCHAR shortened say why ("… because changing the scale of a number is not supported."), and any other change is
 * refused in a table's names.
 */
final class StructuredEvolution {

    private StructuredEvolution() {
    }

    /**
     * Whether a change goes from a structured type to another of its kind: an OBJECT to an OBJECT, an ARRAY to an
     * ARRAY, a MAP to a MAP.
     *
     * @param from the column's type
     * @param to   the type the change writes
     * @return true for such a change
     */
    static boolean applies(final DataType from, final DataType to) {
        return from instanceof StructuredObjectType && to instanceof StructuredObjectType
            || from instanceof StructuredArrayType && to instanceof StructuredArrayType
            || from instanceof MapType && to instanceof MapType;
    }

    /**
     * Refuses an evolution the account refuses.
     *
     * @param column       the column, as the refusal names it
     * @param from         the column's type, in the format's terms on an Iceberg table
     * @param to           the type the change writes, alike
     * @param renameFields whether the change renames the fields
     * @param iceberg      whether the types are an Iceberg table's
     */
    static void require(final String column, final DataType from, final DataType to, final boolean renameFields,
                        final boolean iceberg) {
        member(column, from, to, renameFields, iceberg);
    }

    private static void member(final String path, final DataType from, final DataType to, final boolean rename,
                               final boolean iceberg) {
        if (from instanceof StructuredObjectType && to instanceof StructuredObjectType) {
            object(path, (StructuredObjectType) from, (StructuredObjectType) to, rename, iceberg);
        } else if (from instanceof StructuredArrayType && to instanceof StructuredArrayType) {
            final StructuredArrayType held = (StructuredArrayType) from;
            final StructuredArrayType written = (StructuredArrayType) to;
            if (written.isElementNotNull() && !held.isElementNotNull()) {
                throw nonNullable(path + ".element", "an array or map");
            }
            member(path + ".element", held.getElementType(), written.getElementType(), rename, iceberg);
        } else if (from instanceof MapType && to instanceof MapType) {
            final MapType held = (MapType) from;
            final MapType written = (MapType) to;
            member(path + ".key", held.getKeyType(), written.getKeyType(), rename, iceberg);
            if (written.isValueNotNull() && !held.isValueNotNull()) {
                throw nonNullable(path + ".value", "an array or map");
            }
            member(path + ".value", held.getValueType(), written.getValueType(), rename, iceberg);
        } else if (rename) {
            if (!sameType(from, to, iceberg)) {
                throw changedOnRename(path);
            }
        } else {
            leaf(path, from, to, iceberg);
        }
    }

    private static void object(final String path, final StructuredObjectType from, final StructuredObjectType to,
                               final boolean rename, final boolean iceberg) {
        if (rename) {
            // The keys are renamed by their place: as many as there were, each of the type it was.
            final List<StructuredField> held = from.getFields();
            final List<StructuredField> written = to.getFields();
            if (written.size() != held.size()) {
                throw new RuntimeException(SqlCompilationError.inline("RENAME FIELDS DDL on structured field " + path
                    + " should not " + (written.size() < held.size() ? "drop" : "add") + " fields"));
            }
            for (int i = 0; i < held.size(); i++) {
                final String at = path + "." + held.get(i).getName();
                if (held.get(i).isNotNull() != written.get(i).isNotNull()) {
                    throw changedOnRename(at);
                }
                member(at, held.get(i).getDataType(), written.get(i).getDataType(), true, iceberg);
            }
            return;
        }
        for (final StructuredField field : to.getFields()) {
            final StructuredField old = from.field(field.getName());
            final String at = path + "." + field.getName();
            if (field.isNotNull() && (old == null || !old.isNotNull())) {
                throw nonNullable(at, "a nullable object");
            }
            if (old != null) {
                member(at, old.getDataType(), field.getDataType(), false, iceberg);
            }
        }
    }

    /** A RENAME FIELDS that changes a member's type or its kind. */
    static RuntimeException changedOnRename(final String path) {
        return new RuntimeException(SqlCompilationError.inline("RENAME FIELDS DDL on structured type column should not"
            + " change the type of field " + path + " "));
    }

    private static void leaf(final String path, final DataType from, final DataType to, final boolean iceberg) {
        if (iceberg) {
            final String held = formatName(from);
            final String written = formatName(to);
            if (held.equals(written) || "int".equals(held) && "long".equals(written)
                    || "float".equals(held) && "double".equals(written) || widensDecimal(from, to)) {
                return;
            }
            throw new RuntimeException(SqlCompilationError.trailing("cannot change column " + path + " from type "
                + held + " to " + written));
        }
        final String held = SqlTypeNames.canonical(from);
        final String written = SqlTypeNames.canonical(to);
        if (held.equals(written)) {
            return;
        }
        String because = "";
        if (exact(from) && exact(to)) {
            if (((NumericType) from).getScale() != ((NumericType) to).getScale()) {
                because = " because changing the scale of a number is not supported.";
            } else if (((NumericType) to).getPrecision() >= ((NumericType) from).getPrecision()) {
                return;
            } else {
                throw new RuntimeException("Unsupported feature 'Decreasing the precision of structured field " + path
                    + " is currently not supported'.");
            }
        } else if (from instanceof StringType && to instanceof StringType) {
            if (((StringType) to).getMaxLength() >= ((StringType) from).getMaxLength()) {
                return;
            }
            because = " because reducing the byte-length of a varchar is not supported.";
        }
        throw new RuntimeException(SqlCompilationError.trailing("cannot change column " + path + " from type " + held
            + " to " + written + because));
    }

    /** Whether two member types are one, a structured one's members included. */
    private static boolean sameType(final DataType held, final DataType written, final boolean iceberg) {
        if (IcebergTypes.isStructured(held) || IcebergTypes.isStructured(written)) {
            return SqlTypeNames.canonical(IcebergTypes.tableType(held))
                .equals(SqlTypeNames.canonical(IcebergTypes.tableType(written)))
                && (!iceberg || IcebergTypes.ddlName(held).equals(IcebergTypes.ddlName(written)));
        }
        return iceberg ? formatName(held).equals(formatName(written))
            : SqlTypeNames.canonical(held).equals(SqlTypeNames.canonical(written));
    }

    private static String formatName(final DataType type) {
        final String name = IcebergTypes.formatName(type);
        return name != null ? name : SqlTypeNames.canonical(type);
    }

    private static boolean exact(final DataType type) {
        return type instanceof NumericType && !NumericType.isApproximate(type) && !NumericType.isDecfloat(type);
    }

    /** Whether a member's change takes a decimal to one of its scale and no narrower. */
    private static boolean widensDecimal(final DataType from, final DataType to) {
        return exact(from) && exact(to) && !(from instanceof IcebergNumericType) && !(to instanceof IcebergNumericType)
            && ((NumericType) from).getScale() == ((NumericType) to).getScale()
            && ((NumericType) to).getPrecision() >= ((NumericType) from).getPrecision();
    }

    private static RuntimeException nonNullable(final String path, final String within) {
        return new RuntimeException(SqlCompilationError.inline("DDL operation failed because it would result in a"
            + " non-nullable structured type field '" + path + "' contained within " + within));
    }
}
