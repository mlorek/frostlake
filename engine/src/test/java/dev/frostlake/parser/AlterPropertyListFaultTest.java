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

package dev.frostlake.parser;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A fault in the property list of an ALTER SCHEMA, an ALTER DATABASE or an ALTER VIEW: the list's first name is
 * told first; a database's and a view's list then tell nothing more, a schema's UNSET the parse's own lines, and a
 * schema's SET each value of the properties a comma joins, then what follows a comma no name follows; ALTER TABLE
 * and ALTER TASK tell the parse's lines alone (all live-verified).
 */
public class AlterPropertyListFaultTest extends MeasuredCellsTestSupport {

    @Test
    public void propertyListFaults() {
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected "
                + "'MAX_DATA_EXTENSION_TIME_IN_DAYS'. | syntax error line 1 at position 65 unexpected "
                + "'DATA_RETENTION_TIME_IN_DAYS'.",
            "ALTER SCHEMA test_db.s1976 UNSET MAX_DATA_EXTENSION_TIME_IN_DAYS DATA_RETENTION_TIME_IN_DAYS");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected "
                + "'DATA_RETENTION_TIME_IN_DAYS'. | syntax error line 1 at position 61 unexpected '1'. | syntax error "
                + "line 1 at position 63 unexpected '<EOF>'.",
            "ALTER SCHEMA test_db.s1976 SET DATA_RETENTION_TIME_IN_DAYS = 1,");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected "
                + "'DATA_RETENTION_TIME_IN_DAYS'. | syntax error line 1 at position 61 unexpected '<EOF>'.",
            "ALTER SCHEMA test_db.s1976 UNSET DATA_RETENTION_TIME_IN_DAYS,");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected "
                + "'DATA_RETENTION_TIME_IN_DAYS'. | syntax error line 1 at position 70 unexpected '<EOF>'.",
            "ALTER SCHEMA test_db.s1976 UNSET DATA_RETENTION_TIME_IN_DAYS, COMMENT,");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected ','.",
            "ALTER SCHEMA test_db.s1976 UNSET , COMMENT");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'COMMENT'. | "
                + "syntax error line 1 at position 41 unexpected 'DEFAULT_DDL_COLLATION'.",
            "ALTER SCHEMA test_db.s1976 UNSET COMMENT DEFAULT_DDL_COLLATION MAX_DATA_EXTENSION_TIME_IN_DAYS");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'COMMENT'. | "
                + "syntax error line 1 at position 41 unexpected ''x''. | syntax error line 1 at position 45 "
                + "unexpected '<EOF>'.",
            "ALTER SCHEMA test_db.s1976 SET COMMENT = 'x',");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'COMMENT'. | "
                + "syntax error line 1 at position 41 unexpected ''x''. | syntax error line 1 at position 76 "
                + "unexpected '1'. | syntax error line 1 at position 78 unexpected '<EOF>'.",
            "ALTER SCHEMA test_db.s1976 SET COMMENT = 'x', DATA_RETENTION_TIME_IN_DAYS = 1,");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'COMMENT'. | "
                + "syntax error line 1 at position 41 unexpected ''x''.",
            "ALTER SCHEMA test_db.s1976 SET COMMENT = 'x' DATA_RETENTION_TIME_IN_DAYS");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'COMMENT'. | "
                + "syntax error line 1 at position 41 unexpected ''x''.",
            "ALTER SCHEMA test_db.s1976 SET COMMENT = 'x' DATA_RETENTION_TIME_IN_DAYS =");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected "
                + "'DATA_RETENTION_TIME_IN_DAYS'.",
            "ALTER SCHEMA test_db.s1976 SET DATA_RETENTION_TIME_IN_DAYS 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected "
                + "'DATA_RETENTION_TIME_IN_DAYS'. | syntax error line 1 at position 61 unexpected '1'. | syntax error "
                + "line 1 at position 63 unexpected ','.",
            "ALTER SCHEMA test_db.s1976 SET DATA_RETENTION_TIME_IN_DAYS = 1,, COMMENT = 'x'");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected "
                + "'DATA_RETENTION_TIME_IN_DAYS'. | syntax error line 1 at position 61 unexpected '1'. | syntax error "
                + "line 1 at position 74 unexpected ''x''.",
            "ALTER SCHEMA test_db.s1976 SET DATA_RETENTION_TIME_IN_DAYS = 1, COMMENT = 'x' x");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ','.",
            "ALTER SCHEMA test_db.s1976 SET , COMMENT = 'x'");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected "
                + "'DATA_RETENTION_TIME_IN_DAYS'. | syntax error line 1 at position 61 unexpected ','. | syntax error "
                + "line 1 at position 62 unexpected '<EOF>'.",
            "ALTER SCHEMA test_db.s1976 SET DATA_RETENTION_TIME_IN_DAYS = ,");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected "
                + "'DATA_RETENTION_TIME_IN_DAYS'. | syntax error line 1 at position 71 unexpected '1'. | syntax error "
                + "line 1 at position 73 unexpected '<EOF>'.",
            "ALTER SCHEMA IF EXISTS test_db.s1976 SET DATA_RETENTION_TIME_IN_DAYS = 1,");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 2 at position 4 unexpected "
                + "'DATA_RETENTION_TIME_IN_DAYS'. | syntax error line 2 at position 34 unexpected '1'. | syntax error "
                + "line 2 at position 36 unexpected '<EOF>'.",
            "ALTER SCHEMA test_db.s1976\nSET DATA_RETENTION_TIME_IN_DAYS = 1,");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected "
                + "'DATA_RETENTION_TIME_IN_DAYS'. | syntax error line 1 at position 61 unexpected '1'. | syntax error "
                + "line 1 at position 63 unexpected ';'.",
            "ALTER SCHEMA test_db.s1976 SET DATA_RETENTION_TIME_IN_DAYS = 1,; SELECT 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected "
                + "'MAX_DATA_EXTENSION_TIME_IN_DAYS'. | syntax error line 1 at position 65 unexpected "
                + "'DATA_RETENTION_TIME_IN_DAYS'.",
            "ALTER SCHEMA test_db.s1976 UNSET MAX_DATA_EXTENSION_TIME_IN_DAYS DATA_RETENTION_TIME_IN_DAYS COMMENT");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected "
                + "'MAX_DATA_EXTENSION_TIME_IN_DAYS'. | syntax error line 1 at position 94 unexpected 'COMMENT'.",
            "ALTER SCHEMA test_db.s1976 UNSET MAX_DATA_EXTENSION_TIME_IN_DAYS, DATA_RETENTION_TIME_IN_DAYS COMMENT");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected "
                + "'DATA_RETENTION_TIME_IN_DAYS'. | syntax error line 1 at position 61 unexpected '1'. | syntax error "
                + "line 1 at position 99 unexpected '<EOF>'.",
            "ALTER SCHEMA test_db.s1976 SET DATA_RETENTION_TIME_IN_DAYS = 1 MAX_DATA_EXTENSION_TIME_IN_DAYS = 2,");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected "
                + "'DATA_RETENTION_TIME_IN_DAYS'. | syntax error line 1 at position 61 unexpected '1'.",
            "ALTER SCHEMA test_db.s1976 SET DATA_RETENTION_TIME_IN_DAYS = 1 x");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected "
                + "'MAX_DATA_EXTENSION_TIME_IN_DAYS'.",
            "ALTER DATABASE no_db1976 UNSET MAX_DATA_EXTENSION_TIME_IN_DAYS DATA_RETENTION_TIME_IN_DAYS");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected "
                + "'DATA_RETENTION_TIME_IN_DAYS'.",
            "ALTER DATABASE no_db1976 SET DATA_RETENTION_TIME_IN_DAYS = 1,");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'COMMENT'.",
            "ALTER DATABASE no_db1976 SET COMMENT = 'x',");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'COMMENT'.",
            "ALTER DATABASE no_db1976 UNSET COMMENT,");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'COMMENT'.",
            "ALTER DATABASE no_db1976 SET COMMENT = 'x' DATA_RETENTION_TIME_IN_DAYS");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 54 unexpected '<EOF>'.",
            "ALTER TABLE t1976 SET DATA_RETENTION_TIME_IN_DAYS = 1,");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'COMMENT'.",
            "ALTER TABLE t1976 UNSET DATA_RETENTION_TIME_IN_DAYS COMMENT");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'COMMENT'.",
            "ALTER VIEW v1976 SET COMMENT = 'x',");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '<EOF>'.",
            "ALTER TASK tk1976 SET COMMENT = 'x',");
    }
}
