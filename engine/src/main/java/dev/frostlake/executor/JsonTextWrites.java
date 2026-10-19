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

import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.StructuredArrayType;
import dev.frostlake.types.StructuredObjectType;
import dev.frostlake.types.VariantType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A text an UPDATE's SET or a MERGE's branch writes to a VARIANT, ARRAY or OBJECT column. One written of constants
 * alone — a literal, a call over literals, a cast, a block's bind, a session variable — is compiled, where a text
 * that reads a column is refused as the column's type mismatch, and is read as JSON when the row is written: the
 * document's value is stored whatever the column's kind ({@code '1'} is the number 1 in an ARRAY column too), an
 * empty text is SQL NULL, {@code 'null'} the JSON null, and a text that is no JSON refuses the write in the reader's
 * words, {@code Error parsing JSON: unknown keyword "abc", pos 4} (live-verified).
 */
final class JsonTextWrites {

    private JsonTextWrites() {
    }

    /**
     * Whether a value written to a column is a text read as JSON.
     *
     * @param column    the column written
     * @param value     the value, parsed
     * @param valueType the value's static type
     * @param functions the registry that tells a scalar call from an aggregate
     * @return true for a text written of constants alone to a VARIANT, ARRAY or OBJECT column
     */
    static boolean readAsJson(final TableColumn column, final Expression value, final DataType valueType,
                              final FunctionRegistry functions) {
        return column != null && semiStructured(column.getDataType()) && valueType instanceof StringType
            && ConstantHaving.isConstant(value, functions, true);
    }

    /**
     * What a text read as JSON stores: the document's value, SQL NULL for a NULL or an empty text.
     *
     * @param text      the value computed for the column
     * @param functions the registry that holds the JSON reader
     * @return the value to store
     */
    static Object stored(final Object text, final FunctionRegistry functions) {
        if (!(text instanceof String)) {
            return text;
        }
        final List<Object> argument = new ArrayList<>();
        argument.add(text);
        return functions.getFunction("PARSE_JSON").evaluate(argument);
    }

    /**
     * {@link #stored}, a text that is no JSON failing the write on its column: {@code DML operation to table TV failed
     * on column V with error: Error parsing JSON: …}, as a MERGE's and an UPDATE … FROM's write reports it, where
     * their other failures to compute a value stand bare (live-verified).
     *
     * @param text      the value computed for the column
     * @param functions the registry that holds the JSON reader
     * @param table     the table as the statement names it
     * @param column    the column's name
     * @return the value to store
     */
    static Object storedOnColumn(final Object text, final FunctionRegistry functions, final String table,
                                 final String column) {
        try {
            return stored(text, functions);
        } catch (final RuntimeException notJson) {
            throw DmlWriteTarget.failedOnColumn(table, column, notJson);
        }
    }

    /**
     * The columns of a row a write keeps as they stand, with no conversion to their type: one a text read as JSON was
     * stored in, and an ARRAY column the statement does not write, which may hold such a document.
     *
     * @param table      the table written
     * @param readAsJson one flag per column, set where the statement stored a text read as JSON; null when none
     * @param written    the places of the columns the statement writes
     * @return one flag per column, or null when none is kept
     */
    static boolean[] keptAsWritten(final Table table, final boolean[] readAsJson, final Collection<Integer> written) {
        boolean[] kept = null;
        final List<TableColumn> columns = table.getColumns();
        for (int i = 0; i < columns.size(); i++) {
            final DataType type = columns.get(i).getDataType();
            final boolean json = readAsJson != null && i < readAsJson.length && readAsJson[i];
            final boolean untouchedArray = type instanceof ArrayType && !(type instanceof StructuredArrayType)
                && !written.contains(Integer.valueOf(i));
            if (json || untouchedArray) {
                kept = kept != null ? kept : new boolean[columns.size()];
                kept[i] = true;
            }
        }
        return kept;
    }

    /** Whether a column is VARIANT, ARRAY or OBJECT, and not a structured type. */
    static boolean semiStructured(final DataType type) {
        return type instanceof VariantType
            || type instanceof ArrayType && !(type instanceof StructuredArrayType)
            || type instanceof ObjectType && !(type instanceof StructuredObjectType);
    }
}
