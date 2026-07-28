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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class StringFunctionsExtTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) engine.shutdown();
    }

    private Object q(final String sql) {
        ResultSet rs = engine.executeQuery(sql);
        return rs.getRows().get(0).getValue(0);
    }

    // CHARINDEX / POSITION
    @Test public void testCharindex() {
        assertEquals(4L, q("SELECT CHARINDEX('lo', 'hello world')"));
    }
    @Test public void testCharindexNotFound() {
        assertEquals(0L, q("SELECT CHARINDEX('xyz', 'hello')"));
    }
    @Test public void testCharindexWithStart() {
        // 'hello lo world': searching 'lo' from position 5 (1-based) → index 4 (0-based) → finds at index 6 → position 7
        assertEquals(7L, q("SELECT CHARINDEX('lo', 'hello lo world', 5)"));
    }
    @Test public void testPosition() {
        assertEquals(4L, q("SELECT POSITION('lo', 'hello world')"));
    }

    // CHR / ASCII / UNICODE
    @Test public void testChr() {
        assertEquals("A", q("SELECT CHR(65)"));
    }
    @Test public void testAscii() {
        assertEquals(72L, q("SELECT ASCII('Hello')"));
    }
    @Test public void testUnicode() {
        assertEquals(65L, q("SELECT UNICODE('A')"));
    }

    // REPEAT / SPACE
    @Test public void testRepeat() {
        assertEquals("abcabcabc", q("SELECT REPEAT('abc', 3)"));
    }
    @Test public void testRepeatZero() {
        assertEquals("", q("SELECT REPEAT('abc', 0)"));
    }
    @Test public void testSpace() {
        assertEquals("   ", q("SELECT SPACE(3)"));
    }
    @Test public void testSpaceZero() {
        assertEquals("", q("SELECT SPACE(0)"));
    }

    // SPLIT_PART
    @Test public void testSplitPart() {
        assertEquals("b", q("SELECT SPLIT_PART('a.b.c', '.', 2)"));
    }
    @Test public void testSplitPartFirst() {
        assertEquals("a", q("SELECT SPLIT_PART('a.b.c', '.', 1)"));
    }
    @Test public void testSplitPartLast() {
        assertEquals("c", q("SELECT SPLIT_PART('a.b.c', '.', 3)"));
    }
    @Test public void testSplitPartOutOfRange() {
        assertEquals("", q("SELECT SPLIT_PART('a.b.c', '.', 5)"));
    }

    // STRTOK
    @Test public void testStrtok() {
        assertEquals("hello", q("SELECT STRTOK('hello world foo', ' ', 1)"));
    }
    @Test public void testStrtokSecond() {
        assertEquals("world", q("SELECT STRTOK('hello world foo', ' ', 2)"));
    }
    @Test public void testStrtokDefaultPart() {
        assertEquals("hello", q("SELECT STRTOK('hello world')"));
    }

    // CONTAINS / STARTSWITH / ENDSWITH
    @Test public void testContainsTrue() {
        assertEquals(true, q("SELECT CONTAINS('hello world', 'lo wo')"));
    }
    @Test public void testContainsFalse() {
        assertEquals(false, q("SELECT CONTAINS('hello', 'xyz')"));
    }
    @Test public void testStartswith() {
        assertEquals(true, q("SELECT STARTSWITH('hello world', 'hello')"));
        assertEquals(false, q("SELECT STARTSWITH('hello world', 'world')"));
    }
    @Test public void testEndswith() {
        assertEquals(true, q("SELECT ENDSWITH('hello world', 'world')"));
        assertEquals(false, q("SELECT ENDSWITH('hello world', 'hello')"));
    }

    // TRANSLATE
    @Test public void testTranslate() {
        assertEquals("h3ll0", q("SELECT TRANSLATE('hello', 'eo', '30')"));
    }
    @Test public void testTranslateDelete() {
        assertEquals("hll", q("SELECT TRANSLATE('hello', 'eo', '')"));
    }

    // REGEXP_REPLACE / REGEXP_LIKE / REGEXP_SUBSTR / REGEXP_COUNT
    @Test public void testRegexpReplace() {
        assertEquals("hXXXo", q("SELECT REGEXP_REPLACE('hello', 'el+', 'XXX')"));
    }
    @Test public void testRegexpLikeTrue() {
        assertEquals(true, q("SELECT REGEXP_LIKE('hello123', '[a-z]+[0-9]+')"));
    }
    @Test public void testRegexpLikeFalse() {
        assertEquals(false, q("SELECT REGEXP_LIKE('hello', '[0-9]+')"));
    }
    @Test public void testRegexpSubstr() {
        assertEquals("123", q("SELECT REGEXP_SUBSTR('abc123def', '[0-9]+')"));
    }
    @Test public void testRegexpCount() {
        // 'aababcabc': a at indices 0,1,3,6 = 4 occurrences
        assertEquals(4L, q("SELECT REGEXP_COUNT('aababcabc', 'a')"));
    }

    // EDITDISTANCE
    @Test public void testEditdistanceSame() {
        assertEquals(0L, q("SELECT EDITDISTANCE('abc', 'abc')"));
    }
    @Test public void testEditdistanceDiff() {
        assertEquals(3L, q("SELECT EDITDISTANCE('kitten', 'sitting')"));
    }

    // SOUNDEX
    @Test public void testSoundex() {
        assertEquals("R163", q("SELECT SOUNDEX('Robert')"));
        assertEquals("R163", q("SELECT SOUNDEX('Rupert')"));
    }

    // CONCAT_WS
    @Test public void testConcatWs() {
        assertEquals("a,b,c", q("SELECT CONCAT_WS(',', 'a', 'b', 'c')"));
    }
    @Test public void testConcatWsNullPropagates() {
        // Snowflake: CONCAT_WS returns NULL when ANY value is NULL — it does not skip NULLs (MySQL does).
        assertNull(q("SELECT CONCAT_WS(',', 'a', NULL, 'c')"));
    }
    @Test public void testConcatNullPropagates() {
        // Snowflake: CONCAT returns NULL when any input is NULL — the loader idiom
        // CONCAT(lookup.prefix, ':', NVL(x, '')) must be NULL on a missed lookup, not ':'.
        assertNull(q("SELECT CONCAT('a', NULL, 'c')"));
        assertNull(q("SELECT CONCAT(NULL, ':', '')"));
        assertEquals("a:b", q("SELECT CONCAT('a', ':', 'b')"));
    }

    // LEN / CHAR_LENGTH / OCTET_LENGTH / BIT_LENGTH
    @Test public void testLen() {
        assertEquals(5L, q("SELECT LEN('hello')"));
    }
    @Test public void testCharLength() {
        assertEquals(5L, q("SELECT CHAR_LENGTH('hello')"));
    }
    @Test public void testOctetLength() {
        assertEquals(5L, q("SELECT OCTET_LENGTH('hello')"));
    }
    @Test public void testBitLength() {
        assertEquals(40L, q("SELECT BIT_LENGTH('hello')"));
    }

    // BASE64_ENCODE / BASE64_DECODE_STRING
    @Test public void testBase64RoundTrip() {
        String encoded = (String) q("SELECT BASE64_ENCODE('hello world')");
        assertNotNull(encoded);
        engine.execute("CREATE TABLE b64_test (encoded VARCHAR)");
        engine.execute("INSERT INTO b64_test VALUES ('" + encoded + "')");
        assertEquals("hello world",
            engine.executeQuery("SELECT BASE64_DECODE_STRING(encoded) FROM b64_test")
                .getRows().get(0).getValue(0));
    }

    // HEX_ENCODE / HEX_DECODE_STRING
    @Test public void testHexRoundTrip() {
        String hex = (String) q("SELECT HEX_ENCODE('hi')");
        assertEquals("6869", hex);
        engine.execute("CREATE TABLE hex_test (h VARCHAR)");
        engine.execute("INSERT INTO hex_test VALUES ('" + hex + "')");
        assertEquals("hi",
            engine.executeQuery("SELECT HEX_DECODE_STRING(h) FROM hex_test")
                .getRows().get(0).getValue(0));
    }

    // MD5 / SHA2
    @Test public void testMd5() {
        String hash = (String) q("SELECT MD5('hello')");
        assertEquals(32, hash.length());
        assertEquals("5d41402abc4b2a76b9719d911017c592", hash);
    }
    @Test public void testSha2_256() {
        String hash = (String) q("SELECT SHA2('hello')");
        assertEquals(64, hash.length());
    }
    @Test public void testSha2_512() {
        String hash = (String) q("SELECT SHA2('hello', 512)");
        assertEquals(128, hash.length());
    }
}
