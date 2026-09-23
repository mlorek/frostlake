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
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
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
import dev.frostlake.types.UuidType;
import dev.frostlake.values.VariantValue;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Casting TO a STRUCTURED type — the legality rules and the value transformation for
 * {@code CAST(expr AS OBJECT(...) | ARRAY(t) | MAP(k,v) [RENAME FIELDS | ADD FIELDS])}.
 *
 * <p>Everything here is live-verified against a real Snowflake account on; the shapes it
 * reproduces are:
 *
 * <ul>
 *   <li>{@code RENAME FIELDS} maps the source's declared fields POSITIONALLY onto the target's field
 *       names: {@code CAST(<OBJECT(x VARCHAR, y INT)> AS OBJECT(y VARCHAR, x INT) RENAME FIELDS)} is
 *       {@code {"y":"a","x":1}} — the value of {@code x} landed in the field now named {@code y}. The
 *       field COUNTS must match, otherwise it is a compile error.</li>
 *   <li>{@code ADD FIELDS} matches BY NAME and fills target-only fields with NULL, in the TARGET's
 *       declaration order: {@code CAST(<OBJECT(x VARCHAR)> AS OBJECT(z INT, x VARCHAR) ADD FIELDS)} is
 *       {@code {"z":null,"x":"a"}}. The target must list every source field, otherwise it is a compile
 *       error.</li>
 *   <li>Both modifiers require a STRUCTURED source AND a STRUCTURED target of the same family; a plain
 *       {@code OBJECT} / {@code VARIANT} / {@code ARRAY} source is the compile error "Function CAST
 *       RENAME FIELDS cannot be used with arguments of types OBJECT and OBJECT(y VARCHAR(134217728))".</li>
 *   <li>Without a modifier, a structured-to-structured OBJECT cast must keep the same field NAMES in the
 *       same order (leaf types may differ and the values convert); anything else is "incompatible types:
 *       [STRUCTURED_OBJECT] and [STRUCTURED_OBJECT]".</li>
 *   <li>The field values are converted to the DECLARED field types, and a value that will not convert —
 *       or a key set that does not match a modifier-free target — is the runtime error "Typed object
 *       schema mismatch in conversion".</li>
 *   <li>The modifiers recurse into nested structured fields and into structured ARRAY elements:
 *       {@code CAST(<ARRAY(OBJECT(y VARCHAR))> AS ARRAY(OBJECT(y VARCHAR, z INT)) ADD FIELDS)} is
 *       {@code [{"y":"a","z":null}]}.</li>
 * </ul>
 */
final class StructuredCast {

    /** Snowflake's runtime message when a value does not fit the declared structured shape. */
    /** The row-time refusal of a value whose shape does not fit the declared structure. */
    static final String SCHEMA_MISMATCH = "Typed object schema mismatch in conversion";

    private StructuredCast() {
    }

    /**
     * Snowflake's COMPILE-time rules for a structured cast. Live these fire before any row is
     * read — {@code SELECT 1 WHERE CAST(OBJECT_CONSTRUCT('x','a') AS OBJECT(y VARCHAR) RENAME FIELDS) IS
     * NOT NULL AND FALSE} still fails — so this runs from the plan-time walk as well as per row.
     *
     * <p>An UNDETERMINED source type passes: inference is conservative, and "let the value decide" can
     * never turn into a false rejection.
     */
    static void validate(final CastFieldsModifier modifier, final DataType structuredTarget,
                         final DataType sourceType, final boolean untypedNullSource,
                         final String sourceTypeText, final String targetTypeText) {
        if (modifier != CastFieldsModifier.NONE) {
            validateModifier(modifier, structuredTarget, sourceType, untypedNullSource,
                sourceTypeText, targetTypeText);
            return;
        }
        // A structured OBJECT or a MAP takes a VARIANT, an OBJECT or another of its family: any other source is
        // refused while the statement compiles, naming its family (live-verified).
        final String family = structuredObjectlessFamily(sourceType);
        if (family != null && StructuredTypes.isStructuredObjectFamily(structuredTarget)) {
            throw new RuntimeException(SqlCompilationError.of("incompatible types: [" + family + "] and ["
                + (structuredTarget instanceof MapType ? "MAP" : "STRUCTURED_OBJECT") + "]"));
        }
        // No modifier: only a structured-to-structured OBJECT cast is constrained, and only on the
        // field LAYOUT — CAST(<OBJECT(x VARCHAR, y INT)> AS OBJECT(x VARCHAR, y VARCHAR)) succeeds while
        // renaming/adding/dropping a field without a modifier does not.
        if (StructuredTypes.isStructured(sourceType) && StructuredTypes.isStructured(structuredTarget)) {
            checkFieldLayout(sourceType, structuredTarget);
        }
    }

    /**
     * The family a structured OBJECT or MAP target names a source it has no conversion from — TEXT, UUID, FIXED,
     * REAL, BOOLEAN, BINARY, DATE, TIME, a TIMESTAMP flavour, ARRAY or STRUCTURED_ARRAY — or null for a VARIANT,
     * an OBJECT, a MAP, an undetermined source and a family not measured.
     */
    private static String structuredObjectlessFamily(final DataType source) {
        if (source instanceof UuidType) {
            return "UUID";
        }
        if (source instanceof StringType) {
            return "TEXT";
        }
        if (source instanceof NumericType) {
            return NumericType.isApproximate(source) ? "REAL" : "FIXED";
        }
        if (source instanceof BooleanType) {
            return "BOOLEAN";
        }
        if (source instanceof BinaryType) {
            return "BINARY";
        }
        if (source instanceof StructuredArrayType) {
            return "STRUCTURED_ARRAY";
        }
        if (source instanceof ArrayType) {
            return "ARRAY";
        }
        if (!(source instanceof DateTimeType)) {
            return null;
        }
        final String name = SqlTypeNames.canonical(source);
        if (name.startsWith("TIMESTAMP")) {
            return name.substring(0, name.indexOf('(') < 0 ? name.length() : name.indexOf('('));
        }
        return name.startsWith("TIME") ? "TIME" : "DATE";
    }

    /**
     * Recursively require the same field NAMES in the same order, reporting the LEVEL at which the two
     * types disagree — live, a mismatch inside an array's element objects is reported as
     * "incompatible types: [STRUCTURED_OBJECT] and [STRUCTURED_OBJECT]", naming the element type rather
     * than the arrays that contain it.
     */
    private static void checkFieldLayout(final DataType source, final DataType target) {
        if (source instanceof StructuredObjectType && target instanceof StructuredObjectType) {
            final List<StructuredField> from = ((StructuredObjectType) source).getFields();
            final List<StructuredField> to = ((StructuredObjectType) target).getFields();
            if (from.size() != to.size()) {
                throw incompatible(source, target);
            }
            for (int i = 0; i < from.size(); i++) {
                if (!from.get(i).getName().equals(to.get(i).getName())) {
                    throw incompatible(source, target);
                }
                checkFieldLayout(from.get(i).getDataType(), to.get(i).getDataType());
            }
            return;
        }
        if (source instanceof ArrayType && target instanceof ArrayType) {
            checkFieldLayout(((ArrayType) source).getElementType(), ((ArrayType) target).getElementType());
        }
        // A leaf, a MAP, or a structured/plain mix: no declared field layout to disagree about. MAP is
        // deliberately unconstrained — live, CAST(<OBJECT(x VARCHAR)> AS MAP(VARCHAR,VARCHAR)) and the
        // reverse both succeed without a modifier.
    }

    private static void validateModifier(final CastFieldsModifier modifier, final DataType structuredTarget,
                                         final DataType sourceType, final boolean untypedNullSource,
                                         final String sourceTypeText, final String targetTypeText) {
        if (structuredTarget == null || untypedNullSource) {
            throw modifierNotUsable(modifier, untypedNullSource ? "NULL" : sourceTypeText, targetTypeText);
        }
        if (sourceType == null) {
            return;   // undetermined source: the conservative direction is to accept
        }
        if (!StructuredTypes.isStructured(sourceType)) {
            throw modifierNotUsable(modifier, sourceTypeText, targetTypeText);
        }
        if (StructuredTypes.isStructuredObjectFamily(sourceType)
                != StructuredTypes.isStructuredObjectFamily(structuredTarget)) {
            throw incompatible(sourceType, structuredTarget);
        }
        if (!(sourceType instanceof StructuredObjectType) || !(structuredTarget instanceof StructuredObjectType)) {
            return;   // an ARRAY or MAP level: the modifier applies to the nested objects, not here
        }
        final List<StructuredField> source = ((StructuredObjectType) sourceType).getFields();
        final List<StructuredField> target = ((StructuredObjectType) structuredTarget).getFields();
        if (modifier == CastFieldsModifier.RENAME) {
            // RENAME is positional, so the counts must agree (live: 2 fields -> 1 field is a compile
            // error, and so is 1 -> 2).
            if (source.size() != target.size()) {
                throw incompatible(sourceType, structuredTarget);
            }
            return;
        }
        // ADD FIELDS keeps the source's fields, so the target must still declare every one of them
        // (live: CAST(<OBJECT(x VARCHAR)> AS OBJECT(z INT) ADD FIELDS) is a compile error).
        for (final StructuredField field : source) {
            if (((StructuredObjectType) structuredTarget).field(field.getName()) == null) {
                throw incompatible(sourceType, structuredTarget);
            }
        }
    }

    private static RuntimeException modifierNotUsable(final CastFieldsModifier modifier,
                                                      final String sourceText, final String targetText) {
        return new RuntimeException("Function CAST " + modifier.getSql()
            + " cannot be used with arguments of types " + sourceText + " and " + targetText);
    }

    private static RuntimeException incompatible(final DataType sourceType, final DataType targetType) {
        return new RuntimeException("incompatible types: [" + StructuredTypes.familyName(sourceType)
            + "] and [" + StructuredTypes.familyName(targetType) + "]");
    }

    /**
     * Reshape {@code value} to the declared structured type. SQL NULL stays SQL NULL — live,
     * {@code CAST(NULL::OBJECT(x VARCHAR) AS OBJECT(y VARCHAR) RENAME FIELDS)} is NULL, not an empty
     * object.
     */
    static Object apply(final Object value, final DataType structuredTarget,
                        final CastFieldsModifier modifier, final DataType sourceType) {
        if (value == null) {
            return null;
        }
        final JsonNode node = ArrayFunctionHelper.parseNode(value);
        if (node == null || node.isNull()) {
            return value;
        }
        return VariantValue.ofNode(convert(node, structuredTarget, modifier, sourceType));
    }

    /** Convert one JSON value to {@code target}, recursing into structured members. */
    private static JsonNode convert(final JsonNode value, final DataType target,
                                    final CastFieldsModifier modifier, final DataType sourceType) {
        if (value == null || value.isNull()) {
            return ArrayFunctionHelper.MAPPER.getNodeFactory().nullNode();
        }
        if (target instanceof StructuredObjectType) {
            return convertObject(value, (StructuredObjectType) target, modifier, sourceType);
        }
        if (target instanceof MapType) {
            return convertMap(value, (MapType) target, modifier);
        }
        if (target instanceof StructuredArrayType) {
            return convertArray(value, (StructuredArrayType) target, modifier, sourceType);
        }
        return convertLeaf(value, target);
    }

    private static JsonNode convertObject(final JsonNode value, final StructuredObjectType target,
                                          final CastFieldsModifier modifier, final DataType sourceType) {
        if (!value.isObject()) {
            throw new RuntimeException(SCHEMA_MISMATCH);
        }
        final List<StructuredField> targetFields = target.getFields();
        final ObjectNode out = ArrayFunctionHelper.MAPPER.createObjectNode();
        if (modifier == CastFieldsModifier.RENAME) {
            final List<String> sourceNames = sourceFieldNames(value, sourceType);
            if (sourceNames.size() != targetFields.size()) {
                throw new RuntimeException(SCHEMA_MISMATCH);
            }
            for (int i = 0; i < targetFields.size(); i++) {
                putField(out, targetFields.get(i), value.get(sourceNames.get(i)), modifier,
                    sourceFieldType(sourceType, sourceNames.get(i)));
            }
            return out;
        }
        if (modifier == CastFieldsModifier.NONE) {
            // A modifier-free cast to a structured OBJECT requires the value's key set to match the
            // declared fields EXACTLY: live, both a missing field and an extra one fail with
            // "Typed object schema mismatch in conversion".
            if (value.size() != targetFields.size()) {
                throw new RuntimeException(SCHEMA_MISMATCH);
            }
            for (final StructuredField field : targetFields) {
                if (!value.has(field.getName())) {
                    throw new RuntimeException(SCHEMA_MISMATCH);
                }
            }
        }
        for (final StructuredField field : targetFields) {
            putField(out, field, value.get(field.getName()), modifier,
                sourceFieldType(sourceType, field.getName()));
        }
        return out;
    }

    private static void putField(final ObjectNode out, final StructuredField field, final JsonNode raw,
                                 final CastFieldsModifier modifier, final DataType sourceFieldType) {
        if (raw == null || raw.isNull()) {
            // A NOT NULL field cannot take a null — live, CAST(OBJECT_CONSTRUCT('x',NULL) AS
            // OBJECT(x VARCHAR NOT NULL)) and ADD FIELDS of a NOT NULL field both fail.
            if (field.isNotNull()) {
                throw new RuntimeException(SCHEMA_MISMATCH);
            }
            out.set(field.getName(), ArrayFunctionHelper.MAPPER.getNodeFactory().nullNode());
            return;
        }
        out.set(field.getName(), convert(raw, field.getDataType(), modifier, sourceFieldType));
    }

    private static JsonNode convertMap(final JsonNode value, final MapType target,
                                       final CastFieldsModifier modifier) {
        if (!value.isObject()) {
            throw new RuntimeException(SCHEMA_MISMATCH);
        }
        // A MAP has no declared field names, so the modifiers have nothing to rename or add at this
        // level (live: CAST(<MAP(VARCHAR,VARCHAR)> AS MAP(VARCHAR,VARCHAR) RENAME FIELDS) is the
        // unchanged map); only the value type is applied.
        final ObjectNode out = ArrayFunctionHelper.MAPPER.createObjectNode();
        final Iterator<String> keys = value.propertyNames().iterator();
        while (keys.hasNext()) {
            final String key = keys.next();
            out.set(key, convert(value.get(key), target.getValueType(), modifier, null));
        }
        return out;
    }

    private static JsonNode convertArray(final JsonNode value, final StructuredArrayType target,
                                         final CastFieldsModifier modifier, final DataType sourceType) {
        if (!value.isArray()) {
            throw new RuntimeException(SCHEMA_MISMATCH);
        }
        final DataType sourceElement = sourceType instanceof ArrayType
            ? ((ArrayType) sourceType).getElementType() : null;
        final ArrayNode out = ArrayFunctionHelper.MAPPER.createArrayNode();
        for (final JsonNode element : value) {
            out.add(convert(element, target.getElementType(), modifier, sourceElement));
        }
        return out;
    }

    /**
     * A leaf field value converted to its declared type — reusing the engine's ordinary CAST semantics,
     * so {@code OBJECT(x INT)} over the text {@code "5"} yields the number 5 exactly as {@code '5'::INT}
     * does. A value that will not convert is the structured runtime error rather than the scalar one.
     */
    private static JsonNode convertLeaf(final JsonNode value, final DataType target) {
        if (!(target instanceof StringType) && !(target instanceof NumericType)
                && !(target instanceof BooleanType)) {
            return value;   // VARIANT / plain OBJECT / plain ARRAY fields keep the value as it stands
        }
        if (value.isObject() || value.isArray()) {
            throw new RuntimeException(SCHEMA_MISMATCH);
        }
        final Object converted;
        try {
            converted = ValueCaster.castValue(ArrayFunctionHelper.fromNode(value), castTypeName(target));
        } catch (final RuntimeException e) {
            throw new RuntimeException(SCHEMA_MISMATCH);
        }
        return ArrayFunctionHelper.canonicalize(
            ArrayFunctionHelper.toNode(ArrayFunctionHelper.MAPPER, converted));
    }

    /**
     * The declared type as {@link ValueCaster} spells cast targets. Mapped by FAMILY rather than by
     * name so the less common spellings (CHAR, SMALLINT, UUID, ...) convert like their family instead
     * of falling through unconverted.
     */
    private static String castTypeName(final DataType target) {
        if (target instanceof BooleanType) {
            return "BOOLEAN";
        }
        if (target instanceof NumericType) {
            final NumericType numeric = (NumericType) target;
            final String name = numeric.getName().toUpperCase();
            if (name.equals("NUMBER") || name.equals("DECIMAL") || name.equals("NUMERIC")) {
                return "NUMBER(" + numeric.getPrecision() + "," + numeric.getScale() + ")";
            }
            if (name.contains("FLOAT") || name.contains("DOUBLE") || name.contains("REAL")) {
                return "FLOAT";
            }
            return "INTEGER";
        }
        return "VARCHAR";
    }

    /**
     * The source's field names in DECLARATION order, which is what {@code RENAME FIELDS} zips against.
     * A source type with no declared fields (a MAP) contributes none, which makes the positional rename
     * a count mismatch — live, {@code CAST(<MAP(VARCHAR,VARCHAR)> AS OBJECT(y VARCHAR) RENAME FIELDS)}
     * compiles and then fails "Typed object schema mismatch in conversion".
     */
    private static List<String> sourceFieldNames(final JsonNode value, final DataType sourceType) {
        final List<String> names = new ArrayList<>();
        if (sourceType instanceof StructuredObjectType) {
            for (final StructuredField field : ((StructuredObjectType) sourceType).getFields()) {
                names.add(field.getName());
            }
            return names;
        }
        if (sourceType != null) {
            return names;   // MAP or another fieldless structured type
        }
        final Iterator<String> keys = value.propertyNames().iterator();
        while (keys.hasNext()) {
            names.add(keys.next());
        }
        return names;
    }

    private static DataType sourceFieldType(final DataType sourceType, final String name) {
        if (!(sourceType instanceof StructuredObjectType)) {
            return null;
        }
        final StructuredField field = ((StructuredObjectType) sourceType).field(name);
        return field != null ? field.getDataType() : null;
    }
}
