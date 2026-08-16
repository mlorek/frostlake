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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A VARCHAR put INTO a container stays a string member, however much its text reads as JSON — and so
 * it comes back out a VARCHAR.
 *
 * <p>This is the write-side twin of the PARSE_JSON rule: {@code PARSE_JSON('"[1,2,3]"')} is a string
 * because its argument's TYPE says so, and {@code ARRAY_CONSTRUCT('[1,2]')} holds a string for the
 * same reason. Frostlake re-read the text as structure when embedding it, so the element came back an
 * ARRAY and nothing downstream could tell it from a real one. Extraction was never at fault: a
 * container built by {@code PARSE_JSON} already returned the right thing.
 */
public class ContainerStringMemberTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE kv (k VARCHAR, v VARCHAR)");
        engine.execute("INSERT INTO kv VALUES ('plans', '[]'), ('name', 'bob')");
    }

    private Object one(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    /** The member keeps its JSON-string form, so the container renders it quoted. */
    @Test
    public void aJsonLookingStringIsStoredAsAString() {
        assertEquals("[\"[1,2]\"]", one("SELECT TO_JSON(ARRAY_CONSTRUCT('[1,2]'))"));
        assertEquals("{\"a\":\"[1,2]\"}", one("SELECT TO_JSON(OBJECT_CONSTRUCT('a','[1,2]'))"));
        assertEquals("{\"a\":\"{\\\"b\\\":1}\"}", one("SELECT TO_JSON(OBJECT_CONSTRUCT('a','{\"b\":1}'))"));
    }

    /** And reads back out as a VARCHAR, whose text is the content without the JSON quotes. */
    @Test
    public void thatMemberReadsBackAsAVarchar() {
        assertEquals("VARCHAR", one("SELECT TYPEOF(ARRAY_CONSTRUCT('[1,2]')[0])"));
        assertEquals("VARCHAR", one("SELECT TYPEOF(OBJECT_CONSTRUCT('a','[1,2]'):a)"));
        assertEquals("[1,2]", one("SELECT ARRAY_CONSTRUCT('[1,2]')[0]::VARCHAR"));
        assertEquals(5L, ((Number) one(
            "SELECT LENGTH(ARRAY_CONSTRUCT('[1,2]')[0]::VARCHAR)")).longValue());
    }

    /** A value that really IS semi-structured is unaffected — the two sit side by side. */
    @Test
    public void aRealContainerMemberStaysAContainer() {
        assertEquals("[\"[1,2]\",[3,4]]",
            one("SELECT TO_JSON(ARRAY_CONSTRUCT('[1,2]', PARSE_JSON('[3,4]')))"));
        assertEquals("ARRAY", one("SELECT TYPEOF(ARRAY_CONSTRUCT('[1,2]', PARSE_JSON('[3,4]'))[1])"));
        assertEquals("ARRAY", one("SELECT TYPEOF(PARSE_JSON('{\"k\":[3,4]}'):k)"));
        assertEquals("VARCHAR", one("SELECT TYPEOF(PARSE_JSON('{\"k\":\"[3,4]\"}'):k)"));
    }

    /** The aggregate builders follow the same rule, which is where double-encoding used to show up. */
    @Test
    public void theAggregateBuildersAgree() {
        assertEquals("{\"name\":\"bob\",\"plans\":\"[]\"}",
            one("SELECT TO_JSON(OBJECT_AGG(k, v::VARIANT)) FROM kv"));
        assertEquals("VARCHAR", one("SELECT TYPEOF(OBJECT_AGG(k, v::VARIANT):plans) FROM kv"));
        assertEquals("[\"[]\",\"bob\"]", one("SELECT TO_JSON(ARRAY_AGG(v::VARIANT)) FROM kv"));
    }
}
