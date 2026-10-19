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
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ARTIFACT_REPOSITORY on a Python routine (live-verified): the repository is named, not quoted, and is the shared
 * one or one the schema holds; it is told back as written, with the packages taken from it, and written into the
 * DDL even when the routine names no package. A routine of another language has no such property.
 */
public class RoutineArtifactRepositoryTest extends BaseDatabaseTest {

    private static final String PYTHON = "CREATE OR REPLACE FUNCTION ar_f(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON"
        + " RUNTIME_VERSION = '3.11' HANDLER = 'h' ARTIFACT_REPOSITORY = ";
    private static final String BODY = " AS $$\ndef h(x):\n    return x\n$$";

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
    }

    private String described(final String property) {
        final ResultSet rows = engine.executeQuery("DESCRIBE FUNCTION ar_f(NUMBER)");
        return cell(rows, soleRowWhere(rows, "property", property), "value");
    }

    private String ddl() {
        return String.valueOf(engine.executeQuery("SELECT GET_DDL('FUNCTION', 'ar_f(NUMBER)')").getRows().get(0)
            .getValue(0));
    }

    @Test
    public void theRepositoryIsToldAsWritten() {
        engine.execute(PYTHON + "SNOWFLAKE.SNOWPARK.PYPI_SHARED_REPOSITORY PACKAGES = ('requests')" + BODY);
        assertEquals("SNOWFLAKE.SNOWPARK.PYPI_SHARED_REPOSITORY", described("artifact_repository"));
        assertEquals("['requests']", described("artifact_repository_packages"));
        assertTrue(ddl().contains("RUNTIME_VERSION = '3.11'\nARTIFACT_REPOSITORY ="
            + " SNOWFLAKE.SNOWPARK.PYPI_SHARED_REPOSITORY\nPACKAGES = ('requests')\nHANDLER = 'h'\n"), ddl());
    }

    @Test
    public void aRepositoryWithoutPackagesIsStillTold() {
        engine.execute(PYTHON + "snowflake.snowpark.pypi_shared_repository" + BODY);
        assertEquals("snowflake.snowpark.pypi_shared_repository", described("artifact_repository"));
        assertEquals("[]", described("artifact_repository_packages"));
        assertTrue(ddl().contains("RUNTIME_VERSION = '3.11'\nARTIFACT_REPOSITORY ="
            + " snowflake.snowpark.pypi_shared_repository\nHANDLER = 'h'\n"), ddl());
    }

    @Test
    public void aRepositoryIsANameThatResolves() {
        assertTrue(refusal(PYTHON + "'snowflake.snowpark.pypi_shared_repository' PACKAGES = ('requests')" + BODY)
            .contains("invalid value ''snowflake.snowpark.pypi_shared_repository'' for property"
                + " 'ARTIFACT_REPOSITORY'"));
        assertTrue(refusal(PYTHON + "pypi_shared_repository" + BODY)
            .contains("Object 'pypi_shared_repository' does not exist or not authorized."), "as written");
        assertTrue(refusal(PYTHON + "test_db.test_schema.ar_norepo" + BODY)
            .contains("Object 'test_db.test_schema.ar_norepo' does not exist or not authorized."));
        assertTrue(refusal(PYTHON + "\"ar norepo\"" + BODY)
            .contains("Object '\"ar norepo\"' does not exist or not authorized."));
    }

    @Test
    public void onlyAPythonRoutineHasARepository() {
        final String sentence = "invalid property 'ARTIFACT_REPOSITORY' for 'FUNCTION'";
        final String repository = " ARTIFACT_REPOSITORY = snowflake.snowpark.pypi_shared_repository";
        assertTrue(refusal("CREATE OR REPLACE FUNCTION ar_j(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.h'"
            + repository + " AS $$ class H { public static long h(long x) { return x; } } $$").contains(sentence));
        assertTrue(refusal("CREATE OR REPLACE FUNCTION ar_js(x FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT" + repository
            + " AS 'return X;'").contains(sentence));
        assertTrue(refusal("CREATE OR REPLACE FUNCTION ar_s(x NUMBER) RETURNS NUMBER" + repository + " AS 'x'")
            .contains(sentence));
        assertTrue(refusal("CREATE OR REPLACE PROCEDURE ar_p() RETURNS VARCHAR LANGUAGE SQL" + repository
            + " AS 'BEGIN RETURN 1; END'").contains(sentence), "a procedure is told as a function");
    }
}
