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
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BeginEndDeclareTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(BeginEndDeclareTest.class);

    @Test
    public void testBeginWithDeclareDefault() {
        logger.info("Testing BEGIN...END block with DECLARE DEFAULT");

        engine.execute("CREATE TABLE results (value INTEGER)");

        engine.execute("""
            DECLARE x INTEGER DEFAULT 10;
            BEGIN
                INSERT INTO results VALUES (:x);
            END;
            """);

        final ResultSet rs = engine.executeQuery("SELECT * FROM results");
        logger.info("Row count: {}", rs.getRowCount());
        if (rs.getRowCount() > 0) {
            final Object value = rs.getRows().get(0).getValue(0);
            logger.info("Value: {}", value);
            if (value != null) {
                assertEquals(10L, ((Number) value).longValue());
            } else {
                logger.error("Value is null!");
                throw new AssertionError("Value should not be null");
            }
        } else {
            logger.error("No rows found!");
            throw new AssertionError("Should have 1 row");
        }
    }

    @Test
    public void testBeginWithDeclareColonEquals() {
        logger.info("Testing BEGIN...END block with DECLARE :=");

        engine.execute("CREATE TABLE results (value INTEGER)");

        engine.execute("""
            DECLARE x INTEGER := 15;
            BEGIN
                INSERT INTO results VALUES (:x);
            END;
            """);

        final ResultSet rs = engine.executeQuery("SELECT * FROM results");
        logger.info("Row count: {}", rs.getRowCount());
        if (rs.getRowCount() > 0) {
            final Object value = rs.getRows().get(0).getValue(0);
            logger.info("Value: {}", value);
            if (value != null) {
                assertEquals(15L, ((Number) value).longValue());
            } else {
                logger.error("Value is null!");
                throw new AssertionError("Value should not be null");
            }
        } else {
            logger.error("No rows found!");
            throw new AssertionError("Should have 1 row");
        }
    }

    /**
     * A typed declaration's initializer sees an UNTYPED declaration above it — the two shapes are
     * separate grammar alternatives, and the whole-block name check walks them in source order.
     */
    @Test
    public void testTypedDeclarationSeesEarlierUntypedOne() {
        logger.info("Testing typed declaration referencing an earlier untyped one");

        final ResultSet result = engine.executeQuery("""
            DECLARE
                prefix := 'violations: ';
                message STRING := prefix || 'none';
            BEGIN
                RETURN :message;
            END;
            """);

        assertEquals("violations: none", result.getRows().get(0).getValue(0));
    }
}
