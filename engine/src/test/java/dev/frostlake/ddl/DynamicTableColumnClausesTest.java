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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A dynamic table's own column list takes, after a column's name and type, NOT NULL, a projection policy, a masking
 * policy with the columns its USING names, tags and a comment, in that order and each once (live-verified): the
 * policies apply where the table is read, POLICY_REFERENCES and TAG_REFERENCES_ALL_COLUMNS list them, DESCRIBE names
 * the masking policy, and GET_DDL tells the masking policy, the tags and the comment back, the policy spelled WITH
 * and in full; a column whose clauses are out of their order, or written twice, is refused at the column's name, and
 * a DEFAULT as an unsupported feature.
 */
public class DynamicTableColumnClausesTest extends MeasuredCellsTestSupport {

    /** The warehouse the dynamic tables refresh on: the session's, else the one an embedded engine knows. */
    private String warehouse;

    @BeforeEach
    public void readWarehouse() {
        final Object current = engine.executeQuery("SELECT CURRENT_WAREHOUSE()").getRows().get(0).getValue(0);
        warehouse = current == null ? "COMPUTE_WH" : current.toString();
    }

    @Test
    public void theColumnListsPoliciesTagsAndComments() {
        engine.execute("CREATE OR REPLACE TABLE dc906 (a INT, b INT)");
        engine.execute("INSERT INTO dc906 VALUES (1, 2)");
        engine.execute("CREATE OR REPLACE MASKING POLICY mp906 AS (v INT) RETURNS INT -> 0");
        engine.execute("CREATE OR REPLACE MASKING POLICY mpu906 AS (v INT, w INT) RETURNS INT -> w");
        engine.execute("CREATE OR REPLACE PROJECTION POLICY pp906 AS () RETURNS PROJECTION_CONSTRAINT -> "
            + "PROJECTION_CONSTRAINT(ALLOW => false)");
        engine.execute("CREATE OR REPLACE TAG tg906");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE dd906 (a WITH MASKING POLICY mp906 WITH TAG (tg906 = 'x'), "
            + "b) TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse + " AS SELECT a, b FROM dc906");
        assertCells(new String[][] {
            {"SELECT * FROM dd906", "0|2"},
            {"SELECT POLICY_NAME, POLICY_KIND, REF_COLUMN_NAME FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'dd906', REF_ENTITY_DOMAIN => "
                + "'table')) ORDER BY 1, 2, 3",
                "MP906|MASKING_POLICY|A"},
            {"SELECT COLUMN_NAME, TAG_NAME, TAG_VALUE FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS('"
                + "dd906', 'table')) ORDER BY 1, 2",
                "A|TG906|x"},
            {"DESCRIBE TABLE dd906 ->> SELECT \"name\", \"policy name\", \"comment\" FROM $1 ORDER BY 1",
                "A|TEST_DB.TEST_SCHEMA.MP906|null ;; B|null|null"},
            {"SELECT REPLACE(GET_DDL('TABLE', 'dd906'), COALESCE((SELECT CURRENT_WAREHOUSE()), 'COMPUTE_WH'), 'WH')",
                "create or replace dynamic table DD906( | \tA WITH MASKING POLICY TEST_DB.TEST_SCHEMA.MP906 WITH "
                + "TAG (TEST_DB.TEST_SCHEMA.TG906='x'), | \tB | ) target_lag = '1 hour' refresh_mode = AUTO "
                + "initialize = ON_CREATE warehouse = WH |  as SELECT a, b FROM dc906;"},
        });
    }

    @Test
    public void theClausesOrderAndRefusals() {
        engine.execute("CREATE OR REPLACE TABLE dc907 (a INT, b INT)");
        engine.execute("INSERT INTO dc907 VALUES (1, 2)");
        engine.execute("CREATE OR REPLACE MASKING POLICY mp907 AS (v INT) RETURNS INT -> 0");
        engine.execute("CREATE OR REPLACE PROJECTION POLICY pp907 AS () RETURNS PROJECTION_CONSTRAINT -> "
            + "PROJECTION_CONSTRAINT(ALLOW => true)");
        engine.execute("CREATE OR REPLACE TAG tg907");
        assertCells(new String[][] {
            {"CREATE OR REPLACE DYNAMIC TABLE dd907 (a WITH MASKING POLICY mp907 WITH PROJECTION POLICY pp907 "
                + "WITH TAG (tg907 = 'y') COMMENT 'd', b) TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse
                + " AS SELECT a, b FROM dc907",
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'a'."},
            {"DESCRIBE TABLE dd907 ->> SELECT \"name\", \"type\", \"policy name\", \"comment\" FROM $1 ORDER BY 1",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 0 : SQL compilation "
                + "error: | Table 'DD907' does not exist or not authorized. Your primary role <ROLE> must have at "
                + "least one privilege granted on TABLE DD907."},
            {"CREATE OR REPLACE DYNAMIC TABLE dd907 (a WITH MASKING POLICY mp907 WITH PROJECTION POLICY pp907, "
                + "b) TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse + " AS SELECT a, b FROM dc907",
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'a'."},
            {"DESCRIBE TABLE dd907 ->> SELECT \"name\", \"type\", \"policy name\", \"comment\" FROM $1 ORDER BY 1",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 0 : SQL compilation "
                + "error: | Table 'DD907' does not exist or not authorized. Your primary role <ROLE> must have at "
                + "least one privilege granted on TABLE DD907."},
            {"CREATE OR REPLACE DYNAMIC TABLE dd907 (a WITH PROJECTION POLICY pp907 WITH MASKING POLICY mp907, "
                + "b) TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse + " AS SELECT a, b FROM dc907",
                "Dynamic table DD907 successfully created."},
            {"DESCRIBE TABLE dd907 ->> SELECT \"name\", \"type\", \"policy name\", \"comment\" FROM $1 ORDER BY 1",
                "A|NUMBER(38,0)|TEST_DB.TEST_SCHEMA.MP907|null ;; B|NUMBER(38,0)|null|null"},
            {"CREATE OR REPLACE DYNAMIC TABLE dd907 (a WITH MASKING POLICY mp907 WITH TAG (tg907 = 'y') COMMENT "
                + "'d', b) TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse + " AS SELECT a, b FROM dc907",
                "Dynamic table DD907 successfully created."},
            {"DESCRIBE TABLE dd907 ->> SELECT \"name\", \"type\", \"policy name\", \"comment\" FROM $1 ORDER BY 1",
                "A|NUMBER(38,0)|TEST_DB.TEST_SCHEMA.MP907|d ;; B|NUMBER(38,0)|null|null"},
            {"CREATE OR REPLACE DYNAMIC TABLE dd907 (a WITH PROJECTION POLICY pp907 WITH TAG (tg907 = 'y'), b) "
                + "TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse + " AS SELECT a, b FROM dc907",
                "Dynamic table DD907 successfully created."},
            {"DESCRIBE TABLE dd907 ->> SELECT \"name\", \"type\", \"policy name\", \"comment\" FROM $1 ORDER BY 1",
                "A|NUMBER(38,0)|null|null ;; B|NUMBER(38,0)|null|null"},
            {"CREATE OR REPLACE DYNAMIC TABLE dd907 (a WITH TAG (tg907 = 'y') COMMENT 'd', b) TARGET_LAG = '1 "
                + "hour' WAREHOUSE = " + warehouse + " AS SELECT a, b FROM dc907",
                "Dynamic table DD907 successfully created."},
            {"DESCRIBE TABLE dd907 ->> SELECT \"name\", \"type\", \"policy name\", \"comment\" FROM $1 ORDER BY 1",
                "A|NUMBER(38,0)|null|d ;; B|NUMBER(38,0)|null|null"},
            {"CREATE OR REPLACE DYNAMIC TABLE dd907 (a COMMENT 'd' WITH TAG (tg907 = 'y'), b) TARGET_LAG = '1 "
                + "hour' WAREHOUSE = " + warehouse + " AS SELECT a, b FROM dc907",
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'a'."},
            {"DESCRIBE TABLE dd907 ->> SELECT \"name\", \"type\", \"policy name\", \"comment\" FROM $1 ORDER BY 1",
                "A|NUMBER(38,0)|null|d ;; B|NUMBER(38,0)|null|null"},
            {"CREATE OR REPLACE DYNAMIC TABLE dd907 (a WITH TAG (tg907 = 'y') WITH MASKING POLICY mp907, b) "
                + "TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse + " AS SELECT a, b FROM dc907",
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'a'."},
            {"DESCRIBE TABLE dd907 ->> SELECT \"name\", \"type\", \"policy name\", \"comment\" FROM $1 ORDER BY 1",
                "A|NUMBER(38,0)|null|d ;; B|NUMBER(38,0)|null|null"},
            {"CREATE OR REPLACE DYNAMIC TABLE dd907 (a COMMENT 'd' WITH MASKING POLICY mp907, b) TARGET_LAG = "
                + "'1 hour' WAREHOUSE = " + warehouse + " AS SELECT a, b FROM dc907",
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'a'."},
            {"DESCRIBE TABLE dd907 ->> SELECT \"name\", \"type\", \"policy name\", \"comment\" FROM $1 ORDER BY 1",
                "A|NUMBER(38,0)|null|d ;; B|NUMBER(38,0)|null|null"},
            {"CREATE OR REPLACE DYNAMIC TABLE dd907 (a INT WITH MASKING POLICY mp907, b) TARGET_LAG = '1 hour' "
                + "WAREHOUSE = " + warehouse + " AS SELECT a, b FROM dc907",
                "Dynamic table DD907 successfully created."},
            {"DESCRIBE TABLE dd907 ->> SELECT \"name\", \"type\", \"policy name\", \"comment\" FROM $1 ORDER BY 1",
                "A|NUMBER(38,0)|TEST_DB.TEST_SCHEMA.MP907|null ;; B|NUMBER(38,0)|null|null"},
            {"CREATE OR REPLACE DYNAMIC TABLE dd907 (a INT COMMENT 'x', b) TARGET_LAG = '1 hour' WAREHOUSE = "
                + warehouse + " AS SELECT a, b FROM dc907",
                "Dynamic table DD907 successfully created."},
            {"DESCRIBE TABLE dd907 ->> SELECT \"name\", \"type\", \"policy name\", \"comment\" FROM $1 ORDER BY 1",
                "A|NUMBER(38,0)|null|x ;; B|NUMBER(38,0)|null|null"},
            {"CREATE OR REPLACE DYNAMIC TABLE dd907 (a WITH MASKING POLICY mp907 WITH PROJECTION POLICY pp907 "
                + "WITH TAG (tg907 = 'y'), b) TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse
                + " AS SELECT a, b FROM dc907",
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'a'."},
            {"DESCRIBE TABLE dd907 ->> SELECT \"name\", \"type\", \"policy name\", \"comment\" FROM $1 ORDER BY 1",
                "A|NUMBER(38,0)|null|x ;; B|NUMBER(38,0)|null|null"},
            {"CREATE OR REPLACE DYNAMIC TABLE dd907 (a WITH MASKING POLICY mp907 WITH PROJECTION POLICY pp907 "
                + "COMMENT 'd', b) TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse + " AS SELECT a, b FROM dc907",
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'a'."},
            {"DESCRIBE TABLE dd907 ->> SELECT \"name\", \"type\", \"policy name\", \"comment\" FROM $1 ORDER BY 1",
                "A|NUMBER(38,0)|null|x ;; B|NUMBER(38,0)|null|null"},
            {"CREATE OR REPLACE DYNAMIC TABLE dd907 (a WITH MASKING POLICY mp907 MASKING POLICY mp907, b) "
                + "TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse + " AS SELECT a, b FROM dc907",
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'a'."},
            {"DESCRIBE TABLE dd907 ->> SELECT \"name\", \"type\", \"policy name\", \"comment\" FROM $1 ORDER BY 1",
                "A|NUMBER(38,0)|null|x ;; B|NUMBER(38,0)|null|null"},
            {"CREATE OR REPLACE DYNAMIC TABLE dd907 (a COMMENT 'd' COMMENT 'e', b) TARGET_LAG = '1 hour' "
                + "WAREHOUSE = " + warehouse + " AS SELECT a, b FROM dc907",
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'a'."},
            {"DESCRIBE TABLE dd907 ->> SELECT \"name\", \"type\", \"policy name\", \"comment\" FROM $1 ORDER BY 1",
                "A|NUMBER(38,0)|null|x ;; B|NUMBER(38,0)|null|null"},
            {"CREATE OR REPLACE DYNAMIC TABLE dd907 (a WITH TAG (tg907 = 'y') WITH TAG (tg907 = 'z'), b) "
                + "TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse + " AS SELECT a, b FROM dc907",
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'a'."},
            {"DESCRIBE TABLE dd907 ->> SELECT \"name\", \"type\", \"policy name\", \"comment\" FROM $1 ORDER BY 1",
                "A|NUMBER(38,0)|null|x ;; B|NUMBER(38,0)|null|null"},
            {"CREATE OR REPLACE DYNAMIC TABLE dd907 (a PROJECTION POLICY pp907, b) TARGET_LAG = '1 hour' "
                + "WAREHOUSE = " + warehouse + " AS SELECT a, b FROM dc907",
                "Dynamic table DD907 successfully created."},
            {"DESCRIBE TABLE dd907 ->> SELECT \"name\", \"type\", \"policy name\", \"comment\" FROM $1 ORDER BY 1",
                "A|NUMBER(38,0)|null|null ;; B|NUMBER(38,0)|null|null"},
            {"CREATE OR REPLACE DYNAMIC TABLE dd907 (a WITH MASKING POLICY nosuch907, b) TARGET_LAG = '1 hour' "
                + "WAREHOUSE = " + warehouse + " AS SELECT a, b FROM dc907",
                "REFUSED SQL compilation error: | Masking policy 'TEST_DB.TEST_SCHEMA.NOSUCH907' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH907."},
            {"DESCRIBE TABLE dd907 ->> SELECT \"name\", \"type\", \"policy name\", \"comment\" FROM $1 ORDER BY 1",
                "A|NUMBER(38,0)|null|null ;; B|NUMBER(38,0)|null|null"},
            {"CREATE OR REPLACE DYNAMIC TABLE dd907 (a INT NOT NULL, b) TARGET_LAG = '1 hour' WAREHOUSE = "
                + warehouse + " AS SELECT a, b FROM dc907",
                "Dynamic table DD907 successfully created."},
            {"DESCRIBE TABLE dd907 ->> SELECT \"name\", \"type\", \"policy name\", \"comment\" FROM $1 ORDER BY 1",
                "A|NUMBER(38,0)|null|null ;; B|NUMBER(38,0)|null|null"},
            {"CREATE OR REPLACE DYNAMIC TABLE dd907 (a INT DEFAULT 1, b) TARGET_LAG = '1 hour' WAREHOUSE = "
                + warehouse + " AS SELECT a, b FROM dc907",
                "REFUSED Unsupported feature 'Column constraints [ WITH DEFAULT null ]'."},
            {"DESCRIBE TABLE dd907 ->> SELECT \"name\", \"type\", \"policy name\", \"comment\" FROM $1 ORDER BY 1",
                "A|NUMBER(38,0)|null|null ;; B|NUMBER(38,0)|null|null"},
        });
    }
}
