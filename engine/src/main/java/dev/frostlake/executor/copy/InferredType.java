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

package dev.frostlake.executor.copy;

/**
 * The type INFER_SCHEMA holds for a staged column, and the two ways columns' types come together.
 *
 * <p><b>Within one file</b> a column is read value by value, and the type held so far takes the next value in
 * when that value is of its own family — a NUMBER widening to the larger integer part and the larger scale —
 * or when the value is text the held type still reads: a TEXT takes any, a REAL takes an integer's digits, a
 * BOOLEAN takes {@code 1} and {@code 0}, and a DATE takes a timestamp's. Nothing else is taken in, so the order matters: a
 * DATE followed by a timestamp stays DATE while a timestamp followed by a DATE falls to TEXT, and a REAL
 * followed by an integer stays REAL while an integer followed by a REAL falls to TEXT. Only text is read this
 * way — a JSON number is a number, so a JSON REAL followed by a JSON integer falls to TEXT.
 *
 * <p><b>Across files</b> the per-file types meet by family alone: the same family merges (NUMBER widening),
 * and different families fall to TEXT — a DATE in one file beside a timestamp in another is TEXT. A column
 * without a single value in a file takes no part in the meeting, and is TEXT only when no file gives it one.
 * A self-describing format (Parquet, Avro, ORC) falls to VARIANT instead.
 *
 * <p>Under {@code KIND => 'ICEBERG'} nothing falls anywhere: a value or a file a column cannot take in is
 * refused, {@code Incompatible data types detected: TEXT and FIXED.}, the newcomer named first.
 */
public final class InferredType {

    /** The widest NUMBER. */
    private static final int MAX_PRECISION = 38;

    private final InferredKind kind;

    /** A NUMBER's digits before the point, at least one. */
    private final int integerDigits;

    /** A NUMBER's digits after the point. */
    private final int scale;

    /** A declared type's spelling; null for the other families. */
    private final String declared;

    private InferredType(final InferredKind kind, final int integerDigits, final int scale, final String declared) {
        this.kind = kind;
        this.integerDigits = integerDigits;
        this.scale = scale;
        this.declared = declared;
    }

    /**
     * A type of one of the families that carry nothing further.
     *
     * @param kind the family, anything but NUMBER and DECLARED
     * @return the type
     */
    public static InferredType of(final InferredKind kind) {
        return new InferredType(kind, 0, 0, null);
    }

    /**
     * A NUMBER holding {@code integerDigits} digits before the point and {@code scale} after it, which spells
     * itself {@code NUMBER(integerDigits + scale, scale)} — capped at 38 digits, the scale giving way first.
     *
     * @param integerDigits the digits before the point, at least one
     * @param scale the digits after the point
     * @return the type
     */
    public static InferredType number(final int integerDigits, final int scale) {
        final int wholeDigits = Math.max(1, integerDigits);
        return new InferredType(InferredKind.NUMBER, wholeDigits,
            Math.max(0, Math.min(scale, MAX_PRECISION - wholeDigits)), null);
    }

    /**
     * A type a self-describing file declares, spelled as the account reports it.
     *
     * @param typeText the spelling, such as {@code NUMBER(38, 0)} or {@code TIMESTAMP_LTZ(6)}
     * @return the type
     */
    public static InferredType declared(final String typeText) {
        return new InferredType(InferredKind.DECLARED, 0, 0, typeText);
    }

    /**
     * The type's family.
     *
     * @return the family
     */
    public InferredKind kind() {
        return kind;
    }

    /**
     * The type as the TYPE column spells it: {@code NUMBER(4, 2)} with its space, {@code TEXT}, {@code REAL},
     * {@code TIMESTAMP_NTZ}, or a declared type's own spelling.
     *
     * @return the spelling
     */
    public String typeText() {
        switch (kind) {
            case NUMBER:
                return "NUMBER(" + (integerDigits + scale) + ", " + scale + ")";
            case TIMESTAMP:
                return "TIMESTAMP_NTZ";
            case DECLARED:
                return declared;
            default:
                return kind.name();
        }
    }

    /**
     * The name a refusal gives the type: its family's, or a declared type's own spelling.
     *
     * @return the name
     */
    public String internalName() {
        return kind == InferredKind.DECLARED ? declared : kind.internalName();
    }

    /**
     * The type a column holds once one more value of a file is read.
     *
     * @param current the type held so far, or null before the first value
     * @param value the value's own type, or null for a NULL
     * @param text the value as written when it is text — a CSV field or a JSON string — else null
     * @param iceberg whether a value the column cannot take in is refused rather than falling to TEXT
     * @return the type the column holds now
     */
    public static InferredType absorb(final InferredType current, final InferredType value, final String text,
                                      final boolean iceberg) {
        if (value == null) {
            return current;
        }
        if (current == null) {
            return value;
        }
        if (current.kind == InferredKind.TEXT && !iceberg) {
            return current;
        }
        if (current.kind == value.kind) {
            return current.kind == InferredKind.NUMBER ? current.widenedWith(value) : current;
        }
        if (text != null && readsText(current, value, text)) {
            return current;
        }
        if (iceberg) {
            throw incompatible(value, current);
        }
        return of(InferredKind.TEXT);
    }

    /**
     * The type a column holds across two files, each file's type settled on its own.
     *
     * @param first the type the files read so far give the column, or null when none gave it a value
     * @param next the next file's type, or null when it gave the column no value
     * @param selfDescribing whether the files declare their types, so a conflict falls to VARIANT
     * @param iceberg whether a conflict is refused instead
     * @return the merged type
     */
    public static InferredType merge(final InferredType first, final InferredType next, final boolean selfDescribing,
                                     final boolean iceberg) {
        if (first == null) {
            return next;
        }
        if (next == null) {
            return first;
        }
        if (first.kind == next.kind && first.kind == InferredKind.NUMBER) {
            return first.widenedWith(next);
        }
        if (first.kind == next.kind && (first.kind != InferredKind.DECLARED || first.declared.equals(next.declared))) {
            return first;
        }
        if (iceberg) {
            throw incompatible(next, first);
        }
        return of(selfDescribing ? InferredKind.VARIANT : InferredKind.TEXT);
    }

    /** Whether a held type still reads a text value of another family. */
    private static boolean readsText(final InferredType current, final InferredType value, final String text) {
        switch (current.kind) {
            case TEXT:
                return true;
            case REAL:
                return value.kind == InferredKind.NUMBER;
            case BOOLEAN:
                return "1".equals(text) || "0".equals(text);
            case DATE:
                return value.kind == InferredKind.TIMESTAMP;
            default:
                return false;
        }
    }

    /** The NUMBER both of two NUMBERs fit: the larger integer part and the larger scale. */
    private InferredType widenedWith(final InferredType other) {
        return number(Math.max(integerDigits, other.integerDigits), Math.max(scale, other.scale));
    }

    /** The refusal of a type a column cannot take in, the newcomer named first. */
    private static RuntimeException incompatible(final InferredType newcomer, final InferredType held) {
        return new RuntimeException("Incompatible data types detected: " + newcomer.internalName() + " and "
            + held.internalName() + ".");
    }
}
