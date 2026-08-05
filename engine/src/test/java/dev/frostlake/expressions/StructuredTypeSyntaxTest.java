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

package dev.frostlake.expressions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The STRUCTURED semi-structured types — {@code ARRAY(INT)}, {@code OBJECT(field type [NOT NULL], ...)},
 * {@code MAP(k, v)} — in casts, including the {@code RENAME FIELDS} / {@code ADD FIELDS} modifiers.
 *
 * <p>Snowflake keeps these DISTINCT from the plain semi-structured {@code OBJECT} / {@code ARRAY} /
 * {@code VARIANT}: the declared shape decides which casts compile and what the result looks like. Every
 * expectation below was measured on a real Snowflake account.
 */
public class StructuredTypeSyntaxTest extends BaseDatabaseTest {

    /** A structured OBJECT value with one field, used as a legal modifier SOURCE. */
    private static final String OBJ_X = "CAST(OBJECT_CONSTRUCT('x','a') AS OBJECT(x VARCHAR))";

    /** A structured OBJECT value with two fields. */
    private static final String OBJ_XY =
        "CAST(OBJECT_CONSTRUCT('x','a','y',1) AS OBJECT(x VARCHAR, y INT))";

    /** A structured OBJECT whose x field holds a convertible NUMERIC STRING. */
    private static final String OBJ_X5Y =
        "CAST(OBJECT_CONSTRUCT('x','5','y',1) AS OBJECT(x VARCHAR, y INT))";

    /** A structured OBJECT with a nested structured OBJECT field. */
    private static final String OBJ_NESTED =
        "CAST(OBJECT_CONSTRUCT('x',OBJECT_CONSTRUCT('y','a')) AS OBJECT(x OBJECT(y VARCHAR)))";

    /** A structured ARRAY of structured OBJECTs. */
    private static final String ARR_OBJ =
        "CAST([OBJECT_CONSTRUCT('y','a')] AS ARRAY(OBJECT(y VARCHAR)))";

    private Object scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getRows().get(0).getValue(0);
    }

    /**
     * The first cell's JSON text with layout whitespace removed, so the assertions read the same
     * against the embedded engine (compact) and a live account (which pretty-prints unless
     * JSON_INDENT is 0). No value in this class contains a space inside a string, so this is
     * whitespace-only normalization.
     */
    private String json(final String sql) {
        return String.valueOf(scalar(sql)).replace(" ", "").replace("\n", "");
    }

    private void assertRejected(final String sql, final String expectedFragment) {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(String.valueOf(error.getMessage()).contains(expectedFragment),
            "expected \"" + expectedFragment + "\" in: " + error.getMessage());
    }

    // ------------------------------------------------------------------
    // Casting TO a structured type, without a modifier
    // ------------------------------------------------------------------

    /** A plain semi-structured value casts to a structured type when its key set matches exactly. */
    @Test
    public void plainCastToAStructuredTypeStillWorks() {
        // Live: CAST(OBJECT_CONSTRUCT('x','a') AS OBJECT(x VARCHAR)) and the :: spelling both yield
        // {"x":"a"}; CAST([1,2] AS ARRAY(INT)) is [1,2]; a plain OBJECT casts to MAP unchanged.
        assertEquals("{\"x\":\"a\"}", json("SELECT CAST(OBJECT_CONSTRUCT('x','a') AS OBJECT(x VARCHAR))"));
        assertEquals("{\"x\":\"a\"}", json("SELECT OBJECT_CONSTRUCT('x','a')::OBJECT(x VARCHAR)"));
        assertEquals("[1,2,3]", json("SELECT CAST([1, 2, 3] AS ARRAY(INT))"));
        assertEquals("{\"k\":\"v\"}",
            json("SELECT CAST(OBJECT_CONSTRUCT('k','v') AS MAP(VARCHAR,VARCHAR))"));
    }

    /** The declared field/element types are APPLIED to the values, not merely recorded. */
    @Test
    public void castToAStructuredTypeConvertsTheValues() {
        // Live: CAST(OBJECT_CONSTRUCT('x','5') AS OBJECT(x INT)) is {"x":5} (the string became a
        // number), the reverse is {"x":"5"}, MAP(VARCHAR,INT) over '5' is {"k":5}, and casting a
        // structured ARRAY(INT) to ARRAY(VARCHAR) yields ["1","2"].
        assertEquals("{\"x\":5}", json("SELECT CAST(OBJECT_CONSTRUCT('x','5') AS OBJECT(x INT))"));
        assertEquals("{\"x\":5}",
            json("SELECT CAST(CAST(OBJECT_CONSTRUCT('x','5') AS OBJECT(x VARCHAR)) AS OBJECT(x INT))"));
        assertEquals("{\"x\":\"5\"}",
            json("SELECT CAST(CAST(OBJECT_CONSTRUCT('x',5) AS OBJECT(x INT)) AS OBJECT(x VARCHAR))"));
        assertEquals("{\"k\":5}", json("SELECT CAST(OBJECT_CONSTRUCT('k','5') AS MAP(VARCHAR,INT))"));
        assertEquals("[\"1\",\"2\"]",
            json("SELECT CAST(CAST([1,2] AS ARRAY(INT)) AS ARRAY(VARCHAR))"));
    }

    /** A value that will not convert to its declared field type is a runtime schema mismatch. */
    @Test
    public void unconvertibleFieldValueIsRejected() {
        // Live: CAST(OBJECT_CONSTRUCT('x','a') AS OBJECT(x INT)) fails "Typed object schema mismatch
        // in conversion", and so does CAST(['a','b'] AS ARRAY(INT)).
        assertRejected("SELECT CAST(OBJECT_CONSTRUCT('x','a') AS OBJECT(x INT))",
            "Typed object schema mismatch in conversion");
        assertRejected("SELECT CAST(['a','b'] AS ARRAY(INT))",
            "Typed object schema mismatch in conversion");
    }

    /** The value's key set must match the declared fields EXACTLY — no extras, none missing. */
    @Test
    public void castToAStructuredObjectRequiresAnExactKeySet() {
        // Live: a missing field (OBJECT(x VARCHAR, q INT) over {"x":"a"}), an extra key
        // (OBJECT(x VARCHAR) over {"x":"a","extra":9}) and an empty OBJECT_CONSTRUCT() all fail
        // "Typed object schema mismatch in conversion".
        assertRejected("SELECT CAST(OBJECT_CONSTRUCT('x','a') AS OBJECT(x VARCHAR, q INT))",
            "Typed object schema mismatch in conversion");
        assertRejected("SELECT CAST(OBJECT_CONSTRUCT('x','a','extra',9) AS OBJECT(x VARCHAR))",
            "Typed object schema mismatch in conversion");
        assertRejected("SELECT CAST(OBJECT_CONSTRUCT() AS OBJECT(x VARCHAR))",
            "Typed object schema mismatch in conversion");
    }

    /** A NOT NULL field will not take a null value. */
    @Test
    public void notNullFieldRejectsANullValue() {
        // Live: CAST(OBJECT_CONSTRUCT('x',NULL) AS OBJECT(x VARCHAR NOT NULL)) and
        // CAST(<OBJECT(x VARCHAR)> AS OBJECT(x VARCHAR, z INT NOT NULL) ADD FIELDS) both fail
        // "Typed object schema mismatch in conversion".
        assertRejected("SELECT CAST(OBJECT_CONSTRUCT('x',NULL) AS OBJECT(x VARCHAR NOT NULL))",
            "Typed object schema mismatch in conversion");
        assertRejected("SELECT CAST(" + OBJ_X + " AS OBJECT(x VARCHAR, z INT NOT NULL) ADD FIELDS)",
            "Typed object schema mismatch in conversion");
    }

    /** Structured field names keep their spelling — they are NOT folded like SQL identifiers. */
    @Test
    public void structuredFieldNamesAreCaseSensitive() {
        // Live: CAST(<OBJECT(x VARCHAR)> AS OBJECT(X VARCHAR) RENAME FIELDS) is {"X":"a"} — the
        // unquoted X stayed upper case — while CAST(OBJECT_CONSTRUCT('x','a') AS OBJECT(X VARCHAR))
        // FAILS, because the declared X does not match the key x.
        assertEquals("{\"X\":\"a\"}",
            json("SELECT CAST(" + OBJ_X + " AS OBJECT(X VARCHAR) RENAME FIELDS)"));
        assertRejected("SELECT CAST(OBJECT_CONSTRUCT('x','a') AS OBJECT(X VARCHAR))",
            "Typed object schema mismatch in conversion");
        assertEquals("{\"X\":\"a\"}",
            json("SELECT CAST(OBJECT_CONSTRUCT('X','a') AS OBJECT(\"X\" VARCHAR))"));
    }

    /** A field name declared twice is rejected outright. */
    @Test
    public void duplicateFieldNameIsRejected() {
        // Live: CAST(OBJECT_CONSTRUCT('x','a') AS OBJECT(x VARCHAR, x INT)) fails
        // "SQL compilation error:\n... Duplicate field name 'x'".
        assertRejected("SELECT CAST(OBJECT_CONSTRUCT('x','a') AS OBJECT(x VARCHAR, x INT))",
            "Duplicate field name 'x'");
        assertRejected("SELECT CAST(" + OBJ_XY + " AS OBJECT(p VARCHAR, p INT) RENAME FIELDS)",
            "Duplicate field name 'p'");
    }

    /** SQL NULL stays SQL NULL through a structured cast, modifier or not. */
    @Test
    public void nullStaysNull() {
        // Live: NULL::OBJECT(x VARCHAR) is NULL, and so is
        // CAST(NULL::OBJECT(x VARCHAR) AS OBJECT(y VARCHAR) RENAME FIELDS).
        assertNull(scalar("SELECT NULL::OBJECT(x VARCHAR)"));
        assertNull(scalar("SELECT CAST(NULL::OBJECT(x VARCHAR) AS OBJECT(y VARCHAR) RENAME FIELDS)"));
    }

    // ------------------------------------------------------------------
    // RENAME FIELDS
    // ------------------------------------------------------------------

    /** RENAME FIELDS maps the source's fields POSITIONALLY onto the target's names. */
    @Test
    public void renameFieldsIsPositional() {
        // Live: <OBJECT(x VARCHAR, y INT)> renamed to OBJECT(p VARCHAR, q INT) is {"p":"a","q":1};
        // renamed to OBJECT(y VARCHAR, x INT) it is {"y":"a","x":1} — the value of x landed in the
        // field now called y, proving the mapping is by POSITION and not by name.
        assertEquals("{\"p\":\"a\",\"q\":1}",
            json("SELECT CAST(" + OBJ_XY + " AS OBJECT(p VARCHAR, q INT) RENAME FIELDS)"));
        assertEquals("{\"y\":\"a\",\"x\":1}",
            json("SELECT CAST(" + OBJ_XY + " AS OBJECT(y VARCHAR, x INT) RENAME FIELDS)"));
        assertEquals("{\"x\":\"a\",\"y\":1}",
            json("SELECT CAST(" + OBJ_XY + " AS OBJECT(x VARCHAR, y INT) RENAME FIELDS)"));
    }

    /** The renamed fields are converted to the target field types too. */
    @Test
    public void renameFieldsConvertsToTheTargetFieldTypes() {
        // Live: <OBJECT(x VARCHAR, y INT)> holding {"x":"5","y":1}, renamed to OBJECT(p INT, q VARCHAR),
        // is {"p":5,"q":"1"}.
        assertEquals("{\"p\":5,\"q\":\"1\"}",
            json("SELECT CAST(" + OBJ_X5Y + " AS OBJECT(p INT, q VARCHAR) RENAME FIELDS)"));
        // ... and a value that will not convert positionally fails at run time, as live does for
        // OBJECT(p INT, q VARCHAR) over {"x":"a","y":1}.
        assertRejected("SELECT CAST(" + OBJ_XY + " AS OBJECT(p INT, q VARCHAR) RENAME FIELDS)",
            "Typed object schema mismatch in conversion");
    }

    /** Being positional, RENAME FIELDS needs the same number of fields on both sides. */
    @Test
    public void renameFieldsRequiresEqualFieldCounts() {
        // Live: 2 fields -> 1 field and 1 field -> 2 fields both fail
        // "incompatible types: [STRUCTURED_OBJECT] and [STRUCTURED_OBJECT]".
        assertRejected("SELECT CAST(" + OBJ_XY + " AS OBJECT(p VARCHAR) RENAME FIELDS)",
            "incompatible types: [STRUCTURED_OBJECT] and [STRUCTURED_OBJECT]");
        assertRejected("SELECT CAST(" + OBJ_X + " AS OBJECT(p VARCHAR, q INT) RENAME FIELDS)",
            "incompatible types: [STRUCTURED_OBJECT] and [STRUCTURED_OBJECT]");
    }

    // ------------------------------------------------------------------
    // ADD FIELDS
    // ------------------------------------------------------------------

    /** ADD FIELDS matches BY NAME, fills the new fields with NULL and keeps the TARGET's order. */
    @Test
    public void addFieldsMatchesByNameAndDefaultsNewFieldsToNull() {
        // Live: <OBJECT(x VARCHAR)> to OBJECT(x VARCHAR, z INT) ADD FIELDS is {"x":"a","z":null};
        // adding two is {"x":"a","z":null,"w":null}; declaring the NEW field FIRST gives
        // {"z":null,"x":"a"} — the result follows the target's declaration order while x still found
        // its value by name.
        assertEquals("{\"x\":\"a\",\"z\":null}",
            json("SELECT CAST(" + OBJ_X + " AS OBJECT(x VARCHAR, z INT) ADD FIELDS)"));
        assertEquals("{\"x\":\"a\",\"z\":null,\"w\":null}",
            json("SELECT CAST(" + OBJ_X + " AS OBJECT(x VARCHAR, z INT, w VARCHAR) ADD FIELDS)"));
        assertEquals("{\"z\":null,\"x\":\"a\"}",
            json("SELECT CAST(" + OBJ_X + " AS OBJECT(z INT, x VARCHAR) ADD FIELDS)"));
        assertEquals("{\"x\":\"a\",\"y\":1,\"z\":null}",
            json("SELECT CAST(" + OBJ_XY + " AS OBJECT(x VARCHAR, y INT, z VARCHAR) ADD FIELDS)"));
    }

    /** ADD FIELDS only adds: the target must still declare every field the source has. */
    @Test
    public void addFieldsRequiresTheTargetToKeepEverySourceField() {
        // Live: dropping x (OBJECT(z INT) ADD FIELDS over <OBJECT(x VARCHAR)>), dropping y, and
        // renaming y to q all fail "incompatible types: [STRUCTURED_OBJECT] and [STRUCTURED_OBJECT]".
        assertRejected("SELECT CAST(" + OBJ_X + " AS OBJECT(z INT) ADD FIELDS)",
            "incompatible types: [STRUCTURED_OBJECT] and [STRUCTURED_OBJECT]");
        assertRejected("SELECT CAST(" + OBJ_XY + " AS OBJECT(x VARCHAR, z INT) ADD FIELDS)",
            "incompatible types: [STRUCTURED_OBJECT] and [STRUCTURED_OBJECT]");
        assertRejected("SELECT CAST(" + OBJ_XY + " AS OBJECT(x VARCHAR, q INT, z INT) ADD FIELDS)",
            "incompatible types: [STRUCTURED_OBJECT] and [STRUCTURED_OBJECT]");
    }

    /** Adding nothing is legal — the target may simply repeat the source's fields. */
    @Test
    public void addFieldsWithNoNewFieldIsAllowed() {
        // Live: CAST(<OBJECT(x VARCHAR, y INT)> AS OBJECT(x VARCHAR, y INT) ADD FIELDS) is the value
        // unchanged, and reordering the existing fields follows the target's order.
        assertEquals("{\"x\":\"5\",\"y\":1}",
            json("SELECT CAST(" + OBJ_X5Y + " AS OBJECT(x VARCHAR, y INT) ADD FIELDS)"));
        assertEquals("{\"y\":1,\"x\":\"5\",\"z\":null}",
            json("SELECT CAST(" + OBJ_X5Y + " AS OBJECT(y INT, x VARCHAR, z INT) ADD FIELDS)"));
    }

    // ------------------------------------------------------------------
    // Where the modifiers are legal at all
    // ------------------------------------------------------------------

    /** Both modifiers demand a STRUCTURED source — a plain OBJECT / VARIANT / ARRAY is a compile error. */
    @Test
    public void fieldModifiersRequireAStructuredSource() {
        // Live: every one of these fails "Function CAST <MODIFIER> FIELDS cannot be used with
        // arguments of types <source> and <target>" — a plain OBJECT_CONSTRUCT, a PARSE_JSON VARIANT,
        // a plain array literal, a plain OBJECT cast to MAP, and an untyped NULL.
        assertRejected("SELECT CAST(OBJECT_CONSTRUCT('x','a') AS OBJECT(y VARCHAR) RENAME FIELDS)",
            "Function CAST RENAME FIELDS cannot be used with arguments of types");
        assertRejected("SELECT CAST(OBJECT_CONSTRUCT('x','a') AS OBJECT(x VARCHAR, z INT) ADD FIELDS)",
            "Function CAST ADD FIELDS cannot be used with arguments of types");
        assertRejected("SELECT CAST(PARSE_JSON('{\"x\":\"a\"}') AS OBJECT(y VARCHAR) RENAME FIELDS)",
            "Function CAST RENAME FIELDS cannot be used with arguments of types");
        assertRejected("SELECT CAST([1,2] AS ARRAY(INT) RENAME FIELDS)",
            "Function CAST RENAME FIELDS cannot be used with arguments of types");
        assertRejected("SELECT CAST([1,2] AS ARRAY(INT) ADD FIELDS)",
            "Function CAST ADD FIELDS cannot be used with arguments of types");
        assertRejected("SELECT CAST(OBJECT_CONSTRUCT('k','v') AS MAP(VARCHAR,VARCHAR) RENAME FIELDS)",
            "Function CAST RENAME FIELDS cannot be used with arguments of types");
        assertRejected("SELECT CAST(NULL AS OBJECT(y VARCHAR) RENAME FIELDS)",
            "Function CAST RENAME FIELDS cannot be used with arguments of types");
    }

    /** ... and a STRUCTURED target: a plain OBJECT or VARIANT target is a compile error too. */
    @Test
    public void fieldModifiersRequireAStructuredTarget() {
        // Live: CAST(<OBJECT(x VARCHAR, y INT)> AS OBJECT RENAME FIELDS) fails "... cannot be used
        // with arguments of types OBJECT(x VARCHAR(134217728), y NUMBER(38,0)) and OBJECT", and the
        // VARIANT target fails the same way.
        assertRejected("SELECT CAST(" + OBJ_XY + " AS OBJECT RENAME FIELDS)",
            "Function CAST RENAME FIELDS cannot be used with arguments of types");
        assertRejected("SELECT CAST(" + OBJ_XY + " AS VARIANT RENAME FIELDS)",
            "Function CAST RENAME FIELDS cannot be used with arguments of types");
    }

    /** A structured ARRAY and a structured OBJECT are different families and do not cast across. */
    @Test
    public void fieldModifiersRequireTheSameStructuredFamily() {
        // Live: CAST(<ARRAY(INT)> AS OBJECT(x INT) RENAME FIELDS) fails
        // "incompatible types: [STRUCTURED_ARRAY] and [STRUCTURED_OBJECT]".
        assertRejected("SELECT CAST(CAST([1,2] AS ARRAY(INT)) AS OBJECT(x INT) RENAME FIELDS)",
            "incompatible types: [STRUCTURED_ARRAY] and [STRUCTURED_OBJECT]");
    }

    /** The modifier is rejected at COMPILE time — before any row is read. */
    @Test
    public void fieldModifierRejectionIsCompileTime() {
        // Live: the error is a "SQL compilation error", so it fires even where no row can reach the
        // expression — over an EMPTY table, behind a WHERE FALSE, and inside a WHERE that is AND FALSE.
        // (Snowflake also rejects it inside a CASE arm guarded by WHEN FALSE; the engine's plan-time
        // walk does not descend into CASE branches, which is a difference in WHEN the error fires, not
        // in whether the expression is legal.)
        engine.execute("CREATE TABLE empty_rows (i INT)");
        assertRejected("""
            SELECT CAST(OBJECT_CONSTRUCT('x','a') AS OBJECT(y VARCHAR) RENAME FIELDS) FROM empty_rows""",
            "Function CAST RENAME FIELDS cannot be used with arguments of types");
        assertRejected("""
            SELECT CAST(OBJECT_CONSTRUCT('x','a') AS OBJECT(y VARCHAR) RENAME FIELDS)
                FROM empty_rows WHERE FALSE""",
            "Function CAST RENAME FIELDS cannot be used with arguments of types");
        assertRejected("""
            SELECT 1 WHERE CAST(OBJECT_CONSTRUCT('x','a') AS OBJECT(y VARCHAR) RENAME FIELDS)
                IS NOT NULL AND FALSE""",
            "Function CAST RENAME FIELDS cannot be used with arguments of types");
    }

    /** Without a modifier, a structured-to-structured OBJECT cast must keep the same field layout. */
    @Test
    public void structuredToStructuredWithoutAModifierKeepsTheFieldLayout() {
        // Live: narrowing, widening and renaming without a modifier all fail "incompatible types:
        // [STRUCTURED_OBJECT] and [STRUCTURED_OBJECT]" — only the LEAF types may change.
        assertRejected("SELECT CAST(" + OBJ_XY + " AS OBJECT(x VARCHAR))",
            "incompatible types: [STRUCTURED_OBJECT] and [STRUCTURED_OBJECT]");
        assertRejected("SELECT CAST(" + OBJ_X + " AS OBJECT(x VARCHAR, z INT))",
            "incompatible types: [STRUCTURED_OBJECT] and [STRUCTURED_OBJECT]");
        assertRejected("SELECT CAST(" + OBJ_XY + " AS OBJECT(p VARCHAR, q INT))",
            "incompatible types: [STRUCTURED_OBJECT] and [STRUCTURED_OBJECT]");
        assertEquals("{\"x\":\"5\",\"y\":\"1\"}",
            json("SELECT CAST(" + OBJ_X5Y + " AS OBJECT(x VARCHAR, y VARCHAR))"));
    }

    // ------------------------------------------------------------------
    // Nesting, MAP and ARRAY behaviour of the modifiers
    // ------------------------------------------------------------------

    /** The modifiers recurse into nested structured fields and into structured ARRAY elements. */
    @Test
    public void fieldModifiersRecurseIntoNestedStructures() {
        // Live: renaming the outer AND inner field of OBJECT(x OBJECT(y VARCHAR)) gives
        // {"p":{"q":"a"}}; ADD FIELDS on the inner and outer level gives
        // {"x":{"y":"a","z":null},"w":null}; and over ARRAY(OBJECT(y VARCHAR)) the element objects are
        // reshaped: [{"y":"a","z":null}] for ADD and [{"q":"a"}] for RENAME.
        assertEquals("{\"p\":{\"q\":\"a\"}}",
            json("SELECT CAST(" + OBJ_NESTED + " AS OBJECT(p OBJECT(q VARCHAR)) RENAME FIELDS)"));
        assertEquals("{\"x\":{\"y\":\"a\",\"z\":null},\"w\":null}",
            json("SELECT CAST(" + OBJ_NESTED
                + " AS OBJECT(x OBJECT(y VARCHAR, z INT), w INT) ADD FIELDS)"));
        assertEquals("[{\"y\":\"a\",\"z\":null}]",
            json("SELECT CAST(" + ARR_OBJ + " AS ARRAY(OBJECT(y VARCHAR, z INT)) ADD FIELDS)"));
        assertEquals("[{\"q\":\"a\"}]",
            json("SELECT CAST(" + ARR_OBJ + " AS ARRAY(OBJECT(q VARCHAR)) RENAME FIELDS)"));
    }

    /** The nested field layout is checked without a modifier, at every level. */
    @Test
    public void nestedFieldLayoutIsCheckedWithoutAModifier() {
        // Live: adding an inner field, or renaming an array element's field, without a modifier fails
        // "incompatible types: [STRUCTURED_OBJECT] and [STRUCTURED_OBJECT]".
        assertRejected("SELECT CAST(" + OBJ_NESTED + " AS OBJECT(x OBJECT(y VARCHAR, z INT)))",
            "incompatible types: [STRUCTURED_OBJECT] and [STRUCTURED_OBJECT]");
        assertRejected("SELECT CAST(" + ARR_OBJ + " AS ARRAY(OBJECT(q VARCHAR)))",
            "incompatible types: [STRUCTURED_OBJECT] and [STRUCTURED_OBJECT]");
    }

    /** A MAP has no declared field names, so the modifiers have nothing to do at that level. */
    @Test
    public void mapTargetIgnoresTheFieldModifiers() {
        // Live: MAP -> MAP with RENAME FIELDS or ADD FIELDS is the unchanged map, and a structured
        // OBJECT -> MAP with RENAME FIELDS keeps the object's keys.
        final String map = "CAST(OBJECT_CONSTRUCT('k','v') AS MAP(VARCHAR,VARCHAR))";
        assertEquals("{\"k\":\"v\"}",
            json("SELECT CAST(" + map + " AS MAP(VARCHAR,VARCHAR) RENAME FIELDS)"));
        assertEquals("{\"k\":\"v\"}",
            json("SELECT CAST(" + map + " AS MAP(VARCHAR,VARCHAR) ADD FIELDS)"));
        assertEquals("{\"x\":\"a\"}",
            json("SELECT CAST(" + OBJ_X + " AS MAP(VARCHAR,VARCHAR) RENAME FIELDS)"));
    }

    /** A structured ARRAY takes the modifiers but only converts its elements. */
    @Test
    public void structuredArraySourceAcceptsTheModifiers() {
        // Live: CAST(<ARRAY(INT)> AS ARRAY(VARCHAR) RENAME FIELDS) and the ADD FIELDS spelling are
        // both ["1","2"] — legal because the SOURCE is structured, and a no-op beyond the element cast.
        assertEquals("[\"1\",\"2\"]",
            json("SELECT CAST(CAST([1,2] AS ARRAY(INT)) AS ARRAY(VARCHAR) RENAME FIELDS)"));
        assertEquals("[\"1\",\"2\"]",
            json("SELECT CAST(CAST([1,2] AS ARRAY(INT)) AS ARRAY(VARCHAR) ADD FIELDS)"));
    }

    // ------------------------------------------------------------------
    // TRY_CAST and the :: shorthand
    // ------------------------------------------------------------------

    /** TRY_CAST reaches structured targets but refuses the field modifiers. */
    @Test
    public void tryCastToAStructuredType() {
        // Live: TRY_CAST(<OBJECT(x VARCHAR)> AS OBJECT(x VARCHAR)) is {"x":"a"}; a key set that does
        // not fit yields NULL rather than an error; and a modifier fails "Function RENAME FIELDS
        // modifier is not supported with TRY_CAST".
        assertEquals("{\"x\":\"a\"}", json("SELECT TRY_CAST(" + OBJ_X + " AS OBJECT(x VARCHAR))"));
        assertNull(scalar("SELECT TRY_CAST(OBJECT_CONSTRUCT('x','a','e',1) AS OBJECT(x VARCHAR))"));
        assertRejected("SELECT TRY_CAST(" + OBJ_X + " AS OBJECT(y VARCHAR) RENAME FIELDS)",
            "modifier is not supported with TRY_CAST");
    }

    /** The {@code ::} shorthand has no modifier slot at all. */
    @Test
    public void doubleColonCastTakesNoFieldModifier() {
        // Live: `<expr>::OBJECT(y VARCHAR) RENAME FIELDS` is a SYNTAX error ("unexpected 'FIELDS'").
        assertRejected("SELECT OBJECT_CONSTRUCT('x','a')::OBJECT(x VARCHAR) RENAME FIELDS",
            "FIELDS");
    }

    // ------------------------------------------------------------------
    // Structured table columns
    // ------------------------------------------------------------------

    /** A column declared structured stays structured on read-back, so the modifiers apply to it. */
    @Test
    public void structuredColumnKeepsItsDeclaredShape() {
        // Live: with t(o OBJECT(x VARCHAR, y INT), po OBJECT), SYSTEM$TYPEOF(o) reports
        // OBJECT(x VARCHAR(16777216), y NUMBER(38,0)) while SYSTEM$TYPEOF(po) is plain OBJECT — so
        // CAST(o AS ... RENAME FIELDS) works and CAST(po AS ... RENAME FIELDS) is rejected.
        engine.execute("CREATE TABLE structured_cols (o OBJECT(x VARCHAR, y INT), po OBJECT)");
        engine.execute("INSERT INTO structured_cols SELECT " + OBJ_XY + ", OBJECT_CONSTRUCT('x','a')");
        assertEquals("{\"x\":\"a\",\"y\":1}", json("SELECT o FROM structured_cols"));
        assertEquals("{\"p\":\"a\",\"q\":1}",
            json("SELECT CAST(o AS OBJECT(p VARCHAR, q INT) RENAME FIELDS) FROM structured_cols"));
        assertEquals("{\"x\":\"a\",\"y\":1,\"z\":null}",
            json("SELECT CAST(o AS OBJECT(x VARCHAR, y INT, z INT) ADD FIELDS) FROM structured_cols"));
        assertRejected("SELECT CAST(po AS OBJECT(p VARCHAR) RENAME FIELDS) FROM structured_cols",
            "Function CAST RENAME FIELDS cannot be used with arguments of types");
    }

    @Test
    public void zeroFieldObjectIsItsOwnStructuredType() {
        // Live-verified: `OBJECT()` is legal and distinct — an EMPTY object casts to it, a NON-empty one
        // fails the schema check, NULL::OBJECT() is NULL, and it is a legal ADD FIELDS source.
        assertEquals("{}", String.valueOf(scalar("SELECT CAST(OBJECT_CONSTRUCT() AS OBJECT())")));
        assertEquals("{}", String.valueOf(scalar("SELECT OBJECT_CONSTRUCT()::OBJECT()")));
        assertEquals("{}", String.valueOf(scalar("SELECT CAST(PARSE_JSON('{}') AS OBJECT())")));
        assertNull(scalar("SELECT NULL::OBJECT()"));
        assertEquals("{\"z\":null}", String.valueOf(scalar(
            "SELECT CAST(CAST(OBJECT_CONSTRUCT() AS OBJECT()) AS OBJECT(z INT) ADD FIELDS)")));
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT CAST(OBJECT_CONSTRUCT('x','a') AS OBJECT())");
            }
        }, "a non-empty object does not fit the zero-field schema");
    }

    @Test
    public void emptyTypeParenthesesAreACharacterTypeLeniencyOnly() {
        // Live-verified: VARCHAR() and NVARCHAR() cast fine, while ARRAY(), MAP() and NUMBER() are
        // syntax errors. OBJECT() is separate — it is the zero-field STRUCTURED type, not empty params.
        assertEquals("x", String.valueOf(scalar("SELECT CAST('x' AS VARCHAR())")));
        assertEquals("x", String.valueOf(scalar("SELECT CAST('x' AS NVARCHAR())")));
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT CAST([1,2] AS ARRAY())");
            }
        });
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT CAST(OBJECT_CONSTRUCT() AS MAP())");
            }
        });
    }
}
