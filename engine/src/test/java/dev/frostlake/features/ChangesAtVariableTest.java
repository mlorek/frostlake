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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A CHANGES point may be a session variable, as Snowflake's own CHANGES examples write it ({@code SET ts1 = ...},
 * then {@code AT(TIMESTAMP => $ts1)} and {@code END(TIMESTAMP => $ts2)}), or a cast of one, as "the value must be
 * explicitly cast to a TIMESTAMP" asks. Both were refused as not constant.
 */
public class ChangesAtVariableTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE cav");
        engine.execute("USE SCHEMA cav.PUBLIC");
        engine.execute("CREATE TABLE ct (a INT) CHANGE_TRACKING = TRUE");
        engine.execute("SET t1 = CURRENT_TIMESTAMP()");
        engine.execute("INSERT INTO ct VALUES (5)");
        engine.execute("SET t2 = CURRENT_TIMESTAMP()");
        engine.execute("INSERT INTO ct VALUES (6)");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private List<Object> firstColumn(final String sql) {
        final List<Object> values = new ArrayList<>();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            values.add(String.valueOf(row.getValue(0)));
        }
        return values;
    }

    @Test
    public void aSessionVariableIsAConstantPoint() {
        assertEquals(List.of("5", "6"),
            firstColumn("SELECT a FROM ct CHANGES(INFORMATION => DEFAULT) AT(TIMESTAMP => $t1) ORDER BY a"));
        assertEquals(List.of("5"), firstColumn("SELECT a FROM ct CHANGES(INFORMATION => DEFAULT)"
            + " AT(TIMESTAMP => $t1) END(TIMESTAMP => $t2) ORDER BY a"));
        assertEquals(List.of("6"),
            firstColumn("SELECT a FROM ct CHANGES(INFORMATION => APPEND_ONLY) AT(TIMESTAMP => $t2::TIMESTAMP_LTZ)"));
    }

    @Test
    public void aFunctionWhoseValueMovesIsNoPoint() {
        String refusal = null;
        try {
            engine.executeQuery("SELECT a FROM ct CHANGES(INFORMATION => DEFAULT)"
                + " AT(TIMESTAMP => DATEADD(minute, -30, CURRENT_TIMESTAMP()))");
        } catch (final RuntimeException refused) {
            refusal = refused.getMessage();
        }
        assertTrue(refusal != null && refusal.contains("Query contains time travel to a version containing function"
            + " 'CURRENT_TIMESTAMP', but change tracking is not supported on queries with non-deterministic functions"
            + " in versions."), String.valueOf(refusal));
    }
}
