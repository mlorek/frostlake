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

package dev.frostlake.functions.scalar.file;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * GET_ABSOLUTE_PATH: the stage's location with the relative path appended as written — no slash added between them.
 */
public class GetAbsolutePathTest extends BaseDatabaseTest {

    private static final String LOCATION = "s3://fl-probe-nonexistent-bucket/some/path";

    private String value(final String sql) {
        final Object value = engine.executeQuery(sql).getRows().get(0).getValue(0);
        return value == null ? null : value.toString();
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    @Test
    public void theLocationRunsStraightIntoThePath() {
        engine.execute("CREATE STAGE gap_ext URL = '" + LOCATION + "'");
        assertEquals(LOCATION + "a/b.csv", value("SELECT GET_ABSOLUTE_PATH(@gap_ext, 'a/b.csv')"));
        assertEquals(LOCATION + "/a/b.csv", value("SELECT GET_ABSOLUTE_PATH(@gap_ext, '/a/b.csv')"));
        assertEquals(LOCATION + "a b.csv", value("SELECT GET_ABSOLUTE_PATH('@gap_ext', 'a b.csv')"));
        assertEquals(LOCATION, value("SELECT GET_ABSOLUTE_PATH(@gap_ext, '')"));
        assertNull(value("SELECT GET_ABSOLUTE_PATH(@gap_ext, NULL)"));
    }

    @Test
    public void anInternalStageAppendsToItsOwnLocation() {
        engine.execute("CREATE STAGE gap_int");
        assertEquals("true", value("SELECT GET_ABSOLUTE_PATH(@gap_int, 'a') = GET_STAGE_LOCATION(@gap_int) || 'a'")
            .toLowerCase());
        assertEquals("true", value("SELECT GET_ABSOLUTE_PATH(@gap_int, p) = GET_STAGE_LOCATION(@gap_int) || p"
            + " FROM (SELECT 'd/f.csv' p)").toLowerCase());
    }

    /** The declared width is the location's and the path's together; an empty path counts one character. */
    @Test
    public void theWidthIsTheLocationsAndThePathsTogether() {
        engine.execute("CREATE STAGE gap_w URL = '" + LOCATION + "'");
        assertEquals("VARCHAR(49)[LOB]", value("SELECT SYSTEM$TYPEOF(GET_ABSOLUTE_PATH(@gap_w, 'a/b.csv'))"));
        assertEquals("VARCHAR(43)[LOB]", value("SELECT SYSTEM$TYPEOF(GET_ABSOLUTE_PATH(@gap_w, ''))"));
    }

    @Test
    public void refusals() {
        engine.execute("CREATE TABLE gap_t (a INT)");
        final String prefix = "SQL compilation error: Argument 1 to function 'GET_ABSOLUTE_PATH' ";
        final String kinds = prefix + "provides a user or table stage. These stage kinds are not supported by this"
            + " function.";
        assertEquals(kinds, refusal("SELECT GET_ABSOLUTE_PATH(@~, 'f.csv')"));
        assertEquals(kinds, refusal("SELECT GET_ABSOLUTE_PATH(@%gap_t, 'f.csv')"));
        assertEquals(prefix + "cannot be null or empty.", refusal("SELECT GET_ABSOLUTE_PATH(NULL, 'f.csv')"));
        assertEquals(prefix + "does not start with '@'. Please specify stage name as '@<stage_name>'.",
            refusal("SELECT GET_ABSOLUTE_PATH('gap_t', 'f.csv')"));
        assertEquals("SQL compilation error: Stage '@gap_nosuch' provided to the function 'GET_ABSOLUTE_PATH' does"
            + " not exist or is not authorized.", refusal("SELECT GET_ABSOLUTE_PATH(@gap_nosuch, 'f.csv')"));
        assertEquals("SQL compilation error: error line 1 at position 7\nnot enough arguments for function"
            + " [GET_ABSOLUTE_PATH('@gap_t')], expected 2, got 1", refusal("SELECT GET_ABSOLUTE_PATH(@gap_t)"));
        assertEquals("SQL compilation error: error line 1 at position 7\ntoo many arguments for function"
                + " [GET_ABSOLUTE_PATH('@gap_t', 'a', 'b')] expected 2, got 3",
            refusal("SELECT GET_ABSOLUTE_PATH(@gap_t, 'a', 'b')"));
    }
}
