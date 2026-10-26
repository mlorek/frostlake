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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A view's aggregation and join policies beside its other policies: GET_DDL's order, a second policy refused
 * without FORCE, an idle UNSET naming the view a TABLE, an ENTITY KEY naming no column refused and one counting
 * the entities of a group by another column, POLICY_REFERENCES listing them, and a schema's clone naming its own
 * copies of the policies (all live-verified).
 */
public class ViewPolicySurfacesTest extends MeasuredCellsTestSupport {

    @Test
    public void policySurfaces() {
        engine.execute("CREATE OR REPLACE TABLE t690 (a INT, b INT)");
        engine.execute("INSERT INTO t690 VALUES (1, 10), (1, 20), (2, 30), (3, 40)");
        engine.execute("CREATE OR REPLACE AGGREGATION POLICY ap690 AS () RETURNS AGGREGATION_CONSTRAINT -> "
            + "AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => 2)");
        engine.execute("CREATE OR REPLACE AGGREGATION POLICY ap690b AS () RETURNS AGGREGATION_CONSTRAINT -> "
            + "AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => 3)");
        engine.execute("CREATE OR REPLACE JOIN POLICY jp690 AS () RETURNS JOIN_CONSTRAINT -> "
            + "JOIN_CONSTRAINT(JOIN_REQUIRED => TRUE)");
        engine.execute("CREATE OR REPLACE JOIN POLICY jp690b AS () RETURNS JOIN_CONSTRAINT -> "
            + "JOIN_CONSTRAINT(JOIN_REQUIRED => FALSE)");
        engine.execute("CREATE OR REPLACE ROW ACCESS POLICY rap690 AS (x INT) RETURNS BOOLEAN -> TRUE");
        assertAnswer("View V690A successfully created.",
            "CREATE OR REPLACE VIEW v690a WITH JOIN POLICY jp690 AS SELECT a, b FROM t690");
        assertAnswer("create or replace view V690A( | 	A, | 	B | ) WITH JOIN POLICY TEST_DB.TEST_SCHEMA.JP690 |  as "
                + "SELECT a, b FROM t690;",
            "SELECT GET_DDL('VIEW', 'v690a')");
        assertAnswer("View V690B successfully created.",
            "CREATE OR REPLACE VIEW v690b WITH ROW ACCESS POLICY rap690 ON (a) WITH AGGREGATION POLICY ap690 AS "
                + "SELECT a, b FROM t690");
        assertAnswer("create or replace view V690B( | 	A, | 	B | ) WITH AGGREGATION POLICY TEST_DB.TEST_SCHEMA.AP690 |"
                + "  WITH ROW ACCESS POLICY TEST_DB.TEST_SCHEMA.RAP690 ON (A) |  as SELECT a, b FROM t690;",
            "SELECT GET_DDL('VIEW', 'v690b')");
        assertAnswer("View V690C successfully created.",
            "CREATE OR REPLACE VIEW v690c WITH AGGREGATION POLICY ap690 WITH JOIN POLICY jp690 AS SELECT a FROM t690");
        assertAnswer("create or replace view V690C( | 	A | ) WITH AGGREGATION POLICY TEST_DB.TEST_SCHEMA.AP690 |  "
                + "WITH JOIN POLICY TEST_DB.TEST_SCHEMA.JP690 |  as SELECT a FROM t690;",
            "SELECT GET_DDL('VIEW', 'v690c')");
        assertAnswer("REFUSED Object V690C already has a AGGREGATION_POLICY. Only one AGGREGATION_POLICY is allowed "
                + "at a time.",
            "ALTER VIEW v690c SET AGGREGATION POLICY ap690b");
        assertAnswer("Statement executed successfully.", "ALTER VIEW v690c SET AGGREGATION POLICY ap690b FORCE");
        assertAnswer("create or replace view V690C( | 	A | ) WITH AGGREGATION POLICY TEST_DB.TEST_SCHEMA.AP690B |  "
                + "WITH JOIN POLICY TEST_DB.TEST_SCHEMA.JP690 |  as SELECT a FROM t690;",
            "SELECT GET_DDL('VIEW', 'v690c')");
        assertAnswer("REFUSED Any policy of kind AGGREGATION_POLICY is not attached to TABLE V690A.",
            "ALTER VIEW v690a UNSET AGGREGATION POLICY");
        assertAnswer("REFUSED Object V690A already has a JOIN_POLICY. Only one JOIN_POLICY is allowed at a time.",
            "ALTER VIEW v690a SET JOIN POLICY jp690b");
        assertAnswer("Statement executed successfully.", "ALTER VIEW v690a SET JOIN POLICY jp690b FORCE");
        assertAnswer("Statement executed successfully.", "ALTER VIEW v690a UNSET JOIN POLICY");
        assertAnswer("REFUSED Any policy of kind JOIN_POLICY is not attached to TABLE V690A.",
            "ALTER VIEW v690a UNSET JOIN POLICY");
        assertAnswer("REFUSED SQL compilation error: | column 'NOSUCH' does not exist",
            "CREATE OR REPLACE VIEW v690d WITH AGGREGATION POLICY ap690 ENTITY KEY (nosuch) AS SELECT a, b FROM t690");
        assertAnswer("View V690E successfully created.",
            "CREATE OR REPLACE VIEW v690e WITH AGGREGATION POLICY ap690 ENTITY KEY (a) AS SELECT a, b FROM t690");
        assertAnswer("4", "SELECT COUNT(*) FROM v690e");
        assertAnswer("null|4", "SELECT b, COUNT(*) FROM v690e GROUP BY b ORDER BY b");
        assertAnswer("null|4", "SELECT a, COUNT(*) FROM v690e GROUP BY a ORDER BY a");
        assertAnswer("create or replace view V690E( | 	A, | 	B | ) WITH AGGREGATION POLICY TEST_DB.TEST_SCHEMA.AP690 "
                + "ENTITY KEY (A) |  as SELECT a, b FROM t690;",
            "SELECT GET_DDL('VIEW', 'v690e')");
        assertShape("created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), reserved:VARCHAR(16777216), "
                + "database_name:VARCHAR(16777216), schema_name:VARCHAR(16777216), owner:VARCHAR(16777216), "
                + "comment:VARCHAR(16777216), text:VARCHAR(16777216), is_secure:VARCHAR(16777216), "
                + "is_materialized:VARCHAR(16777216), owner_role_type:VARCHAR(16777216), "
                + "change_tracking:VARCHAR(16777216)",
            "SHOW VIEWS LIKE 'V690B'");
        assertAnswer("A|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; B|NUMBER(38,0)|COLUMN|Y|null|"
                + "N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW v690b");
        assertAnswer("AP690|AGGREGATION_POLICY|V690B|VIEW|null|null ;; RAP690|ROW_ACCESS_POLICY|V690B|VIEW|null|[ "
                + "\"A\" ]",
            "SELECT POLICY_NAME, POLICY_KIND, REF_ENTITY_NAME, REF_ENTITY_DOMAIN, REF_COLUMN_NAME, "
                + "REF_ARG_COLUMN_NAMES FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => "
                + "'TEST_DB.TEST_SCHEMA.V690B', REF_ENTITY_DOMAIN => 'VIEW')) ORDER BY 1");
        assertAnswer("AP690B|AGGREGATION_POLICY|V690C|VIEW|null|null ;; JP690|JOIN_POLICY|V690C|VIEW|null|null",
            "SELECT POLICY_NAME, POLICY_KIND, REF_ENTITY_NAME, REF_ENTITY_DOMAIN, REF_COLUMN_NAME, "
                + "REF_ARG_COLUMN_NAMES FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => "
                + "'TEST_DB.TEST_SCHEMA.V690C', REF_ENTITY_DOMAIN => 'VIEW')) ORDER BY 1");
        assertAnswer("AP690|AGGREGATION_POLICY|V690E|VIEW|null|[ \"A\" ]",
            "SELECT POLICY_NAME, POLICY_KIND, REF_ENTITY_NAME, REF_ENTITY_DOMAIN, REF_COLUMN_NAME, "
                + "REF_ARG_COLUMN_NAMES FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => "
                + "'TEST_DB.TEST_SCHEMA.V690E', REF_ENTITY_DOMAIN => 'VIEW')) ORDER BY 1");
        assertAnswer("Statement executed successfully.",
            "ALTER VIEW v690e SET AGGREGATION POLICY ap690b ENTITY KEY (b) FORCE");
        assertAnswer("create or replace view V690E( | 	A, | 	B | ) WITH AGGREGATION POLICY TEST_DB.TEST_SCHEMA.AP690B "
                + "ENTITY KEY (B) |  as SELECT a, b FROM t690;",
            "SELECT GET_DDL('VIEW', 'v690e')");
        assertAnswer("Schema S690 successfully created.", "CREATE OR REPLACE SCHEMA s690 CLONE test_schema");
        assertAnswer("create or replace view V690C( | 	A | ) WITH AGGREGATION POLICY TEST_DB.S690.AP690B |  WITH JOIN "
                + "POLICY TEST_DB.S690.JP690 |  as SELECT a FROM t690;",
            "SELECT GET_DDL('VIEW', 's690.v690c')");
        assertAnswer("S690 successfully dropped.", "DROP SCHEMA IF EXISTS s690");
    }
}
