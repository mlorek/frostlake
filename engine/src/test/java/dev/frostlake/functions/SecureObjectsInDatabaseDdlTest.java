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
 * GET_DDL of a database or a schema holds the secure views and the secure functions in it, as their owner reads them,
 * each as GET_DDL of the object writes it (live-verified).
 */
public class SecureObjectsInDatabaseDdlTest extends MeasuredCellsTestSupport {

    /** A database and a schema holding a secure view and function. */
    @Test
    public void secureObjectsAreInTheirDatabasesDdl() {
        engine.execute("CREATE OR REPLACE TABLE dt (a INT)");
        engine.execute("CREATE OR REPLACE SECURE VIEW dsv AS SELECT a FROM dt");
        engine.execute("CREATE OR REPLACE VIEW dpv AS SELECT a FROM dt");
        engine.execute("CREATE OR REPLACE SECURE FUNCTION dsf() RETURNS INT AS '1'");
        engine.execute("CREATE OR REPLACE FUNCTION dpf() RETURNS INT AS '2'");
        assertCells(new String[][] {
            {"SELECT GET_DDL('DATABASE', 'test_db')",
                "create or replace database TEST_DB; |  | create or replace schema PUBLIC; |  | create or replace "
                + "schema TEST_SCHEMA; |  | create or replace TABLE DT ( | \tA NUMBER(38,0) | ); | create or replace "
                + "view DPV( | \tA | ) as SELECT a FROM dt; | create or replace secure view DSV( | \tA | ) as SELECT a "
                + "FROM dt; | CREATE OR REPLACE FUNCTION \"DPF\"() | RETURNS NUMBER(38,0) | LANGUAGE SQL | AS '2'; | "
                + "CREATE OR REPLACE SECURE FUNCTION \"DSF\"() | RETURNS NUMBER(38,0) | LANGUAGE SQL | AS '1';"},
            {"SELECT GET_DDL('SCHEMA', 'test_db.test_schema')",
                "create or replace schema TEST_SCHEMA; |  | create or replace TABLE DT ( | \tA NUMBER(38,0) | ); | "
                + "create or replace view DPV( | \tA | ) as SELECT a FROM dt; | create or replace secure view DSV( | "
                + "\tA | ) as SELECT a FROM dt; | CREATE OR REPLACE FUNCTION \"DPF\"() | RETURNS NUMBER(38,0) | "
                + "LANGUAGE SQL | AS '2'; | CREATE OR REPLACE SECURE FUNCTION \"DSF\"() | RETURNS NUMBER(38,0) | "
                + "LANGUAGE SQL | AS '1';"},
            {"SELECT GET_DDL('DATABASE', 'test_db', TRUE)",
                "create or replace database TEST_DB; |  | create or replace schema TEST_DB.PUBLIC; |  | create or "
                + "replace schema TEST_DB.TEST_SCHEMA; |  | create or replace TABLE TEST_DB.TEST_SCHEMA.DT ( | \tA "
                + "NUMBER(38,0) | ); | create or replace view TEST_DB.TEST_SCHEMA.DPV( | \tA | ) as SELECT a FROM dt; "
                + "| create or replace secure view TEST_DB.TEST_SCHEMA.DSV( | \tA | ) as SELECT a FROM dt; | CREATE OR "
                + "REPLACE FUNCTION TEST_DB.TEST_SCHEMA.DPF() | RETURNS NUMBER(38,0) | LANGUAGE SQL | AS '2'; | CREATE "
                + "OR REPLACE SECURE FUNCTION TEST_DB.TEST_SCHEMA.DSF() | RETURNS NUMBER(38,0) | LANGUAGE SQL | AS "
                + "'1';"},
            {"SELECT GET_DDL('VIEW', 'dsv')",
                "create or replace secure view DSV( | \tA | ) as SELECT a FROM dt;"},
            {"SELECT GET_DDL('FUNCTION', 'dsf()')",
                "CREATE OR REPLACE SECURE FUNCTION \"DSF\"() | RETURNS NUMBER(38,0) | LANGUAGE SQL | AS '1';"},
        });
    }
}
