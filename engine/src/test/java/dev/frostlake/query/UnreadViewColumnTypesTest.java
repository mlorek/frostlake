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

package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * A read of a view that keeps no row - a false WHERE, a LIMIT 0 - types its columns as the view's body plans
 * them now, as a read of its rows does, not as the view stored them (all live-verified).
 */
public class UnreadViewColumnTypesTest extends MeasuredCellsTestSupport {

    @Test
    public void columnTypes() {
        engine.execute("CREATE OR REPLACE VIEW vw684 AS SELECT CAST(NULL AS VARCHAR) AS s");
        engine.execute("CREATE OR REPLACE VIEW vw684b AS SELECT 'abc'::VARCHAR(10) AS s, 1 AS n");
        engine.execute("CREATE OR REPLACE TABLE tv684 (s VARCHAR(5))");
        engine.execute("CREATE OR REPLACE VIEW vw684c AS SELECT s FROM tv684");
        engine.execute("CREATE OR REPLACE VIEW vw684d AS SELECT s || 'x' AS s FROM tv684");
        engine.execute("CREATE OR REPLACE VIEW vw684e AS SELECT CAST(NULL AS NUMBER) AS n, NULL AS z");
        assertShape("S:VARCHAR(134217728)", "SELECT * FROM vw684");
        assertShape("S:VARCHAR(134217728)", "SELECT * FROM vw684 WHERE 1 = 0");
        assertShape("S:VARCHAR(134217728)", "SELECT * FROM vw684 LIMIT 0");
        assertShape("S:VARCHAR(134217728)", "SELECT s FROM vw684 WHERE FALSE");
        assertShape("S:VARCHAR(10), N:NUMBER(1,0)", "SELECT * FROM vw684b");
        assertShape("S:VARCHAR(10), N:NUMBER(1,0)", "SELECT * FROM vw684b WHERE FALSE");
        assertShape("S:VARCHAR(5)", "SELECT * FROM vw684c WHERE FALSE");
        assertShape("S:VARCHAR(6)", "SELECT * FROM vw684d");
        assertShape("S:VARCHAR(6)", "SELECT * FROM vw684d WHERE FALSE");
        assertShape("N:NUMBER(38,0), Z:VARCHAR(0)", "SELECT * FROM vw684e");
        assertShape("N:NUMBER(38,0), Z:VARCHAR(0)", "SELECT * FROM vw684e WHERE FALSE");
        assertShape("S:VARCHAR(134217728)", "SELECT * FROM (SELECT CAST(NULL AS VARCHAR) AS s) WHERE FALSE");
        assertShape("S:VARCHAR(134217728)", "SELECT s FROM vw684 WHERE s = 'x'");
        assertAnswer("S|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null", "DESCRIBE VIEW vw684");
    }
}
