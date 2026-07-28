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

package dev.frostlake.scripting;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An ARRAY / OBJECT / VARIANT argument reaches a Python UDF as NATIVE Python dict/list values. The engine carries
 * semi-structured values as JSON text, and that text used to be bound straight into the interpreter, so the
 * handler received a unicode string and any {@code item.get(…)} / {@code .items()} / indexing failed with
 * "AttributeError: 'unicode' object has no attribute 'get'". This is the input counterpart of the conversion
 * already applied to a dict/list RETURN value.
 *
 * <p>Also covers the other half of getting an argument into a handler correctly: {@code RETURNS NULL ON NULL
 * INPUT} / {@code STRICT}, which must short-circuit to NULL without entering the body at all.
 */
public class PythonSemiStructuredArgumentTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    /** Reports the Python type name of its single argument, whatever it is declared as. */
    private void createTypeProbe(final String name, final String declaredType) {
        engine.execute("CREATE OR REPLACE FUNCTION " + name + "(v " + declaredType + ") "
            + "RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION='3.10' HANDLER='h' AS $$\n"
            + "def h(v):\n"
            + "    return type(v).__name__\n"
            + "$$");
    }

    @Test
    public void anArrayOfObjectsIsIterableWithGet() {
        engine.execute("""
            CREATE OR REPLACE FUNCTION pick_values(attributes ARRAY, name VARCHAR)
            RETURNS ARRAY LANGUAGE PYTHON RUNTIME_VERSION='3.10' HANDLER='h'
            AS $$
def h(attributes, name):
    out = []
    for item in attributes:
        if item.get('key') == name:
            out.append(item.get('value'))
    return out
            $$""");
        assertEquals("[\"20\"]", String.valueOf(scalar(
            "SELECT pick_values(PARSE_JSON('[{\"key\":\"a\",\"value\":\"20\"},{\"key\":\"b\",\"value\":\"9\"}]'), 'a')")));
    }

    @Test
    public void anObjectSupportsItems() {
        engine.execute("""
            CREATE OR REPLACE FUNCTION has_non_null(obj OBJECT)
            RETURNS BOOLEAN LANGUAGE PYTHON RUNTIME_VERSION='3.10' HANDLER='h'
            AS $$
def h(obj):
    for k, v in obj.items():
        if v is not None:
            return True
    return False
            $$""");
        assertEquals(true, scalar("SELECT has_non_null(PARSE_JSON('{\"a\":null,\"b\":1}'))"));
        assertEquals(false, scalar("SELECT has_non_null(PARSE_JSON('{\"a\":null}'))"));
    }

    @Test
    public void nestedContainersAreIndexable() {
        engine.execute("""
            CREATE OR REPLACE FUNCTION deep(v VARIANT)
            RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION='3.10' HANDLER='h'
            AS $$
def h(v):
    return str(v['a'][1]['b'])
            $$""");
        assertEquals("deep", scalar("SELECT deep(PARSE_JSON('{\"a\":[0,{\"b\":\"deep\"}]}'))"));
    }

    // ── each JSON kind maps to its Python counterpart ────────────────────────

    @Test
    public void eachJsonKindArrivesAsTheMatchingPythonType() {
        createTypeProbe("probe", "VARIANT");
        assertEquals("dict", scalar("SELECT probe(PARSE_JSON('{\"a\":1}'))"));
        assertEquals("list", scalar("SELECT probe(PARSE_JSON('[1,2]'))"));
        assertEquals("str", scalar("SELECT probe(PARSE_JSON('\"hi\"'))"));
        assertEquals("float", scalar("SELECT probe(PARSE_JSON('1.5'))"));
        assertEquals("bool", scalar("SELECT probe(PARSE_JSON('true'))"));
        assertEquals("NoneType", scalar("SELECT probe(PARSE_JSON('null'))"));
        assertEquals("NoneType", scalar("SELECT probe(NULL)"));
    }

    @Test
    public void anIntegerArrivesAsIntNotLong() {
        // Jython's `long` is a distinct type in Python 2, so binding one would make isinstance(v, int) False
        // for every integer — and the value also came back out as a float.
        createTypeProbe("probe_int", "VARIANT");
        assertEquals("int", scalar("SELECT probe_int(PARSE_JSON('7'))"));
    }

    @Test
    public void aVarcharThatLooksLikeJsonStaysAString() {
        // Only a parameter DECLARED semi-structured is converted; a VARCHAR is bound exactly as before.
        // The type is `str` — the engine embeds Python 3, as Snowflake does; it read `unicode` only
        // while Python 2 (Jython) was the runtime.
        createTypeProbe("probe_varchar", "VARCHAR");
        assertEquals("str", scalar("SELECT probe_varchar('{\"a\":1}')"));
        assertEquals("str", scalar("SELECT probe_varchar('hello')"));
    }

    @Test
    public void aRoundTripPreservesIntegersAndAddedKeys() {
        engine.execute("""
            CREATE OR REPLACE FUNCTION roundtrip(v VARIANT)
            RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION='3.10' HANDLER='h'
            AS $$
def h(v):
    v['added'] = 1
    return v
            $$""");
        assertEquals("{\"a\":1,\"added\":1}", String.valueOf(scalar("SELECT roundtrip(PARSE_JSON('{\"a\":1}'))")));
    }

    // ── RETURNS NULL ON NULL INPUT ───────────────────────────────────────────

    @Test
    public void returnsNullOnNullInputNeverEntersTheBody() {
        // The clause was parsed and stored but not enforced, so a handler written on that guarantee ran with
        // None and raised "AttributeError: 'NoneType' object has no attribute 'items'".
        engine.execute("""
            CREATE OR REPLACE FUNCTION strict_has_value(obj OBJECT)
            RETURNS BOOLEAN
            LANGUAGE PYTHON
            RETURNS NULL ON NULL INPUT
            RUNTIME_VERSION = '3.11'
            HANDLER = 'hasValues'
            AS $$
def hasValues(obj):
    for _, value in obj.items():
        if value != None:
            return True
    return False
            $$""");
        assertEquals(null, scalar("SELECT strict_has_value(NULL)"));
        assertEquals(true, scalar("SELECT strict_has_value(PARSE_JSON('{\"a\":null,\"b\":1}'))"));
        assertEquals(false, scalar("SELECT strict_has_value(PARSE_JSON('{\"a\":null}'))"));
    }

    @Test
    public void strictIsASynonymAndAnyNullArgumentShortCircuits() {
        engine.execute("""
            CREATE OR REPLACE FUNCTION joiner(a VARCHAR, b VARCHAR) RETURNS VARCHAR
            LANGUAGE PYTHON STRICT RUNTIME_VERSION='3.10' HANDLER='h'
            AS $$
def h(a, b):
    return a + b
            $$""");
        assertEquals("xy", scalar("SELECT joiner('x', 'y')"));
        assertEquals(null, scalar("SELECT joiner('x', NULL)"));
        assertEquals(null, scalar("SELECT joiner(NULL, 'y')"));
    }

    @Test
    public void theDefaultCalledOnNullInputStillRunsTheBody() {
        engine.execute("""
            CREATE OR REPLACE FUNCTION lenient(s VARCHAR) RETURNS VARCHAR
            LANGUAGE PYTHON RUNTIME_VERSION='3.10' HANDLER='h'
            AS $$
def h(s):
    return 'was-' + str(s)
            $$""");
        assertEquals("was-None", scalar("SELECT lenient(NULL)"));
    }

    @Test
    public void aSqlLanguageFunctionHonoursItToo() {
        // It is a property of the function, not of the language.
        engine.execute("CREATE OR REPLACE FUNCTION bump(a INTEGER) RETURNS INTEGER STRICT AS $$ a + 1 $$");
        assertEquals(null, scalar("SELECT bump(NULL)"));
        assertEquals(2L, ((Number) scalar("SELECT bump(1)")).longValue());
    }

    // ── Python datetime/date values coming back OUT ──────────────────────────
    // Jython coerces them to java.sql types whose toString renders in the JVM's LOCAL zone, which shifted a
    // UTC wall-clock by the local offset (+1h in BST, 0 in GMT — DST-dependent corruption) and dropped
    // trailing fraction zeros. Snowflake keeps the naive fields verbatim (str(datetime) inside a VARIANT).

    @Test
    public void aDatetimeInsideADictKeepsItsNaiveFieldsAndPythonStrFormat() {
        engine.execute("""
            CREATE OR REPLACE FUNCTION dt_dict()
            RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION='3.10' HANDLER='h'
            AS $$
import datetime
def h():
    return {'ts': datetime.datetime.utcfromtimestamp(1729108031488 / 1000.0)}
            $$""");
        assertEquals("{\"ts\":\"2024-10-16 19:47:11.488000\"}", String.valueOf(scalar("SELECT dt_dict()")));
    }

    @Test
    public void aBareDatetimeReturnBecomesALocalDateTimeWithTheNaiveFields() {
        engine.execute("""
            CREATE OR REPLACE FUNCTION dt_bare()
            RETURNS TIMESTAMP_NTZ LANGUAGE PYTHON RUNTIME_VERSION='3.10' HANDLER='h'
            AS $$
import datetime
def h():
    return datetime.datetime.utcfromtimestamp(1729108031488 / 1000.0)
            $$""");
        final Object v = scalar("SELECT dt_bare()");
        assertEquals("2024-10-16T19:47:11.488", String.valueOf(v));
        assertEquals("LocalDateTime", v.getClass().getSimpleName());
    }

    @Test
    public void aDateInsideADictKeepsItsCalendarDay() {
        // The date coercion writes its fields as LOCAL midnight (unlike datetime), so recovering it at UTC
        // shifted the day backwards in any zone east of UTC.
        engine.execute("""
            CREATE OR REPLACE FUNCTION d_dict()
            RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION='3.10' HANDLER='h'
            AS $$
import datetime
def h():
    return {'d': datetime.date(2024, 10, 16)}
            $$""");
        assertEquals("{\"d\":\"2024-10-16\"}", String.valueOf(scalar("SELECT d_dict()")));
    }

    @Test
    public void aWholeSecondDatetimeHasNoFractionLikePythonStr() {
        engine.execute("""
            CREATE OR REPLACE FUNCTION dt_whole()
            RETURNS VARIANT LANGUAGE PYTHON RUNTIME_VERSION='3.10' HANDLER='h'
            AS $$
import datetime
def h():
    return {'ts': datetime.datetime(2024, 10, 16, 19, 47, 11)}
            $$""");
        assertEquals("{\"ts\":\"2024-10-16 19:47:11\"}", String.valueOf(scalar("SELECT dt_whole()")));
    }

    @Test
    public void worksOverATableColumn() {
        engine.execute("CREATE TABLE docs (payload VARIANT)");
        engine.execute("INSERT INTO docs SELECT PARSE_JSON('{\"n\":3}')");
        engine.execute("""
            CREATE OR REPLACE FUNCTION read_n(v VARIANT)
            RETURNS INTEGER LANGUAGE PYTHON RUNTIME_VERSION='3.10' HANDLER='h'
            AS $$
def h(v):
    return v['n']
            $$""");
        assertEquals(3L, ((Number) scalar("SELECT read_n(payload) FROM docs")).longValue());
    }
}
