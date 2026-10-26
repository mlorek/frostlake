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

package dev.frostlake.security;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A view's column list attaches masking and projection policies, tags and comments to its columns, which its
 * readers meet and DESCRIBE VIEW, GET_DDL and POLICY_REFERENCES report; a column holding both policies, a
 * clause twice or out of order is refused at the list's first column, CREATE OR ALTER VIEW refuses them, and a
 * materialized view's list takes them too (all live-verified).
 */
public class ViewColumnListPoliciesTest extends MeasuredCellsTestSupport {

    @Test
    public void columnClausesOfAView() {
        engine.execute("CREATE OR REPLACE TABLE tv751 (id INT, secret VARCHAR, region VARCHAR)");
        engine.execute("INSERT INTO tv751 VALUES (1, 'a', 'EU'), (2, 'bb', 'US')");
        engine.execute("CREATE OR REPLACE MASKING POLICY mp751 AS (v VARCHAR) RETURNS VARCHAR -> '***'");
        engine.execute("CREATE OR REPLACE MASKING POLICY mpu751 AS (v VARCHAR, r VARCHAR) RETURNS VARCHAR -> CASE "
            + "WHEN r = 'EU' THEN v ELSE '***' END");
        engine.execute("CREATE OR REPLACE PROJECTION POLICY pp751 AS () RETURNS PROJECTION_CONSTRAINT -> "
            + "PROJECTION_CONSTRAINT(ALLOW => false)");
        engine.execute("CREATE OR REPLACE TAG tg751");
        assertAnswer("View V751 successfully created.",
            "CREATE OR REPLACE VIEW v751 (id, secret WITH MASKING POLICY mp751, region) AS SELECT id, secret, region "
                + "FROM tv751");
        assertAnswer("1|***|EU ;; 2|***|US", "SELECT * FROM v751 ORDER BY 1");
        assertAnswer("MP751|MASKING_POLICY|SECRET",
            "SELECT POLICY_NAME, POLICY_KIND, REF_COLUMN_NAME FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'v751', REF_ENTITY_DOMAIN => "
                + "'VIEW')) ORDER BY 1, 2, 3");
        assertAnswer("ID|null|null ;; REGION|null|null ;; SECRET|TEST_DB.TEST_SCHEMA.MP751|null",
            "DESCRIBE VIEW v751 ->> SELECT \"name\", \"policy name\", \"comment\" FROM $1 ORDER BY 1");
        assertAnswer("create or replace view V751( | 	ID, | 	SECRET WITH MASKING POLICY TEST_DB.TEST_SCHEMA.MP751, | "
                + "	REGION | ) as SELECT id, secret, region FROM tv751;",
            "SELECT GET_DDL('VIEW', 'v751')");
        assertAnswer("View V751 successfully created.",
            "CREATE OR REPLACE VIEW v751 (id, secret MASKING POLICY mp751, region) AS SELECT id, secret, region FROM "
                + "tv751");
        assertAnswer("1|***|EU ;; 2|***|US", "SELECT * FROM v751 ORDER BY 1");
        assertAnswer("MP751|MASKING_POLICY|SECRET",
            "SELECT POLICY_NAME, POLICY_KIND, REF_COLUMN_NAME FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'v751', REF_ENTITY_DOMAIN => "
                + "'VIEW')) ORDER BY 1, 2, 3");
        assertAnswer("View V751 successfully created.",
            "CREATE OR REPLACE VIEW v751 (id, secret WITH MASKING POLICY mpu751 USING (secret, region), region) AS "
                + "SELECT id, secret, region FROM tv751");
        assertAnswer("1|a|EU ;; 2|***|US", "SELECT * FROM v751 ORDER BY 1");
        assertAnswer("create or replace view V751( | 	ID, | 	SECRET WITH MASKING POLICY TEST_DB.TEST_SCHEMA.MPU751 "
                + "USING (SECRET, REGION), | 	REGION | ) as SELECT id, secret, region FROM tv751;",
            "SELECT GET_DDL('VIEW', 'v751')");
        assertAnswer("View V751 successfully created.",
            "CREATE OR REPLACE VIEW v751 (id, secret WITH PROJECTION POLICY pp751, region) AS SELECT id, secret, "
                + "region FROM tv751");
        assertAnswer("1|EU ;; 2|US", "SELECT id, region FROM v751 ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: The following columns are restricted by a Projection Policy. "
                + "Please remove them from the list of projected columns: |  |  SECRET",
            "SELECT secret FROM v751");
        assertAnswer("PP751|PROJECTION_POLICY|SECRET",
            "SELECT POLICY_NAME, POLICY_KIND, REF_COLUMN_NAME FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'v751', REF_ENTITY_DOMAIN => "
                + "'VIEW')) ORDER BY 1, 2, 3");
        assertAnswer("ID|null|null ;; REGION|null|null ;; SECRET|null|null",
            "DESCRIBE VIEW v751 ->> SELECT \"name\", \"policy name\", \"comment\" FROM $1 ORDER BY 1");
        assertAnswer("create or replace view V751( | 	ID, | 	SECRET WITH PROJECTION POLICY TEST_DB.TEST_SCHEMA.PP751, "
                + "| 	REGION | ) as SELECT id, secret, region FROM tv751;",
            "SELECT GET_DDL('VIEW', 'v751')");
        assertAnswer("View V751 successfully created.",
            "CREATE OR REPLACE VIEW v751 (id WITH TAG (tg751 = 'x'), secret, region) AS SELECT id, secret, region "
                + "FROM tv751");
        assertAnswer("ID|TG751|x",
            "SELECT COLUMN_NAME, TAG_NAME, TAG_VALUE FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS('v751',"
                + " 'TABLE')) ORDER BY 1, 2");
        assertAnswer("create or replace view V751( | 	ID WITH TAG (TEST_DB.TEST_SCHEMA.TG751='x'), | 	SECRET, | "
                + "	REGION | ) as SELECT id, secret, region FROM tv751;",
            "SELECT GET_DDL('VIEW', 'v751')");
        assertAnswer("View V751 successfully created.",
            "CREATE OR REPLACE VIEW v751 (id COMMENT 'c', secret WITH MASKING POLICY mp751 COMMENT 'd', region) AS "
                + "SELECT id, secret, region FROM tv751");
        assertAnswer("ID|null|c ;; REGION|null|null ;; SECRET|TEST_DB.TEST_SCHEMA.MP751|d",
            "DESCRIBE VIEW v751 ->> SELECT \"name\", \"policy name\", \"comment\" FROM $1 ORDER BY 1");
        assertAnswer("create or replace view V751( | 	ID COMMENT 'c', | 	SECRET WITH MASKING POLICY "
                + "TEST_DB.TEST_SCHEMA.MP751 COMMENT 'd', | 	REGION | ) as SELECT id, secret, region FROM tv751;",
            "SELECT GET_DDL('VIEW', 'v751')");
        assertAnswer("View V751 successfully created.",
            "CREATE OR REPLACE VIEW v751 (id, secret WITH MASKING POLICY mp751 WITH TAG (tg751 = 'y'), region) AS "
                + "SELECT id, secret, region FROM tv751");
        assertAnswer("MP751|MASKING_POLICY|SECRET",
            "SELECT POLICY_NAME, POLICY_KIND, REF_COLUMN_NAME FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'v751', REF_ENTITY_DOMAIN => "
                + "'VIEW')) ORDER BY 1, 2, 3");
        assertAnswer("SECRET|TG751|y",
            "SELECT COLUMN_NAME, TAG_NAME, TAG_VALUE FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS('v751',"
                + " 'TABLE')) ORDER BY 1, 2");
        assertAnswer("create or replace view V751( | 	ID, | 	SECRET WITH MASKING POLICY TEST_DB.TEST_SCHEMA.MP751 "
                + "WITH TAG (TEST_DB.TEST_SCHEMA.TG751='y'), | 	REGION | ) as SELECT id, secret, region FROM tv751;",
            "SELECT GET_DDL('VIEW', 'v751')");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'id'.",
            "CREATE OR REPLACE VIEW v751 (id, secret WITH MASKING POLICY mp751 WITH PROJECTION POLICY pp751, region) "
                + "AS SELECT id, secret, region FROM tv751");
        assertAnswer("MP751|MASKING_POLICY|SECRET",
            "SELECT POLICY_NAME, POLICY_KIND, REF_COLUMN_NAME FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'v751', REF_ENTITY_DOMAIN => "
                + "'VIEW')) ORDER BY 1, 2, 3");
        assertAnswer("REFUSED SQL compilation error: | Masking policy 'TEST_DB.TEST_SCHEMA.NOSUCH751' does not exist "
                + "or not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH751.",
            "CREATE OR REPLACE VIEW v751 (id, secret WITH MASKING POLICY nosuch751, region) AS SELECT id, secret, "
                + "region FROM tv751");
        assertAnswer("View V751 successfully created.",
            "CREATE OR REPLACE VIEW v751 (id, secret WITH MASKING POLICY mp751, region WITH MASKING POLICY mp751) AS "
                + "SELECT id, secret, region FROM tv751");
        assertAnswer("1|***|*** ;; 2|***|***", "SELECT * FROM v751 ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: COLUMN data type NUMBER(38,0) does not match with masking policy "
                + "data type TEXT.",
            "CREATE OR REPLACE VIEW v751 (id WITH MASKING POLICY mp751, secret, region) AS SELECT id, secret, region "
                + "FROM tv751");
        assertAnswer("View V751 successfully created.",
            "CREATE OR REPLACE SECURE VIEW v751 (id, secret WITH MASKING POLICY mp751, region) AS SELECT id, secret, "
                + "region FROM tv751");
        assertAnswer("1|***|EU ;; 2|***|US", "SELECT * FROM v751 ORDER BY 1");
        assertAnswer("REFUSED CREATE OR ALTER does not support setting policies or tags.",
            "CREATE OR ALTER VIEW v751 (id, secret WITH MASKING POLICY mp751, region) AS SELECT id, secret, region "
                + "FROM tv751");
        assertAnswer("V751 already exists, statement succeeded.",
            "CREATE VIEW IF NOT EXISTS v751 (id, secret WITH MASKING POLICY mp751, region) AS SELECT id, secret, "
                + "region FROM tv751");
        assertAnswer("REFUSED SQL compilation error: Policy MP751 cannot be dropped/replaced as it is associated with "
                + "one or more entities.",
            "DROP MASKING POLICY mp751");
    }

    @Test
    public void shapesAndMaterializedViews() {
        engine.execute("CREATE OR REPLACE TABLE tv752 (id INT, secret VARCHAR, region VARCHAR)");
        engine.execute("INSERT INTO tv752 VALUES (1, 'a', 'EU')");
        engine.execute("CREATE OR REPLACE MASKING POLICY mp752 AS (v VARCHAR) RETURNS VARCHAR -> '***'");
        engine.execute("CREATE OR REPLACE MASKING POLICY mq752 AS (v VARCHAR) RETURNS VARCHAR -> 'q'");
        engine.execute("CREATE OR REPLACE TAG tg752");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'id'.",
            "CREATE OR REPLACE VIEW v752 (id, secret WITH TAG (tg752 = 'x') WITH MASKING POLICY mp752, region) AS "
                + "SELECT id, secret, region FROM tv752");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'id'.",
            "CREATE OR REPLACE VIEW v752 (id, secret WITH MASKING POLICY mp752 WITH MASKING POLICY mq752, region) AS "
                + "SELECT id, secret, region FROM tv752");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'id'.",
            "CREATE OR REPLACE VIEW v752 (id, secret WITH TAG (tg752 = 'x') WITH TAG (tg752 = 'y'), region) AS "
                + "SELECT id, secret, region FROM tv752");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'id'.",
            "CREATE OR REPLACE VIEW v752 (id, secret COMMENT 'c' WITH MASKING POLICY mp752, region) AS SELECT id, "
                + "secret, region FROM tv752");
        assertAnswer("View V752 successfully created.",
            "CREATE OR REPLACE VIEW v752 (id, secret MASKING POLICY mp752 TAG (tg752 = 'x'), region) AS SELECT id, "
                + "secret, region FROM tv752");
        assertAnswer("1|***|EU", "SELECT * FROM v752 ORDER BY 1");
        assertAnswer("Materialized view MV752 successfully created.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv752 (id, secret WITH MASKING POLICY mp752) AS SELECT id, secret "
                + "FROM tv752");
        assertAnswer("1|***", "SELECT * FROM mv752 ORDER BY 1");
        assertAnswer("Materialized view MV752 successfully created.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv752 (id, secret WITH TAG (tg752 = 'x')) AS SELECT id, secret FROM "
                + "tv752");
    }
}
