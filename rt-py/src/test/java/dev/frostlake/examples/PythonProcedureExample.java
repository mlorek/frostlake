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

package dev.frostlake.examples;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.rt.py.PythonProcedureExecutor;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.types.VariantType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class PythonProcedureExample {

    /** Static helpers only — never instantiated. */
    private PythonProcedureExample() {
    }
    private static final Logger logger = LoggerFactory.getLogger(PythonProcedureExample.class);

    public static void main(final String[] args) {
        final DatabaseEngine engine = new DatabaseEngine();

        try {
            engine.execute("CREATE DATABASE demo_db");
            engine.execute("USE DATABASE demo_db");

            logger.info("=== Python Stored Procedure Examples ===\n");

            logger.info("1. Simple Python Procedure");
            engine.execute("""
                CREATE PROCEDURE pysp()
                RETURNS VARIANT
                LANGUAGE PYTHON
                RUNTIME_VERSION = '3.11'
                PACKAGES = ('snowflake-snowpark-python')
                HANDLER = 'run'
                AS
                $$
def run(session):
    return "{}"
                $$
                """);

            final Schema schema = engine.getCatalog().getDatabase("DEMO_DB").getSchema("PUBLIC");
            final Procedure pysp = schema.getProcedure("pysp");

            logger.info("   Created procedure: " + pysp.getName());
            logger.info("   Language: " + pysp.getLanguage());
            logger.info("   Handler: " + pysp.getHandler());
            logger.info("   Runtime: " + pysp.getRuntimeVersion());
            logger.info("   Packages: " + pysp.getPackages());
            logger.info("");

            logger.info("2. Execute Simple Python Procedure");
            final List<Parameter> params = new ArrayList<>();
            final String body = """
def run(session):
    return "{}"
""";
            final List<String> packages = Arrays.asList("snowflake-snowpark-python");
            final Procedure proc = new Procedure("simple_proc", params, VariantType.VARIANT,
                                          body, "PYTHON", "run", "3.11", packages);

            final Object result = PythonProcedureExecutor.executePythonProcedure(proc, Arrays.asList(), engine);
            logger.info("   Result: " + result);
            logger.info("");

            logger.info("3. Python Procedure with Database Query");
            engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
            engine.execute("INSERT INTO users VALUES (1, 'Alice', 30)");
            engine.execute("INSERT INTO users VALUES (2, 'Bob', 25)");
            engine.execute("INSERT INTO users VALUES (3, 'Charlie', 35)");

            final String queryBody = """
def run(session):
    result = session.sql("SELECT COUNT(*) as cnt FROM users")
    return str(result.count())
""";

            final Procedure queryProc = new Procedure("count_users", params, VariantType.VARIANT,
                                               queryBody, "PYTHON", "run", "3.11", packages);

            final Object countResult = PythonProcedureExecutor.executePythonProcedure(queryProc, Arrays.asList(), engine);
            logger.info("   User count query returned: " + countResult + " row(s)");
            logger.info("");

            logger.info("4. Python Procedure Returning Dictionary");
            final String dictBody = """
def run(session):
    return {"status": "success", "message": "Processing complete"}
""";

            final Procedure dictProc = new Procedure("status_proc", params, VariantType.VARIANT,
                                              dictBody, "PYTHON", "run", "3.11", packages);

            final Object dictResult = PythonProcedureExecutor.executePythonProcedure(dictProc, Arrays.asList(), engine);
            logger.info("   Result type: " + dictResult.getClass().getSimpleName());
            logger.info("   Result: " + dictResult);
            logger.info("");

            logger.info("5. Python Procedure with Multiple Packages");
            engine.execute("""
                CREATE PROCEDURE multi_package_proc()
                RETURNS VARIANT
                LANGUAGE PYTHON
                RUNTIME_VERSION = '3.11'
                PACKAGES = ('snowflake-snowpark-python', 'pandas', 'numpy')
                HANDLER = 'process_data'
                AS
                $$
def process_data(session):
    return "Data processed successfully"
                $$
                """);

            final Procedure multiPkg = schema.getProcedure("multi_package_proc");
            logger.info("   Procedure: " + multiPkg.getName());
            logger.info("   Packages:");
            for (final String pkg : multiPkg.getPackages()) {
                logger.info("     - " + pkg);
            }
            logger.info("");

            logger.info("6. Python Procedure with Complex Logic");
            final String complexBody = """
def run(session):
    users = session.sql("SELECT * FROM users")
    total_age = 0
    count = 0

    # In real Snowpark, we'd iterate through result
    # For this example, we'll return a summary
    result = session.sql("SELECT AVG(age) as avg_age FROM users")

    return "Average age calculated"
""";

            final Procedure complexProc = new Procedure("analyze_users", params, VariantType.VARIANT,
                                                 complexBody, "PYTHON", "run", "3.11", packages);

            final Object complexResult = PythonProcedureExecutor.executePythonProcedure(complexProc, Arrays.asList(), engine);
            logger.info("   Analysis result: " + complexResult);
            logger.info("");

            logger.info("7. Show All Procedures");
            final ResultSet procedures = engine.showProcedures();
            logger.info("   Total procedures: " + procedures.getRowCount());
            int sqlCount = 0;
            int jsCount = 0;
            int pyCount = 0;
            for (final var row : procedures.getRows()) {
                final String procName = row.getValue(0).toString();
                try {
                    final Procedure proc2 = schema.getProcedure(procName.split("\\.")[2]);
                    if ("SQL".equals(proc2.getLanguage())) sqlCount++;
                    else if ("JAVASCRIPT".equals(proc2.getLanguage())) jsCount++;
                    else if ("PYTHON".equals(proc2.getLanguage())) pyCount++;
                } catch (final Exception e) {
                    // Skip if procedure name parsing fails
                }
            }
            logger.info("   - SQL procedures: " + sqlCount);
            logger.info("   - JavaScript procedures: " + jsCount);
            logger.info("   - Python procedures: " + pyCount);

            logger.info("\n=== Python Stored Procedure Demo Complete ===");
            logger.info("\nKey Features Demonstrated:");
            logger.info("✅ CREATE PROCEDURE with LANGUAGE PYTHON");
            logger.info("✅ HANDLER keyword for entry point specification");
            logger.info("✅ RUNTIME_VERSION for Python version");
            logger.info("✅ PACKAGES clause for dependencies");
            logger.info("✅ Session object with sql() method");
            logger.info("✅ Dictionary return values");
            logger.info("✅ Multiple package dependencies");
            logger.info("✅ Complex logic and data processing");

        } finally {
            engine.shutdown();
        }
    }
}
