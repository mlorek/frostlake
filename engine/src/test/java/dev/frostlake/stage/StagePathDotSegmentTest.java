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

package dev.frostlake.stage;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A stage path is a literal prefix, not a filesystem path: a {@code .} or {@code ..} segment names a
 * directory of its own and never climbs out of the stage. Reading such a path before anything is
 * written there lists nothing, rather than the contents of the collapsed path.
 */
public class StagePathDotSegmentTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE STAGE dotst");
    }

    /** How many rows a listing answers. */
    private int listed(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        int rows = 0;
        while (rs.next()) {
            rows++;
        }
        return rows;
    }

    /** Reading through a path that does not exist yet lists nothing — the path is not collapsed. */
    @Test
    public void aDotSegmentPathIsNotCollapsedOnRead() {
        engine.execute("COPY INTO @dotst/other FROM (SELECT 1) FILE_FORMAT = (TYPE = CSV)");
        assertEquals(0, listed("LIST @dotst/sub/../other"));
        assertEquals(1, listed("LIST @dotst/other"));
    }

    /** A COPY through a '..' segment writes, where it used to fail to create the directory. */
    @Test
    public void aCopyThroughADotSegmentWrites() {
        engine.execute("COPY INTO @dotst/sub/../deep FROM (SELECT 1) FILE_FORMAT = (TYPE = CSV)");
        assertEquals(1, listed("LIST @dotst/sub/../deep"));
    }

    /** A single-dot segment is a name too. */
    @Test
    public void aSingleDotSegmentIsADirectoryName() {
        engine.execute("COPY INTO @dotst/./here FROM (SELECT 1) FILE_FORMAT = (TYPE = CSV)");
        assertEquals(1, listed("LIST @dotst/./here"));
        assertEquals(0, listed("LIST @dotst/here"));
    }

    /** Whatever the path spells, the whole stage still lists what was written to it. */
    @Test
    public void theStageItselfListsWhatWasWritten() {
        engine.execute("COPY INTO @dotst/sub/../other FROM (SELECT 1) FILE_FORMAT = (TYPE = CSV)");
        assertEquals(1, listed("LIST @dotst"));
    }
}
