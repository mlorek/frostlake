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
package dev.frostlake.dml;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A DECFLOAT unloads as its own text, the text a cast to VARCHAR writes - {@code 1e16384}, not its 16385 digits, and
 * {@code 1.2345678901234567890123456789012345678e-20}, not the plain digits of its 59 characters - as every other
 * number unloads as its text.
 */
public class CopyUnloadDecfloatTextTest extends BaseDatabaseTest {

    @BeforeEach
    public void skipWhenLive() {
        Assumptions.assumeFalse(isLiveSnowflake(), "COPY INTO a location is not run against the live account");
    }

    /** Every row's first column as text, sorted. */
    private List<String> texts(final String sql) {
        final ResultSet answer = engine.executeQuery(sql);
        final List<String> texts = new ArrayList<>();
        for (final Row row : answer.getRows()) {
            texts.add(String.valueOf(row.getValue(0)));
        }
        Collections.sort(texts);
        return texts;
    }

    @Test
    public void decfloatUnloadsAsItsOwnText() {
        engine.execute("CREATE STAGE dst");
        engine.execute("CREATE TABLE dsrc (d DECFLOAT)");
        engine.execute("INSERT INTO dsrc VALUES ('1.2345678901234567890123456789012345678e-20'), ('1e16384'),"
            + " ('123.5'), ('-7')");
        engine.execute("COPY INTO @dst/csv/ FROM dsrc FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE)");
        final List<String> expected = List.of("-7", "1.2345678901234567890123456789012345678e-20", "123.5",
            "1e16384");
        assertEquals(expected, texts("SELECT d::VARCHAR FROM dsrc"));
        assertEquals(expected, texts("SELECT $1 FROM @dst/csv/"));
    }
}
