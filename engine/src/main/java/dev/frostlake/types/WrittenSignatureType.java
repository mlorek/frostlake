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

/**
 * A type as a routine's signature writes it after a name — {@code DROP FUNCTION f(VARCHAR(10))}, {@code DESCRIBE
 * PROCEDURE p(TIME)} — which tells a type written bare from one written with a length or digits, where a column's
 * type is its default width either way. A text, a binary, a time and a timestamp written bare name a parameter of
 * any width; written with one they name a parameter of that width alone, the family's default included:
 * {@code f(VARCHAR(16777216))} is no name for a parameter declared {@code VARCHAR} (live-verified). A bare CHAR is
 * the text of length 1 it declares, and a number needs no telling apart: NUMBER(38,0) reads as NUMBER does.
 */
public final class WrittenSignatureType {

    private WrittenSignatureType() {
    }

    /**
     * The type a signature writes.
     *
     * @param parsed            the type as a column of that spelling declares it
     * @param parametersWritten whether the signature writes a length, a precision or digits after the name
     * @return the type, marked unsized where it was written bare
     */
    public static DataType of(final DataType parsed, final boolean parametersWritten) {
        if (parametersWritten || parsed instanceof UuidType) {
            return parsed;
        }
        if (parsed instanceof StringType) {
            final StringType text = (StringType) parsed;
            return text.getMaxLength() == StringType.VARCHAR.getMaxLength()
                ? new StringType(text.getName(), text.getMaxLength(), true) : parsed;
        }
        if (parsed instanceof BinaryType) {
            return BinaryType.unsized(((BinaryType) parsed).isFixed());
        }
        if (parsed instanceof DateTimeType && !"DATE".equalsIgnoreCase(parsed.getName())) {
            return new UnsizedDateTimeType((DateTimeType) parsed);
        }
        return parsed;
    }

    /**
     * Whether a written type was written bare.
     *
     * @param written a type built by {@link #of}
     * @return whether it names a parameter of any width
     */
    public static boolean isBare(final DataType written) {
        if (written instanceof StringType) {
            return ((StringType) written).isUnsized();
        }
        if (written instanceof BinaryType) {
            return ((BinaryType) written).getWidthSpelling() == BinaryWidthSpelling.UNSIZED;
        }
        return written instanceof UnsizedDateTimeType;
    }
}
