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

package dev.frostlake.dml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A TIMESTAMP_TZ written into a TIMESTAMP_LTZ or a TIMESTAMP_NTZ column is refused while the statement compiles,
 * where Frostlake wrote it: the one pair inside the timestamp family that live refuses, whatever writes it — an
 * INSERT from VALUES or a query, an UPDATE, a MERGE branch reading its own table, a typed NULL — and a column-listed
 * CTAS in its own sentence. Every other flavour pair is written (live-verified).
 */
public class TimestampTzColumnMatchTest extends BaseDatabaseTest {

    private static final String INTO_LTZ = "SQL compilation error:\nExpression type does not match column data type, "
        + "expecting TIMESTAMP_LTZ(9) but got TIMESTAMP_TZ(9) for column LTZ";
    private static final String INTO_NTZ = "SQL compilation error:\nExpression type does not match column data type, "
        + "expecting TIMESTAMP_NTZ(9) but got TIMESTAMP_TZ(9) for column NTZ";
    private static final String TZ = "'2026-08-13 12:34:56 +02:00'::TIMESTAMP_TZ";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE it (ltz TIMESTAMP_LTZ, tz TIMESTAMP_TZ, ntz TIMESTAMP_NTZ, d DATE)");
        engine.execute("""
            INSERT INTO it VALUES ('2026-08-13 12:34:56', '2026-08-13 12:34:56 +02:00', '2026-08-13 12:34:56',
                '2026-08-13')""");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
    }

    @Test
    public void aZonedTimestampIntoAnUnzonedColumnIsRefused() {
        assertEquals(INTO_LTZ, refusal("INSERT INTO it (ltz) VALUES (" + TZ + ")"));
        assertEquals(INTO_LTZ, refusal("INSERT INTO it (ltz) SELECT " + TZ));
        assertEquals(INTO_LTZ, refusal("INSERT INTO it (ltz) SELECT tz FROM it"));
        assertEquals(INTO_LTZ, refusal("UPDATE it SET ltz = " + TZ));
        assertEquals(INTO_LTZ, refusal("INSERT INTO it (ltz) VALUES (TO_TIMESTAMP_TZ('2026-08-13 12:34:56 +02:00'))"));
        assertEquals(INTO_LTZ, refusal("INSERT INTO it (ltz) SELECT NULL::TIMESTAMP_TZ"));
        assertEquals(INTO_LTZ, refusal("UPDATE it SET ltz = NULL::TIMESTAMP_TZ"));
        assertEquals(INTO_LTZ, refusal("INSERT INTO it (ltz) SELECT IFF(TRUE, tz, tz) FROM it"));
        assertEquals(INTO_LTZ, refusal("INSERT OVERWRITE INTO it (ltz) SELECT tz FROM it"));
        assertEquals(INTO_LTZ, refusal("MERGE INTO it USING it s ON TRUE WHEN MATCHED THEN UPDATE SET ltz = s.tz"));
        assertEquals(INTO_LTZ, refusal("MERGE INTO it USING it s ON FALSE WHEN NOT MATCHED THEN INSERT (ltz) VALUES (s.tz)"));
        assertEquals(INTO_NTZ, refusal("INSERT INTO it (ntz) VALUES (" + TZ + ")"));
        assertEquals(INTO_NTZ, refusal("INSERT INTO it (ntz) SELECT tz FROM it"));
        assertEquals(INTO_NTZ, refusal("UPDATE it SET ntz = tz"));
        assertEquals("SQL compilation error:\nincompatible types: [TIMESTAMP_TZ(9)] and [TIMESTAMP_LTZ(9)]",
            refusal("CREATE TABLE ct (c TIMESTAMP_LTZ) AS SELECT " + TZ));
    }

    @Test
    public void everyOtherFlavourPairIsWritten() {
        engine.execute("INSERT INTO it (ltz) VALUES ('2026-08-13 12:34:56'::TIMESTAMP_NTZ)");
        engine.execute("INSERT INTO it (ltz) SELECT ntz FROM it WHERE ntz IS NOT NULL");
        engine.execute("INSERT INTO it (tz) SELECT ltz FROM it WHERE ltz IS NOT NULL");
        engine.execute("INSERT INTO it (ntz) SELECT ltz FROM it WHERE ltz IS NOT NULL");
        engine.execute("INSERT INTO it (tz) SELECT ntz FROM it WHERE ntz IS NOT NULL");
        engine.execute("INSERT INTO it (ltz) SELECT d FROM it WHERE d IS NOT NULL");
        engine.execute("INSERT INTO it (d) SELECT ltz FROM it WHERE ltz IS NOT NULL");
        engine.execute("INSERT INTO it (d) SELECT tz FROM it WHERE tz IS NOT NULL");
        engine.execute("INSERT INTO it (ltz) VALUES ('2026-08-13 12:34:56 +02:00')");
        engine.execute("INSERT INTO it (ltz) VALUES (CURRENT_TIMESTAMP())");
        engine.execute("UPDATE it SET d = tz");
        assertEquals(1L, ((Number) engine.executeQuery("SELECT COUNT(*) FROM it WHERE tz IS NOT NULL AND ltz IS NOT NULL")
            .getRows().get(0).getValue(0)).longValue());
    }
}
