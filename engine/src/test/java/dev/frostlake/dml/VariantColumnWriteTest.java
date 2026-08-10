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

package dev.frostlake.dml;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A string cast to VARIANT and written into a VARIANT column stores as a VARIANT STRING: it
 * compares equal to the same string built by PARSE_JSON, whichever of the two write paths
 * produced the cell — including a variant-path extraction cast {@code ::VARIANT}.
 */
public class VariantColumnWriteTest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(VariantColumnWriteTest.class);

    @Test
    public void testCastStringComparesEqualToParsedOne() {
        engine.execute("CREATE TABLE vw2 (v VARIANT)");
        engine.execute("INSERT INTO vw2 SELECT 'abc123'::VARIANT");
        engine.execute("INSERT INTO vw2 SELECT PARSE_JSON('\"abc123\"')");

        assertEquals(1L, engine.executeQuery("SELECT COUNT(DISTINCT v) FROM vw2")
            .getRows().get(0).getValue(0));
        assertEquals("abc123", engine.executeQuery("SELECT v::VARCHAR FROM vw2 LIMIT 1")
            .getRows().get(0).getValue(0));

        logger.info("Cast and parsed strings store the same variant cell");
    }

    @Test
    public void testExtractedPathValueStoresAsVariantString() {
        engine.execute("CREATE TABLE vw3 (v VARIANT)");
        engine.execute(
            "INSERT INTO vw3 SELECT PARSE_JSON('{\"id\": \"prov-1\"}'):id::VARIANT");

        assertEquals(1L, engine.executeQuery(
                "SELECT COUNT(*) FROM vw3 WHERE v = PARSE_JSON('\"prov-1\"')")
            .getRows().get(0).getValue(0));

        logger.info("Path-extracted string stored as a variant string");
    }

    @Test
    public void testVariantStringIntoVarcharUnquotes() {
        engine.execute("CREATE TABLE vw4 (s VARCHAR)");
        engine.execute("INSERT INTO vw4 SELECT PARSE_JSON('{\"n\": \"Theodore\"}'):n::VARIANT");

        assertEquals("Theodore",
            engine.executeQuery("SELECT s FROM vw4").getRows().get(0).getValue(0));
    }
}
