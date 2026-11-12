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
 * The merged key of a FULL USING/NATURAL join carries either side's value, so it is declared in the type both sides'
 * keys fold to, as COALESCE(a.k, b.k) is, and shows its values at that type's scale; a RIGHT join's key is the right
 * side's and takes its type, and an INNER or a LEFT join's keeps the left side's (live-verified). It kept the left
 * column's type: a NUMBER(10,2) key held 123456789012345 and a VARCHAR(5) key an 18-character string, and a table
 * created from the join stored them under those types.
 */
public class OuterJoinMergedKeyTypeTest extends MeasuredCellsTestSupport {

    private void setUpTables() {
        engine.execute("CREATE OR REPLACE TABLE mka (k NUMBER(10,2), s VARCHAR(5))");
        engine.execute("CREATE OR REPLACE TABLE mkb (k INT, s VARCHAR(20))");
        engine.execute("INSERT INTO mka VALUES (1.00, 'x')");
        engine.execute("INSERT INTO mkb VALUES (123456789012345, 'a long string here')");
    }

    @Test
    public void theMergedKeyHoldsBothSidesValues() {
        setUpTables();
        assertShape("K:NUMBER(38,2), S:VARCHAR(20)", "SELECT * FROM mka FULL JOIN mkb USING (k, s)");
        assertAnswer("1.00|x ;; 123456789012345.00|a long string here",
            "SELECT k, s FROM mka FULL JOIN mkb USING (k, s) ORDER BY k");
        // A RIGHT join's key is the right side's, a LEFT or an INNER join's the left side's.
        assertShape("K:NUMBER(38,0)", "SELECT k FROM mka RIGHT JOIN mkb USING (k)");
        assertAnswer("123456789012345", "SELECT k FROM mka RIGHT JOIN mkb USING (k)");
        assertShape("K:NUMBER(10,2)", "SELECT k FROM mka LEFT JOIN mkb USING (k)");
        assertShape("K:NUMBER(10,2)", "SELECT k FROM mkb RIGHT JOIN mka USING (k)");
        assertShape("K:NUMBER(38,0)", "SELECT k FROM mkb LEFT JOIN mka USING (k)");
        assertShape("K:NUMBER(10,2)", "SELECT k FROM mka INNER JOIN mkb USING (k)");
        assertShape("S:VARCHAR(20)", "SELECT s FROM mka RIGHT JOIN mkb USING (s)");
        assertShape("S:VARCHAR(5)", "SELECT s FROM mka LEFT JOIN mkb USING (s)");
        assertShape("K:NUMBER(38,2), S:VARCHAR(20)", "SELECT * FROM mka NATURAL FULL JOIN mkb");
    }

    @Test
    public void aTableCreatedFromTheJoinTakesTheWiderTypes() {
        setUpTables();
        engine.execute("CREATE OR REPLACE TABLE mkc AS SELECT * FROM mka FULL JOIN mkb USING (k, s)");
        assertShape("K:NUMBER(38,2), S:VARCHAR(20)", "SELECT * FROM mkc");
        assertAnswer("1.00|x ;; 123456789012345.00|a long string here", "SELECT k, s FROM mkc ORDER BY k");
        engine.execute("INSERT INTO mkc VALUES (123456789012345, 'a long string here')");
        assertAnswer("3", "SELECT COUNT(*) FROM mkc");
    }
}
