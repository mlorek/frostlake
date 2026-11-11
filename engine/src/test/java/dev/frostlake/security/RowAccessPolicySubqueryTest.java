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
 * A row access policy's body reads its parameters as the row it judges (live-verified): a subquery of the body reads a
 * parameter as its outer row, ahead of a column of the parameter's name, so {@code EXISTS (SELECT 1 FROM m WHERE m.v =
 * val)} keeps the rows whose value m holds, and a table or an alias of the parameter's name stays what it is. A
 * subquery the account cannot evaluate — an aggregate of the parameter, a correlated GROUP BY or LIMIT — is refused as
 * the policy's: {@code Unsupported subquery type cannot be evaluated inside ROW ACCESS POLICY}.
 */
public class RowAccessPolicySubqueryTest extends MeasuredCellsTestSupport {

    @Test
    public void aSubqueryReadsTheParameterAsTheJudgedRow() {
        engine.execute("CREATE OR REPLACE TABLE rm900 (v VARCHAR)");
        engine.execute("INSERT INTO rm900 VALUES ('a'), ('c')");
        engine.execute("CREATE OR REPLACE TABLE rt900 (v VARCHAR)");
        engine.execute("INSERT INTO rt900 VALUES ('a'), ('b'), ('c')");
        engine.execute("CREATE OR REPLACE TABLE val (val VARCHAR)");
        engine.execute("INSERT INTO val VALUES ('b')");
        assertCells(new String[][] {
            {"ALTER TABLE rt900 DROP ALL ROW ACCESS POLICIES", "Statement executed successfully."},
            {"CREATE OR REPLACE ROW ACCESS POLICY rap900_0 AS (val VARCHAR) RETURNS BOOLEAN -> EXISTS (SELECT 1 "
                + "FROM rm900 m WHERE m.v = val)",
                "Row access policy 'RAP900_0' is successfully created."},
            {"ALTER TABLE rt900 ADD ROW ACCESS POLICY rap900_0 ON (v)", "Statement executed successfully."},
            {"SELECT v FROM rt900 ORDER BY v", "a ;; c"},
            {"ALTER TABLE rt900 DROP ALL ROW ACCESS POLICIES", "Statement executed successfully."},
            {"CREATE OR REPLACE ROW ACCESS POLICY rap900_1 AS (val VARCHAR) RETURNS BOOLEAN -> val IN (SELECT v "
                + "FROM rm900)",
                "Row access policy 'RAP900_1' is successfully created."},
            {"ALTER TABLE rt900 ADD ROW ACCESS POLICY rap900_1 ON (v)", "Statement executed successfully."},
            {"SELECT v FROM rt900 ORDER BY v", "a ;; c"},
            {"ALTER TABLE rt900 DROP ALL ROW ACCESS POLICIES", "Statement executed successfully."},
            {"CREATE OR REPLACE ROW ACCESS POLICY rap900_2 AS (val VARCHAR) RETURNS BOOLEAN -> val = (SELECT "
                + "MAX(v) FROM rm900)",
                "Row access policy 'RAP900_2' is successfully created."},
            {"ALTER TABLE rt900 ADD ROW ACCESS POLICY rap900_2 ON (v)", "Statement executed successfully."},
            {"SELECT v FROM rt900 ORDER BY v", "c"},
            {"ALTER TABLE rt900 DROP ALL ROW ACCESS POLICIES", "Statement executed successfully."},
            {"CREATE OR REPLACE ROW ACCESS POLICY rap900_3 AS (val VARCHAR) RETURNS BOOLEAN -> val = (SELECT "
                + "MAX(v) FROM rm900 m WHERE m.v = val)",
                "Row access policy 'RAP900_3' is successfully created."},
            {"ALTER TABLE rt900 ADD ROW ACCESS POLICY rap900_3 ON (v)", "Statement executed successfully."},
            {"SELECT v FROM rt900 ORDER BY v", "a ;; c"},
            {"ALTER TABLE rt900 DROP ALL ROW ACCESS POLICIES", "Statement executed successfully."},
            {"CREATE OR REPLACE ROW ACCESS POLICY rap900_4 AS (val VARCHAR) RETURNS BOOLEAN -> (SELECT COUNT(*) "
                + "FROM rm900 m WHERE m.v = val) > 0",
                "Row access policy 'RAP900_4' is successfully created."},
            {"ALTER TABLE rt900 ADD ROW ACCESS POLICY rap900_4 ON (v)", "Statement executed successfully."},
            {"SELECT v FROM rt900 ORDER BY v", "a ;; c"},
            {"ALTER TABLE rt900 DROP ALL ROW ACCESS POLICIES", "Statement executed successfully."},
            {"CREATE OR REPLACE ROW ACCESS POLICY rap900_5 AS (val VARCHAR) RETURNS BOOLEAN -> val = (SELECT "
                + "MAX(val) FROM val)",
                "Row access policy 'RAP900_5' is successfully created."},
            {"ALTER TABLE rt900 ADD ROW ACCESS POLICY rap900_5 ON (v)", "Statement executed successfully."},
            {"SELECT v FROM rt900 ORDER BY v",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside ROW ACCESS "
                + "POLICY"},
            {"ALTER TABLE rt900 DROP ALL ROW ACCESS POLICIES", "Statement executed successfully."},
            {"CREATE OR REPLACE ROW ACCESS POLICY rap900_6 AS (val VARCHAR) RETURNS BOOLEAN -> NOT EXISTS "
                + "(SELECT 1 FROM rm900 m WHERE m.v = val)",
                "Row access policy 'RAP900_6' is successfully created."},
            {"ALTER TABLE rt900 ADD ROW ACCESS POLICY rap900_6 ON (v)", "Statement executed successfully."},
            {"SELECT v FROM rt900 ORDER BY v", "b"},
            {"ALTER TABLE rt900 DROP ALL ROW ACCESS POLICIES", "Statement executed successfully."},
            {"CREATE OR REPLACE ROW ACCESS POLICY rap900_7 AS (val VARCHAR) RETURNS BOOLEAN -> val NOT IN "
                + "(SELECT v FROM rm900)",
                "Row access policy 'RAP900_7' is successfully created."},
            {"ALTER TABLE rt900 ADD ROW ACCESS POLICY rap900_7 ON (v)", "Statement executed successfully."},
            {"SELECT v FROM rt900 ORDER BY v", "b"},
            {"ALTER TABLE rt900 DROP ALL ROW ACCESS POLICIES", "Statement executed successfully."},
            {"CREATE OR REPLACE ROW ACCESS POLICY rap900_8 AS (val VARCHAR) RETURNS BOOLEAN -> val IN (SELECT v "
                + "FROM rm900 m WHERE m.v = val)",
                "Row access policy 'RAP900_8' is successfully created."},
            {"ALTER TABLE rt900 ADD ROW ACCESS POLICY rap900_8 ON (v)", "Statement executed successfully."},
            {"SELECT v FROM rt900 ORDER BY v", "a ;; c"},
            {"ALTER TABLE rt900 DROP ALL ROW ACCESS POLICIES", "Statement executed successfully."},
            {"CREATE OR REPLACE ROW ACCESS POLICY rap900_9 AS (val VARCHAR) RETURNS BOOLEAN -> EXISTS (SELECT 1 "
                + "FROM rm900)",
                "Row access policy 'RAP900_9' is successfully created."},
            {"ALTER TABLE rt900 ADD ROW ACCESS POLICY rap900_9 ON (v)", "Statement executed successfully."},
            {"SELECT v FROM rt900 ORDER BY v", "a ;; b ;; c"},
            {"ALTER TABLE rt900 DROP ALL ROW ACCESS POLICIES", "Statement executed successfully."},
            {"CREATE OR REPLACE ROW ACCESS POLICY rap900_10 AS (val VARCHAR) RETURNS BOOLEAN -> (SELECT "
                + "COUNT(*) FROM rm900) > 1",
                "Row access policy 'RAP900_10' is successfully created."},
            {"ALTER TABLE rt900 ADD ROW ACCESS POLICY rap900_10 ON (v)", "Statement executed successfully."},
            {"SELECT v FROM rt900 ORDER BY v", "a ;; b ;; c"},
            {"ALTER TABLE rt900 DROP ALL ROW ACCESS POLICIES", "Statement executed successfully."},
            {"CREATE OR REPLACE ROW ACCESS POLICY rap900_11 AS (val VARCHAR) RETURNS BOOLEAN -> val = ANY "
                + "(SELECT v FROM rm900)",
                "Row access policy 'RAP900_11' is successfully created."},
            {"ALTER TABLE rt900 ADD ROW ACCESS POLICY rap900_11 ON (v)", "Statement executed successfully."},
            {"SELECT v FROM rt900 ORDER BY v", "a ;; c"},
            {"ALTER TABLE rt900 DROP ALL ROW ACCESS POLICIES", "Statement executed successfully."},
            {"CREATE OR REPLACE ROW ACCESS POLICY rap900_12 AS (val VARCHAR) RETURNS BOOLEAN -> EXISTS (SELECT "
                + "1 FROM rm900 m WHERE m.v = val AND m.v = (SELECT MAX(v) FROM rm900))",
                "Row access policy 'RAP900_12' is successfully created."},
            {"ALTER TABLE rt900 ADD ROW ACCESS POLICY rap900_12 ON (v)", "Statement executed successfully."},
            {"SELECT v FROM rt900 ORDER BY v", "c"},
            {"ALTER TABLE rt900 DROP ALL ROW ACCESS POLICIES", "Statement executed successfully."},
        });
    }

    @Test
    public void aCorrelatedSubqueryTheAccountCannotEvaluateIsThePolicysRefusal() {
        engine.execute("CREATE OR REPLACE TABLE rm901 (v VARCHAR)");
        engine.execute("INSERT INTO rm901 VALUES ('a'), ('c')");
        engine.execute("CREATE OR REPLACE TABLE rt901 (v VARCHAR)");
        engine.execute("INSERT INTO rt901 VALUES ('a'), ('b'), ('c')");
        assertCells(new String[][] {
            {"ALTER TABLE rt901 DROP ALL ROW ACCESS POLICIES", "Statement executed successfully."},
            {"CREATE OR REPLACE ROW ACCESS POLICY rap901_0 AS (val VARCHAR) RETURNS BOOLEAN -> (SELECT MAX(m.v) "
                + "FROM rm901 m WHERE m.v < val) IS NOT NULL",
                "Row access policy 'RAP901_0' is successfully created."},
            {"ALTER TABLE rt901 ADD ROW ACCESS POLICY rap901_0 ON (v)", "Statement executed successfully."},
            {"SELECT v FROM rt901 ORDER BY v", "b ;; c"},
            {"ALTER TABLE rt901 DROP ALL ROW ACCESS POLICIES", "Statement executed successfully."},
            {"CREATE OR REPLACE ROW ACCESS POLICY rap901_1 AS (val VARCHAR) RETURNS BOOLEAN -> EXISTS (SELECT 1 "
                + "FROM rm901 m WHERE m.v < val)",
                "Row access policy 'RAP901_1' is successfully created."},
            {"ALTER TABLE rt901 ADD ROW ACCESS POLICY rap901_1 ON (v)", "Statement executed successfully."},
            {"SELECT v FROM rt901 ORDER BY v", "b ;; c"},
            {"ALTER TABLE rt901 DROP ALL ROW ACCESS POLICIES", "Statement executed successfully."},
            {"CREATE OR REPLACE ROW ACCESS POLICY rap901_2 AS (val VARCHAR) RETURNS BOOLEAN -> (SELECT MAX(val "
                + "|| m.v) FROM rm901 m) IS NOT NULL",
                "Row access policy 'RAP901_2' is successfully created."},
            {"ALTER TABLE rt901 ADD ROW ACCESS POLICY rap901_2 ON (v)", "Statement executed successfully."},
            {"SELECT v FROM rt901 ORDER BY v",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside ROW ACCESS "
                + "POLICY"},
            {"ALTER TABLE rt901 DROP ALL ROW ACCESS POLICIES", "Statement executed successfully."},
            {"CREATE OR REPLACE ROW ACCESS POLICY rap901_3 AS (val VARCHAR) RETURNS BOOLEAN -> (SELECT MAX(m.v) "
                + "FROM rm901 m WHERE m.v = val AND m.v > 'a') IS NOT NULL",
                "Row access policy 'RAP901_3' is successfully created."},
            {"ALTER TABLE rt901 ADD ROW ACCESS POLICY rap901_3 ON (v)", "Statement executed successfully."},
            {"SELECT v FROM rt901 ORDER BY v", "c"},
            {"ALTER TABLE rt901 DROP ALL ROW ACCESS POLICIES", "Statement executed successfully."},
            {"CREATE OR REPLACE ROW ACCESS POLICY rap901_4 AS (val VARCHAR) RETURNS BOOLEAN -> EXISTS (SELECT 1 "
                + "FROM rm901 m WHERE UPPER(m.v) = UPPER(val))",
                "Row access policy 'RAP901_4' is successfully created."},
            {"ALTER TABLE rt901 ADD ROW ACCESS POLICY rap901_4 ON (v)", "Statement executed successfully."},
            {"SELECT v FROM rt901 ORDER BY v", "a ;; c"},
            {"ALTER TABLE rt901 DROP ALL ROW ACCESS POLICIES", "Statement executed successfully."},
            {"CREATE OR REPLACE ROW ACCESS POLICY rap901_5 AS (val VARCHAR) RETURNS BOOLEAN -> val = ANY "
                + "(SELECT m.v FROM rm901 m WHERE m.v = val)",
                "Row access policy 'RAP901_5' is successfully created."},
            {"ALTER TABLE rt901 ADD ROW ACCESS POLICY rap901_5 ON (v)", "Statement executed successfully."},
            {"SELECT v FROM rt901 ORDER BY v", "a ;; c"},
            {"ALTER TABLE rt901 DROP ALL ROW ACCESS POLICIES", "Statement executed successfully."},
            {"CREATE OR REPLACE ROW ACCESS POLICY rap901_6 AS (val VARCHAR) RETURNS BOOLEAN -> (SELECT COUNT(*) "
                + "FROM rm901 m WHERE m.v = val GROUP BY m.v) = 1",
                "Row access policy 'RAP901_6' is successfully created."},
            {"ALTER TABLE rt901 ADD ROW ACCESS POLICY rap901_6 ON (v)", "Statement executed successfully."},
            {"SELECT v FROM rt901 ORDER BY v",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside ROW ACCESS "
                + "POLICY"},
            {"ALTER TABLE rt901 DROP ALL ROW ACCESS POLICIES", "Statement executed successfully."},
            {"CREATE OR REPLACE ROW ACCESS POLICY rap901_7 AS (val VARCHAR) RETURNS BOOLEAN -> EXISTS (SELECT 1 "
                + "FROM rm901 m WHERE m.v = val) OR val = 'b'",
                "Row access policy 'RAP901_7' is successfully created."},
            {"ALTER TABLE rt901 ADD ROW ACCESS POLICY rap901_7 ON (v)", "Statement executed successfully."},
            {"SELECT v FROM rt901 ORDER BY v", "a ;; b ;; c"},
            {"ALTER TABLE rt901 DROP ALL ROW ACCESS POLICIES", "Statement executed successfully."},
            {"CREATE OR REPLACE ROW ACCESS POLICY rap901_8 AS (val VARCHAR) RETURNS BOOLEAN -> NOT EXISTS "
                + "(SELECT 1 FROM rm901 m WHERE m.v = val) AND val <> 'c'",
                "Row access policy 'RAP901_8' is successfully created."},
            {"ALTER TABLE rt901 ADD ROW ACCESS POLICY rap901_8 ON (v)", "Statement executed successfully."},
            {"SELECT v FROM rt901 ORDER BY v", "b"},
            {"ALTER TABLE rt901 DROP ALL ROW ACCESS POLICIES", "Statement executed successfully."},
            {"CREATE OR REPLACE ROW ACCESS POLICY rap901_9 AS (val VARCHAR) RETURNS BOOLEAN -> EXISTS (SELECT 1 "
                + "FROM (SELECT v FROM rm901) m WHERE m.v = val)",
                "Row access policy 'RAP901_9' is successfully created."},
            {"ALTER TABLE rt901 ADD ROW ACCESS POLICY rap901_9 ON (v)", "Statement executed successfully."},
            {"SELECT v FROM rt901 ORDER BY v", "a ;; c"},
            {"ALTER TABLE rt901 DROP ALL ROW ACCESS POLICIES", "Statement executed successfully."},
            {"CREATE OR REPLACE ROW ACCESS POLICY rap901_10 AS (val VARCHAR) RETURNS BOOLEAN -> (SELECT "
                + "MAX(m.v) FROM rm901 m WHERE m.v = val) = val",
                "Row access policy 'RAP901_10' is successfully created."},
            {"ALTER TABLE rt901 ADD ROW ACCESS POLICY rap901_10 ON (v)", "Statement executed successfully."},
            {"SELECT v FROM rt901 ORDER BY v", "a ;; c"},
            {"ALTER TABLE rt901 DROP ALL ROW ACCESS POLICIES", "Statement executed successfully."},
            {"CREATE OR REPLACE ROW ACCESS POLICY rap901_11 AS (val VARCHAR) RETURNS BOOLEAN -> val IN (SELECT "
                + "m.v FROM rm901 m WHERE m.v = val LIMIT 1)",
                "Row access policy 'RAP901_11' is successfully created."},
            {"ALTER TABLE rt901 ADD ROW ACCESS POLICY rap901_11 ON (v)", "Statement executed successfully."},
            {"SELECT v FROM rt901 ORDER BY v",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside ROW ACCESS "
                + "POLICY"},
            {"ALTER TABLE rt901 DROP ALL ROW ACCESS POLICIES", "Statement executed successfully."},
        });
    }
}
