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

package dev.frostlake.features;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * A session variable as the value of an external stage's options and of a network policy's properties
 * (live-verified): a stage's URL takes a text alone, any other value refused by the variable's name; a key among its
 * CREDENTIALS or ENCRYPTION takes a text, a number refused by its value; its STORAGE_INTEGRATION reads a text as the
 * integration's name, a number being no type of it. A network policy's lists take a text for each item and its
 * COMMENT a text, a Boolean leaving none. DESCRIBE STAGE lists an external stage's location as a JSON array of its URL.
 */
public class ExternalStageVariablesTest extends MeasuredCellsTestSupport {

    private static final String URL = "URL = 's3://flq-no-such-bucket-46/p/'";

    private void variables() {
        engine.execute("SET nul = NULL");
        engine.execute("SET b = TRUE");
        engine.execute("SET n = 5");
        engine.execute("SET c = 'a comment'");
        engine.execute("SET u = 's3://flq-no-such-bucket-45/p/'");
        engine.execute("SET k1 = 'k1'");
        engine.execute("SET k2 = 'k2'");
        engine.execute("SET si = 'flq_no_such_si45'");
        engine.execute("SET kk = 'kms45'");
        engine.execute("SET ip = '192.168.1.0/24'");
    }

    @Test
    public void anExternalStageTakesItsOptionsFromVariables() {
        variables();
        assertCells(new String[][] {
            {"CREATE OR REPLACE STAGE xs45 URL = $u", "Stage area XS45 successfully created."},
            {"CREATE OR REPLACE STAGE xs45 URL = 's3://flq-no-such-bucket-45/p/' CREDENTIALS = (AWS_KEY_ID = $k1 "
                + "AWS_SECRET_KEY = $k2)", "Stage area XS45 successfully created."},
            {"CREATE OR REPLACE STAGE xs45 URL = 's3://flq-no-such-bucket-45/p/' ENCRYPTION = (TYPE = 'AWS_SSE_KMS' "
                + "KMS_KEY_ID = $kk)", "Stage area XS45 successfully created."},
            {"CREATE OR REPLACE STAGE xs46 URL = $n",
                "REFUSED SQL compilation error: | invalid value [$n] for parameter 'URL'"},
            {"CREATE OR REPLACE STAGE xs46 URL = $nul", "REFUSED SQL compilation error: | invalid value [$nul] for "
                + "parameter 'URL'"},
            {"CREATE OR REPLACE STAGE xs46 URL = $b", "REFUSED SQL compilation error: | invalid value [$b] for "
                + "parameter 'URL'"},
            {"CREATE OR REPLACE STAGE xs46 " + URL + " STORAGE_INTEGRATION = $n",
                "REFUSED SQL compilation error: | invalid type of property '$n' for 'STORAGE_INTEGRATION'"},
            {"CREATE OR REPLACE STAGE xs46 " + URL + " ENCRYPTION = (TYPE = 'AWS_SSE_KMS' KMS_KEY_ID = $n)",
                "REFUSED SQL compilation error: | invalid value [5] for parameter 'KMS_KEY_ID'"},
        });
        assertAnswer("REFUSED SQL compilation error: | Integration 'FLQ_NO_SUCH_SI45' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on INTEGRATION "
                + "FLQ_NO_SUCH_SI45.",
            "CREATE OR REPLACE STAGE xs45 URL = 's3://flq-no-such-bucket-45/p/' STORAGE_INTEGRATION = $si");
        engine.execute("CREATE OR REPLACE STAGE xs46 " + URL);
        assertAnswer("Statement executed successfully.", "ALTER STAGE xs46 SET URL = $u");
        engine.executeQuery("DESC STAGE xs46");
        assertAnswer("[\"s3://flq-no-such-bucket-45/p/\"]",
            "SELECT \"property_value\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"property\" = 'URL'");
        engine.execute("CREATE OR REPLACE STAGE is46");
        engine.executeQuery("DESC STAGE is46");
        assertAnswer("",
            "SELECT \"property_value\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"property\" = 'URL'");
    }

    @Test
    public void aNetworkPolicyTakesItsListsAndCommentFromVariables() {
        variables();
        try {
            assertCells(new String[][] {
                {"CREATE OR REPLACE NETWORK POLICY np45 ALLOWED_IP_LIST = ($ip) COMMENT = $c",
                    "Network policy NP45 is created."},
                {"CREATE OR REPLACE NETWORK POLICY np45 ALLOWED_IP_LIST = ('192.168.1.0/24') BLOCKED_IP_LIST = ($ip)",
                    "Network policy NP45 is created."},
                {"CREATE OR REPLACE NETWORK POLICY np45 ALLOWED_IP_LIST = ('192.168.1.0/24') COMMENT = $c",
                    "Network policy NP45 is created."},
                {"DESC NETWORK POLICY np45", "ALLOWED_IP_LIST|192.168.1.0/24"},
                {"ALTER NETWORK POLICY np45 SET COMMENT = $nul", "Statement executed successfully."},
                {"CREATE OR REPLACE NETWORK POLICY np46 ALLOWED_IP_LIST = ($n)",
                    "REFUSED SQL compilation error: | invalid value '$n' for property 'ALLOWED_IP_LIST'"},
                {"CREATE OR REPLACE NETWORK POLICY np46 ALLOWED_IP_LIST = ($nul)",
                    "REFUSED SQL compilation error: | invalid value '$nul' for property 'ALLOWED_IP_LIST'"},
                {"CREATE OR REPLACE NETWORK POLICY np46 ALLOWED_IP_LIST = ('10.0.0.1') COMMENT = $n",
                    "REFUSED SQL compilation error: | invalid type of property '$n' for 'COMMENT'"},
                {"CREATE OR REPLACE NETWORK POLICY np46 ALLOWED_IP_LIST = ('10.0.0.1') COMMENT = $b",
                    "Network policy NP46 is created."},
            });
            engine.executeQuery("SHOW NETWORK POLICIES LIKE 'NP46'");
            assertAnswer("", "SELECT \"comment\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        } finally {
            answer("DROP NETWORK POLICY IF EXISTS np45");
            answer("DROP NETWORK POLICY IF EXISTS np46");
        }
    }
}
