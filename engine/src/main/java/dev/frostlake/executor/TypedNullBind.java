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

import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringType;
import dev.frostlake.types.StructuredArrayType;
import dev.frostlake.types.StructuredObjectType;
import dev.frostlake.types.UuidType;
import dev.frostlake.types.VariantType;

/**
 * The text a scripting variable holding NULL takes when a statement's text binds it: NULL cast to the variable's
 * declared type, where live types the bound NULL as the declaration does — a RESULTSET's query {@code SELECT :s}
 * over a NUMBER(10,2) holding NULL declares a NUMBER(10,2) column and SYSTEM$TYPEOF reads {@code NUMBER(10,2)[SB1]},
 * over a VARCHAR(10) a VARCHAR(10) one, over a DATE a DATE one — and the bare NULL, which has no type at all, where
 * live binds the NULL as none ({@link NullBindSlot}, all live-verified):
 *
 * <ul>
 *   <li>a whole item of a VALUES row, and an argument of a date or time function, whatever the declaration;</li>
 *   <li>a DATE a number or an INTERVAL is added to or taken from, and a time or a timestamp an INTERVAL is;</li>
 *   <li>a text anywhere it may meet a number — only where it is read as text does it keep its type.</li>
 * </ul>
 *
 * <p>A declaration of another family — a geography, a vector, a structured type — keeps the bare NULL, and so does a
 * text declared without a length: its declaration is recorded at the described maximum, where live binds such a
 * variable at the width nothing bounds, VARCHAR(134217728).
 */
final class TypedNullBind {

    private TypedNullBind() {
    }

    /**
     * The NULL a variable of {@code declared} type binds as where it stands.
     *
     * @param declared the variable's declared type, or null when it has none
     * @param slot     where the bind stands
     * @return {@code NULL::<type>}, or a bare {@code NULL}
     */
    static String spell(final DataType declared, final NullBindSlot slot) {
        if (!castable(declared) || !keepsType(declared, slot)) {
            return "NULL";
        }
        // A binary is spelled at the width SYSTEM$TYPEOF names: a BINARY declared without one at the 64MB maximum.
        return "NULL::" + (declared instanceof BinaryType ? ((BinaryType) declared).typeofText()
            : SqlTypeNames.canonical(declared));
    }

    /** Whether live binds the NULL as the declared type where it stands, rather than as no type. */
    private static boolean keepsType(final DataType declared, final NullBindSlot slot) {
        if (slot.isValuesItem() || slot.isDateTimeArgument()) {
            return false;
        }
        if (declared instanceof StringType) {
            return slot.readsAsText();
        }
        if (declared instanceof DateTimeType) {
            return !slot.foldsTemporalArithmetic("DATE".equals(declared.getName()));
        }
        return true;
    }

    private static boolean castable(final DataType declared) {
        if (declared instanceof StringType) {
            return !(declared instanceof UuidType)
                && ((StringType) declared).getMaxLength() != SqlTypeNames.DESCRIBED_STRING_MAXIMUM;
        }
        if (declared instanceof ArrayType) {
            return !(declared instanceof StructuredArrayType);
        }
        if (declared instanceof ObjectType) {
            return !(declared instanceof StructuredObjectType);
        }
        return declared instanceof NumericType || declared instanceof BooleanType || declared instanceof DateTimeType
            || declared instanceof BinaryType || declared instanceof VariantType;
    }
}
