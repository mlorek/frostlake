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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GET_STAGE_LOCATION: an external stage's URL exactly as it was created with, an internal stage's storage location
 * ending in a slash — a cloud URL on the account, the stage's directory as a {@code file://} URL here.
 */
public class GetStageLocationTest extends BaseDatabaseTest {

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
    public void anExternalStageAnswersItsUrlAsWritten() {
        engine.execute("CREATE STAGE gsl_ext URL = 's3://fl-probe-nonexistent-bucket/some/path'");
        engine.execute("CREATE STAGE gsl_ext2 URL = 's3://fl-probe-nonexistent-bucket/p2/'");
        assertEquals("s3://fl-probe-nonexistent-bucket/some/path", value("SELECT GET_STAGE_LOCATION(@gsl_ext)"));
        assertEquals("s3://fl-probe-nonexistent-bucket/p2/", value("SELECT GET_STAGE_LOCATION('@gsl_ext2')"));
        assertEquals("VARCHAR[LOB]", value("SELECT SYSTEM$TYPEOF(GET_STAGE_LOCATION(@gsl_ext))"));
    }

    @Test
    public void anInternalStageAnswersALocationEndingInASlash() {
        engine.execute("CREATE STAGE gsl_int");
        engine.execute("CREATE STAGE \"gsl quoted\"");
        final String location = value("SELECT GET_STAGE_LOCATION(@gsl_int)");
        assertTrue(location.endsWith("/"), location);
        assertTrue(location.startsWith(isLiveSnowflake() ? "s3://" : "file://"), location);
        assertTrue(value("SELECT GET_STAGE_LOCATION('@\"gsl quoted\"')").endsWith("/"));
    }

    @Test
    public void refusals() {
        engine.execute("CREATE TABLE gsl_t (a INT)");
        final String prefix = "SQL compilation error: Argument 1 to function 'GET_STAGE_LOCATION' ";
        final String kinds = prefix + "provides a user or table stage. These stage kinds are not supported by this"
            + " function.";
        assertEquals(kinds, refusal("SELECT GET_STAGE_LOCATION(@~)"));
        assertEquals(kinds, refusal("SELECT GET_STAGE_LOCATION(@%gsl_t)"));
        assertEquals(prefix + "cannot be null or empty.", refusal("SELECT GET_STAGE_LOCATION(NULL)"));
        assertEquals(prefix + "does not start with '@'. Please specify stage name as '@<stage_name>'.",
            refusal("SELECT GET_STAGE_LOCATION('gsl_t')"));
        assertEquals(prefix + "should only contain the stage name and not a path.",
            refusal("SELECT GET_STAGE_LOCATION('@gsl_t/d')"));
        assertEquals("SQL compilation error: Stage '@gsl_nosuch' provided to the function 'GET_STAGE_LOCATION' does"
            + " not exist or is not authorized.", refusal("SELECT GET_STAGE_LOCATION(@gsl_nosuch)"));
        assertEquals("SQL compilation error:\nargument 1 to function GET_STAGE_LOCATION needs to be constant, found"
            + " 'X'", refusal("SELECT GET_STAGE_LOCATION(x) FROM (SELECT '@gsl_t' x)"));
        assertEquals("SQL compilation error: error line 1 at position 7\ntoo many arguments for function"
            + " [GET_STAGE_LOCATION('@gsl_t', 'x')] expected 1, got 2", refusal("SELECT GET_STAGE_LOCATION(@gsl_t, 'x')"));
        assertEquals("SQL compilation error: error line 1 at position 7\nnot enough arguments for function"
            + " [GET_STAGE_LOCATION()], expected 1, got 0", refusal("SELECT GET_STAGE_LOCATION()"));
    }
}
