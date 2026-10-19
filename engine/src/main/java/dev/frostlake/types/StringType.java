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

package dev.frostlake.types;

public class StringType extends DataType {

    /**
     * Pinned at the value the class had before {@link #unsized} was added, so a snapshot written before it still
     * reads (its strings read as sized, which every one of them was).
     */
    private static final long serialVersionUID = 7234115287022531106L;

    private final int maxLength;

    /**
     * Whether the string was written with no width of its own where a structured cast target names its leaves:
     * {@code ::OBJECT(a VARCHAR)}. SYSTEM$TYPEOF names such a leaf bare, while DESCRIBE RESULT and a refusal
     * name the width it is held at (live-verified). Every other string is sized.
     */
    private final boolean unsized;

    public StringType(final String name, final int maxLength) {
        this(name, maxLength, false);
    }

    /**
     * A string held at {@code maxLength}, written without a width of its own when {@code unsized} says so.
     *
     * @param name      the spelling
     * @param maxLength the width it is held at
     * @param unsized   whether it was written with no width (see {@link #isUnsized})
     */
    public StringType(final String name, final int maxLength, final boolean unsized) {
        super(name, TypeCategory.STRING);
        this.maxLength = maxLength;
        this.unsized = unsized;
    }

    public int getMaxLength() {
        return maxLength;
    }

    /** Whether a structured cast target wrote this leaf with no width: see {@link StructuredLeafWidths}. */
    public boolean isUnsized() {
        return unsized;
    }

    @Override
    public Object parseValue(final String value) {
        if (value == null || value.equalsIgnoreCase("NULL")) {
            return null;
        }
        return value;
    }

    @Override
    public String formatValue(final Object value) {
        if (value == null) return "NULL";
        return "'" + value.toString() + "'";
    }

    @Override
    public boolean isCompatible(final DataType other) {
        return other.getCategory() == TypeCategory.STRING;
    }

    @Override
    public DataType getCommonType(final DataType other) {
        if (!(other instanceof StringType)) {
            return null;
        }
        final StringType otherString = (StringType) other;
        final int maxLen = Math.max(this.maxLength, otherString.maxLength);
        return new StringType("VARCHAR", maxLen);
    }

    @Override
    public int getSize() {
        return maxLength > 0 ? maxLength : 16777216; // Default max size
    }

    public static StringType VARCHAR = new StringType("VARCHAR", 16777216);
    public static StringType STRING = new StringType("STRING", 16777216);
    public static StringType TEXT = new StringType("TEXT", 16777216);
    public static StringType CHAR = new StringType("CHAR", 1);
}
