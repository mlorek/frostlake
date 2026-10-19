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
 * The sentence a CREATE answers with spells the name of a view, a materialized view and a dynamic table as the name
 * must be written, quoted only where a bare name would read differently, and the name of every other kind without
 * its quotes (live-verified); a dynamic table must read at least one base table.
 */
public class QuotedCreationSentenceTest extends MeasuredCellsTestSupport {

    @Test
    public void eachKindSpellsItsQuotedName() {
        engine.execute("CREATE OR REPLACE TABLE st_t (a INT)");
        assertCells(new String[][] {
            {"CREATE OR REPLACE DYNAMIC TABLE \"ldt\" TARGET_LAG = '1 hour' WAREHOUSE = compute_wh AS SELECT 1 AS a",
                "REFUSED SQL compilation error: Dynamic Tables must have at least one base table."},
            {"CREATE OR REPLACE MATERIALIZED VIEW \"lmv\" AS SELECT a FROM st_t",
                "Materialized view \"lmv\" successfully created."},
            {"CREATE OR REPLACE VIEW \"lv\" AS SELECT 1 AS a", "View \"lv\" successfully created."},
            {"CREATE OR REPLACE TABLE \"lt\" (a INT)", "Table lt successfully created."},
            {"CREATE OR REPLACE SEQUENCE \"lsq\"", "Sequence lsq successfully created."},
            {"CREATE OR REPLACE STREAM \"lst\" ON TABLE st_t", "Stream lst successfully created."},
            {"CREATE OR REPLACE TASK \"ltk\" SCHEDULE = '60 MINUTE' AS SELECT 1", "Task ltk successfully created."},
            {"CREATE OR REPLACE FILE FORMAT \"lff\" TYPE = CSV", "File format lff successfully created."},
            {"CREATE OR REPLACE STAGE \"lstg\"", "Stage area lstg successfully created."},
            {"CREATE OR REPLACE ALERT \"lal\" SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN SELECT 1",
                "Alert lal successfully created."},
            {"CREATE OR REPLACE MASKING POLICY \"lmp\" AS (v VARCHAR) RETURNS VARCHAR -> v",
                "Masking policy lmp successfully created."},
            {"CREATE OR REPLACE ROW ACCESS POLICY \"lrp\" AS (v VARCHAR) RETURNS BOOLEAN -> TRUE",
                "Row access policy 'lrp' is successfully created."},
            {"CREATE OR REPLACE TAG \"ltg\"", "Tag ltg successfully created."},
            {"CREATE OR REPLACE FUNCTION \"lfn\"() RETURNS INT AS '1'", "Function lfn successfully created."},
            {"CREATE OR REPLACE PROCEDURE \"lpr\"() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'",
                "Function lpr successfully created."},
            {"CREATE OR REPLACE SCHEMA \"lsch\"", "Schema lsch successfully created."},
        });
    }

    @Test
    public void aQuotedNameIsQuotedOnlyWhereItMustBe() {
        engine.execute("CREATE OR REPLACE TABLE sq_t (a INT)");
        assertCells(new String[][] {
            {"CREATE OR REPLACE DYNAMIC TABLE \"ldt\" TARGET_LAG = '1 hour' WAREHOUSE = compute_wh "
                + "AS SELECT a FROM sq_t",
                "Dynamic table \"ldt\" successfully created."},
            {"CREATE OR REPLACE DYNAMIC TABLE \"LDT2\" TARGET_LAG = '1 hour' WAREHOUSE = compute_wh "
                + "AS SELECT a FROM sq_t",
                "Dynamic table LDT2 successfully created."},
            {"CREATE OR REPLACE VIEW \"LV3\" AS SELECT 1 AS a", "View LV3 successfully created."},
            {"CREATE OR REPLACE VIEW \"a b\" AS SELECT 1 AS a", "View \"a b\" successfully created."},
            {"CREATE OR REPLACE VIEW \"Mixed\" AS SELECT 1 AS a", "View \"Mixed\" successfully created."},
            {"CREATE OR REPLACE MATERIALIZED VIEW \"LMV3\" AS SELECT a FROM sq_t",
                "Materialized view LMV3 successfully created."},
        });
    }
}
