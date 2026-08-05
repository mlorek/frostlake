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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Verifies each Snowflake alias resolves to (and matches) its already-implemented base function. */
public class AliasCoverageTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE nums (x NUMBER)");
        engine.execute("INSERT INTO nums VALUES (2), (4), (4), (6)");
    }

    private Object q(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void testPowAliasesPower() {
        assertEquals(8.0, ((Number) q("SELECT POW(2, 3)")).doubleValue(), 0.0001);
        assertEquals(q("SELECT POWER(2, 3)"), q("SELECT POW(2, 3)"));
    }

    @Test
    public void testTruncateAliasesTrunc() {
        assertEquals(q("SELECT TRUNC(10.567, 1)"), q("SELECT TRUNCATE(10.567, 1)"));
    }

    @Test
    public void testDateAliasesToDate() {
        assertEquals(q("SELECT TO_DATE('2024-01-01')"), q("SELECT DATE('2024-01-01')"));
    }

    @Test
    public void testTimeAliasesToTime() {
        assertEquals(q("SELECT TO_TIME('13:30:00')"), q("SELECT TIME('13:30:00')"));
    }

    @Test
    public void testTimestampaddAliasesDateadd() {
        assertEquals(q("SELECT DATEADD('day', 5, '2024-01-10')"),
                     q("SELECT TIMESTAMPADD('day', 5, '2024-01-10')"));
    }

    @Test
    public void testTimestampdiffAliasesDatediff() {
        assertEquals(q("SELECT DATEDIFF('day', '2024-01-01', '2024-01-10')"),
                     q("SELECT TIMESTAMPDIFF('day', '2024-01-01', '2024-01-10')"));
    }

    @Test
    public void testDayofmonthAliasesDay() {
        assertEquals(15, ((Number) q("SELECT DAYOFMONTH('2024-03-15'::DATE)")).intValue());
        assertEquals(q("SELECT DAY('2024-03-15'::DATE)"), q("SELECT DAYOFMONTH('2024-03-15'::DATE)"));
    }

    @Test
    public void testLocaltimeAliasesCurrentTime() {
        assertNotNull(q("SELECT LOCALTIME()"));
    }

    @Test
    public void testSystimestampAliasesSysdate() {
        assertNotNull(q("SELECT SYSTIMESTAMP()"));
    }

    @Test
    public void testVariancePopAliasesVarPop() {
        assertEquals(q("SELECT VAR_POP(x) FROM nums"), q("SELECT VARIANCE_POP(x) FROM nums"));
    }

    @Test
    public void testVarianceSampAliasesVarSamp() {
        assertEquals(q("SELECT VAR_SAMP(x) FROM nums"), q("SELECT VARIANCE_SAMP(x) FROM nums"));
    }
}
