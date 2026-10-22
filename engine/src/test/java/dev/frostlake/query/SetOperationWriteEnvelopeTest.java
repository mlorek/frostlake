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
 * Where a text beside a NUMBER is converted decides how a write refuses it. A UNION ALL arm's value converts as the
 * row is read: a plain SELECT refuses it bare, a CTAS or an INSERT ... SELECT inside its DML sentence naming the
 * column. GREATEST and LEAST, and a UNION's duplicate check, convert while the query compares, so every statement
 * refuses them bare; COALESCE and IFF convert only the branch they take (all live-verified).
 */
public class SetOperationWriteEnvelopeTest extends MeasuredCellsTestSupport {

    @Test
    public void whereATextConvertsDecidesTheWritesSentence() {
        assertAnswer("Table T successfully created.", "CREATE TABLE t (id NUMBER(38,0))");
        assertAnswer("1", "INSERT INTO t VALUES (5)");
        assertAnswer("REFUSED Numeric value '99999999999999999999999999999999999999.5' is not recognized",
            "SELECT id FROM t UNION ALL SELECT '99999999999999999999999999999999999999.5'");
        assertAnswer("REFUSED DML operation to table TEST_DB.TEST_SCHEMA.W failed on column ID with error: Numeric "
                + "value '99999999999999999999999999999999999999.5' is not recognized",
            "CREATE TABLE w AS SELECT id FROM t UNION ALL SELECT '99999999999999999999999999999999999999.5'");
        assertAnswer("Table W2 successfully created.", "CREATE TABLE w2 (id NUMBER(38,1))");
        assertAnswer("REFUSED DML operation to table W2 failed on column ID with error: Numeric value "
                + "'99999999999999999999999999999999999999.5' is not recognized",
            "INSERT INTO w2 SELECT id FROM t UNION ALL SELECT '99999999999999999999999999999999999999.5'");
        assertAnswer("REFUSED Numeric value '99999999999999999999999999999999999999.5' is not recognized",
            "SELECT GREATEST(id, '99999999999999999999999999999999999999.5') FROM t");
        assertAnswer("REFUSED Numeric value '99999999999999999999999999999999999999.5' is not recognized",
            "CREATE TABLE g AS SELECT GREATEST(id, '99999999999999999999999999999999999999.5') g FROM t");
        assertAnswer("REFUSED Numeric value '99999999999999999999999999999999999999.5' is not recognized",
            "INSERT INTO w2 SELECT GREATEST(id, '99999999999999999999999999999999999999.5') FROM t");
        assertAnswer("REFUSED DML operation to table TEST_DB.TEST_SCHEMA.W3 failed on column ID with error: Numeric "
                + "value 'abc' is not recognized",
            "CREATE TABLE w3 AS SELECT id FROM t UNION ALL SELECT 'abc'");
        assertAnswer("REFUSED Numeric value '99999999999999999999999999999999999999.5' is not recognized",
            "CREATE TABLE w4 AS SELECT id FROM t UNION SELECT '99999999999999999999999999999999999999.5'");
        assertAnswer("REFUSED DML operation to table TEST_DB.TEST_SCHEMA.W5 failed on column X with error: Numeric "
                + "value '99999999999999999999999999999999999999.5' is not recognized",
            "CREATE TABLE w5 AS SELECT '99999999999999999999999999999999999999.5' x UNION ALL SELECT id FROM t");
        assertAnswer("REFUSED Numeric value 'abc' is not recognized",
            "CREATE TABLE g2 AS SELECT LEAST(id, 'abc') g FROM t");
        assertAnswer("Table C1 successfully created.",
            "CREATE TABLE c1 AS SELECT COALESCE(id, '99999999999999999999999999999999999999.5') c FROM t");
        assertAnswer("Table I1 successfully created.",
            "CREATE TABLE i1 AS SELECT IFF(id > 0, id, '99999999999999999999999999999999999999.5') c FROM t");
    }
}
