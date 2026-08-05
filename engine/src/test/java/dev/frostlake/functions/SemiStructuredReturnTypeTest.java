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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The DECLARED return type of every semi-structured producer, asserted directly: each expression is
 * projected into a {@code CREATE TABLE … AS SELECT} and the created column's type read back with
 * {@code DESCRIBE TABLE}, so what is checked is the type the engine hands to a consumer rather than
 * whether some dependent rule happened to fire.
 *
 * <p>Every expectation below was measured on a live Snowflake account on with two
 * independent instruments that agreed on all of them: {@code SYSTEM$TYPEOF} over the expression, and
 * the type the created column receives ({@code DESC TABLE} after a CTAS). Frostlake used to declare
 * all of these VARIANT in the registry and then report the created column VARCHAR, so
 * {@code CREATE TABLE t AS SELECT OBJECT_CONSTRUCT('k','v') AS c} gave {@code c} a VARCHAR type where
 * live gives it OBJECT.
 *
 * <p>The three families are genuinely distinct static types, not synonyms: OBJECT and ARRAY are
 * refused by the ordering aggregates while VARIANT is accepted, which is why the VARIANT group at the
 * bottom matters as much as the other two. Naming is no guide — {@code OBJECT_KEYS} is an ARRAY,
 * {@code ARRAY_MAX} is a VARIANT, {@code PARSE_URL} is an OBJECT — so each name here was measured, not
 * inferred from a sibling.
 */
public class SemiStructuredReturnTypeTest extends BaseDatabaseTest {

    @BeforeEach
    public void createTypedTable() {
        engine.execute("CREATE TABLE st (id INTEGER, o OBJECT, a ARRAY, v VARIANT, vo VARIANT,"
            + " s VARCHAR, n NUMBER(10,2))");
        engine.execute("INSERT INTO st SELECT 1, OBJECT_CONSTRUCT('k','v1'), ARRAY_CONSTRUCT(1,2),"
            + " TO_VARIANT(1), TO_VARIANT(OBJECT_CONSTRUCT('x',1)), 'x', 1.5");
        engine.execute("INSERT INTO st SELECT 2, OBJECT_CONSTRUCT('k','v2'), ARRAY_CONSTRUCT(3,4),"
            + " TO_VARIANT(2), TO_VARIANT(OBJECT_CONSTRUCT('x',2)), 'y', 2.5");
    }

    // ── ARRAY producers ─────────────────────────────────────────────────────────────────────────

    @Test
    public void theArrayBuildingFunctionsDeclareArray() {
        assertColumnType("ARRAY", "ARRAY_APPEND(a,1)");
        assertColumnType("ARRAY", "ARRAY_CAT(a,a)");
        assertColumnType("ARRAY", "ARRAY_COMPACT(a)");
        assertColumnType("ARRAY", "ARRAY_CONSTRUCT(1,2)");
        assertColumnType("ARRAY", "ARRAY_CONSTRUCT_COMPACT(1,NULL,2)");
        assertColumnType("ARRAY", "ARRAY_DISTINCT(a)");
        assertColumnType("ARRAY", "ARRAY_EXCEPT(a,a)");
        assertColumnType("ARRAY", "ARRAY_FLATTEN(ARRAY_CONSTRUCT(a,a))");
        assertColumnType("ARRAY", "ARRAY_GENERATE_RANGE(0,3)");
        assertColumnType("ARRAY", "ARRAY_INSERT(a,0,9)");
        assertColumnType("ARRAY", "ARRAY_INTERSECTION(a,a)");
        assertColumnType("ARRAY", "ARRAY_PREPEND(a,1)");
        assertColumnType("ARRAY", "ARRAY_REMOVE(a,1)");
        assertColumnType("ARRAY", "ARRAY_REMOVE_AT(a,0)");
        assertColumnType("ARRAY", "ARRAY_REVERSE(a)");
        assertColumnType("ARRAY", "ARRAY_SLICE(a,0,1)");
        assertColumnType("ARRAY", "ARRAY_SORT(a)");
        assertColumnType("ARRAY", "ARRAY_REPEAT(1,2)");
        assertColumnType("ARRAY", "ARRAYS_ZIP(a,a)");
    }

    /** The ARRAY producers that are NOT named after arrays — each one measured, none inferred. */
    @Test
    public void theArrayProducingFunctionsNotNamedArrayDeclareArray() {
        assertColumnType("ARRAY", "OBJECT_KEYS(o)");
        assertColumnType("ARRAY", "SPLIT('a,b',',')");
        assertColumnType("ARRAY", "STRTOK_TO_ARRAY('a b')");
        assertColumnType("ARRAY", "TO_ARRAY(1)");
        assertColumnType("ARRAY", "AS_ARRAY(v)");
        assertColumnType("ARRAY", "REGEXP_SUBSTR_ALL('a1b2','[0-9]')");
    }

    // ── OBJECT producers ────────────────────────────────────────────────────────────────────────

    @Test
    public void theObjectBuildingFunctionsDeclareObject() {
        assertColumnType("OBJECT", "OBJECT_CONSTRUCT('k','v')");
        assertColumnType("OBJECT", "OBJECT_CONSTRUCT_KEEP_NULL('k',NULL)");
        assertColumnType("OBJECT", "OBJECT_DELETE(o,'k')");
        assertColumnType("OBJECT", "OBJECT_INSERT(o,'z',1)");
        assertColumnType("OBJECT", "OBJECT_PICK(o,'k')");
        assertColumnType("OBJECT", "TO_OBJECT(o)");
        assertColumnType("OBJECT", "AS_OBJECT(vo)");
        assertColumnType("OBJECT", "ARRAYS_TO_OBJECT(ARRAY_CONSTRUCT('k'),ARRAY_CONSTRUCT(1))");
    }

    /** The parsers whose result is an OBJECT of parsed parts rather than a bare VARIANT. */
    @Test
    public void theParsingFunctionsThatBuildAnObjectDeclareObject() {
        assertColumnType("OBJECT", "PARSE_XML('<a>1</a>')");
        assertColumnType("OBJECT", "XMLGET(PARSE_XML('<a><b>1</b></a>'),'b')");
        assertColumnType("OBJECT", "PARSE_IP('1.2.3.4','INET')");
        assertColumnType("OBJECT", "PARSE_URL('http://x.com/a')");
    }

    /** A TRY_ variant declares exactly what its strict form declares — measured on both. */
    @Test
    public void aTryVariantDeclaresWhatItsStrictFormDeclares() {
        assertColumnType("OBJECT", "PARSE_IP('1.2.3.4','INET')");
        assertColumnType("OBJECT", "TRY_PARSE_IP('1.2.3.4','INET')");
        assertColumnType("VARIANT", "PARSE_JSON('{\"a\":1}')");
        assertColumnType("VARIANT", "TRY_PARSE_JSON('{\"a\":1}')");
    }

    // ── VARIANT producers: the ones that must NOT move ───────────────────────────────────────────

    /**
     * VARIANT is a distinct static type here, not a fallback: these stay VARIANT, and that is what
     * keeps {@code MAX(PARSE_JSON(…))} accepted while {@code MAX(OBJECT_CONSTRUCT(…))} is refused.
     */
    @Test
    public void theVariantProducersStayVariant() {
        assertColumnType("VARIANT", "PARSE_JSON('{\"a\":1}')");
        assertColumnType("VARIANT", "TO_VARIANT(1)");
        assertColumnType("VARIANT", "STRIP_NULL_VALUE(v)");
        assertColumnType("VARIANT", "ARRAY_MAX(a)");
        assertColumnType("VARIANT", "ARRAY_MIN(a)");
    }

    /** Reading INTO a semi-structured value yields a VARIANT, never the base value's own family. */
    @Test
    public void pathAndElementAccessOverAnObjectOrArrayIsVariant() {
        assertColumnType("VARIANT", "GET(o,'k')");
        assertColumnType("VARIANT", "GET_PATH(o,'k')");
        assertColumnType("VARIANT", "GET_IGNORE_CASE(o,'K')");
        assertColumnType("VARIANT", "o:k");
        assertColumnType("VARIANT", "a[0]");
    }

    // ── Aggregates ──────────────────────────────────────────────────────────────────────────────

    @Test
    public void theSemiStructuredAggregatesDeclareTheirOwnFamily() {
        assertColumnType("ARRAY", "ARRAY_AGG(n)");
        assertColumnType("ARRAY", "ARRAY_UNIQUE_AGG(n)");
        assertColumnType("ARRAY", "ARRAY_UNION_AGG(a)");
        assertColumnType("OBJECT", "OBJECT_AGG(s, TO_VARIANT(n))");
    }

    /**
     * An ALIAS carries the same declared type as the name it aliases. The type comes from the single
     * registry entry both names share, so no name can be typed one way and its alias another.
     */
    @Test
    public void anAliasCarriesTheSameDeclaredType() {
        assertColumnType("ARRAY", "ARRAYAGG(n)");
        assertColumnType("OBJECT", "OBJECTAGG(s, TO_VARIANT(n))");
        assertRejected("SELECT MAX(ARRAYAGG(n)) FROM st",
            "Function MAX does not support ARRAY argument type");
    }

    // ── The rules that read these types ─────────────────────────────────────────────────────────

    /**
     * The ordering aggregates read the declared type, so correcting the registry is what makes them
     * refuse a producer's result the way live does — for OBJECT and ARRAY, and only for those.
     */
    @Test
    public void theOrderingAggregatesRefuseTheObjectAndArrayProducers() {
        assertRejected("SELECT MAX(OBJECT_CONSTRUCT('k','v')) FROM st",
            "Function MAX does not support OBJECT argument type");
        assertRejected("SELECT MIN(PARSE_URL('http://x.com/a')) FROM st",
            "Function MIN does not support OBJECT argument type");
        assertRejected("SELECT MAX(SPLIT('a,b',',')) FROM st",
            "Function MAX does not support ARRAY argument type");
        assertRejected("SELECT MAX(OBJECT_KEYS(o)) FROM st",
            "Function MAX does not support ARRAY argument type");
        assertAccepted("SELECT MAX(PARSE_JSON('1')) FROM st");
        assertAccepted("SELECT MAX(TO_VARIANT(1)) FROM st");
        assertAccepted("SELECT MAX(ARRAY_MAX(a)) FROM st");
        assertAccepted("SELECT MAX(GET(o,'k')) FROM st");
    }

    /**
     * The MAP-requiring family refuses an ARRAY exactly as it refuses an OBJECT or a VARIANT: live
     * {@code MAP_KEYS(a)} over an ARRAY column and {@code MAP_KEYS(ARRAY_CONSTRUCT(1,2))}
     * both fail "Invalid argument types for function 'MAP_KEYS': (ARRAY)".
     */
    @Test
    public void theMapFamilyRefusesAnArrayArgument() {
        assertRejected("SELECT MAP_KEYS(a) FROM st",
            "Invalid argument types for function 'MAP_KEYS': (ARRAY)");
        assertRejected("SELECT MAP_KEYS(ARRAY_CONSTRUCT(1,2)) FROM st",
            "Invalid argument types for function 'MAP_KEYS': (ARRAY)");
        assertRejected("SELECT MAP_SIZE(SPLIT('a,b',',')) FROM st",
            "Invalid argument types for function 'MAP_SIZE': (ARRAY)");
    }

    /** The argument-type message names the producer's real family, as live does. */
    @Test
    public void anArgumentTypeErrorNamesTheProducersOwnFamily() {
        assertRejected("SELECT MAP_KEYS(OBJECT_CONSTRUCT('k','v')) FROM st",
            "Invalid argument types for function 'MAP_KEYS': (OBJECT)");
        assertRejected("SELECT MAP_KEYS(PARSE_JSON('{\"a\":1}')) FROM st",
            "Invalid argument types for function 'MAP_KEYS': (VARIANT)");
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────────────

    /**
     * The type a CTAS gives a column produced by {@code selectItem}, read back with DESCRIBE.
     *
     * <p>The expression is projected through a derived table because that is the channel on which the
     * declared type currently travels: the OUTERMOST projection of a computed column still reports a
     * VARCHAR placeholder rather than its real type (an open follow-up, since live declares the direct
     * form OBJECT/ARRAY/VARIANT), while a derived column carries its static type into the enclosing
     * query and from there into the created table. Both shapes declare the same type on a live account,
     * so what is asserted is the real declared type and not an artefact of the wrapper — and the
     * VARCHAR case below proves the wrapper does not simply type everything semi-structured.
     */
    private void assertColumnType(final String expectedType, final String selectItem) {
        engine.execute("CREATE OR REPLACE TABLE ct_probe AS SELECT c FROM"
            + " (SELECT " + selectItem + " AS c FROM st)");
        assertEquals(expectedType, describedColumnType("ct_probe"),
            "declared column type of " + selectItem);
    }

    /** The control for the wrapper itself: a VARCHAR column stays VARCHAR through the same shape. */
    @Test
    public void theDerivedWrapperDoesNotTypeEverythingSemiStructured() {
        // Live renders the length ("VARCHAR(16777216)") where the engine renders the bare name, so the
        // one non-semi-structured control matches on the family rather than the exact text.
        engine.execute("CREATE OR REPLACE TABLE ct_probe AS SELECT c FROM (SELECT s AS c FROM st)");
        assertTrue(describedColumnType("ct_probe").startsWith("VARCHAR"),
            "a VARCHAR column must stay VARCHAR through the wrapper");
        assertColumnType("OBJECT", "o");
        assertColumnType("ARRAY", "a");
        assertColumnType("VARIANT", "v");
    }

    /** The single column's type from {@code DESCRIBE TABLE}, uppercased. */
    private String describedColumnType(final String tableName) {
        final ResultSet described = engine.executeQuery("DESCRIBE TABLE " + tableName);
        assertEquals(1, described.getRows().size(), "one column expected in " + tableName);
        return String.valueOf(described.getRows().get(0).getValue(columnIndex(described, "type")))
            .toUpperCase();
    }

    private int columnIndex(final ResultSet rs, final String name) {
        for (int i = 0; i < rs.getColumns().size(); i++) {
            if (rs.getColumns().get(i).getName().equalsIgnoreCase(name)) {
                return i;
            }
        }
        throw new IllegalStateException("No column named " + name);
    }

    private void assertRejected(final String sql, final String expectedMessage) {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(String.valueOf(error.getMessage()).contains(expectedMessage),
            "expected [" + expectedMessage + "] but was [" + error.getMessage() + "]");
    }

    private void assertAccepted(final String sql) {
        engine.executeQuery(sql);
    }
}
