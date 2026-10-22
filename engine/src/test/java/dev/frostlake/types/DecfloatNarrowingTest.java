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

package dev.frostlake.types;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A DECFLOAT converted to an exact NUMBER the NUMBER has no room for is refused naming the conversion with the
 * DECFLOAT as it was written - Decfloat out of representable range, operation: TO_NUMBER(99.95, 3, 1) - whatever
 * converts it: a cast to NUMBER(p,s), INT or a bare NUMBER, TO_NUMBER and TO_DECIMAL, a cast of a column, and a
 * write into a NUMBER column, which wraps it in its DML sentence (all live-verified).
 */
public class DecfloatNarrowingTest extends MeasuredCellsTestSupport {

    @Test
    public void aDecfloatPastTheNumberNamesTheConversion() {
        assertAnswer("REFUSED Decfloat out of representable range, operation: TO_NUMBER(12345, 3, 1)",
            "SELECT CAST(DECFLOAT '12345' AS NUMBER(3,1))");
        assertAnswer("REFUSED Decfloat out of representable range, operation: TO_NUMBER(99.95, 3, 1)",
            "SELECT CAST(DECFLOAT '99.95' AS NUMBER(3,1))");
        assertAnswer("REFUSED Decfloat out of representable range, operation: TO_NUMBER(-1e38, 38, 0)",
            "SELECT CAST(DECFLOAT '-1e38' AS INT)");
        assertAnswer("REFUSED Decfloat out of representable range, operation: TO_NUMBER(1e38, 38, 0)",
            "SELECT DECFLOAT '1e38'::NUMBER(38,0)");
        assertAnswer("REFUSED Decfloat out of representable range, operation: TO_NUMBER(1e38, 38, 0)",
            "SELECT TO_NUMBER(DECFLOAT '1e38')");
        assertAnswer("REFUSED Decfloat out of representable range, operation: TO_NUMBER(1e38, 10, 2)",
            "SELECT TO_DECIMAL(DECFLOAT '1e38', 10, 2)");
        assertAnswer("REFUSED Decfloat out of representable range, operation: TO_NUMBER(1.5e40, 20, 2)",
            "SELECT DECFLOAT '1.5e40'::NUMBER(20,2)");
        assertAnswer("REFUSED Decfloat out of representable range, operation: TO_NUMBER(123.456, 4, 2)",
            "SELECT DECFLOAT '123.456'::NUMBER(4,2)");
        assertAnswer("REFUSED Decfloat out of representable range, operation: "
                + "TO_NUMBER(1.2345678901234567890123456789012345678e50, 38, 0)",
            "SELECT DECFLOAT '1.2345678901234567890123456789012345678e50'::NUMBER");
        assertAnswer("0E-37", "SELECT CAST(DECFLOAT '1e-50' AS NUMBER(38,37))");
        assertAnswer("Table DD successfully created.", "CREATE TABLE dd (x DECFLOAT)");
        assertAnswer("1", "INSERT INTO dd SELECT DECFLOAT '1e38'");
        assertAnswer("REFUSED Decfloat out of representable range, operation: TO_NUMBER(1e38, 38, 0)",
            "SELECT x::INT FROM dd");
        assertAnswer("Table DN successfully created.", "CREATE TABLE dn (n NUMBER(38,0))");
        assertAnswer("REFUSED DML operation to table DN failed on column N with error: Decfloat out of representable "
                + "range, operation: TO_NUMBER(1e38, 38, 0)",
            "INSERT INTO dn SELECT x FROM dd");
        assertAnswer("REFUSED DML operation to table DN failed on column N with error: Decfloat out of representable "
                + "range, operation: TO_NUMBER(1e38, 38, 0)",
            "INSERT INTO dn VALUES (DECFLOAT '1e38')");
    }
}
