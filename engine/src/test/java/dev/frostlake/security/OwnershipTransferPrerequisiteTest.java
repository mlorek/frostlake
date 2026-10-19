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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * What moving a stream or a paused pipe to another role asks of the role that moves it, and what an object given
 * away leaves visible, as measured on live: SELECT on the table a stream reads, INSERT on the table a paused pipe
 * loads, each refused in live's sentence; INFORMATION_SCHEMA lists nothing the session's roles no longer reach, and
 * DESCRIBE PROCEDURE answers an empty body. Embedded only: the live account may not create roles or move ownership.
 */
public class OwnershipTransferPrerequisiteTest extends MeasuredCellsTestSupport {

    @Override
    protected void setupTest() {
        Assumptions.assumeFalse(isLiveSnowflake(), "the live account may not create roles or move ownership");
    }

    @Test
    public void aStreamAndAPausedPipeAskForTheirTablesPrivilege() {
        engine.execute("USE ROLE ACCOUNTADMIN");
        engine.execute("USE SECONDARY ROLES NONE");
        engine.execute("DROP ROLE IF EXISTS flq_vs_r");
        engine.execute("CREATE ROLE flq_vs_r");
        engine.execute("DROP ROLE IF EXISTS flq_vs_r2");
        engine.execute("CREATE ROLE flq_vs_r2");
        engine.execute("CREATE OR REPLACE SCHEMA vs_s2");
        engine.execute("USE SCHEMA test_schema");
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT, b INT)");
        engine.execute("CREATE OR REPLACE TABLE kept (a INT)");
        engine.execute("CREATE OR REPLACE TABLE t2 (a INT)");
        engine.execute("CREATE OR REPLACE STAGE st1");
        engine.execute("CREATE OR REPLACE PROCEDURE p1() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
        engine.execute("CREATE OR REPLACE STREAM sm1 ON TABLE t1");
        engine.execute("CREATE OR REPLACE STREAM sm_kept ON TABLE kept");
        engine.execute("CREATE OR REPLACE PIPE pp AS COPY INTO t2 FROM @st1");
        engine.execute("ALTER PIPE pp SET PIPE_EXECUTION_PAUSED = TRUE");
        engine.execute("GRANT OWNERSHIP ON TABLE t1 TO ROLE flq_vs_r COPY CURRENT GRANTS");
        engine.execute("GRANT OWNERSHIP ON TABLE t2 TO ROLE flq_vs_r COPY CURRENT GRANTS");
        engine.execute("GRANT OWNERSHIP ON PROCEDURE p1() TO ROLE flq_vs_r COPY CURRENT GRANTS");
        engine.execute("GRANT OWNERSHIP ON SCHEMA vs_s2 TO ROLE flq_vs_r COPY CURRENT GRANTS");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'T1'", "0"},
            {"SELECT table_name FROM information_schema.tables WHERE table_name IN ('T1', 'KEPT') ORDER BY 1", "KEPT"},
            {"SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = 'VS_S2'", "0"},
        });
        engine.execute("DESCRIBE PROCEDURE p1()");
        assertCells(new String[][] {
            {"SELECT \"value\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"property\" = 'body'", ""},
            {"GRANT OWNERSHIP ON STREAM sm1 TO ROLE flq_vs_r2",
                "REFUSED To grant use role with SELECT privilege on the target table TEST_DB.TEST_SCHEMA.T1"},
            {"GRANT OWNERSHIP ON PIPE pp TO ROLE flq_vs_r2",
                "REFUSED Pipe may have pending files that could be loaded into table when resumed. To do grant, "
                + "use role with INSERT privilege on the target table T2"},
        });
    }
}
