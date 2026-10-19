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

package dev.frostlake.functions;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A statement that calls a SQL function compiles the function's body with the call's arguments in place of its
 * parameters, so a GET_DDL in the body that names its object through a parameter looks the object up while the
 * statement compiles (live-verified): a missing one refuses the statement wherever the call stands — under
 * SYSTEM$TYPEOF, over no row, in a branch never taken — whether the argument is a literal, a constant expression, a
 * named argument, the parameter's default or a parameter the calling function was passed.
 */
public class CalledFunctionGetDdlArgumentTest extends MeasuredCellsTestSupport {

    @Test
    public void aCalledBodysGetDdlReadsItsConstantArguments() {
        engine.execute("CREATE OR REPLACE TABLE gtt (x INT)");
        engine.execute("CREATE OR REPLACE VIEW gtv AS SELECT 1 AS c");
        engine.execute("CREATE OR REPLACE FUNCTION gdp(t VARCHAR) RETURNS VARCHAR AS $$ GET_DDL('table', t) $$");
        engine.execute("CREATE OR REPLACE FUNCTION gdp2(k VARCHAR, t VARCHAR) RETURNS VARCHAR AS $$ GET_DDL(k, t) $$");
        engine.execute("CREATE OR REPLACE FUNCTION gdp3(t VARCHAR DEFAULT 'nosuch_d') RETURNS VARCHAR AS $$ "
            + "GET_DDL('table', t) $$");
        engine.execute("CREATE OR REPLACE FUNCTION gdpw(t VARCHAR) RETURNS VARCHAR AS $$ gdp(t) $$");
        assertCells(new String[][] {
            {"SELECT SYSTEM$TYPEOF(gdp('nosuch_t'))",
                "REFUSED SQL compilation error: | Table 'NOSUCH_T' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T."},
            {"SELECT SYSTEM$TYPEOF(gdp('gtt'))", "VARCHAR[LOB]"},
            {"SELECT gdp('nosuch_t') FROM gtt",
                "REFUSED SQL compilation error: | Table 'NOSUCH_T' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T."},
            {"SELECT SYSTEM$TYPEOF(gdp(t => 'nosuch_t'))",
                "REFUSED SQL compilation error: | Table 'NOSUCH_T' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T."},
            {"SELECT SYSTEM$TYPEOF(gdp(UPPER('nosuch_t')))",
                "REFUSED SQL compilation error: | Table 'NOSUCH_T' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T."},
            {"SELECT SYSTEM$TYPEOF(gdp('nosuch_' || 't'))",
                "REFUSED SQL compilation error: | Table 'NOSUCH_T' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T."},
            {"SELECT SYSTEM$TYPEOF(gdp2('table', 'nosuch_t'))",
                "REFUSED SQL compilation error: | Table 'NOSUCH_T' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T."},
            {"SELECT SYSTEM$TYPEOF(gdp2('view', 'gtt'))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(gdp2('view', 'gtv'))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(gdp3())",
                "REFUSED SQL compilation error: | Table 'NOSUCH_D' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_D."},
            {"SELECT SYSTEM$TYPEOF(gdpw('nosuch_t'))",
                "REFUSED SQL compilation error: | Table 'NOSUCH_T' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T."},
            {"SELECT 1 WHERE FALSE AND gdp('nosuch_t') IS NULL",
                "REFUSED SQL compilation error: | Table 'NOSUCH_T' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T."},
            {"SELECT CASE WHEN FALSE THEN gdp('nosuch_t') END",
                "REFUSED SQL compilation error: | Table 'NOSUCH_T' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T."},
            {"SELECT SYSTEM$TYPEOF(gdp('NOSUCH_T'))",
                "REFUSED SQL compilation error: | Table 'NOSUCH_T' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T."},
            {"SELECT SYSTEM$TYPEOF(gdp('\"nosuch_q\"'))",
                "REFUSED SQL compilation error: | Table '\"nosuch_q\"' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE \"nosuch_q\"."},
            {"SELECT SYSTEM$TYPEOF(gdp('test_db.public.nosuch_t'))",
                "REFUSED SQL compilation error: | Table 'TEST_DB.PUBLIC.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.PUBLIC.NOSUCH_T."},
        });
    }
}
