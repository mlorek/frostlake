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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A VARIANT string whose CONTENT looks like JSON structure ({@code '["ROLE"]'}) must stay a STRING:
 * Snowflake's {@code ::ARRAY} (which is TO_ARRAY) wraps it into a one-element array — it never parses
 * it — while a field that IS an array passes through. Path extraction keeps such strings in quoted
 * JSON form so the two cases stay distinguishable; {@code ::VARCHAR} unquotes back to the raw text.
 * The real-world shape is an ingest attribute list whose {@code attribute_value} is the TEXT
 * {@code "[\"ROLE\"]"} — the loader round-trips it through {@code ARRAY_TO_STRING(x::ARRAY, ',')} and
 * later {@code PARSE_JSON(...)::ARRAY}.
 */
public class VariantStringCastTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE TABLE v (src VARIANT)");
        engine.execute("""
            INSERT INTO v SELECT PARSE_JSON('{"str_arr":"[\\\\"ROLE\\\\"]","empty_str":"[]","real_arr":["A","B"],"plain":"x"}')""");
    }

    private String str(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void arrayCastWrapsAStringValueAndPassesARealArrayThrough() {
        assertEquals("[\"[\\\"ROLE\\\"]\"]", str("SELECT src:str_arr::ARRAY FROM v"));
        assertEquals("[\"[]\"]", str("SELECT src:empty_str::ARRAY FROM v"));
        assertEquals("[\"A\",\"B\"]", str("SELECT src:real_arr::ARRAY FROM v"));
    }

    @Test
    public void arrayToStringOverTheWrapRecoversTheRawText() {
        // The loader idiom: ARRAY_TO_STRING(attr::ARRAY, ',') must yield the ORIGINAL string, so a later
        // TRY_PARSE_JSON(...) sees valid JSON. Parsing the string into a real array gave 'ROLE' instead.
        assertEquals("[\"ROLE\"]", str("SELECT ARRAY_TO_STRING(src:str_arr::ARRAY, ',') FROM v"));
        assertEquals("[]", str("SELECT ARRAY_TO_STRING(src:empty_str::ARRAY, ',') FROM v"));
        assertEquals("A,B", str("SELECT ARRAY_TO_STRING(src:real_arr::ARRAY, ',') FROM v"));
    }

    @Test
    public void varcharCastUnquotesTheStringForm() {
        assertEquals("[\"ROLE\"]", str("SELECT src:str_arr::VARCHAR FROM v"));
        assertEquals("[]", str("SELECT src:empty_str::VARCHAR FROM v"));
        assertEquals("x", str("SELECT src:plain::VARCHAR FROM v"));
    }

    @Test
    public void parseJsonOfTheUnquotedTextYieldsTheRealArray() {
        assertEquals("[\"ROLE\"]", str("SELECT PARSE_JSON(src:str_arr::VARCHAR)::ARRAY FROM v"));
    }

    @Test
    public void tryParseJsonOfAVariantStringFieldParsesItsEmbeddedJson() {
        // The metadata shape: a variant string FIELD whose content is embedded JSON text, fed to
        // TRY_PARSE_JSON without an explicit ::VARCHAR (Snowflake coerces implicitly). The quoted
        // path-extraction form must unquote first — parsing the quoted form yielded a plain string,
        // so :RuleName silently nulled and the loader fell back to a derived name.
        engine.execute("CREATE TABLE meta_v (src VARIANT)");
        engine.execute("""
            INSERT INTO meta_v SELECT PARSE_JSON('{"value":{"metadata":"{\\\\"RuleName\\\\":\\\\"Public Instance Rule\\\\",\\\\"Category\\\\":\\\\"Network\\\\"}"}}')""");
        assertEquals("Public Instance Rule",
            str("SELECT TRY_PARSE_JSON(src:value.metadata):RuleName::VARCHAR FROM meta_v"));
        assertEquals("Network",
            str("SELECT PARSE_JSON(src:value.metadata):Category::VARCHAR FROM meta_v"));
    }

    @Test
    public void parseJsonOfALiteralQuotedStringStaysAJsonString() {
        // A LITERAL '"abc"' is valid JSON (a bare string) — it must still parse to the string, not error.
        assertEquals("abc", str("SELECT PARSE_JSON('\"abc\"')::VARCHAR"));
    }
}
