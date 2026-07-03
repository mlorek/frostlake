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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * REAL and DOUBLE PRECISION are FLOAT synonyms and must be accepted as data-type names (in CREATE TABLE
 * and CAST). REAL / PRECISION also remain usable as identifiers. Previously both type names were rejected.
 */
public class RealDoublePrecisionTypeTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void realAndDoublePrecisionColumnTypes() {
        engine.execute("CREATE TABLE t (a REAL, b DOUBLE PRECISION)");
        engine.execute("INSERT INTO t VALUES (1.5, 2.5)");
        assertEquals(1.5, ((Number) scalar("SELECT a FROM t")).doubleValue(), 1e-9);
        assertEquals(2.5, ((Number) scalar("SELECT b FROM t")).doubleValue(), 1e-9);
    }

    @Test
    public void castToRealAndDoublePrecision() {
        assertEquals(3.0, ((Number) scalar("SELECT CAST(3 AS REAL)")).doubleValue(), 1e-9);
        assertEquals(3.0, ((Number) scalar("SELECT CAST(3 AS DOUBLE PRECISION)")).doubleValue(), 1e-9);
        assertEquals(3.0, ((Number) scalar("SELECT 3::REAL")).doubleValue(), 1e-9);
    }

    @Test
    public void realAndPrecisionStillUsableAsIdentifiers() {
        engine.execute("CREATE TABLE m (real NUMBER, precision NUMBER)");
        engine.execute("INSERT INTO m VALUES (7, 8)");
        assertEquals(7L, ((Number) scalar("SELECT real FROM m")).longValue());
        assertEquals(8L, ((Number) scalar("SELECT precision FROM m")).longValue());
    }
}
