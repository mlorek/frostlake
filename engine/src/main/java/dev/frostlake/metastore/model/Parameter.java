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

package dev.frostlake.metastore.model;

import dev.frostlake.types.BinaryType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.UuidType;

public class Parameter {
    private final String name;
    private final DataType dataType;
    private final String defaultValue;

    /**
     * Whether a text or a binary parameter was declared with no length of its own, {@code p VARCHAR}. Its type is
     * held at the width a column of that spelling has, so the width alone cannot tell it from a parameter declared
     * at that width, {@code p VARCHAR(16777216)} — and a signature written with a length names the one and not the
     * other (see {@code Schema}).
     */
    private final boolean unsized;

    /**
     * The default as the account tells it back, printed from the plan it compiled to — {@code CAST('2020-01-01' AS
     * DATE)} for {@code '2020-01-01'::DATE}, {@code null} for {@code NULL} — or null when it is told as written.
     * The default as written is the one evaluated.
     */
    private final String defaultTold;

    public Parameter(final String name, final DataType dataType) {
        this(name, dataType, null);
    }

    public Parameter(final String name, final DataType dataType, final String defaultValue) {
        this(name, dataType, defaultValue, false);
    }

    /**
     * @param name         the parameter's name
     * @param dataType     its declared type
     * @param defaultValue its default as written, or null for none
     * @param unsized      whether it is a text or a binary declared with no length
     */
    public Parameter(final String name, final DataType dataType, final String defaultValue, final boolean unsized) {
        this(name, dataType, defaultValue, unsized, null);
    }

    /**
     * @param name         the parameter's name
     * @param dataType     its declared type
     * @param defaultValue its default as written, or null for none
     * @param unsized      whether it is a text or a binary declared with no length
     * @param defaultTold  its default as it is told back, or null when that is the default as written
     */
    public Parameter(final String name, final DataType dataType, final String defaultValue, final boolean unsized,
                     final String defaultTold) {
        this.name = name;
        this.dataType = dataType;
        this.defaultValue = defaultValue;
        this.unsized = unsized;
        this.defaultTold = defaultTold;
    }

    /**
     * This parameter with its default told another way than it was written.
     *
     * @param told the default as it is told back
     * @return the parameter so described
     */
    public Parameter toldAs(final String told) {
        return new Parameter(name, dataType, defaultValue, unsized, told);
    }

    public String getName() {
        return name;
    }

    public DataType getDataType() {
        return dataType;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public boolean hasDefault() {
        return defaultValue != null;
    }

    /** The default as it is told back: printed from its plan where that was kept, as written otherwise. */
    public String getToldDefault() {
        return defaultTold != null ? defaultTold : defaultValue;
    }

    /** The default as it is told back where that differs from what was written, or null. */
    public String getDefaultTold() {
        return defaultTold;
    }

    /** Whether this is a text or a binary parameter declared with no length of its own. */
    public boolean isUnsized() {
        return unsized;
    }

    /**
     * Whether a type is a text or a binary held at the length a column declared bare has — the only types a
     * declaration without a length leaves at a width it did not write. A CHAR is the text of length 1 it declares.
     *
     * @param type a parameter's declared type
     * @return whether a bare declaration and one at the default length are the same type
     */
    public static boolean holdsDefaultLength(final DataType type) {
        if (type instanceof UuidType) {
            return false;
        }
        if (type instanceof StringType) {
            return ((StringType) type).getMaxLength() == StringType.VARCHAR.getMaxLength();
        }
        return type instanceof BinaryType && ((BinaryType) type).getMaxLength() == BinaryType.DEFAULT_WIDTH;
    }
}
