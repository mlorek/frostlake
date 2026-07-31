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

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Writes into fixed-point columns round to the COLUMN's scale — live-verified: a bare NUMBER column
 * is NUMBER(38,0), so INSERT of 10.5 stores 11 and 2.71828 stores 3 (HALF_UP), the same for the
 * INTEGER aliases; a scaled column rounds to its own scale. FLOAT/DOUBLE columns keep the value.
 */
public class NumberColumnScaleRoundingTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void bareNumberColumnRoundsFractionalWrites() {
        engine.execute("CREATE TABLE nr_bare (v NUMBER)");
        engine.execute("INSERT INTO nr_bare VALUES (10.5), (2.71828), (-5.7)");
        assertEquals("11,3,-6",
            scalar("SELECT LISTAGG(v, ',') WITHIN GROUP (ORDER BY v DESC) FROM nr_bare").toString());
    }

    @Test
    public void integerAliasColumnsRoundTheSameWay() {
        engine.execute("CREATE TABLE nr_int (v INTEGER)");
        engine.execute("INSERT INTO nr_int VALUES (10.5)");
        assertEquals(11L, ((Number) scalar("SELECT v FROM nr_int")).longValue());
    }

    @Test
    public void scaledColumnRoundsToItsOwnScale() {
        engine.execute("CREATE TABLE nr_scaled (v NUMBER(10,2))");
        engine.execute("INSERT INTO nr_scaled VALUES (1.005), (1.2)");
        assertEquals("1.01,1.20",
            scalar("SELECT LISTAGG(v, ',') WITHIN GROUP (ORDER BY v) FROM nr_scaled").toString());
    }

    @Test
    public void doubleColumnsKeepTheFraction() {
        engine.execute("CREATE TABLE nr_double (v DOUBLE)");
        engine.execute("INSERT INTO nr_double VALUES (10.5)");
        assertEquals(10.5, ((Number) scalar("SELECT v FROM nr_double")).doubleValue(), 0.0001);
    }

    @Test
    public void updatesRoundLikeInserts() {
        engine.execute("CREATE TABLE nr_upd (v NUMBER)");
        engine.execute("INSERT INTO nr_upd VALUES (1)");
        engine.execute("UPDATE nr_upd SET v = 2.6");
        assertEquals(3L, ((Number) scalar("SELECT v FROM nr_upd")).longValue());
    }
}
