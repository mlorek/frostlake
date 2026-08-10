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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The window frame-vector path ({@code execution.window.frameVectors}) is dual-path: the same
 * queries must answer identically with the flag on (the default — arguments evaluated once per
 * partition row into a vector) and off (the per-frame-row evaluation path). Runs each shape on two
 * engines differing only in the flag and compares whole result sets.
 */
public class WindowFrameVectorsFlagTest {

    private DatabaseEngine vectorEngine;
    private DatabaseEngine rowPathEngine;

    @AfterEach
    public void teardown() {
        if (vectorEngine != null) {
            vectorEngine.shutdown();
        }
        if (rowPathEngine != null) {
            rowPathEngine.shutdown();
        }
    }

    private DatabaseEngine engineWithFlag(final boolean frameVectors) {
        final Properties overrides = new Properties();
        overrides.setProperty(EngineConfig.PROP_EXECUTION_WINDOW_FRAME_VECTORS, String.valueOf(frameVectors));
        final DatabaseEngine engine = new DatabaseEngine(new EngineConfig(overrides));
        engine.execute("CREATE DATABASE flag_db");
        engine.execute("USE DATABASE flag_db");
        engine.execute("CREATE SCHEMA s");
        engine.execute("USE SCHEMA s");
        engine.execute("CREATE TABLE t (id INTEGER, grp VARCHAR, x INTEGER)");
        engine.execute("""
            INSERT INTO t (id, grp, x) VALUES
            (1, 'a', 10), (2, 'a', NULL), (3, 'a', 30), (4, 'a', 30),
            (5, 'b', 5), (6, 'b', 15), (7, 'b', 25), (8, 'b', NULL)
            """);
        return engine;
    }

    private void assertSameOnBothPaths(final String sql) {
        if (vectorEngine == null) {
            vectorEngine = engineWithFlag(true);
            rowPathEngine = engineWithFlag(false);
        }
        final ResultSet withVectors = vectorEngine.executeQuery(sql);
        final ResultSet withoutVectors = rowPathEngine.executeQuery(sql);
        assertEquals(withoutVectors.getRows().size(), withVectors.getRows().size(), sql);
        for (int i = 0; i < withVectors.getRows().size(); i++) {
            final Row expected = withoutVectors.getRows().get(i);
            final Row actual = withVectors.getRows().get(i);
            assertEquals(expected.getValues(), actual.getValues(), sql + " row " + i);
        }
    }

    @Test
    public void runningAggregatesMatch() {
        assertSameOnBothPaths("SELECT id, SUM(x) OVER (ORDER BY id) FROM t ORDER BY id");
        assertSameOnBothPaths("SELECT id, AVG(x) OVER (PARTITION BY grp ORDER BY id) FROM t ORDER BY id");
        assertSameOnBothPaths("SELECT id, COUNT(x) OVER (ORDER BY id) FROM t ORDER BY id");
        assertSameOnBothPaths("SELECT id, MIN(x) OVER (ORDER BY id), MAX(x) OVER (ORDER BY id) FROM t ORDER BY id");
    }

    @Test
    public void explicitFramesMatch() {
        assertSameOnBothPaths("""
            SELECT id, SUM(x) OVER (ORDER BY id ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING) FROM t ORDER BY id
            """);
        assertSameOnBothPaths("""
            SELECT id, SUM(x) OVER (PARTITION BY grp ORDER BY id ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)
            FROM t ORDER BY id
            """);
    }

    @Test
    public void valueFunctionsMatch() {
        assertSameOnBothPaths("SELECT id, FIRST_VALUE(x) OVER (PARTITION BY grp ORDER BY id) FROM t ORDER BY id");
        assertSameOnBothPaths("SELECT id, LAST_VALUE(x) OVER (PARTITION BY grp ORDER BY id) FROM t ORDER BY id");
        assertSameOnBothPaths("SELECT id, NTH_VALUE(x, 2) OVER (PARTITION BY grp ORDER BY id) FROM t ORDER BY id");
        assertSameOnBothPaths("SELECT id, RATIO_TO_REPORT(x) OVER (PARTITION BY grp) FROM t ORDER BY id");
    }

    @Test
    public void conditionalEventsMatch() {
        assertSameOnBothPaths("SELECT id, CONDITIONAL_TRUE_EVENT(x > 10) OVER (ORDER BY id) FROM t ORDER BY id");
        assertSameOnBothPaths("SELECT id, CONDITIONAL_CHANGE_EVENT(x) OVER (PARTITION BY grp ORDER BY id) FROM t ORDER BY id");
    }

    @Test
    public void nonVectorizableArgumentsStillAgree() {
        // UPPER(grp) is a function call, so the safety gate keeps it on the row path even with the
        // flag on — both engines answer through the same code and must agree trivially.
        assertSameOnBothPaths("SELECT id, MIN(UPPER(grp)) OVER (ORDER BY id) FROM t ORDER BY id");
        // A cast and an arithmetic expression ARE vector-safe shapes.
        assertSameOnBothPaths("SELECT id, SUM(x::NUMBER + 1) OVER (ORDER BY id) FROM t ORDER BY id");
    }
}
