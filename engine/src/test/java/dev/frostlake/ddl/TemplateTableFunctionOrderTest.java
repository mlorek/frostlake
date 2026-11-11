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
 * Whether a USING TEMPLATE query reads a table function is judged before the table's properties, those written before
 * the template and those after its query alike, and its parentheses may hold more of them and a LIMIT. Every answer is
 * live-verified.
 */
public class TemplateTableFunctionOrderTest extends MeasuredCellsTestSupport {

    private static final String QUERY = "SELECT ARRAY_AGG(OBJECT_CONSTRUCT('COLUMN_NAME', 'A', 'TYPE', 'INT', "
        + "'NULLABLE', TRUE))";
    private static final String TEMPLATE = "CREATE OR REPLACE TABLE tt976 USING TEMPLATE ";
    private static final String NO_FUNCTION =
        "REFUSED Unsupported feature 'Table function must be used in the TEMPLATE sub-query'.";

    @Test
    public void theTableFunctionIsJudgedBeforeTheProperties() {
        assertCells(new String[][] {
            {TEMPLATE + "(" + QUERY + ")", NO_FUNCTION},
            {TEMPLATE + "((" + QUERY + "))", NO_FUNCTION},
            {TEMPLATE + "(" + QUERY + " LIMIT 1)", NO_FUNCTION},
            {TEMPLATE + "(" + QUERY + ") COMMENT = 'c'", NO_FUNCTION},
            {TEMPLATE + "(" + QUERY + ") x = 1", NO_FUNCTION},
            {TEMPLATE + "(" + QUERY + ") CLUSTER BY (a)", NO_FUNCTION},
            {"CREATE OR REPLACE TABLE tt976 COMMENT = 'c' USING TEMPLATE (" + QUERY + ")", NO_FUNCTION},
        });
    }
}
