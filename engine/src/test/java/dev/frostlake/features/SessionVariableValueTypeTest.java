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
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A session variable is typed by the value it holds: its static type takes part in every compile-time rule —
 * {@code $sv = CURRENT_DATE()} over a variable holding 1 is refused as {@code 1 = CURRENT_DATE()} is — its
 * storage tag follows that one value as a literal's does, a timestamp keeps the flavour it was set to and an
 * empty text is one character wide. Live-verified.
 */
public class SessionVariableValueTypeTest extends BaseDatabaseTest {

    private String scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        assertEquals(1, rs.getRowCount(), sql);
        return String.valueOf(rs.getRows().get(0).getValue(0));
    }

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        return refused.getMessage();
    }

    @Test
    public void theVariablesTypeTakesPartInTheComparisonRule() {
        engine.execute("SET sv = 1");
        assertEquals("SQL compilation error:\nCan not convert parameter 'CURRENT_DATE()' of type [DATE] into expected "
            + "type [NUMBER(1,0)]", refusal("SELECT $sv = CURRENT_DATE() AS r"));
        assertEquals("SQL compilation error:\nCan not convert parameter '$SV' of type [NUMBER(1,0)] into expected "
            + "type [DATE]", refusal("SELECT CURRENT_DATE() = $sv AS r"));
        assertEquals("true", scalar("SELECT $sv = 1 AS r"));
        engine.execute("SET sv3 = 1.5");
        assertEquals("SQL compilation error:\nCan not convert parameter 'CURRENT_DATE()' of type [DATE] into expected "
            + "type [NUMBER(2,1)]", refusal("SELECT $sv3 = CURRENT_DATE() AS r"));
        engine.execute("SET sv4 = CURRENT_DATE()");
        assertEquals("SQL compilation error:\nCan not convert parameter '1' of type [NUMBER(1,0)] into expected "
            + "type [DATE]", refusal("SELECT $sv4 = 1 AS r"));
        engine.execute("SET sv5 = (SELECT 1)");
        assertEquals("SQL compilation error:\nCan not convert parameter 'CURRENT_DATE()' of type [DATE] into expected "
            + "type [NUMBER(1,0)]", refusal("SELECT $sv5 = CURRENT_DATE() AS r"));
    }

    @Test
    public void theStorageTagFollowsTheValueHeld() {
        engine.execute("SET n1 = 12345");
        engine.execute("SET n2 = 0.001");
        engine.execute("SET n3 = 127");
        engine.execute("SET n4 = 128");
        engine.execute("SET n5 = 32767");
        engine.execute("SET n6 = 32768");
        engine.execute("SET n7 = -129");
        assertEquals("NUMBER(5,0)[SB2]", scalar("SELECT SYSTEM$TYPEOF($n1) AS r"));
        assertEquals("NUMBER(4,3)[SB1]", scalar("SELECT SYSTEM$TYPEOF($n2) AS r"));
        assertEquals("NUMBER(3,0)[SB1]", scalar("SELECT SYSTEM$TYPEOF($n3) AS r"));
        assertEquals("NUMBER(3,0)[SB2]", scalar("SELECT SYSTEM$TYPEOF($n4) AS r"));
        assertEquals("NUMBER(5,0)[SB2]", scalar("SELECT SYSTEM$TYPEOF($n5) AS r"));
        assertEquals("NUMBER(5,0)[SB4]", scalar("SELECT SYSTEM$TYPEOF($n6) AS r"));
        assertEquals("NUMBER(3,0)[SB2]", scalar("SELECT SYSTEM$TYPEOF($n7) AS r"));
    }

    @Test
    public void aTimestampKeepsItsFlavourAndAnEmptyTextIsOneWide() {
        engine.execute("SET t1 = CURRENT_TIMESTAMP()");
        engine.execute("SET t2 = '2020-01-01 10:00:00'::TIMESTAMP_TZ");
        engine.execute("SET t3 = '2020-01-01 10:00:00'::TIMESTAMP_LTZ");
        engine.execute("SET t4 = '2020-01-01 10:00:00'::TIMESTAMP_NTZ");
        assertEquals("TIMESTAMP_LTZ(9)[SB16]", scalar("SELECT SYSTEM$TYPEOF($t1) AS r"));
        assertEquals("TIMESTAMP_TZ(9)[SB16]", scalar("SELECT SYSTEM$TYPEOF($t2) AS r"));
        assertEquals("TIMESTAMP_LTZ(9)[SB16]", scalar("SELECT SYSTEM$TYPEOF($t3) AS r"));
        assertEquals("TIMESTAMP_NTZ(9)[SB16]", scalar("SELECT SYSTEM$TYPEOF($t4) AS r"));
        assertEquals("SQL compilation error:\nCan not convert parameter '1' of type [NUMBER(1,0)] into expected "
            + "type [TIMESTAMP_LTZ(9)]", refusal("SELECT $t1 = 1 AS r"));
        engine.execute("SET e = ''");
        assertEquals("VARCHAR(1)[LOB]", scalar("SELECT SYSTEM$TYPEOF($e) AS r"));
    }
}
