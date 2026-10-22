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

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.MapType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringType;
import dev.frostlake.types.StructuredArrayType;
import dev.frostlake.types.StructuredField;
import dev.frostlake.types.StructuredObjectType;
import dev.frostlake.types.StructuredTypes;
import dev.frostlake.types.WrittenSignatureType;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * How the overloads of a routine name are told apart, by the types of their parameters, and how a signature a
 * statement writes names one of them: the rules {@link OverloadStore} keeps a function's and a procedure's
 * overloads by.
 */
public final class RoutineSignatures {

    /** The length of a text parameter declared without one, as a written signature names it. */
    private static final int UNSIZED_TEXT_LENGTH = 134217728;

    private RoutineSignatures() {
    }

    /**
     * Whether two parameter lists are one overload: each parameter of one type family, and a structured parameter of
     * one shape besides — its fields by name and in order, each member's family — whatever the widths inside it and
     * whether a field says NOT NULL. {@code f(OBJECT(b INT))} and {@code f(OBJECT(a VARCHAR))} are overloads of
     * {@code f(OBJECT(a INT))}, and {@code f(OBJECT(a NUMBER(10,2)))} is the same signature (live-verified).
     */
    static boolean sameSignature(final List<Parameter> params1, final List<Parameter> params2) {
        if (params1.size() != params2.size()) {
            return false;
        }
        for (int i = 0; i < params1.size(); i++) {
            if (!sameSignatureType(params1.get(i).getDataType(), params2.get(i).getDataType(), false)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether two parameter lists are one signature to the letter: {@link #sameSignature}, and a structured field that
     * says NOT NULL in one says it in the other. A plain CREATE finds its name taken only by such an overload, an
     * overload competes with another by such types, and a temporary routine steps aside only for a permanent one of
     * such a signature; one that differs from an overload of its lifetime by a field's NOT NULL alone is refused
     * when it is added (live-verified).
     */
    static boolean exactSignature(final List<Parameter> params1, final List<Parameter> params2) {
        if (params1.size() != params2.size()) {
            return false;
        }
        for (int i = 0; i < params1.size(); i++) {
            if (!sameSignatureType(params1.get(i).getDataType(), params2.get(i).getDataType(), true)) {
                return false;
            }
        }
        return true;
    }

    /** Whether two parameter types are one in a signature: one type, and one shape when structured. */
    private static boolean sameSignatureType(final DataType one, final DataType other, final boolean notNull) {
        return one.equals(other) && shapeOf(one, notNull).equals(shapeOf(other, notNull));
    }

    /** Whether these argument types name the parameters by type family, a structured one by its shape besides. */
    static boolean matches(final List<Parameter> params, final List<DataType> argumentTypes) {
        if (params.size() != argumentTypes.size()) {
            return false;
        }
        for (int i = 0; i < params.size(); i++) {
            final DataType declared = params.get(i).getDataType();
            if (!signatureFamily(declared).equals(signatureFamily(argumentTypes.get(i)))
                    || !shapeOf(declared, false).equals(shapeOf(argumentTypes.get(i), false))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a signature a statement WRITES names these parameters. The family decides first (see
     * {@link #signatureFamily(DataType)}); then a type written with a length, a precision or a scale names only a
     * parameter declared with the same, while one written bare names any of its family (live-verified):
     *
     * <pre>
     *   f(code CHAR(2))      VARCHAR, STRING, TEXT, CHAR(2), VARCHAR(2)     not CHAR, CHARACTER, NCHAR, VARCHAR(3)
     *   f(code VARCHAR(1))   CHAR — a bare CHAR is a text of length 1
     *   f(code VARCHAR)      VARCHAR, VARCHAR(134217728)                    not CHAR, VARCHAR(5), VARCHAR(16777216)
     *   f(code VARCHAR(16777216))   VARCHAR, VARCHAR(16777216)              not VARCHAR(134217728)
     *   f(n NUMBER(5,2))     NUMBER, INT, NUMBER(38,0), NUMBER(5,2)         not NUMBER(10,0)
     *   f(b BINARY(4))       BINARY, BINARY(4)                              not BINARY(5), BINARY(8388608)
     *   f(b BINARY)          BINARY, BINARY(67108864)                       not BINARY(8388608)
     *   f(t TIMESTAMP_NTZ(3)) TIMESTAMP_NTZ, TIMESTAMP_NTZ(3)               not TIMESTAMP_NTZ(6), TIMESTAMP_NTZ(9)
     *   f(t TIME)            TIME, TIME(9)                                  not TIME(3)
     * </pre>
     *
     * <p>A parameter declared without a length is as wide as a text or a binary gets, which is not the width a
     * column of that spelling has: it is named by the written maximum and by no other length. A number is the one
     * family whose default written out — NUMBER(38,0) — reads as written bare.
     */
    static boolean matchesWritten(final List<Parameter> params, final List<DataType> argumentTypes) {
        if (!matches(params, argumentTypes)) {
            return false;
        }
        for (int i = 0; i < params.size(); i++) {
            if (!namesDeclaredWidth(params.get(i), argumentTypes.get(i))
                    || !signatureShape(params.get(i).getDataType()).equals(signatureShape(argumentTypes.get(i)))) {
                return false;
            }
        }
        return true;
    }

    /**
     * What of a type a written signature has to repeat to name a parameter of it, beyond its family: a structured
     * type's shape — its fields by name and in order, each field's and element's own family, and a field's NOT
     * NULL — while the widths inside a shape count for nothing: {@code f(OBJECT(a NUMBER, b VARCHAR(5)))} names a
     * parameter declared {@code OBJECT(a INT, b VARCHAR(10))}, and neither {@code OBJECT(b VARCHAR(10), a INT)},
     * {@code OBJECT(a VARCHAR)} nor a shape without a field's NOT NULL does (live-verified). Empty for a type
     * that has no shape.
     */
    private static String signatureShape(final DataType type) {
        return shapeOf(type, true);
    }

    /** A type's shape (see {@link #signatureShape}), a field's NOT NULL in it or not. */
    private static String shapeOf(final DataType type, final boolean notNull) {
        if (type instanceof StructuredObjectType) {
            final StringBuilder shape = new StringBuilder("OBJECT(");
            for (final StructuredField field : ((StructuredObjectType) type).getFields()) {
                shape.append(field.getName()).append(' ').append(shapeMember(field.getDataType(), notNull))
                    .append(notNull && field.isNotNull() ? " NOT NULL" : "").append(',');
            }
            return shape.append(')').toString();
        }
        if (type instanceof MapType) {
            return "MAP(" + shapeMember(((MapType) type).getKeyType(), notNull) + ","
                + shapeMember(((MapType) type).getValueType(), notNull)
                + (notNull ? StructuredTypes.notNullMark(type) : "") + ")";
        }
        if (type instanceof StructuredArrayType) {
            return "ARRAY(" + shapeMember(((StructuredArrayType) type).getElementType(), notNull)
                + (notNull ? StructuredTypes.notNullMark(type) : "") + ")";
        }
        return "";
    }

    /** A member of a shape: its own shape when it has one, else its family. */
    private static String shapeMember(final DataType type, final boolean notNull) {
        final String shape = shapeOf(type, notNull);
        return shape.isEmpty() ? signatureFamily(type) : shape;
    }

    /**
     * Whether a written type's length, precision and scale are the parameter's own, or were not written at all
     * (see {@link WrittenSignatureType}).
     */
    private static boolean namesDeclaredWidth(final Parameter parameter, final DataType written) {
        final DataType declared = parameter.getDataType();
        if (written instanceof StringType && declared instanceof StringType) {
            return WrittenSignatureType.isBare(written) || ((StringType) written).getMaxLength()
                == (parameter.isUnsized() ? UNSIZED_TEXT_LENGTH : ((StringType) declared).getMaxLength());
        }
        if (written instanceof BinaryType && declared instanceof BinaryType) {
            return WrittenSignatureType.isBare(written) || ((BinaryType) written).getMaxLength()
                == (parameter.isUnsized() ? BinaryType.NOMINAL_MAXIMUM : ((BinaryType) declared).getMaxLength());
        }
        if (written instanceof NumericType && declared instanceof NumericType
                && "NUMBER".equals(signatureFamily(written))) {
            final NumericType asWritten = (NumericType) written;
            final NumericType asDeclared = (NumericType) declared;
            return asWritten.getPrecision() == NumericType.NUMBER.getPrecision() && asWritten.getScale() == 0
                || asWritten.getPrecision() == asDeclared.getPrecision()
                    && asWritten.getScale() == asDeclared.getScale();
        }
        if (written instanceof DateTimeType && declared instanceof DateTimeType) {
            return WrittenSignatureType.isBare(written)
                || ((DateTimeType) written).getPrecision() == ((DateTimeType) declared).getPrecision();
        }
        return true;
    }

    /**
     * The CANONICAL type family a routine's signature is stored under: Snowflake normalizes a
     * parameter's declared alias away, so a DROP / DESCRIBE names the FAMILY. Live-verified on a real
     * account, each spelling re-creating the routine between attempts: an
     * {@code x INTEGER} parameter is dropped by INTEGER, INT, BIGINT and NUMBER alike; a
     * {@code x DECIMAL(10,2)} parameter by DECIMAL and by NUMBER; a {@code x DOUBLE} parameter by
     * DOUBLE; and a {@code x CHAR(5)} parameter by STRING and by VARCHAR. Two overloads are the same
     * signature by family alone, whatever their lengths: {@code f(CHAR(3))} beside {@code f(CHAR(2))}
     * already exists.
     */
    private static String signatureFamily(final DataType type) {
        // A structured OBJECT or ARRAY is another signature than the plain type of its name, as CREATE already
        // counts it (f(x OBJECT) and f(x OBJECT(a INT)) are two overloads), so DROP, DESCRIBE, GET_DDL and
        // OR REPLACE must not take one for the other. A MAP has a name of its own.
        if (type instanceof StructuredObjectType) {
            return "STRUCTURED OBJECT";
        }
        if (type instanceof StructuredArrayType) {
            return "STRUCTURED ARRAY";
        }
        return signatureFamily(type.getName());
    }

    /**
     * {@link #signatureFamily(DataType)} for a type written by name, as a GRANT on a routine writes its argument
     * types. A bare TIMESTAMP names the default TIMESTAMP_NTZ there, and an OBJECT or ARRAY written with a shape names
     * the structured type, as the declared parameter's own family does.
     *
     * @param typeName the type's name; any parameters after it are ignored but for an OBJECT's or ARRAY's shape
     * @return the family
     */
    public static String signatureFamily(final String typeName) {
        final int open = typeName.indexOf('(');
        final String name = (open >= 0 ? typeName.substring(0, open) : typeName).trim().toUpperCase(Locale.ROOT);
        if (open >= 0 && ("OBJECT".equals(name) || "ARRAY".equals(name))
                && !typeName.substring(open + 1).replace(")", "").trim().isEmpty()) {
            return "STRUCTURED " + name;
        }
        if ("TIMESTAMP".equals(name)) {
            return "TIMESTAMP_NTZ";
        }
        switch (name) {
            case "CHAR": case "CHARACTER": case "NCHAR": case "NVARCHAR": case "NVARCHAR2":
            case "VARCHAR2":
            case "CHAR VARYING": case "STRING": case "TEXT":
                return "VARCHAR";
            case "DECIMAL": case "NUMERIC": case "INT": case "INTEGER": case "BIGINT":
            case "SMALLINT": case "TINYINT": case "BYTEINT":
                return "NUMBER";
            case "DOUBLE": case "DOUBLE PRECISION": case "REAL": case "FLOAT4": case "FLOAT8":
                return "FLOAT";
            case "VARBINARY":
                return "BINARY";
            case "DATETIME":
                return "TIMESTAMP_NTZ";
            default:
                return name;
        }
    }

    /**
     * A routine's signature as its argument types' families, {@code (NUMBER, VARCHAR)}, a structured one with its
     * shape: what tells its overloads apart, and what privileges on one overload are recorded under (GrantNames).
     * {@code f1(x OBJECT(a INT))} and {@code f1(x OBJECT(b INT))} are two overloads, so each keeps privileges of its
     * own, and a REVOKE or a DROP of one leaves the other's.
     *
     * @param parameters the routine's parameters
     * @return the signature
     */
    public static String signatureText(final List<Parameter> parameters) {
        final StringBuilder text = new StringBuilder("(");
        for (int i = 0; i < parameters.size(); i++) {
            // A parameter with a default is marked, as SHOW GRANTS names it: D(NUMBER, DEFAULT VARCHAR)
            // (live-verified).
            text.append(i > 0 ? ", " : "").append(parameters.get(i).hasDefault() ? "DEFAULT " : "")
                .append(recordedType(parameters.get(i).getDataType()));
        }
        return text.append(')').toString();
    }

    /**
     * A parameter's type as a signature records it: its family, and a structured type's shape with each member's
     * family and no NOT NULL in it, {@code OBJECT(a NUMBER, b VARCHAR)}, as the overload's identity reads it
     * (see {@link #sameSignature}).
     */
    private static String recordedType(final DataType type) {
        if (type instanceof StructuredObjectType) {
            final StringBuilder shape = new StringBuilder("OBJECT(");
            final List<StructuredField> fields = ((StructuredObjectType) type).getFields();
            for (int i = 0; i < fields.size(); i++) {
                shape.append(i > 0 ? ", " : "").append(fields.get(i).getName()).append(' ')
                    .append(recordedType(fields.get(i).getDataType()));
            }
            return shape.append(')').toString();
        }
        if (type instanceof MapType) {
            return "MAP(" + recordedType(((MapType) type).getKeyType()) + ", "
                + recordedType(((MapType) type).getValueType()) + ")";
        }
        if (type instanceof StructuredArrayType) {
            return "ARRAY(" + recordedType(((StructuredArrayType) type).getElementType()) + ")";
        }
        return signatureFamily(type);
    }

    /** Whether two different signatures take a call of some argument count with the same leading types. */
    static boolean competes(final List<Parameter> one, final List<Parameter> other) {
        if (exactSignature(one, other)) {
            return false;
        }
        // The fewest arguments both take: where the leading types differ there, they differ for every longer call.
        final int fewest = Math.max(requiredCount(one), requiredCount(other));
        if (fewest > Math.min(one.size(), other.size())) {
            return false;
        }
        for (int i = 0; i < fewest; i++) {
            if (!sameSignatureType(one.get(i).getDataType(), other.get(i).getDataType(), true)) {
                return false;
            }
        }
        return true;
    }

    /** How many leading parameters a call must supply: the index after the last one with no default. */
    private static int requiredCount(final List<Parameter> parameters) {
        int required = 0;
        for (int i = 0; i < parameters.size(); i++) {
            if (!parameters.get(i).hasDefault()) {
                required = i + 1;
            }
        }
        return required;
    }

    /**
     * The refusal a routine name already carrying the signature gets: the name quoted only when it needs
     * quotes, {@code Object '"b"' already exists.} for a quoted {@code "b"} (live-verified).
     *
     * @param name the routine's canonical name
     * @return the refusal's message
     */
    public static String alreadyExists(final String name) {
        return SqlCompilationError.of("Object '" + SqlIdentifiers.spellCanonical(name) + "' already exists.");
    }

    /**
     * The refusal an overload that would compete with an existing one gets, on one line (live-verified).
     *
     * @param kind FUNCTION or PROCEDURE
     * @param name the routine's canonical name
     * @return the refusal's message
     */
    public static String ambiguousOverload(final String kind, final String name) {
        return SqlCompilationError.inline("Cannot overload " + kind + " '" + SqlIdentifiers.spellCanonical(name)
            + "' as it would cause ambiguous " + kind + " overloading.");
    }

    /**
     * Refuses a new routine an existing one of its signature holds the name for (live-verified): a temporary routine
     * may not take a permanent one's signature, refused with that one's signature and return type spelled
     * ({@code Object 'PERMF():NUMBER(38,0)' already exists.}); a permanent routine beside a temporary one, and a
     * temporary one beside another session's, are each created; two of the same lifetime and session are refused
     * for the name alone.
     *
     * @param created  the new routine
     * @param existing the existing routine of the signature
     */
    static void refuseSignatureHeld(final Routine created, final Routine existing) {
        if (created.isTemporary() && !existing.isTemporary()) {
            final List<Parameter> parameters = existing.getParameters();
            final StringBuilder signature = new StringBuilder(SqlIdentifiers.spellCanonical(created.getName()))
                .append('(');
            for (int i = 0; i < parameters.size(); i++) {
                signature.append(i == 0 ? "" : ", ").append(SqlTypeNames.routineType(parameters.get(i).getDataType()));
            }
            signature.append("):").append(existing.getReturnType() == null ? "TABLE"
                : SqlTypeNames.routineType(existing.getReturnType(), existing.getReturnSized()));
            throw new RuntimeException(SqlCompilationError.of("Object '" + signature + "' already exists."));
        }
        if (sameLifetime(created, existing)) {
            throw new RuntimeException(alreadyExists(created.getName()));
        }
    }

    /** Whether two routines are both permanent, or both temporary to one session. */
    static boolean sameLifetime(final Routine created, final Routine existing) {
        return created.isTemporary() == existing.isTemporary()
            && (!created.isTemporary() || Objects.equals(created.getCreatedInSession(),
                existing.getCreatedInSession()));
    }

    /**
     * The refusal a routine gets whose signature an overload of the same lifetime holds but for a structured field's
     * NOT NULL, the new routine spelled with its parameters' names and what it returns:
     * {@code Object 'F1(X OBJECT(a NUMBER NOT NULL)):VARCHAR' already exists.}, and
     * {@code Object 'H1(X OBJECT(a NUMBER)):TABLE: (C NUMBER)' already exists.} for one that returns a table
     * (live-verified). It is judged when the routine is added, after everything else about it.
     */
    static RuntimeException notNullOnlyTwin(final Routine created) {
        final StringBuilder signature = new StringBuilder(SqlIdentifiers.spellCanonical(created.getName()))
            .append('(').append(namedTypes(created.getParameters())).append("):");
        if (created.returnsTable()) {
            signature.append("TABLE: (").append(namedTypes(created.getReturnColumns())).append(')');
        } else {
            signature.append(SqlTypeNames.routineType(created.getReturnType(), created.getReturnSized()));
        }
        return new RuntimeException(SqlCompilationError.of("Object '" + signature + "' already exists."));
    }

    /**
     * Parameters or columns by name and bare type, as a routine's spelled signature lists them:
     * {@code X OBJECT(a NUMBER NOT NULL), Z NUMBER}.
     */
    private static String namedTypes(final List<Parameter> items) {
        final StringBuilder text = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            text.append(i == 0 ? "" : ", ").append(SqlIdentifiers.spellCanonical(items.get(i).getName()))
                .append(' ').append(StructuredTypes.signatureText(items.get(i).getDataType()));
        }
        return text.toString();
    }
}
