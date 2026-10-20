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

import org.junit.jupiter.api.Test;

/**
 * A row access policy over a column a masking policy masks, the masking policy EXEMPT_OTHER_POLICIES = TRUE, judges
 * the stored value, and the rows it keeps are shown masked (live-verified): a policy keeping id 1 shows the row as
 * {@code 100|a}, one keeping id 100 keeps nothing, and an UPDATE or a DELETE changes the rows the policy keeps.
 */
public class MaskedRowAccessOrderTest extends MeasuredCellsTestSupport {

    /** Each statement and its answer. */
    @Test
    public void theRowAccessPolicyReadsTheStoredValue() {
        engine.execute("CREATE OR REPLACE TABLE rat (id INT, s VARCHAR)");
        engine.execute("INSERT INTO rat VALUES (1, 'a'), (2, 'b')");
        assertCells(new String[][] {
            {"CREATE OR REPLACE MASKING POLICY ra_mp AS (v INT) RETURNS INT -> v * 100 EXEMPT_OTHER_POLICIES = TRUE",
                "Masking policy RA_MP successfully created."},
            {"ALTER TABLE rat MODIFY COLUMN id SET MASKING POLICY ra_mp",
                "Statement executed successfully."},
            {"CREATE OR REPLACE ROW ACCESS POLICY ra_rap AS (i INT) RETURNS BOOLEAN -> i = 1",
                "Row access policy 'RA_RAP' is successfully created."},
            {"ALTER TABLE rat ADD ROW ACCESS POLICY ra_rap ON (id)",
                "Statement executed successfully."},
            {"SELECT * FROM rat ORDER BY 1",
                "100|a"},
            {"CREATE OR REPLACE ROW ACCESS POLICY ra_rap2 AS (i INT) RETURNS BOOLEAN -> i = 100",
                "Row access policy 'RA_RAP2' is successfully created."},
            {"ALTER TABLE rat DROP ROW ACCESS POLICY ra_rap",
                "Statement executed successfully."},
            {"ALTER TABLE rat ADD ROW ACCESS POLICY ra_rap2 ON (id)",
                "Statement executed successfully."},
            {"SELECT * FROM rat ORDER BY 1",
                ""},
            {"SELECT COUNT(*) FROM rat",
                "0"},
            {"SELECT id FROM rat WHERE id = 100",
                ""},
            {"ALTER TABLE rat DROP ROW ACCESS POLICY ra_rap2",
                "Statement executed successfully."},
            {"ALTER TABLE rat ADD ROW ACCESS POLICY ra_rap ON (id)",
                "Statement executed successfully."},
            {"SELECT id, s FROM rat WHERE id > 50",
                "100|a"},
            {"UPDATE rat SET s = 'z'",
                "1|0"},
            {"SELECT * FROM rat ORDER BY 1",
                "100|z"},
            {"DELETE FROM rat",
                "1"},
            {"ALTER TABLE rat DROP ROW ACCESS POLICY ra_rap",
                "Statement executed successfully."},
            {"SELECT * FROM rat ORDER BY 1",
                "200|b"},
        });
    }
}
