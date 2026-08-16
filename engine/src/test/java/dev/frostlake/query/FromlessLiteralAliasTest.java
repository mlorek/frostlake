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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * FROM-less select-list semantics around derived names: only a REFERENCE-shaped item reuses an
 * earlier alias's value, so two string literals that are equal modulo case keep their own
 * values even though both derive the same upper-cased column name; and a set-operation CTAS
 * accepts unaliased literal items, named by their verbatim text.
 */
public class FromlessLiteralAliasTest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(FromlessLiteralAliasTest.class);

    @Test
    public void testCaseTwinLiteralsKeepTheirOwnValues() {
        final ResultSet result = engine.executeQuery("""
            SELECT EXPECTED FROM (
                SELECT 1 AS TEST_ID, 'IDENTITY' AS INPUT_VAL, 'Person' AS EXPECTED, 'P' AS ACTUAL
                UNION ALL
                SELECT 2, 'CONTAINER', 'Container', 'C'
            ) WHERE TEST_ID = 2
            """);

        assertEquals(1, result.getRowCount());
        assertEquals("Container", result.getRows().get(0).getValue(0));

        logger.info("Case-twin literals kept their own values");
    }

    @Test
    public void testBareAliasReferenceIsStillReused() {
        final ResultSet result = engine.executeQuery("SELECT 41 AS x, x + 1 AS y");

        assertEquals(41L, result.getRows().get(0).getValue(0));
        assertEquals(42L, result.getRows().get(0).getValue(1));

        logger.info("Bare alias reference still resolves to the earlier value");
    }

    @Test
    public void testUnionCtasAcceptsUnaliasedLiteralItems() {
        engine.execute("CREATE TABLE src_t (a INTEGER)");
        engine.execute("""
            CREATE TABLE reset_t AS
            SELECT 1 FROM src_t WHERE FALSE UNION ALL
            SELECT 1 FROM src_t WHERE FALSE
            """);

        final ResultSet result = engine.executeQuery("SELECT * FROM reset_t");
        assertEquals(0, result.getRowCount());
        assertEquals("1", result.getColumns().get(0).getName());

        logger.info("CTAS over unaliased literal items accepted and named by text");
    }
}
