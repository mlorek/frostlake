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
 * A statement of a flow (->>) that fails as it runs or as it compiles is reported as an uncaught STATEMENT_ERROR placed
 * at the line and position the statement starts at, while a SHOW's page size of 0 is refused inside a flow as it is
 * outside one (live-verified).
 */
public class FlowChainRefusalsTest extends MeasuredCellsTestSupport {

    /** Each failing statement of a flow is placed; a page size of 0 is refused as it reads. */
    @Test
    public void aFlowsFailingStatementIsPlacedWhereItStarts() {
        assertCells(new String[][] {
            {"SHOW TABLES LIMIT 0 ->> SELECT \"name\" FROM $1",
                "REFUSED page size \"0\" must be greater than 0 in limit clause"},
            {"SELECT 1/0 ->> SELECT * FROM $1",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 0 : Division by zero"},
            {"SELECT 1 ->> SELECT 1/0 FROM $1",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 13 : Division by zero"},
            {"SELECT * FROM nosuch784 ->> SELECT * FROM $1",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 0 : SQL compilation error: "
                + "| Object 'NOSUCH784' does not exist or not authorized. Your primary role <ROLE> must have at least "
                + "one privilege granted on TABLE NOSUCH784."},
            {"SHOW TABLES LIKE 'x' LIMIT 0 FROM 'a' ->> SELECT \"name\" FROM $1",
                "REFUSED page size \"0\" must be greater than 0 in limit clause"},
        });
    }
}
