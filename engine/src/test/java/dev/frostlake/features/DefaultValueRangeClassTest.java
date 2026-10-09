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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A column DEFAULT too wide for its column fails the row with the storage class of the VALUE when the statement
 * leaves the column out — as a query's value does — and with the column's own class when DEFAULT is written in
 * the VALUES list, where it sits in the column's slot like any other item (live-verified).
 */
public class DefaultValueRangeClassTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private static String outOfRange(final String table, final String type, final String value) {
        return "DML operation to table " + table + " failed on column A with error: Number out of representable"
            + " range: type " + type + "{nullable}, value " + value;
    }

    @Test
    public void anOmittedColumnsDefaultIsClassedByItsValue() {
        engine.execute("CREATE TABLE dvr1 (a NUMBER(2,0) DEFAULT 1000, b INT)");
        assertEquals(outOfRange("DVR1", "FIXED[SB2](2,0)", "1000"), refusal("INSERT INTO dvr1 (b) VALUES (1)"));
        assertEquals(outOfRange("DVR1", "FIXED[SB2](2,0)", "1000"), refusal("INSERT INTO dvr1 (b) SELECT 1"));
        engine.execute("CREATE TABLE dvr2 (a NUMBER(5,0) DEFAULT 9223372036854775807, b INT)");
        assertEquals(outOfRange("DVR2", "FIXED[SB8](5,0)", "9223372036854775807"),
            refusal("INSERT INTO dvr2 (b) VALUES (1)"));
        engine.execute("CREATE TABLE dvr3 (a NUMBER(5,0) DEFAULT 12345678901234567890123, b INT)");
        assertEquals(outOfRange("DVR3", "FIXED[SB16](5,0)", "12345678901234567890123"),
            refusal("INSERT INTO dvr3 (b) VALUES (1), (2)"));
        engine.execute("CREATE TABLE dvr4 (a NUMBER(4,2) DEFAULT 123.456, b INT)");
        assertEquals(outOfRange("DVR4", "FIXED[SB2](4,2)", "123.456"), refusal("INSERT INTO dvr4 (b) VALUES (1)"));
        engine.execute("CREATE TABLE dvr5 (a NUMBER(2,0) DEFAULT -1000, b INT)");
        assertEquals(outOfRange("DVR5", "FIXED[SB2](2,0)", "-1000"), refusal("INSERT INTO dvr5 (b) VALUES (1)"));
    }

    @Test
    public void aDefaultWrittenInTheValuesListIsClassedByItsColumn() {
        engine.execute("CREATE TABLE dvr6 (a NUMBER(2,0) DEFAULT 1000, b INT)");
        assertEquals(outOfRange("DVR6", "FIXED[SB1](2,0)", "1000"),
            refusal("INSERT INTO dvr6 (a, b) VALUES (DEFAULT, 1)"));
        assertEquals(outOfRange("DVR6", "FIXED[SB1](2,0)", "1000"), refusal("INSERT INTO dvr6 VALUES (DEFAULT, 1)"));
        // A written value and a selected one, for contrast.
        engine.execute("CREATE TABLE dvr7 (a NUMBER(5,0), b INT)");
        assertEquals(outOfRange("DVR7", "FIXED[SB4](5,0)", "12345678901234567890123"),
            refusal("INSERT INTO dvr7 VALUES (12345678901234567890123, 1)"));
        assertEquals(outOfRange("DVR7", "FIXED[SB16](5,0)", "12345678901234567890123"),
            refusal("INSERT INTO dvr7 SELECT 12345678901234567890123, 1"));
    }

    @Test
    public void aMergesInsertBranchClassesTheDefaultByItsValue() {
        engine.execute("CREATE TABLE dvr8 (a NUMBER(2,0) DEFAULT 1000, b INT)");
        engine.execute("CREATE TABLE dvr_src (b INT)");
        engine.execute("INSERT INTO dvr_src VALUES (1)");
        assertEquals(outOfRange("DVR8", "FIXED[SB2](2,0)", "1000"), refusal(
            "MERGE INTO dvr8 USING dvr_src ON dvr8.b = -1 WHEN NOT MATCHED THEN INSERT (b) VALUES (dvr_src.b)"));
        assertEquals(outOfRange("DVR8", "FIXED[SB2](2,0)", "1000"), refusal("MERGE INTO dvr8 USING dvr_src"
            + " ON dvr8.b = -1 WHEN NOT MATCHED THEN INSERT (a, b) VALUES (DEFAULT, dvr_src.b)"));
    }
}
