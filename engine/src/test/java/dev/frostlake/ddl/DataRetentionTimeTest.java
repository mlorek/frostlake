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

/**
 * DATA_RETENTION_TIME_IN_DAYS is a STORED value on a database and a schema, and INHERITANCE IS
 * LIVE (live-verified): a schema with no value of its own reports its database's CURRENT value —
 * an ALTER on the database shows through immediately — and UNSET restores exactly that fallback,
 * never a copy taken at creation. A table reports its own value when it declares one and its
 * container's chain otherwise.
 *
 * <p>★ THE TWO VALUE REFUSALS: past 90 days is {@code Exceeds maximum allowable retention time
 * (90 day(s)).} on a database and a schema alike, and a negative value is the bracketed
 * invalid-value shape with the bare integer echo.
 */
public class DataRetentionTimeTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return e.getMessage();
    }

    private String cell(final String showSql, final String nameColumn, final String name,
                        final String valueColumn) {
        final ResultSet rs = engine.executeQuery(showSql);
        int nameIdx = -1;
        int valIdx = -1;
        for (int c = 0; c < rs.getColumns().size(); c++) {
            if (rs.getColumns().get(c).getName().equalsIgnoreCase(nameColumn)) {
                nameIdx = c;
            }
            if (rs.getColumns().get(c).getName().equalsIgnoreCase(valueColumn)) {
                valIdx = c;
            }
        }
        for (int r = 0; r < rs.getRows().size(); r++) {
            if (name.equalsIgnoreCase(String.valueOf(rs.getRows().get(r).getValue(nameIdx)))) {
                return String.valueOf(rs.getRows().get(r).getValue(valIdx));
            }
        }
        return "NOT-FOUND";
    }

    @Test
    public void theWrittenValueIsStoredAndReadBack() {
        engine.execute("CREATE OR REPLACE SCHEMA drt_s2 DATA_RETENTION_TIME_IN_DAYS = 2");
        assertEquals("2", cell("SHOW SCHEMAS", "name", "DRT_S2", "retention_time"));
        assertEquals("2", cell("SELECT SCHEMA_NAME, RETENTION_TIME FROM INFORMATION_SCHEMA.SCHEMATA",
            "SCHEMA_NAME", "DRT_S2", "RETENTION_TIME"));
        engine.execute("DROP SCHEMA IF EXISTS drt_s2");
    }

    @Test
    public void inheritanceIsResolvedLive() {
        engine.execute("CREATE OR REPLACE DATABASE drt_db DATA_RETENTION_TIME_IN_DAYS = 5");
        assertEquals("5", cell("SHOW DATABASES", "name", "DRT_DB", "retention_time"));
        engine.execute("CREATE SCHEMA drt_db.inh_s");
        assertEquals("5",
            cell("SHOW SCHEMAS IN DATABASE drt_db", "name", "INH_S", "retention_time"));
        engine.execute("CREATE TABLE drt_db.inh_s.t1 (a INT)");
        assertEquals("5",
            cell("SHOW TABLES IN SCHEMA drt_db.inh_s", "name", "T1", "retention_time"));
        engine.execute("ALTER DATABASE drt_db SET DATA_RETENTION_TIME_IN_DAYS = 7");
        assertEquals("7",
            cell("SHOW SCHEMAS IN DATABASE drt_db", "name", "INH_S", "retention_time"));
        engine.execute("CREATE TABLE drt_db.inh_s.t2 (a INT) DATA_RETENTION_TIME_IN_DAYS = 2");
        assertEquals("2",
            cell("SHOW TABLES IN SCHEMA drt_db.inh_s", "name", "T2", "retention_time"));
        engine.execute("DROP DATABASE IF EXISTS drt_db");
    }

    @Test
    public void unsetRestoresTheContainersValue() {
        engine.execute("CREATE OR REPLACE DATABASE drt_db2 DATA_RETENTION_TIME_IN_DAYS = 5");
        engine.execute("CREATE SCHEMA drt_db2.u_s");
        engine.execute("ALTER SCHEMA drt_db2.u_s SET DATA_RETENTION_TIME_IN_DAYS = 3");
        assertEquals("3",
            cell("SHOW SCHEMAS IN DATABASE drt_db2", "name", "U_S", "retention_time"));
        engine.execute("ALTER SCHEMA drt_db2.u_s UNSET DATA_RETENTION_TIME_IN_DAYS");
        assertEquals("5",
            cell("SHOW SCHEMAS IN DATABASE drt_db2", "name", "U_S", "retention_time"));
        engine.execute("DROP DATABASE IF EXISTS drt_db2");
    }

    @Test
    public void pastNinetyDaysRefusesOnBothKinds() {
        assertEquals("SQL compilation error:\nExceeds maximum allowable retention time (90 day(s)).",
            refusal("CREATE OR REPLACE SCHEMA drt_cap DATA_RETENTION_TIME_IN_DAYS = 91"));
        assertEquals("SQL compilation error:\nExceeds maximum allowable retention time (90 day(s)).",
            refusal("CREATE OR REPLACE DATABASE drt_capdb DATA_RETENTION_TIME_IN_DAYS = 91"));
    }

    @Test
    public void aNegativeValueIsTheBracketedInvalidValueShape() {
        assertEquals("SQL compilation error:\ninvalid value [-1] for parameter"
                + " 'DATA_RETENTION_TIME_IN_DAYS'",
            refusal("CREATE OR REPLACE SCHEMA drt_neg DATA_RETENTION_TIME_IN_DAYS = -1"));
    }
}
