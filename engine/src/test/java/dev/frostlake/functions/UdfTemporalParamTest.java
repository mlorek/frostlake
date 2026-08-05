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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SQL-UDF argument binding follows the DECLARED parameter types, as Snowflake does: a date-flavored
 * value passed to a TIMESTAMP_NTZ parameter is a timestamp inside the body, so VARIANT/OBJECT output
 * renders it in full timestamp text ('2026-01-03 00:00:00.000'), never java.time's T-separated form or
 * date-only text. Also covers Snowflake's no-underscore TIMESTAMPNTZ cast alias, which appears in real
 * bodies.
 */
public class UdfTemporalParamTest extends BaseDatabaseTest {

    private String scalar(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        final Object v = result.getRows().get(0).getValue(0);
        return v == null ? null : v.toString();
    }

    @Test
    public void timestampParamRendersFullFormInsideObjectOutput() {
        engine.execute(
            """
            CREATE FUNCTION wrap_ts(ts TIMESTAMP_NTZ(9)) RETURNS OBJECT LANGUAGE SQL AS
            $$ SELECT OBJECT_CONSTRUCT('at', ts) $$
            """);
        assertEquals("{\"at\":\"2026-01-03 00:00:00.000\"}",
            scalar("SELECT wrap_ts('2026-01-03'::DATE) AS r"));
    }

    @Test
    public void dateArithmeticOverTimestampParamStaysInTimestampDomain() {
        engine.execute(
            """
            CREATE FUNCTION expiry(ts TIMESTAMP_NTZ(9)) RETURNS OBJECT LANGUAGE SQL AS
            $$ SELECT OBJECT_CONSTRUCT('until', DATEADD(DAY, 90, ts)) $$
            """);
        assertEquals("{\"until\":\"2026-04-03 00:00:00.000\"}",
            scalar("SELECT expiry('2026-01-03'::DATE) AS r"));
    }

    @Test
    public void noUnderscoreTimestampAliasCastsToTimestamp() {
        assertEquals("{\"a\":\"9999-12-31 00:00:00.000\"}",
            scalar("SELECT OBJECT_CONSTRUCT('a', '9999-12-31'::TIMESTAMPNTZ) AS r"));
    }
}
