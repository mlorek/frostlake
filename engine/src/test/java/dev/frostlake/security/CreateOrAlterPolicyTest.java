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
 * CREATE OR ALTER MASKING POLICY and CREATE OR ALTER ROW ACCESS POLICY make the policy, or change it to what the
 * statement writes (live-verified): its body — a column the policy is attached to reads the new one — its COMMENT and
 * a masking policy's EXEMPT_OTHER_POLICIES, a property left out unset. The signature stays: another return type is
 * {@code Property 'FUNCTION_RETURN_TYPE' cannot be changed in CREATE OR ALTER.} (STRING being VARCHAR, VARCHAR(10)
 * another type), another argument list {@code Property 'FUNCTION_SIGNATURE' …}. A body is judged as a CREATE judges
 * it, a row access policy takes no EXEMPT_OTHER_POLICIES, and IF NOT EXISTS is no part of the statement.
 */
public class CreateOrAlterPolicyTest extends MeasuredCellsTestSupport {

    @Test
    public void aMaskingPolicyBecomesWhatTheStatementWrites() {
        engine.execute("CREATE OR REPLACE MASKING POLICY m8 AS (v VARCHAR) RETURNS VARCHAR -> '***' COMMENT = 'only'");
        engine.execute("CREATE OR REPLACE TABLE mt (s VARCHAR)");
        engine.execute("INSERT INTO mt VALUES ('secret')");
        engine.execute("ALTER TABLE mt MODIFY COLUMN s SET MASKING POLICY m8");
        assertCells(new String[][] {
            {"SELECT s FROM mt", "***"},
            {"CREATE OR ALTER MASKING POLICY m8 AS (v VARCHAR) RETURNS VARCHAR -> '***' EXEMPT_OTHER_POLICIES = TRUE",
                "Statement executed successfully."},
            {"SELECT GET_DDL('POLICY', 'm8')",
                "create or replace masking policy M8 as (V VARCHAR)  | returns VARCHAR -> | '***' | "
                + "EXEMPT_OTHER_POLICIES=TRUE | ;"},
        });
        engine.execute("SHOW MASKING POLICIES LIKE 'M8'");
        assertCells(new String[][] {
            {"SELECT \"name\", \"comment\", \"kind\", \"options\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1",
                "M8||MASKING_POLICY|{\"EXEMPT_OTHER_POLICIES\":true}"},
            {"CREATE OR ALTER MASKING POLICY m8 AS (v VARCHAR) RETURNS VARCHAR -> 'xxx' COMMENT = 'two'",
                "Statement executed successfully."},
            {"SELECT GET_DDL('POLICY', 'm8')",
                "create or replace masking policy M8 as (V VARCHAR)  | returns VARCHAR -> | 'xxx' | COMMENT='two' | ;"},
            {"SELECT s FROM mt", "xxx"},
            {"DESCRIBE MASKING POLICY m8", "M8|(V VARCHAR)|VARCHAR|'xxx'"},
            {"CREATE OR ALTER MASKING POLICY m8 AS (v VARCHAR) RETURNS VARCHAR -> 'xxx' EXEMPT_OTHER_POLICIES = FALSE",
                "Statement executed successfully."},
            {"SELECT GET_DDL('POLICY', 'm8')",
                "create or replace masking policy M8 as (V VARCHAR)  | returns VARCHAR -> | 'xxx' | ;"},
            {"CREATE OR ALTER MASKING POLICY m8 AS (v NUMBER) RETURNS NUMBER -> 0",
                "REFUSED SQL compilation error: Property 'FUNCTION_RETURN_TYPE' cannot be changed in CREATE OR ALTER."},
            {"CREATE OR ALTER MASKING POLICY m8 AS (w VARCHAR) RETURNS VARCHAR -> w",
                "REFUSED SQL compilation error: Property 'FUNCTION_SIGNATURE' cannot be changed in CREATE OR ALTER."},
            {"CREATE OR ALTER MASKING POLICY m8 AS (v VARCHAR, c INT) RETURNS VARCHAR -> v",
                "REFUSED SQL compilation error: Property 'FUNCTION_SIGNATURE' cannot be changed in CREATE OR ALTER."},
            {"SELECT GET_DDL('POLICY', 'm8')",
                "create or replace masking policy M8 as (V VARCHAR)  | returns VARCHAR -> | 'xxx' | ;"},
            {"CREATE OR ALTER MASKING POLICY m9 AS (v VARCHAR) RETURNS VARCHAR -> v COMMENT = 'new'",
                "Masking policy M9 successfully created."},
            {"SELECT GET_DDL('POLICY', 'm9')",
                "create or replace masking policy M9 as (V VARCHAR)  | returns VARCHAR -> | v | COMMENT='new' | ;"},
        });
        engine.execute("SHOW MASKING POLICIES LIKE 'M9'");
        assertCells(new String[][] {
            {"SELECT \"name\", \"comment\", \"kind\", \"options\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1",
                "M9|new|MASKING_POLICY|"},
            {"CREATE OR ALTER MASKING POLICY m9 AS (v VARCHAR) RETURNS VARCHAR -> v",
                "Statement executed successfully."},
            {"SELECT GET_DDL('POLICY', 'm9')",
                "create or replace masking policy M9 as (V VARCHAR)  | returns VARCHAR -> | v | ;"},
            {"CREATE OR ALTER ROW ACCESS POLICY rp AS (v VARCHAR) RETURNS BOOLEAN -> TRUE",
                "Row access policy 'RP' is successfully created."},
            {"CREATE OR ALTER MASKING POLICY m10 AS (v VARCHAR) RETURNS NUMBER -> 1",
                "REFUSED SQL compilation error: Masking policy function argument and return type mismatch."},
            {"CREATE OR ALTER MASKING POLICY m8 AS (v VARCHAR) RETURNS VARCHAR -> nosuch_col_mp",
                "REFUSED SQL compilation error: error line 1 at position 1 | invalid identifier 'NOSUCH_COL_MP'"},
            {"SELECT GET_DDL('POLICY', 'm8')",
                "create or replace masking policy M8 as (V VARCHAR)  | returns VARCHAR -> | 'xxx' | ;"},
        });
    }

    @Test
    public void aRowAccessPolicyAndAConditionalOne() {
        engine.execute("CREATE OR REPLACE ROW ACCESS POLICY rp AS (v VARCHAR) RETURNS BOOLEAN -> TRUE COMMENT = 'one'");
        engine.execute("CREATE OR REPLACE TABLE rt (s VARCHAR)");
        engine.execute("INSERT INTO rt VALUES ('a'), ('b')");
        engine.execute("ALTER TABLE rt ADD ROW ACCESS POLICY rp ON (s)");
        engine.execute("CREATE OR REPLACE MASKING POLICY mc2 AS (v VARCHAR, w INT) RETURNS VARCHAR -> CASE WHEN w > "
            + "0 THEN v ELSE '***' END COMMENT = 'c'");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM rt", "2"},
            {"CREATE OR ALTER ROW ACCESS POLICY rp AS (v VARCHAR) RETURNS BOOLEAN -> v = 'a'",
                "Statement executed successfully."},
            {"SELECT GET_DDL('POLICY', 'rp')",
                "create or replace row access policy RP as (V VARCHAR)  | returns BOOLEAN -> | v = 'a' | ;"},
            {"SELECT COUNT(*) FROM rt", "1"},
        });
        engine.execute("SHOW ROW ACCESS POLICIES LIKE 'RP'");
        assertCells(new String[][] {
            {"SELECT \"name\", \"comment\", \"options\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1", "RP||"},
            {"CREATE OR ALTER ROW ACCESS POLICY rp AS (v NUMBER) RETURNS BOOLEAN -> TRUE",
                "REFUSED SQL compilation error: Property 'FUNCTION_SIGNATURE' cannot be changed in CREATE OR ALTER."},
            {"CREATE OR ALTER ROW ACCESS POLICY rp AS (w VARCHAR) RETURNS BOOLEAN -> TRUE",
                "REFUSED SQL compilation error: Property 'FUNCTION_SIGNATURE' cannot be changed in CREATE OR ALTER."},
            {"CREATE OR ALTER ROW ACCESS POLICY rp AS (v VARCHAR) RETURNS BOOLEAN -> TRUE COMMENT = 'two' "
                + "EXEMPT_OTHER_POLICIES = TRUE",
                "REFUSED SQL compilation error: | invalid property 'EXEMPT_OTHER_POLICIES' for 'ROW_ACCESS_POLICY'"},
            {"SELECT GET_DDL('POLICY', 'rp')",
                "create or replace row access policy RP as (V VARCHAR)  | returns BOOLEAN -> | v = 'a' | ;"},
            {"SELECT COUNT(*) FROM rt", "1"},
            {"CREATE OR ALTER ROW ACCESS POLICY rp2 AS (v VARCHAR) RETURNS BOOLEAN -> TRUE",
                "Row access policy 'RP2' is successfully created."},
            {"SELECT GET_DDL('POLICY', 'rp2')",
                "create or replace row access policy RP2 as (V VARCHAR)  | returns BOOLEAN -> | TRUE | ;"},
            {"CREATE OR ALTER MASKING POLICY mc2 AS (v VARCHAR, w INT) RETURNS VARCHAR -> v",
                "Statement executed successfully."},
            {"SELECT GET_DDL('POLICY', 'mc2')",
                "create or replace masking policy MC2 as (V VARCHAR, W NUMBER(38,0))  | returns VARCHAR -> | v | ;"},
            {"CREATE OR ALTER MASKING POLICY mc2 AS (v VARCHAR, w NUMBER) RETURNS VARCHAR -> v",
                "Statement executed successfully."},
            {"CREATE OR ALTER MASKING POLICY mc2 AS (v VARCHAR, w INT) RETURNS VARCHAR(10) -> v",
                "REFUSED SQL compilation error: Property 'FUNCTION_RETURN_TYPE' cannot be changed in CREATE OR ALTER."},
            {"SELECT GET_DDL('POLICY', 'mc2')",
                "create or replace masking policy MC2 as (V VARCHAR, W NUMBER(38,0))  | returns VARCHAR -> | v | ;"},
            {"CREATE OR ALTER MASKING POLICY mc2 AS (v STRING, w INT) RETURNS STRING -> v",
                "Statement executed successfully."},
        });
    }
}
