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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GET_RELATIVE_PATH: an absolute path with the stage's location taken off its front, character by character and
 * case-sensitively; a path that does not start with the location is refused while the row is read.
 */
public class GetRelativePathTest extends BaseDatabaseTest {

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
    public void theLocationIsTakenOffTheFront() {
        engine.execute("CREATE STAGE grp_ext URL = '" + LOCATION + "'");
        assertEquals("/a/b.csv", value("SELECT GET_RELATIVE_PATH(@grp_ext, '" + LOCATION + "/a/b.csv')"));
        assertEquals("x/b.csv", value("SELECT GET_RELATIVE_PATH(@grp_ext, '" + LOCATION + "x/b.csv')"));
        assertEquals("", value("SELECT GET_RELATIVE_PATH(@grp_ext, '" + LOCATION + "')"));
        assertEquals("/", value("SELECT GET_RELATIVE_PATH('@grp_ext', '" + LOCATION + "/')"));
        assertEquals("", value("SELECT GET_RELATIVE_PATH(@grp_ext, '')"));
        assertNull(value("SELECT GET_RELATIVE_PATH(@grp_ext, NULL)"));
        assertEquals("VARCHAR[LOB]", value("SELECT SYSTEM$TYPEOF(GET_RELATIVE_PATH(@grp_ext, '" + LOCATION + "/a'))"));
    }

    @Test
    public void anAbsolutePathOfTheStageComesBack() {
        engine.execute("CREATE STAGE grp_int");
        assertEquals("d/f.csv", value("SELECT GET_RELATIVE_PATH(@grp_int, GET_ABSOLUTE_PATH(@grp_int, 'd/f.csv'))"));
    }

    @Test
    public void aPathOutsideTheLocationIsRefused() {
        engine.execute("CREATE STAGE grp_out URL = '" + LOCATION + "'");
        assertEquals("Absolute file path 'x' does not belong to stage '@\"TEST_DB\".\"TEST_SCHEMA\".\"GRP_OUT\"' whose"
            + " location is '" + LOCATION + "'", refusal("SELECT GET_RELATIVE_PATH(@grp_out, 'x')"));
        assertTrue(refusal("SELECT GET_RELATIVE_PATH(@grp_out, 'S3://FL-PROBE-NONEXISTENT-BUCKET/some/path/a.csv')")
            .startsWith("Absolute file path 'S3://FL-PROBE-NONEXISTENT-BUCKET/some/path/a.csv' does not belong"));
        engine.execute("CREATE STAGE grp_long");
        final String internal = refusal("SELECT GET_RELATIVE_PATH(@grp_long, 'zz')");
        assertTrue(internal.startsWith("Absolute file path 'zz' does not belong to stage"
            + " '@\"TEST_DB\".\"TEST_SCHEMA\".\"GRP_LONG\"' whose location is '"), internal);
        assertTrue(internal.endsWith("...'"), internal);
    }

    @Test
    public void refusals() {
        engine.execute("CREATE TABLE grp_t (a INT)");
        final String kinds = "SQL compilation error: Argument 1 to function 'GET_RELATIVE_PATH' provides a user or"
            + " table stage. These stage kinds are not supported by this function.";
        assertEquals(kinds, refusal("SELECT GET_RELATIVE_PATH(@~, 'f.csv')"));
        assertEquals(kinds, refusal("SELECT GET_RELATIVE_PATH(@%grp_t, 'f.csv')"));
        assertEquals("SQL compilation error: Stage '@grp_nosuch' provided to the function 'GET_RELATIVE_PATH' does"
            + " not exist or is not authorized.", refusal("SELECT GET_RELATIVE_PATH(@grp_nosuch, 'f.csv')"));
        assertEquals("SQL compilation error: error line 1 at position 7\nnot enough arguments for function"
            + " [GET_RELATIVE_PATH('@grp_t')], expected 2, got 1", refusal("SELECT GET_RELATIVE_PATH(@grp_t)"));
    }
}
