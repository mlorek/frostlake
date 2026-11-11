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
 * A materialized view's column policies in POLICY_REFERENCES (live-verified): listed in the domain MATERIALIZED_VIEW
 * when the view is named under 'table', and none under 'view'; ALTER MATERIALIZED VIEW takes no MODIFY COLUMN.
 */
public class MaterializedViewPolicyReferencesTest extends MeasuredCellsTestSupport {

    @Test
    public void aMaterializedViewsColumnPolicies() {
        engine.execute("CREATE OR REPLACE TABLE pt931 (a INT, s VARCHAR)");
        engine.execute("INSERT INTO pt931 VALUES (1, 'x')");
        engine.execute("CREATE OR REPLACE MASKING POLICY mp931 AS (v VARCHAR) RETURNS VARCHAR -> v");
        engine.execute("CREATE OR REPLACE ROW ACCESS POLICY rp931 AS (a INT) RETURNS BOOLEAN -> TRUE");
        assertCells(new String[][] {
            {"CREATE OR REPLACE MATERIALIZED VIEW mv931 AS SELECT a, s FROM pt931",
                "Materialized view MV931 successfully created."},
            {"ALTER MATERIALIZED VIEW mv931 MODIFY COLUMN s SET MASKING POLICY mp931",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'MODIFY'."},
            {"SELECT POLICY_NAME, REF_ENTITY_NAME, REF_ENTITY_DOMAIN, REF_COLUMN_NAME, POLICY_KIND, "
                + "POLICY_STATUS FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(POLICY_NAME => 'mp931')) ORDER BY "
                + "1, 4",
                ""},
            {"SELECT POLICY_NAME, REF_ENTITY_NAME, REF_ENTITY_DOMAIN, REF_COLUMN_NAME, POLICY_KIND, "
                + "POLICY_STATUS FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'mv931', "
                + "REF_ENTITY_DOMAIN => 'table')) ORDER BY 1, 4",
                ""},
            {"SELECT POLICY_NAME, REF_ENTITY_NAME, REF_ENTITY_DOMAIN, REF_COLUMN_NAME, POLICY_KIND, "
                + "POLICY_STATUS FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'mv931', "
                + "REF_ENTITY_DOMAIN => 'view')) ORDER BY 1, 4",
                ""},
            {"SELECT POLICY_NAME, REF_ENTITY_NAME, REF_ENTITY_DOMAIN, REF_COLUMN_NAME, POLICY_KIND, "
                + "POLICY_STATUS FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(POLICY_NAME => 'rp931')) ORDER BY "
                + "1, 4",
                ""},
            {"CREATE OR REPLACE MATERIALIZED VIEW mw931 (a, s WITH MASKING POLICY mp931) AS SELECT a, s FROM pt931",
                "Materialized view MW931 successfully created."},
            {"SELECT POLICY_NAME, REF_ENTITY_NAME, REF_ENTITY_DOMAIN, REF_COLUMN_NAME, POLICY_KIND, "
                + "POLICY_STATUS FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'mw931', "
                + "REF_ENTITY_DOMAIN => 'table')) ORDER BY 1, 4",
                "MP931|MW931|MATERIALIZED_VIEW|S|MASKING_POLICY|ACTIVE"},
            {"SELECT a, s FROM mv931", "1|x"},
        });
    }
}
