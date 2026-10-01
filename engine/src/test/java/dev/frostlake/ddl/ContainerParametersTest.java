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
 * Database and schema parameters: CREATE takes them in any order, ALTER sets and unsets them, and
 * SHOW PARAMETERS IN DATABASE | SCHEMA reports each with the level it comes from.
 */
public class ContainerParametersTest extends BaseDatabaseTest {

    private String value(final String scope, final String key) {
        final ResultSet rs = engine.executeQuery("SHOW PARAMETERS LIKE '" + key + "' IN " + scope);
        return cell(rs, soleRowWhere(rs, "key", key), "value");
    }

    private String level(final String scope, final String key) {
        final ResultSet rs = engine.executeQuery("SHOW PARAMETERS LIKE '" + key + "' IN " + scope);
        return cell(rs, soleRowWhere(rs, "key", key), "level");
    }

    @Test
    public void createDatabaseTakesItsPropertiesInAnyOrder() {
        engine.execute("CREATE DATABASE cp_db1 LOG_LEVEL = 'WARN' COMMENT = 'c' DATA_RETENTION_TIME_IN_DAYS = 3"
            + " MAX_DATA_EXTENSION_TIME_IN_DAYS = 7 EXTERNAL_VOLUME = my_vol");
        assertEquals("WARN", value("DATABASE cp_db1", "LOG_LEVEL"));
        assertEquals("DATABASE", level("DATABASE cp_db1", "LOG_LEVEL"));
        assertEquals("3", value("DATABASE cp_db1", "DATA_RETENTION_TIME_IN_DAYS"));
        assertEquals("7", value("DATABASE cp_db1", "MAX_DATA_EXTENSION_TIME_IN_DAYS"));
        assertEquals("MY_VOL", value("DATABASE cp_db1", "EXTERNAL_VOLUME"));
        assertEquals("OFF", value("DATABASE cp_db1", "TRACE_LEVEL"));
        assertEquals("", level("DATABASE cp_db1", "TRACE_LEVEL"));
        final ResultSet dbs = engine.executeQuery("SHOW DATABASES LIKE 'CP_DB1'");
        assertEquals("c", cell(dbs, soleRowWhere(dbs, "name", "CP_DB1"), "comment"));
    }

    @Test
    public void aSchemaInheritsItsDatabasesParametersUntilItSetsItsOwn() {
        engine.execute("CREATE DATABASE cp_db2 TRACE_LEVEL = 'ALWAYS'");
        engine.execute("CREATE SCHEMA cp_db2.s1 LOG_LEVEL = 'ERROR'");
        assertEquals("ALWAYS", value("SCHEMA cp_db2.s1", "TRACE_LEVEL"));
        assertEquals("DATABASE", level("SCHEMA cp_db2.s1", "TRACE_LEVEL"));
        assertEquals("ERROR", value("SCHEMA cp_db2.s1", "LOG_LEVEL"));
        assertEquals("SCHEMA", level("SCHEMA cp_db2.s1", "LOG_LEVEL"));
        engine.execute("ALTER SCHEMA cp_db2.s1 SET TRACE_LEVEL = 'ON_EVENT'");
        assertEquals("ON_EVENT", value("SCHEMA cp_db2.s1", "TRACE_LEVEL"));
        engine.execute("ALTER SCHEMA cp_db2.s1 UNSET TRACE_LEVEL");
        assertEquals("ALWAYS", value("SCHEMA cp_db2.s1", "TRACE_LEVEL"));
        engine.execute("ALTER DATABASE cp_db2 UNSET TRACE_LEVEL");
        assertEquals("OFF", value("SCHEMA cp_db2.s1", "TRACE_LEVEL"));
        assertEquals("", level("SCHEMA cp_db2.s1", "TRACE_LEVEL"));
        assertTrue(engine.executeQuery("SHOW PARAMETERS IN SCHEMA cp_db2.s1").getRows().size() > 10);
    }

    @Test
    public void alterDatabaseSetsAndUnsetsAParameterAndTheComment() {
        engine.execute("CREATE DATABASE cp_db3 COMMENT = 'before'");
        engine.execute("ALTER DATABASE cp_db3 SET SUSPEND_TASK_AFTER_NUM_FAILURES = 4");
        assertEquals("4", value("DATABASE cp_db3", "SUSPEND_TASK_AFTER_NUM_FAILURES"));
        engine.execute("ALTER DATABASE cp_db3 UNSET SUSPEND_TASK_AFTER_NUM_FAILURES");
        assertEquals("10", value("DATABASE cp_db3", "SUSPEND_TASK_AFTER_NUM_FAILURES"));
        engine.execute("ALTER DATABASE cp_db3 UNSET COMMENT");
        final ResultSet dbs = engine.executeQuery("SHOW DATABASES LIKE 'CP_DB3'");
        assertEquals("", cell(dbs, soleRowWhere(dbs, "name", "CP_DB3"), "comment"));
        engine.execute("CREATE SCHEMA cp_db3.s COMMENT = 'x'");
        engine.execute("ALTER SCHEMA cp_db3.s UNSET COMMENT");
        final ResultSet schemas = engine.executeQuery("SHOW SCHEMAS LIKE 'S' IN DATABASE cp_db3");
        assertEquals("", cell(schemas, soleRowWhere(schemas, "name", "S"), "comment"));
    }

    @Test
    public void anUnknownPropertyIsRefusedAndCreatesNothing() {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE DATABASE cp_db4 NO_SUCH_PROPERTY = 1");
            }
        });
        assertTrue(refused.getMessage().contains("invalid property 'NO_SUCH_PROPERTY' for 'DATABASE'"),
            refused.getMessage());
        assertEquals(0, engine.executeQuery("SHOW DATABASES LIKE 'CP_DB4'").getRows().size());
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE DATABASE cp_db4 DATA_RETENTION_TIME_IN_DAYS = 100");
            }
        });
        assertEquals(0, engine.executeQuery("SHOW DATABASES LIKE 'CP_DB4'").getRows().size(),
            "a refused retention leaves no database behind");
    }

    @Test
    public void aCloneKeepsTheSourcesParameters() {
        engine.execute("CREATE DATABASE cp_db5 LOG_LEVEL = 'INFO'");
        engine.execute("CREATE DATABASE cp_db5_copy CLONE cp_db5");
        assertEquals("INFO", value("DATABASE cp_db5_copy", "LOG_LEVEL"));
    }
}
