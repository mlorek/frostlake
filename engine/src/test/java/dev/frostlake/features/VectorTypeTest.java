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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The VECTOR(FLOAT|INT, n) column type: declaration, storage round-trip and DESCRIBE rendering.
 */
public class VectorTypeTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(VectorTypeTest.class);

    @Test
    public void floatVectorColumnRoundTrip() {
        engine.execute("CREATE TABLE vt_f (id INTEGER, emb VECTOR(FLOAT, 3))");
        engine.execute("INSERT INTO vt_f SELECT 1, [0.1, 0.2, 0.3]::VECTOR(FLOAT, 3)");

        final ResultSet rs = engine.executeQuery("SELECT emb FROM vt_f WHERE id = 1");
        assertEquals(1, rs.getRows().size());
        final Object value = rs.getRows().get(0).getValue(0);
        assertNotNull(value);
        logger.info("Stored FLOAT vector value: {} ({})", value, value.getClass().getSimpleName());
    }

    @Test
    public void intVectorColumnRoundTrip() {
        engine.execute("CREATE TABLE vt_i (id INTEGER, v VECTOR(INT, 2))");
        engine.execute("INSERT INTO vt_i SELECT 1, [7, 9]::VECTOR(INT, 2)");

        final ResultSet rs = engine.executeQuery("SELECT v FROM vt_i");
        assertEquals(1, rs.getRows().size());
        assertNotNull(rs.getRows().get(0).getValue(0));
    }

    @Test
    public void describeShowsVectorType() {
        engine.execute("CREATE TABLE vt_d (emb VECTOR(FLOAT, 4))");
        final ResultSet rs = engine.executeQuery("DESCRIBE TABLE vt_d");
        assertEquals(1, rs.getRows().size());
        final String type = String.valueOf(rs.getRows().get(0).getValue(1)).toUpperCase();
        assertTrue(type.contains("VECTOR"), "DESCRIBE must render the VECTOR type, got: " + type);
    }
}
