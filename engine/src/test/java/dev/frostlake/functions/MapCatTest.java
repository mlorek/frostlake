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
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * MAP_CAT and the MAP data type (a MAP is backed by an OBJECT): a {@code ::MAP(keyType, valueType)} cast is a
 * pass-through of the object, and MAP_CAT merges two maps with {@code map2} winning on a shared key.
 */
public class MapCatTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void mapCatMergesTwoMaps() {
        assertEquals("{\"a\":\"1\",\"b\":\"2\"}", String.valueOf(scalar(
            "SELECT MAP_CAT(OBJECT_CONSTRUCT('a','1'), OBJECT_CONSTRUCT('b','2'))")));
    }

    @Test
    public void mapCatSecondMapWinsOnSharedKey() {
        assertEquals("{\"a\":\"1\",\"b\":\"2\",\"c\":\"3\"}", String.valueOf(scalar(
            "SELECT MAP_CAT(OBJECT_CONSTRUCT('a','1','b','x'), OBJECT_CONSTRUCT('b','2','c','3'))")));
    }

    @Test
    public void castToMapIsAnObjectPassThrough() {
        assertEquals("{\"a\":\"1\"}", String.valueOf(scalar(
            "SELECT OBJECT_CONSTRUCT('a','1')::MAP(VARCHAR,VARCHAR)")));
        assertEquals("{\"a\":\"1\"}", String.valueOf(scalar(
            "SELECT CAST(OBJECT_CONSTRUCT('a','1') AS MAP)")));
    }

    @Test
    public void loaderShapeMapCatOfCastsBackToVariant() {
        assertEquals("{\"a\":\"1\",\"b\":\"2\"}", String.valueOf(scalar(
            "SELECT MAP_CAT(OBJECT_CONSTRUCT('a','1')::MAP(VARCHAR,VARCHAR), "
            + "OBJECT_CONSTRUCT('b','2')::MAP(VARCHAR,VARCHAR))::VARIANT")));
    }

    @Test
    public void nullHandling() {
        assertNull(scalar("SELECT MAP_CAT(NULL::MAP, NULL::MAP)"));
        assertEquals("{\"b\":\"2\"}", String.valueOf(scalar("SELECT MAP_CAT(NULL, OBJECT_CONSTRUCT('b','2'))")));
    }

    @Test
    public void mapUsableAsColumnTypeAndName() {
        engine.execute("CREATE TABLE mt (id INTEGER, map MAP(VARCHAR,VARCHAR))");
        engine.execute("INSERT INTO mt SELECT 1, OBJECT_CONSTRUCT('k','v')::MAP(VARCHAR,VARCHAR)");
        assertEquals("{\"k\":\"v\"}", String.valueOf(scalar("SELECT map FROM mt")));
    }
}
