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
 * Two names that differ only in case are two objects: CREATE OR REPLACE of one leaves the other alone, a plain CREATE
 * of one is no clash with the other, and a quoted name in another case reaches nothing - while an unquoted name folds
 * to upper case and reaches the upper-case object (live-verified for views, sequences, file formats and stages).
 */
public class CaseExactObjectNamesTest extends MeasuredCellsTestSupport {

    private static final String NAMES = "SELECT \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1";

    private static String missing(final String kind, final String granted, final String name) {
        return "REFUSED SQL compilation error: | " + kind + " 'TEST_DB.TEST_SCHEMA.\"" + name + "\"' does not exist or "
            + "not authorized. Your primary role <ROLE> must have at least one privilege granted on " + granted
            + " TEST_DB.TEST_SCHEMA.\"" + name + "\".";
    }

    @Test
    public void twoViewsAndTwoSequencesThatDifferInCase() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE VIEW \"v775\" AS SELECT 1 AS a", "View \"v775\" successfully created."},
            {"CREATE OR REPLACE VIEW V775 AS SELECT 2 AS a", "View V775 successfully created."},
            {"SELECT a FROM \"v775\"", "1"},
            {"SELECT a FROM v775", "2"},
            {"CREATE OR REPLACE SEQUENCE \"q775\"", "Sequence q775 successfully created."},
            {"CREATE OR REPLACE SEQUENCE Q775 START = 10", "Sequence Q775 successfully created."},
            {"SELECT \"q775\".NEXTVAL, q775.NEXTVAL", "1|10"},
        });
        answer("SHOW VIEWS LIKE '%775'");
        assertAnswer("V775 ;; v775", NAMES);
        answer("SHOW SEQUENCES LIKE '%775'");
        assertAnswer("Q775 ;; q775", NAMES);
        assertCells(new String[][] {
            {"DROP VIEW \"v775\"", "v775 successfully dropped."},
            {"DROP SEQUENCE \"q775\"", "q775 successfully dropped."},
        });
        answer("SHOW VIEWS LIKE '%775'");
        assertAnswer("V775", NAMES);
        answer("SHOW SEQUENCES LIKE '%775'");
        assertAnswer("Q775", NAMES);
    }

    @Test
    public void twoFileFormatsAndTwoStagesThatDifferInCase() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE FILE FORMAT \"ff775\" TYPE = CSV", "File format ff775 successfully created."},
            {"CREATE FILE FORMAT FF775 TYPE = JSON", "File format FF775 successfully created."},
            {"CREATE OR REPLACE FILE FORMAT FF775 TYPE = PARQUET", "File format FF775 successfully created."},
            {"CREATE OR REPLACE STAGE \"st775\"", "Stage area st775 successfully created."},
            {"CREATE OR REPLACE STAGE ST775", "Stage area ST775 successfully created."},
        });
        answer("SHOW FILE FORMATS LIKE '%775'");
        assertAnswer("FF775|PARQUET ;; ff775|CSV",
            "SELECT \"name\", \"type\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
        answer("SHOW STAGES LIKE '%775'");
        assertAnswer("ST775 ;; st775", NAMES);
    }

    @Test
    public void aQuotedNameInAnotherCaseReachesNothing() {
        answer("CREATE OR REPLACE SEQUENCE ONLYUPQ");
        answer("CREATE OR REPLACE VIEW ONLYUPV AS SELECT 1 AS a");
        answer("CREATE OR REPLACE FILE FORMAT ONLYUPF TYPE = CSV");
        answer("CREATE OR REPLACE STAGE ONLYUPS");
        assertCells(new String[][] {
            {"SELECT \"onlyupq\".NEXTVAL", "REFUSED SQL compilation error: error line 1 at position 7 | invalid "
                + "identifier '\"onlyupq\".NEXTVAL'"},
            {"SELECT * FROM \"onlyupv\"", "REFUSED SQL compilation error: | Object '\"onlyupv\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "\"onlyupv\"."},
            {"DROP VIEW \"onlyupv\"", missing("View", "TABLE", "onlyupv")},
            {"DROP SEQUENCE \"onlyupq\"", missing("Sequence", "SEQUENCE", "onlyupq")},
            {"DROP FILE FORMAT \"onlyupf\"", missing("File format", "FILE FORMAT", "onlyupf")},
            {"DESCRIBE SEQUENCE \"onlyupq\"", missing("Sequence", "SEQUENCE", "onlyupq")},
            {"ALTER VIEW \"onlyupv\" SET COMMENT = 'x'", missing("View", "TABLE", "onlyupv")},
            {"ALTER SEQUENCE \"onlyupq\" SET INCREMENT = 2", missing("Sequence", "SEQUENCE", "onlyupq")},
            {"DROP STAGE \"onlyups\"", missing("Stage", "STAGE", "onlyups")},
            {"SELECT onlyupq.NEXTVAL > 0, (SELECT a FROM onlyupv)", "true|1"},
            {"ALTER VIEW onlyupv SET COMMENT = 'x'", "Statement executed successfully."},
        });
    }
}
