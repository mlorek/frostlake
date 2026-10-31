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
 * ALTER TABLE ... DROP COLUMN keeps a column a policy reads, refused in one line: a column in the ON list of the
 * table's row access policy is "Cannot drop column 'REG' which is associated with a Row access policy." and one
 * a conditional masking policy reads through USING "... with a Masking policy.", even beside the column it
 * masks; a column a masking or a projection policy is set on may go (all live-verified).
 */
public class DroppedPolicyColumnTest extends MeasuredCellsTestSupport {

    @Test
    public void aColumnARowAccessPolicyReadsStays() {
        assertAnswer("Row access policy 'RP' is successfully created.",
            "CREATE ROW ACCESS POLICY rp AS (r VARCHAR) RETURNS BOOLEAN -> TRUE");
        assertAnswer("Row access policy 'RP2' is successfully created.",
            "CREATE ROW ACCESS POLICY rp2 AS (r VARCHAR, n INT) RETURNS BOOLEAN -> TRUE");
        assertAnswer("Masking policy MP successfully created.",
            "CREATE MASKING POLICY mp AS (val INT) RETURNS INT -> val");
        assertAnswer("Projection policy 'PP' is successfully created.",
            "CREATE PROJECTION POLICY pp AS () RETURNS PROJECTION_CONSTRAINT -> PROJECTION_CONSTRAINT(ALLOW => true)");
        assertAnswer("Table T successfully created.", "CREATE TABLE t (reg VARCHAR, v INT, w INT)");
        assertAnswer("Statement executed successfully.", "ALTER TABLE t ADD ROW ACCESS POLICY rp ON (reg)");
        assertAnswer("REFUSED SQL compilation error: Cannot drop column 'REG' which is associated with a Row access "
                + "policy.",
            "ALTER TABLE t DROP COLUMN reg");
        assertAnswer("REFUSED SQL compilation error: Cannot drop column 'REG' which is associated with a Row access "
                + "policy.",
            "ALTER TABLE t DROP COLUMN IF EXISTS reg");
        assertAnswer("REFUSED SQL compilation error: Cannot drop column 'REG' which is associated with a Row access "
                + "policy.",
            "ALTER TABLE t DROP COLUMN w, reg");
        assertAnswer("3",
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'T' AND TABLE_SCHEMA = 'TEST_SCHEMA'");
        assertAnswer("REFUSED SQL compilation error: | column 'reg' does not exist",
            "ALTER TABLE t DROP COLUMN \"reg\"");
        assertAnswer("Table T2 successfully created.", "CREATE TABLE t2 (reg VARCHAR, n INT, x INT)");
        assertAnswer("Statement executed successfully.", "ALTER TABLE t2 ADD ROW ACCESS POLICY rp2 ON (reg, n)");
        assertAnswer("REFUSED SQL compilation error: Cannot drop column 'N' which is associated with a Row access "
                + "policy.",
            "ALTER TABLE t2 DROP COLUMN n");
        assertAnswer("Table T3 successfully created.", "CREATE TABLE t3 (v INT, w INT)");
        assertAnswer("Statement executed successfully.", "ALTER TABLE t3 MODIFY COLUMN v SET MASKING POLICY mp");
        assertAnswer("Statement executed successfully.", "ALTER TABLE t3 DROP COLUMN v");
        assertAnswer("1",
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'T3' AND TABLE_SCHEMA = 'TEST_SCHEMA'");
        assertAnswer("Table T4 successfully created.", "CREATE TABLE t4 (v INT, w INT)");
        assertAnswer("Statement executed successfully.", "ALTER TABLE t4 MODIFY COLUMN v SET PROJECTION POLICY pp");
        assertAnswer("Statement executed successfully.", "ALTER TABLE t4 DROP COLUMN v");
        assertAnswer("1",
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'T4' AND TABLE_SCHEMA = 'TEST_SCHEMA'");
        assertAnswer("Statement executed successfully.", "ALTER TABLE t DROP ROW ACCESS POLICY rp");
        assertAnswer("Statement executed successfully.", "ALTER TABLE t DROP COLUMN reg");
    }

    @Test
    public void aColumnAConditionalMaskingPolicyReadsStays() {
        assertAnswer("Table M successfully created.", "CREATE TABLE m (id INT, ssn STRING, region STRING)");
        assertAnswer("1", "INSERT INTO m VALUES (1, '111', 'EU')");
        assertAnswer("Masking policy CMP successfully created.",
            "CREATE MASKING POLICY cmp AS (v STRING, r STRING) RETURNS STRING -> CASE WHEN r = 'EU' THEN v ELSE "
                + "'***' END");
        assertAnswer("Statement executed successfully.",
            "ALTER TABLE m MODIFY COLUMN ssn SET MASKING POLICY cmp USING (ssn, region)");
        assertAnswer("REFUSED SQL compilation error: Cannot drop column 'REGION' which is associated with a Masking "
                + "policy.",
            "ALTER TABLE m DROP COLUMN region");
        assertAnswer("1|111|EU", "SELECT * FROM m");
        assertAnswer("REFUSED SQL compilation error: Cannot drop column 'REGION' which is associated with a Masking "
                + "policy.",
            "ALTER TABLE m DROP COLUMN ssn, region");
        assertAnswer("1|111|EU", "SELECT * FROM m");
        assertAnswer("Table M2 successfully created.", "CREATE TABLE m2 (id INT, ssn STRING, region STRING)");
        assertAnswer("1", "INSERT INTO m2 VALUES (1, '111', 'EU')");
        assertAnswer("Statement executed successfully.",
            "ALTER TABLE m2 MODIFY COLUMN ssn SET MASKING POLICY cmp USING (ssn, region)");
        assertAnswer("Statement executed successfully.", "ALTER TABLE m2 DROP COLUMN ssn");
        assertAnswer("1|EU", "SELECT * FROM m2");
        assertAnswer("Table M3 successfully created.", "CREATE TABLE m3 (id INT, ssn STRING, region STRING)");
        assertAnswer("Statement executed successfully.",
            "ALTER TABLE m3 MODIFY COLUMN ssn SET MASKING POLICY cmp USING (ssn, region)");
        assertAnswer("REFUSED SQL compilation error: Cannot drop column 'REGION' which is associated with a Masking "
                + "policy.",
            "ALTER TABLE m3 DROP COLUMN IF EXISTS region");
    }
}
