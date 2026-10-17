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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * A quoted tag name is that name alone: DROP, CREATE OR REPLACE, ALTER, DESCRIBE TAG and a tag an object is given
 * name the tag spelled exactly so, never its upper-case namesake; and TAG_REFERENCES and TAG_REFERENCES_ALL_COLUMNS
 * answer columns of the widest text (live-verified).
 */
public class TagExactNameTest extends MeasuredCellsTestSupport {

    private static final String TAGS = "SELECT \"name\", \"comment\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"
        + " ORDER BY 1";

    private static final String MISSING = "REFUSED SQL compilation error: | Tag 'TEST_DB.TEST_SCHEMA.\"cc1452\"' does"
        + " not exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on TAG"
        + " TEST_DB.TEST_SCHEMA.\"cc1452\".";

    private static final String WIDEST = "VARCHAR(134217728)";

    @AfterEach
    public void dropTags() {
        answer("DROP TABLE IF EXISTS tr1452");
        answer("DROP TAG IF EXISTS \"cc1452\"");
        answer("DROP TAG IF EXISTS CC1452");
    }

    @Test
    public void aQuotedNameNeverReachesItsUpperCaseNamesake() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE TAG CC1452 COMMENT = 'upper'", "Tag CC1452 successfully created."},
            {"DROP TAG \"cc1452\"", MISSING},
            {"CREATE OR REPLACE TAG \"cc1452\" COMMENT = 'lower'", "Tag cc1452 successfully created."},
        });
        answer("SHOW TAGS LIKE '%1452'");
        assertAnswer("CC1452|upper ;; cc1452|lower", TAGS);
        assertAnswer("Statement executed successfully.", "ALTER TAG \"cc1452\" SET COMMENT = 'lower2'");
        answer("SHOW TAGS LIKE '%1452'");
        assertAnswer("CC1452|upper ;; cc1452|lower2", TAGS);
        assertAnswer("cc1452 successfully dropped.", "DROP TAG \"cc1452\"");
        answer("SHOW TAGS LIKE '%1452'");
        assertAnswer("CC1452|upper", TAGS);
        assertCells(new String[][] {
            {"ALTER TAG \"cc1452\" SET COMMENT = 'x'", MISSING},
            {"ALTER TAG \"cc1452\" ADD ALLOWED_VALUES 'a'", MISSING},
            {"DESCRIBE TAG \"cc1452\"", "REFUSED Unsupported feature 'TAG'."},
            {"CREATE TAG \"cc1452\"", "Tag cc1452 successfully created."},
            {"CREATE TAG CC1452", "REFUSED SQL compilation error: | Object 'CC1452' already exists."},
        });
    }

    @Test
    public void anObjectCarriesEachOfTheTwoTags() {
        answer("CREATE OR REPLACE TAG CC1452");
        answer("CREATE OR REPLACE TAG \"cc1452\"");
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE tr1452 (a INT)", "Table TR1452 successfully created."},
            {"ALTER TABLE tr1452 SET TAG \"cc1452\" = 'v'", "Statement executed successfully."},
            {"ALTER TABLE tr1452 SET TAG CC1452 = 'u'", "Statement executed successfully."},
            {"SELECT TAG_NAME, TAG_VALUE FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('tr1452', 'TABLE')) ORDER BY 1",
                "CC1452|u ;; cc1452|v"},
            {"SELECT SYSTEM$GET_TAG('\"cc1452\"', 'tr1452', 'TABLE'), SYSTEM$GET_TAG('cc1452', 'tr1452', 'TABLE')",
                "v|u"},
            {"DROP TAG \"cc1452\"", "cc1452 successfully dropped."},
            {"DROP TAG CC1452", "CC1452 successfully dropped."},
        });
        answer("SHOW TAGS LIKE '%1452'");
        assertAnswer("", TAGS);
    }

    @Test
    public void theReferenceFunctionsAnswerTheWidestText() {
        answer("CREATE OR REPLACE TABLE tr1452 (a INT)");
        final String columns = "TAG_DATABASE:" + WIDEST + ", TAG_SCHEMA:" + WIDEST + ", TAG_NAME:" + WIDEST
            + ", TAG_VALUE:" + WIDEST + ", LEVEL:" + WIDEST + ", OBJECT_DATABASE:" + WIDEST + ", OBJECT_SCHEMA:"
            + WIDEST + ", OBJECT_NAME:" + WIDEST + ", DOMAIN:" + WIDEST + ", COLUMN_NAME:" + WIDEST
            + ", APPLY_METHOD:" + WIDEST;
        assertShapes(columns,
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('tr1452', 'TABLE'))",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS('tr1452', 'TABLE'))");
    }
}
