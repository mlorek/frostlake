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
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a description tells of a routine a handler runs (live-verified). A Java or Scala routine adds its imports,
 * handler, target path, runtime version and packages; a Python one its imports, handler, runtime version and
 * packages, then the artifact repository its packages come from and what an account installed for it; a SQL or
 * JavaScript routine adds nothing. The DDL writes the same properties, each on a line of its own, and
 * INFORMATION_SCHEMA tells the lists as JSON.
 */
public class RoutineHandlerDescriptionTest extends BaseDatabaseTest {

    private static final String PYTHON = " LANGUAGE PYTHON RUNTIME_VERSION = '3.11' HANDLER = 'h'";
    private static final String PYTHON_BODY = " AS $$\ndef h(x):\n    return x\n$$";
    private static final String JAVA_BODY = " AS $$ class H { public static long h(long x) { return x; } } $$";

    private List<String> properties(final String sql) {
        final ResultSet rows = engine.executeQuery(sql);
        final List<String> names = new ArrayList<>();
        for (final Row row : rows.getRows()) {
            names.add(cell(rows, row, "property"));
        }
        return names;
    }

    private String described(final String sql, final String property) {
        final ResultSet rows = engine.executeQuery(sql);
        return cell(rows, soleRowWhere(rows, "property", property), "value");
    }

    private String first(final String sql) {
        final Object value = engine.executeQuery(sql).getRows().get(0).getValue(0);
        return value == null ? null : String.valueOf(value);
    }

    @Test
    public void aJavaRoutineTellsItsHandlerProperties() {
        engine.execute("CREATE FUNCTION hd_j(x NUMBER) RETURNS NUMBER LANGUAGE JAVA RUNTIME_VERSION = '17' PACKAGES ="
            + " ('com.snowflake:snowpark:latest') HANDLER = 'H.h'" + JAVA_BODY);
        final String describe = "DESCRIBE FUNCTION hd_j(NUMBER)";
        assertEquals(Arrays.asList("signature", "returns", "language", "null handling", "volatility", "body",
            "imports", "handler", "target_path", "runtime_version", "packages"), properties(describe));
        assertEquals("[]", described(describe, "imports"));
        assertEquals("H.h", described(describe, "handler"));
        assertNull(described(describe, "target_path"));
        assertEquals("17", described(describe, "runtime_version"));
        assertTrue(described(describe, "packages").startsWith("[com.snowflake:snowpark:"),
            "a package is told bare, in brackets");
        final String ddl = first("SELECT GET_DDL('FUNCTION', 'hd_j(NUMBER)')");
        assertTrue(ddl.contains("LANGUAGE JAVA\nRUNTIME_VERSION = '17'\nPACKAGES = ('com.snowflake:snowpark:"), ddl);
        assertTrue(ddl.contains("')\nHANDLER = 'H.h'\nAS "), ddl);
        assertEquals("H.h", first("SELECT handler FROM information_schema.functions WHERE function_name = 'HD_J'"));
        assertNull(first("SELECT imports FROM information_schema.functions WHERE function_name = 'HD_J'"));
    }

    @Test
    public void aJavaScriptRoutineTellsNoneOfThem() {
        engine.execute("CREATE FUNCTION hd_js(x FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS 'return X;'");
        assertEquals(Arrays.asList("signature", "returns", "language", "null handling", "volatility", "body"),
            properties("DESCRIBE FUNCTION hd_js(FLOAT)"));
    }

    @Test
    public void aPythonFunctionTellsTheRepositoryItsPackagesComeFrom() {
        engine.execute("CREATE FUNCTION hd_p(x NUMBER) RETURNS NUMBER" + PYTHON
            + " PACKAGES = ('numpy', 'requests') COMMENT = 'c'" + PYTHON_BODY);
        final String describe = "DESCRIBE FUNCTION hd_p(NUMBER)";
        assertEquals(Arrays.asList("signature", "returns", "language", "null handling", "volatility", "body",
            "imports", "handler", "runtime_version", "packages", "artifact_repository",
            "artifact_repository_packages", "installed_packages", "is_aggregate",
            "artifact_repository_installed_packages"), properties(describe));
        assertEquals("[]", described(describe, "packages"), "the packages are told with their repository");
        assertEquals("snowflake.snowpark.pypi_shared_repository", described(describe, "artifact_repository"));
        assertEquals("['numpy','requests']", described(describe, "artifact_repository_packages"));
        assertEquals("false", described(describe, "is_aggregate"));
        assertEquals("3.11", described(describe, "runtime_version"));
        assertTrue(first("SELECT GET_DDL('FUNCTION', 'hd_p(NUMBER)')").contains("LANGUAGE PYTHON\n"
            + "RUNTIME_VERSION = '3.11'\nARTIFACT_REPOSITORY = snowflake.snowpark.pypi_shared_repository\n"
            + "PACKAGES = ('numpy','requests')\nHANDLER = 'h'\nCOMMENT='c'\nAS "));
        final String cells = " FROM information_schema.functions WHERE function_name = 'HD_P'";
        assertEquals("[\"numpy\",\"requests\"]", first("SELECT packages" + cells));
        assertEquals("snowflake.snowpark.pypi_shared_repository", first("SELECT artifact_repository" + cells));
        assertEquals("3.11", first("SELECT runtime_version" + cells));
        assertNull(first("SELECT target_path" + cells));
    }

    @Test
    public void aPythonFunctionWithoutPackagesTellsNoRepository() {
        engine.execute("CREATE FUNCTION hd_q(x NUMBER) RETURNS NUMBER" + PYTHON + PYTHON_BODY);
        assertEquals(Arrays.asList("signature", "returns", "language", "null handling", "volatility", "body",
            "imports", "handler", "runtime_version", "packages", "installed_packages", "is_aggregate"),
            properties("DESCRIBE FUNCTION hd_q(NUMBER)"));
        assertTrue(first("SELECT GET_DDL('FUNCTION', 'hd_q(NUMBER)')").contains("LANGUAGE PYTHON\n"
            + "RUNTIME_VERSION = '3.11'\nHANDLER = 'h'\nAS "));
    }

    @Test
    public void aPythonProcedureTellsTheSameWithoutTheAggregateRow() {
        engine.execute("CREATE PROCEDURE hd_pp(x NUMBER) RETURNS NUMBER" + PYTHON
            + " PACKAGES = ('snowflake-snowpark-python') EXECUTE AS CALLER AS $$\ndef h(session, x):\n"
            + "    return x\n$$");
        final String describe = "DESCRIBE PROCEDURE hd_pp(NUMBER)";
        assertEquals(Arrays.asList("signature", "returns", "language", "null handling", "volatility", "execute as",
            "body", "imports", "handler", "runtime_version", "packages", "artifact_repository",
            "artifact_repository_packages", "installed_packages", "artifact_repository_installed_packages"),
            properties(describe));
        assertEquals("['snowflake-snowpark-python']", described(describe, "artifact_repository_packages"));
        assertTrue(first("SELECT GET_DDL('PROCEDURE', 'hd_pp(NUMBER)')").contains("RUNTIME_VERSION = '3.11'\n"
            + "ARTIFACT_REPOSITORY = snowflake.snowpark.pypi_shared_repository\n"
            + "PACKAGES = ('snowflake-snowpark-python')\nHANDLER = 'h'\nEXECUTE AS CALLER\nAS "));
        final String cells = " FROM information_schema.procedures WHERE procedure_name = 'HD_PP'";
        assertEquals("[\"snowflake-snowpark-python\"]", first("SELECT packages" + cells));
        assertEquals("snowflake.snowpark.pypi_shared_repository", first("SELECT artifact_repository" + cells));
    }
}
