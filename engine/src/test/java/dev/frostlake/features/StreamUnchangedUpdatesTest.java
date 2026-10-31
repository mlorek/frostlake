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
 * An update that leaves a row as a stream's reader sees it is no change: a table stream drops an update that
 * sets a column to its own value, and a view stream one that changes only a masked value or a column the view
 * does not project; SYSTEM$STREAM_HAS_DATA and CHANGES agree (all live-verified).
 */
public class StreamUnchangedUpdatesTest extends MeasuredCellsTestSupport {

    @Test
    public void updatesThatChangeNothing() {
        engine.execute("CREATE OR REPLACE TABLE tu751 (id INT, region VARCHAR, secret VARCHAR, other VARCHAR) "
            + "CHANGE_TRACKING = TRUE");
        engine.execute("CREATE OR REPLACE ROW ACCESS POLICY ru751 AS (r VARCHAR) RETURNS BOOLEAN -> r = 'EU'");
        engine.execute("CREATE OR REPLACE MASKING POLICY mu751 AS (v VARCHAR) RETURNS VARCHAR -> '***'");
        engine.execute("CREATE OR REPLACE TABLE tw751 (id INT, region VARCHAR, other VARCHAR) CHANGE_TRACKING = TRUE");
        engine.execute("INSERT INTO tu751 VALUES (1, 'EU', 'a', 'o'), (2, 'US', 'bb', 'o'), (3, 'EU', 'ccc', 'o')");
        engine.execute("INSERT INTO tw751 VALUES (1, 'EU', 'o'), (2, 'US', 'o')");
        engine.execute("ALTER TABLE tu751 ADD ROW ACCESS POLICY ru751 ON (region)");
        engine.execute("ALTER TABLE tu751 MODIFY COLUMN secret SET MASKING POLICY mu751");
        engine.execute("CREATE OR REPLACE VIEW vu751 CHANGE_TRACKING = TRUE AS SELECT id, region, secret, "
            + "LENGTH(secret) AS n FROM tu751");
        engine.execute("CREATE OR REPLACE VIEW vw751 CHANGE_TRACKING = TRUE AS SELECT id, region FROM tw751");
        engine.execute("CREATE OR REPLACE STREAM su751 ON VIEW vu751");
        engine.execute("CREATE OR REPLACE STREAM sw751 ON VIEW vw751");
        engine.execute("CREATE OR REPLACE STREAM st751 ON TABLE tw751");
        try {
            Thread.sleep(isLiveSnowflake() ? 2500L : 60L);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        engine.execute("UPDATE tu751 SET secret = 'zzzz' WHERE id IN (1, 2)");
        engine.execute("DELETE FROM tu751 WHERE id = 3");
        engine.execute("UPDATE tw751 SET other = 'p' WHERE id = 1");
        engine.execute("UPDATE tw751 SET region = region WHERE id = 2");
        try {
            Thread.sleep(isLiveSnowflake() ? 2500L : 60L);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertAnswer("3|***|3|DELETE|false",
            "SELECT id, secret, n, METADATA$ACTION, METADATA$ISUPDATE FROM su751 ORDER BY 1, 4");
        assertAnswer("", "SELECT id, region, METADATA$ACTION, METADATA$ISUPDATE FROM sw751 ORDER BY 1, 3");
        assertAnswer("1|EU|o|DELETE|true ;; 1|EU|p|INSERT|true",
            "SELECT id, region, other, METADATA$ACTION, METADATA$ISUPDATE FROM st751 ORDER BY 1, 4");
        assertAnswer("0", "SELECT COUNT(*) FROM sw751");
        assertAnswer("2", "SELECT COUNT(*) FROM st751");
    }

    @Test
    public void hasDataChangesAndAChangeUndone() {
        engine.execute("CREATE OR REPLACE TABLE tu752 (id INT, region VARCHAR) CHANGE_TRACKING = TRUE");
        engine.execute("INSERT INTO tu752 VALUES (1, 'EU'), (2, 'US')");
        engine.execute("CREATE OR REPLACE STREAM su752 ON TABLE tu752");
        engine.execute("CREATE OR REPLACE STREAM sv752 ON TABLE tu752");
        try {
            Thread.sleep(isLiveSnowflake() ? 2500L : 60L);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        engine.execute("SET u1 = DATE_PART(EPOCH_MILLISECOND, CURRENT_TIMESTAMP())");
        try {
            Thread.sleep(isLiveSnowflake() ? 2500L : 60L);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        engine.execute("UPDATE tu752 SET region = region WHERE id = 1");
        try {
            Thread.sleep(isLiveSnowflake() ? 2500L : 60L);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertAnswer("true", "SELECT SYSTEM$STREAM_HAS_DATA('su752')");
        assertAnswer("0", "SELECT COUNT(*) FROM su752");
        assertAnswer("",
            "SELECT id, region, METADATA$ACTION, METADATA$ISUPDATE FROM tu752 CHANGES(INFORMATION => DEFAULT) "
                + "AT(TIMESTAMP => TO_TIMESTAMP_LTZ($u1, 3)) ORDER BY 1, 3");
        assertAnswer("1|0", "UPDATE tu752 SET region = 'XX' WHERE id = 2");
        assertAnswer("1|0", "UPDATE tu752 SET region = 'US' WHERE id = 2");
        assertAnswer("", "SELECT id, region, METADATA$ACTION, METADATA$ISUPDATE FROM sv752 ORDER BY 1, 3");
        assertAnswer("",
            "SELECT id, region, METADATA$ACTION, METADATA$ISUPDATE FROM tu752 CHANGES(INFORMATION => DEFAULT) "
                + "AT(TIMESTAMP => TO_TIMESTAMP_LTZ($u1, 3)) ORDER BY 1, 3");
    }
}
