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
 * SYSTEM$AUTO_REFRESH_STATUS takes a constant text naming a table as written: a NULL, a number or a column is
 * refused, a missing object is named as written, and a view, a table or a temporary table that does not refresh
 * is refused as no auto-refreshed table - even in a query that answers no row (all live-verified).
 */
public class AutoRefreshStatusTest extends MeasuredCellsTestSupport {

    @Test
    public void namedObjects() {
        engine.execute("CREATE OR REPLACE TABLE ar681 (a INT)");
        engine.execute("INSERT INTO ar681 VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE VIEW v681 AS SELECT 1 AS a");
        engine.execute("CREATE OR REPLACE TEMPORARY TABLE tt681 (a INT)");
        engine.execute("CREATE OR REPLACE STAGE st681");
        assertAnswer("REFUSED inputs may not be null", "SELECT SYSTEM$AUTO_REFRESH_STATUS(NULL)");
        assertAnswer("REFUSED SQL compilation error: | argument 0 to function SqlIdentifier{qualifierNames=[], "
                + "identifierName=SYSTEM$AUTO_REFRESH_STATUS} needs to be constant, found 'CAST(5 AS "
                + "VARCHAR(134217728))'",
            "SELECT SYSTEM$AUTO_REFRESH_STATUS(5)");
        assertAnswer("REFUSED SQL compilation error: | Table '\"\"' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE \"\".",
            "SELECT SYSTEM$AUTO_REFRESH_STATUS('')");
        assertAnswer("REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SELECT SYSTEM$AUTO_REFRESH_STATUS('test_db.nosuch_s.t')");
        assertAnswer("REFUSED SQL compilation error: | invalid parameter 'Auto Refresh is not supported for table "
                + "v681'",
            "SELECT SYSTEM$AUTO_REFRESH_STATUS('v681')");
        assertAnswer("REFUSED SQL compilation error: | invalid parameter 'Auto Refresh is not supported for table "
                + "AR681'",
            "SELECT SYSTEM$AUTO_REFRESH_STATUS('AR681')");
        assertAnswer("REFUSED SQL compilation error: | Table '\"ar681\"' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE \"ar681\".",
            "SELECT SYSTEM$AUTO_REFRESH_STATUS('\"ar681\"')");
        assertAnswer("REFUSED SQL compilation error: | invalid parameter 'Auto Refresh is not supported for table "
                + "test_db.test_schema.ar681'",
            "SELECT SYSTEM$AUTO_REFRESH_STATUS('test_db.test_schema.ar681')");
        assertAnswer("REFUSED SQL compilation error: | invalid parameter 'Auto Refresh is not supported for table "
                + "tt681'",
            "SELECT SYSTEM$AUTO_REFRESH_STATUS('tt681')");
        assertAnswer("REFUSED SQL compilation error: | Table 'ST681' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE ST681.",
            "SELECT SYSTEM$AUTO_REFRESH_STATUS('st681')");
        assertAnswer("REFUSED SQL compilation error: | argument 0 to function SqlIdentifier{qualifierNames=[], "
                + "identifierName=SYSTEM$AUTO_REFRESH_STATUS} needs to be constant, found 'CAST(AR681.A AS "
                + "VARCHAR(134217728))'",
            "SELECT SYSTEM$AUTO_REFRESH_STATUS(a::VARCHAR) FROM ar681");
        assertAnswer("REFUSED SQL compilation error: | invalid parameter 'Auto Refresh is not supported for table "
                + "ar681'",
            "SELECT a, SYSTEM$AUTO_REFRESH_STATUS('ar681') FROM ar681 ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | invalid parameter 'Auto Refresh is not supported for table "
                + "ar681'",
            "SELECT SYSTEM$AUTO_REFRESH_STATUS('ar681') WHERE FALSE");
        assertAnswer("REFUSED SQL compilation error: | invalid parameter 'Auto Refresh is not supported for table "
                + "ar681'",
            "SELECT TYPEOF(TRY_PARSE_JSON(SYSTEM$AUTO_REFRESH_STATUS('ar681')))");
        assertAnswer("REFUSED SQL compilation error: | invalid parameter 'Auto Refresh is not supported for table "
                + "ar681'",
            "SELECT system$auto_refresh_status('ar681')");
    }
}
