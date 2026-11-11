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
 * ALTER ... SET given a parenthesized list (live-verified): a list of constants given a number parameter is invalid
 * value [TOK_CONSTANT_LIST], given a switch an invalid value specified, given STAGE_FILE_FORMAT a format that does not
 * exist; a list of properties given a parameter is invalid value [TOK_PROPERTY_LIST], an empty list a parameter's
 * TOK_CONSTANT_LIST or a switch's invalid value; a text parameter takes a list of constants and a comment a list of
 * properties, holding the parser's word for each. A schema reads its lists alike.
 */
public class AlterSetListValuesTest extends MeasuredCellsTestSupport {

    @Test
    public void listValuesAreJudgedByTheirName() {
        engine.execute("CREATE OR REPLACE TABLE t933 (a INT)");
        assertCells(new String[][] {
            {"ALTER TABLE t933 SET DATA_RETENTION_TIME_IN_DAYS = (a = 1)",
                "REFUSED SQL compilation error: | invalid value [TOK_PROPERTY_LIST] for parameter "
                + "'DATA_RETENTION_TIME_IN_DAYS'"},
            {"ALTER TABLE t933 SET DATA_RETENTION_TIME_IN_DAYS = (1)",
                "REFUSED SQL compilation error: | invalid value [TOK_CONSTANT_LIST] for parameter "
                + "'DATA_RETENTION_TIME_IN_DAYS'"},
            {"ALTER TABLE t933 SET DATA_RETENTION_TIME_IN_DAYS = ()",
                "REFUSED SQL compilation error: | invalid value [TOK_CONSTANT_LIST] for parameter "
                + "'DATA_RETENTION_TIME_IN_DAYS'"},
            {"ALTER TABLE t933 SET MAX_DATA_EXTENSION_TIME_IN_DAYS = (1)",
                "REFUSED SQL compilation error: | invalid value [TOK_CONSTANT_LIST] for parameter "
                + "'MAX_DATA_EXTENSION_TIME_IN_DAYS'"},
            {"ALTER TABLE t933 SET CHANGE_TRACKING = (TRUE)",
                "REFUSED Invalid value specified for property 'CHANGE_TRACKING'"},
            {"ALTER TABLE t933 SET ENABLE_SCHEMA_EVOLUTION = (TRUE)",
                "REFUSED Invalid value specified for property 'ENABLE_SCHEMA_EVOLUTION'"},
            {"ALTER TABLE t933 SET CHANGE_TRACKING = (A = 1)",
                "REFUSED SQL compilation error: | invalid value [TOK_PROPERTY_LIST] for parameter 'CHANGE_TRACKING'"},
            {"ALTER TABLE t933 SET CHANGE_TRACKING = ()",
                "REFUSED Invalid value specified for property 'CHANGE_TRACKING'"},
            {"ALTER TABLE t933 SET STAGE_FILE_FORMAT = (TYPE)",
                "REFUSED SQL compilation error: | File format 'TOK_CONSTANT_LIST' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FILE FORMAT "
                + "TOK_CONSTANT_LIST."},
            {"ALTER TABLE t933 SET DEFAULT_DDL_COLLATION = ('en')", "Statement executed successfully."},
        });
        engine.execute("SHOW PARAMETERS LIKE 'DEFAULT_DDL_COLLATION' IN TABLE t933");
        assertCells(new String[][] {
            {"SELECT \"value\", \"level\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))", "TOK_CONSTANT_LIST|TABLE"},
            {"ALTER TABLE t933 SET COMMENT = (a = 'b')", "Statement executed successfully."},
        });
        engine.execute("SHOW TABLES LIKE 'T933'");
        assertCells(new String[][] {
            {"SELECT \"comment\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))", "TOK_PROPERTY_LIST"},
            {"ALTER TABLE t933 SET DATA_RETENTION_TIME_IN_DAYS = (1, 2)",
                "REFUSED SQL compilation error: | invalid value [TOK_CONSTANT_LIST] for parameter "
                + "'DATA_RETENTION_TIME_IN_DAYS'"},
            {"ALTER TABLE t933 SET MAX_DATA_EXTENSION_TIME_IN_DAYS = ('a')",
                "REFUSED SQL compilation error: | invalid value [TOK_CONSTANT_LIST] for parameter "
                + "'MAX_DATA_EXTENSION_TIME_IN_DAYS'"},
            {"ALTER SCHEMA test_schema SET DATA_RETENTION_TIME_IN_DAYS = (1)",
                "REFUSED SQL compilation error: | invalid value [TOK_CONSTANT_LIST] for parameter "
                + "'DATA_RETENTION_TIME_IN_DAYS'"},
            {"ALTER SCHEMA test_schema SET DATA_RETENTION_TIME_IN_DAYS = (a = 1)",
                "REFUSED SQL compilation error: | invalid value [TOK_PROPERTY_LIST] for parameter "
                + "'DATA_RETENTION_TIME_IN_DAYS'"},
            {"ALTER SCHEMA test_schema SET MAX_DATA_EXTENSION_TIME_IN_DAYS = ()",
                "REFUSED SQL compilation error: | invalid value [TOK_CONSTANT_LIST] for parameter "
                + "'MAX_DATA_EXTENSION_TIME_IN_DAYS'"},
        });
    }
}
