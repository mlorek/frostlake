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

package dev.frostlake.parser;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * The syntax-error lines of a CREATE OR ALTER MASKING POLICY with IF NOT EXISTS or SECURE, and a plain CREATE
 * ROW ACCESS POLICY that takes EXEMPT_OTHER_POLICIES (all live-verified).
 */
public class CreateOrAlterPolicyLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void policyLines() {
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'NOT'. | syntax "
                + "error line 1 at position 48 unexpected 'AS'. | syntax error line 1 at position 54 unexpected "
                + "'VARCHAR'. | syntax error line 1 at position 61 unexpected ')'.",
            "CREATE OR ALTER MASKING POLICY IF NOT EXISTS m9 AS (v VARCHAR) RETURNS VARCHAR -> v");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'MASKING'.",
            "CREATE OR ALTER SECURE MASKING POLICY m9 AS (v VARCHAR) RETURNS VARCHAR -> v");
        assertAnswer("Row access policy 'RAP26' is successfully created.",
            "CREATE ROW ACCESS POLICY rap26 AS (v VARCHAR) RETURNS BOOLEAN -> TRUE EXEMPT_OTHER_POLICIES = TRUE");
        assertAnswer("Row access policy 'RAP26B' is successfully created.",
            "CREATE OR REPLACE ROW ACCESS POLICY rap26b AS (v VARCHAR) RETURNS BOOLEAN -> TRUE EXEMPT_OTHER_POLICIES "
                + "= TRUE");
    }
}
