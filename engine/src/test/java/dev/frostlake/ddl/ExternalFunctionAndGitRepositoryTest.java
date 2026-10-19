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

import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * CREATE [SECURE] EXTERNAL FUNCTION and CREATE GIT REPOSITORY are read and their API integration judged: a missing
 * schema first, then a missing API_INTEGRATION or ORIGIN, then the integration itself (live-verified). An external
 * function an API integration lets through is kept and listed as one; a call of it is refused.
 */
public class ExternalFunctionAndGitRepositoryTest extends MeasuredCellsTestSupport {

    private static final String F = "CREATE OR REPLACE EXTERNAL FUNCTION f1450(x INT) RETURNS VARIANT ";

    private static final String G = "CREATE OR REPLACE GIT REPOSITORY g1450 ";

    private static final String NO_INTEGRATION = "REFUSED SQL compilation error: | Integration 'NOSUCH_INT' does not "
        + "exist or not authorized.";

    private static final String NO_INTEGRATION_HINTED = NO_INTEGRATION + " Your primary role <ROLE> must have at "
        + "least one privilege granted on INTEGRATION NOSUCH_INT.";

    @Test
    public void theIntegrationAndTheSchemaAreJudged() {
        assertCells(new String[][] {
            {F + "API_INTEGRATION = nosuch_int AS 'https://example.com/x'", NO_INTEGRATION_HINTED},
            {"CREATE EXTERNAL FUNCTION f1450(x INT) RETURNS VARIANT API_INTEGRATION = nosuch_int AS "
                + "'https://example.com/x'", NO_INTEGRATION_HINTED},
            {"CREATE OR REPLACE SECURE EXTERNAL FUNCTION f1450(x INT) RETURNS VARIANT NOT NULL RETURNS NULL ON NULL "
                + "INPUT VOLATILE COMMENT = 'c' API_INTEGRATION = nosuch_int HEADERS = ('a' = 'b') CONTEXT_HEADERS = "
                + "(CURRENT_USER) MAX_BATCH_ROWS = 10 COMPRESSION = AUTO AS 'https://example.com/x'",
                NO_INTEGRATION_HINTED},
            {F + "AS 'https://example.com/x'", "REFUSED Property 'API_INTEGRATION' must be specified"},
            {F + "API_INTEGRATION = nosuch_int", NO_INTEGRATION_HINTED},
            {"CREATE OR REPLACE EXTERNAL FUNCTION nosuch_s1450.f1450(x INT) RETURNS VARIANT API_INTEGRATION = "
                + "nosuch_int AS 'https://example.com/x'", "REFUSED SQL compilation error: | Schema "
                + "'TEST_DB.NOSUCH_S1450' does not exist or not authorized. Your primary role <ROLE> must have USAGE "
                + "or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S1450."},
            {G + "ORIGIN = 'https://github.com/x/y.git' API_INTEGRATION = nosuch_int", NO_INTEGRATION},
            {"CREATE GIT REPOSITORY g1450 ORIGIN = 'https://github.com/x/y.git' API_INTEGRATION = nosuch_int",
                NO_INTEGRATION},
            {G + "ORIGIN = 'https://github.com/x/y.git'", "REFUSED SQL compilation error: | Missing option(s): "
                + "[API_INTEGRATION]"},
            {G + "API_INTEGRATION = nosuch_int", "REFUSED SQL compilation error: | Missing option(s): [ORIGIN]"},
            {G + "ORIGIN = 'https://github.com/x/y.git' API_INTEGRATION = nosuch_int GIT_CREDENTIALS = nosuch_secret "
                + "COMMENT = 'c'", NO_INTEGRATION},
        });
    }

    @Test
    public void anExternalFunctionIsKeptAndItsCallRefused() {
        assumeFalse(isLiveSnowflake(), "no API integration is created on the account");
        answer("CREATE OR REPLACE API INTEGRATION ef_api1450 API_PROVIDER = aws_api_gateway API_AWS_ROLE_ARN = "
            + "'arn:aws:iam::1:role/r' API_ALLOWED_PREFIXES = ('https://example.com/') ENABLED = TRUE");
        answer("CREATE OR REPLACE API INTEGRATION ef_git1450 API_PROVIDER = git_https_api API_ALLOWED_PREFIXES = "
            + "('https://github.com/') ENABLED = TRUE");
        answer("CREATE OR REPLACE NOTIFICATION INTEGRATION ef_ns1450 TYPE = EMAIL ENABLED = TRUE");
        try {
            assertCells(new String[][] {
                {F + "API_INTEGRATION = ef_api1450 AS 'https://example.com/x'", "Function F1450 successfully created."},
                {"SELECT f1450(1)", "REFUSED External function F1450 cannot be called: the remote service at "
                    + "https://example.com/x is not reached from this engine."},
                {F + "API_INTEGRATION = ef_ns1450 AS 'https://example.com/x'", "REFUSED SQL compilation error: | "
                    + "Integration 'EF_NS1450' is not an API integration."},
                {F + "API_INTEGRATION = ef_git1450 AS 'https://example.com/x'", "REFUSED SQL compilation error: "
                    + "Invalid API Provider for 'External Function' object."},
                {G + "ORIGIN = 'https://github.com/x/y.git' API_INTEGRATION = ef_ns1450",
                    "REFUSED SQL compilation error: | Integration 'EF_NS1450' does not exist or not authorized."},
            });
            answer("SHOW USER FUNCTIONS LIKE 'F1450'");
            assertAnswer("Y|EXTERNAL", "SELECT \"is_external_function\", \"language\" FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        } finally {
            answer("DROP INTEGRATION IF EXISTS ef_api1450");
            answer("DROP INTEGRATION IF EXISTS ef_git1450");
            answer("DROP INTEGRATION IF EXISTS ef_ns1450");
        }
    }
}
