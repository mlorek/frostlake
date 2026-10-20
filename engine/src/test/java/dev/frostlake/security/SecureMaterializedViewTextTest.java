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

import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * A SECURE materialized view's definition is its owner's to read, as a secure view's is (documented): a role that
 * does not own it is shown an empty text by SHOW MATERIALIZED VIEWS and SHOW VIEWS and no DDL, while a plain
 * materialized view's text is shown to it. Engine only: the live account runs no GRANT.
 */
public class SecureMaterializedViewTextTest extends MeasuredCellsTestSupport {

    @Override
    protected void setupTest() {
        assumeFalse(isLiveSnowflake(), "the live account runs no GRANT");
    }

    @Test
    public void aNonOwnerIsShownNoSecureMaterializedViewText() {
        engine.execute("USE ROLE ACCOUNTADMIN");
        engine.execute("USE SECONDARY ROLES NONE");
        engine.execute("CREATE ROLE smv_owner");
        try {
            engine.execute("CREATE OR REPLACE TABLE smv_t (v NUMBER)");
            engine.execute("CREATE OR REPLACE SECURE MATERIALIZED VIEW smv_s AS SELECT v FROM smv_t WHERE v > 1");
            engine.execute("CREATE OR REPLACE MATERIALIZED VIEW smv_p AS SELECT v FROM smv_t WHERE v > 2");
            assertCells(new String[][] {
                {"SHOW MATERIALIZED VIEWS LIKE 'SMV_S' ->> SELECT \"text\" = '' FROM $1", "false"},
                {"GRANT OWNERSHIP ON MATERIALIZED VIEW smv_s TO ROLE smv_owner COPY CURRENT GRANTS",
                    "Statement executed successfully."},
                {"GRANT OWNERSHIP ON MATERIALIZED VIEW smv_p TO ROLE smv_owner COPY CURRENT GRANTS",
                    "Statement executed successfully."},
                {"GRANT SELECT ON MATERIALIZED VIEW smv_s TO ROLE PUBLIC", "Statement executed successfully."},
                {"GRANT SELECT ON MATERIALIZED VIEW smv_p TO ROLE PUBLIC", "Statement executed successfully."},
                {"SHOW MATERIALIZED VIEWS LIKE 'SMV_%' ->> SELECT \"name\", \"owner\", \"text\" = '' FROM $1 "
                    + "ORDER BY 1", "SMV_P|SMV_OWNER|false ;; SMV_S|SMV_OWNER|true"},
                {"SHOW VIEWS LIKE 'SMV_%' ->> SELECT \"name\", \"text\" = '' FROM $1 ORDER BY 1",
                    "SMV_P|false ;; SMV_S|true"},
                {"SELECT GET_DDL('view', 'smv_s')",
                    "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
                {"GRANT ROLE smv_owner TO ROLE ACCOUNTADMIN", "Statement executed successfully."},
                {"SHOW MATERIALIZED VIEWS LIKE 'SMV_S' ->> SELECT \"text\" = '' FROM $1", "false"},
            });
        } finally {
            engine.execute("GRANT ROLE smv_owner TO ROLE ACCOUNTADMIN");
            engine.execute("DROP MATERIALIZED VIEW IF EXISTS smv_s");
            engine.execute("DROP MATERIALIZED VIEW IF EXISTS smv_p");
            engine.execute("DROP ROLE IF EXISTS smv_owner");
            engine.execute("USE SECONDARY ROLES ALL");
        }
    }
}
