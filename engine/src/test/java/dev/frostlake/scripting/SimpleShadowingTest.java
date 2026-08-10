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

public class SimpleShadowingTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(SimpleShadowingTest.class);

    @Test
    public void testSimpleShadowing() {
        logger.info("Testing simple variable shadowing");

        engine.execute("CREATE TABLE results (value INTEGER)");

        engine.execute("""
            DECLARE x INTEGER := 5;
            BEGIN
                INSERT INTO results VALUES (:x);
                DECLARE x INTEGER := 20;
                BEGIN
                    INSERT INTO results VALUES (:x);
                END;
                INSERT INTO results VALUES (:x);
            END;
            """);

        final ResultSet rs = engine.executeQuery("SELECT * FROM results ORDER BY value");
        logger.info("Results: {}, {}, {}",
            rs.getRows().get(0).getValue(0),
            rs.getRows().get(1).getValue(0),
            rs.getRows().get(2).getValue(0));

        assertEquals(3, rs.getRowCount());
        assertEquals(5L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(5L, ((Number) rs.getRows().get(1).getValue(0)).longValue());
        assertEquals(20L, ((Number) rs.getRows().get(2).getValue(0)).longValue());
    }

    @Test
    public void testDoubleShadowing() {
        logger.info("Testing double level shadowing");

        engine.execute("CREATE TABLE results (value INTEGER)");

        engine.execute("""
            DECLARE x INTEGER := 5;
            BEGIN
                INSERT INTO results VALUES (:x);
                DECLARE x INTEGER := 20;
                BEGIN
                    INSERT INTO results VALUES (:x);
                    DECLARE x INTEGER := 100;
                    BEGIN
                        INSERT INTO results VALUES (:x);
                    END;
                    INSERT INTO results VALUES (:x);
                END;
                INSERT INTO results VALUES (:x);
            END;
            """);

        final ResultSet rs = engine.executeQuery("SELECT * FROM results");
        logger.info("Row count: {}", rs.getRowCount());
        for (int i = 0; i < rs.getRowCount(); i++) {
            logger.info("Row {}: {}", i, rs.getRows().get(i).getValue(0));
        }

        assertEquals(5, rs.getRowCount());
        assertEquals(5L, ((Number) rs.getRows().get(0).getValue(0)).longValue());   // First INSERT
        assertEquals(20L, ((Number) rs.getRows().get(1).getValue(0)).longValue());  // Second INSERT
        assertEquals(100L, ((Number) rs.getRows().get(2).getValue(0)).longValue()); // Third INSERT
        assertEquals(20L, ((Number) rs.getRows().get(3).getValue(0)).longValue());  // Fourth INSERT (after innermost block exits)
        assertEquals(5L, ((Number) rs.getRows().get(4).getValue(0)).longValue());   // Fifth INSERT (after middle block exits)
    }
}
