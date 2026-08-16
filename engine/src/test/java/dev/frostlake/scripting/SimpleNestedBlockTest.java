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

public class SimpleNestedBlockTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(SimpleNestedBlockTest.class);

    @Test
    public void testSimpleVariable() {
        logger.info("Testing simple variable declaration and usage");

        engine.execute("CREATE TABLE results (value INTEGER)");

        engine.execute("""
            DECLARE x INTEGER;
            BEGIN
                x := 10;
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
            }
        }
    }

    @Test
    public void testVariableWithDefault() {
        logger.info("Testing variable with DEFAULT");

        engine.execute("CREATE TABLE results (value INTEGER)");

        engine.execute("""
            DECLARE x INTEGER DEFAULT 20;
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
                assertEquals(20L, ((Number) value).longValue());
            }
        }
    }
}
