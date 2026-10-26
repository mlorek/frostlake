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
 * A view's own column masking, from its column list or from ALTER VIEW, applies to a stream on the view and to
 * CHANGES over it as it applies where the view is read (all live-verified).
 */
public class ViewStreamColumnMaskingTest extends MeasuredCellsTestSupport {

    @Test
    public void aMaskedViewsChanges() {
        engine.execute("CREATE OR REPLACE TABLE te751 (id INT, secret VARCHAR) CHANGE_TRACKING = TRUE");
        engine.execute("CREATE OR REPLACE MASKING POLICY mpe751 AS (v VARCHAR) RETURNS VARCHAR -> '***'");
        engine.execute("CREATE OR REPLACE VIEW ve751 (id, secret WITH MASKING POLICY mpe751) CHANGE_TRACKING = TRUE "
            + "AS SELECT id, secret FROM te751");
        engine.execute("CREATE OR REPLACE VIEW vf751 CHANGE_TRACKING = TRUE AS SELECT id, secret FROM te751");
        engine.execute("ALTER VIEW vf751 MODIFY COLUMN secret SET MASKING POLICY mpe751");
        engine.execute("CREATE OR REPLACE STREAM se751 ON VIEW ve751");
        engine.execute("CREATE OR REPLACE STREAM sf751 ON VIEW vf751");
        try {
            Thread.sleep(isLiveSnowflake() ? 2500L : 60L);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        engine.execute("SET e1 = DATE_PART(EPOCH_MILLISECOND, CURRENT_TIMESTAMP())");
        try {
            Thread.sleep(isLiveSnowflake() ? 2500L : 60L);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        engine.execute("INSERT INTO te751 VALUES (1, 'a')");
        try {
            Thread.sleep(isLiveSnowflake() ? 2500L : 60L);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertAnswer("1|***", "SELECT id, secret FROM ve751 ORDER BY 1");
        assertAnswer("1|***", "SELECT id, secret FROM vf751 ORDER BY 1");
        assertAnswer("1|***|INSERT", "SELECT id, secret, METADATA$ACTION FROM se751 ORDER BY 1");
        assertAnswer("1|***|INSERT", "SELECT id, secret, METADATA$ACTION FROM sf751 ORDER BY 1");
        assertAnswer("1|***",
            "SELECT id, secret FROM ve751 CHANGES(INFORMATION => DEFAULT) AT(TIMESTAMP => TO_TIMESTAMP_LTZ($e1, 3)) "
                + "ORDER BY 1");
        assertAnswer("1|***",
            "SELECT id, secret FROM vf751 CHANGES(INFORMATION => DEFAULT) AT(TIMESTAMP => TO_TIMESTAMP_LTZ($e1, 3)) "
                + "ORDER BY 1");
        assertAnswer("1|***",
            "SELECT id, secret FROM ve751 CHANGES(INFORMATION => APPEND_ONLY) AT(TIMESTAMP => TO_TIMESTAMP_LTZ($e1, "
                + "3)) ORDER BY 1");
    }
}
