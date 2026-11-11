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

package dev.frostlake.scripting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A statement a Python handler issues through session.sql that the account refuses raises Snowpark's
 * snowflake.snowpark.exceptions.SnowparkSQLException, its error_code '1304', its sfqid the failed statement's query
 * ID, which LAST_QUERY_ID() then names, and its text {@code (1304): <query id>: <code> (<state>): <message>}, the code
 * in six digits. Uncaught, the call's refusal ends on that line (all live-verified).
 */
public class PythonSnowparkSqlExceptionTest extends MeasuredCellsTestSupport {

    private static final String QID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private void createProcedures() {
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE OR REPLACE PROCEDURE ph960(q VARCHAR) RETURNS VARCHAR LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.11' PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\n"
            + "import re\n"
            + "def run(session, q):\n"
            + "    try:\n"
            + "        session.sql(q).collect()\n"
            + "        return 'ok'\n"
            + "    except Exception as e:\n"
            + "        text = str(e)\n"
            + "        ids = re.findall('" + QID + "', text)\n"
            + "        last = session.sql('SELECT LAST_QUERY_ID()').collect()[0][0]\n"
            + "        same = 'same' if ids and ids[0] == last else 'differs'\n"
            + "        code = str(getattr(e, 'error_code', None))\n"
            + "        sfqid = getattr(e, 'sfqid', None)\n"
            + "        qid = 'none' if sfqid is None else ('same' if sfqid == last else 'other')\n"
            + "        return (type(e).__module__ + '.' + type(e).__name__ + '|' + same + '|' + code + '|'\n"
            + "                + qid + '|'\n"
            + "                + re.sub('" + QID + "', '<QID>', text))\n"
            + "$$");
        engine.execute("CREATE OR REPLACE PROCEDURE pu960(q VARCHAR) RETURNS VARCHAR LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.11' PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\n"
            + "def run(session, q):\n"
            + "    session.sql(q).collect()\n"
            + "    return 'ok'\n"
            + "$$");
    }

    @Test
    public void aRefusedStatementRaisesSnowparksException() {
        createProcedures();
        final String raised = "snowflake.snowpark.exceptions.SnowparkSQLException|same|1304|same|(1304): <QID>: ";
        assertAnswer(raised + "002003 (42S02): SQL compilation error: | Object 'NOSUCH960' does not exist or not "
            + "authorized. This executable runs with owner's rights. The owner role <ROLE> must have at least one "
            + "privilege granted on TABLE NOSUCH960.", "CALL ph960('SELECT * FROM nosuch960')");
        assertAnswer(raised + "100051 (22012): Division by zero", "CALL ph960('SELECT 1/0')");
        assertAnswer(raised + "001003 (42000): SQL compilation error: | syntax error line 1 at position 0 "
            + "unexpected 'SELEC'.", "CALL ph960('SELEC 1')");
        assertAnswer(raised + "100038 (22018): Numeric value 'x' is not recognized",
            "CALL ph960('SELECT CAST(''x'' AS INT)')");
    }

    @Test
    public void uncaughtTheCallsRefusalEndsOnSnowparksLine() {
        createProcedures();
        final String refusal = answer("CALL pu960('SELECT * FROM nosuch960')").replaceAll(QID, "<QID>");
        assertEquals("snowflake.snowpark.exceptions.SnowparkSQLException: (1304): <QID>: 002003 (42S02): SQL "
            + "compilation error: | Object 'NOSUCH960' does not exist or not authorized. This executable runs with "
            + "owner's rights. The owner role <ROLE> must have at least one privilege granted on TABLE NOSUCH960. "
            + "|  in function PU960 with handler run",
            refusal.substring(refusal.lastIndexOf("snowflake.snowpark.exceptions.")));
    }
}
