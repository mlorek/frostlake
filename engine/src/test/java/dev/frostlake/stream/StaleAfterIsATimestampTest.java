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

package dev.frostlake.stream;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SHOW STREAMS' stale_after is a TIMESTAMP_LTZ(3), as created_on is (live-verified), where it was ISO text. DESCRIBE
 * STREAM answers the same row, as documented, where it listed three property/value pairs.
 */
public class StaleAfterIsATimestampTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE sa");
        engine.execute("USE SCHEMA sa.PUBLIC");
        engine.execute("CREATE TABLE src (a INT)");
        engine.execute("CREATE STREAM st ON TABLE src");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private List<Object> typesAfter(final String listing) {
        engine.executeQuery(listing);
        final Row row = engine.executeQuery("SELECT SYSTEM$TYPEOF(\"stale_after\"), SYSTEM$TYPEOF(\"created_on\"),"
            + " \"stale_after\" > \"created_on\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))").getRows().get(0);
        return row.getValues();
    }

    @Test
    public void staleAfterIsATimestampLikeCreatedOn() {
        assertEquals(List.of("TIMESTAMP_LTZ(3)[SB8]", "TIMESTAMP_LTZ(3)[SB8]", true),
            typesAfter("SHOW STREAMS LIKE 'ST'"));
    }

    @Test
    public void describeStreamAnswersTheShowStreamsRow() {
        final ResultSet shown = engine.executeQuery("SHOW STREAMS LIKE 'ST'");
        final ResultSet described = engine.executeQuery("DESCRIBE STREAM st");
        assertEquals(shown.getColumns().size(), described.getColumns().size());
        assertEquals(1, described.getRows().size());
        assertEquals(shown.getRows().get(0).getValues(), described.getRows().get(0).getValues());
        assertEquals(List.of("TIMESTAMP_LTZ(3)[SB8]", "TIMESTAMP_LTZ(3)[SB8]", true), typesAfter("DESCRIBE STREAM st"));
    }
}
