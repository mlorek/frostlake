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

package dev.frostlake.expressions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Whether a BINARY stays a BINARY through the constructs that pass values along. Frostlake used to type
 * every one of these TEXT(16777216) — not a wrong cell but a wrong FAMILY, so a view over
 * {@code IFF(c, b, b)} declared a VARCHAR column where live declares a BINARY.
 *
 * <p>Three shapes, each with its own rule, all live-measured:
 *
 * <ul>
 *   <li>a CONDITIONAL takes the WIDEST branch, and is fixed only when EVERY branch is;</li>
 *   <li>an aggregate or window function that hands its argument back keeps that argument's type
 *       exactly — width and spelling included;</li>
 *   <li>CONCAT sums what it joins, while SUBSTR and the trimming pair keep the INPUT's width and do
 *       not narrow to the length asked for.</li>
 * </ul>
 *
 * <p>The conditional rule is NOT the union's rule, which is the trap this class records: over a
 * BINARY(4) beside a VARBINARY(4) a UNION answers fixed true and IFF answers false.
 */
public class BinaryFamilyThroughConstructsTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE bfc (b4 BINARY(4), b100 BINARY(100), vb VARBINARY(4), n NUMBER)");
        engine.execute("INSERT INTO bfc VALUES (TO_BINARY('41424344'), TO_BINARY('4142'),"
            + " TO_BINARY('4142'), 1)");
    }

    private String declaredType(final String expression) {
        engine.execute("CREATE OR REPLACE VIEW bfc_v AS SELECT " + expression + " AS c FROM bfc");
        final ResultSet rs = engine.executeQuery("SHOW COLUMNS IN TABLE test_db.test_schema.bfc_v");
        rs.next();
        return String.valueOf(rs.getValue("data_type"));
    }

    private void assertBinary(final int width, final boolean fixed, final String expression) {
        assertEquals("{\"type\":\"BINARY\",\"length\":" + width + ",\"byteLength\":" + width
            + ",\"nullable\":true,\"fixed\":" + fixed + "}", declaredType(expression), expression);
    }

    /** A conditional takes the widest branch. */
    @Test
    public void aConditionalTakesItsWidestBranch() {
        assertBinary(4, true, "IFF(n = 1, b4, b4)");
        assertBinary(100, true, "IFF(n = 1, b4, b100)");
        assertBinary(4, true, "COALESCE(b4, b4)");
        assertBinary(100, true, "COALESCE(b4, b100)");
        assertBinary(4, true, "NVL(b4, b4)");
        assertBinary(100, true, "IFNULL(b4, b100)");
        assertBinary(100, true, "GREATEST(b4, b100)");
        assertBinary(100, true, "LEAST(b4, b100)");
    }

    /** CASE the same, however many branches it has. */
    @Test
    public void aCaseExpressionTakesItsWidestBranchToo() {
        assertBinary(4, true, "CASE WHEN n = 1 THEN b4 ELSE b4 END");
        assertBinary(100, true, "CASE WHEN n = 1 THEN b4 ELSE b100 END");
        assertBinary(100, false,
            "CASE WHEN n = 1 THEN b4 WHEN n = 2 THEN b100 ELSE b4 || b4 END");
    }

    /**
     * And it is fixed only when EVERY branch is — which is where the conditionals and a UNION part
     * company: the same BINARY(4)-beside-VARBINARY(4) pair is fixed through a union and not here.
     */
    @Test
    public void aConditionalIsFixedOnlyWhenEveryBranchIs() {
        assertBinary(4, false, "IFF(n = 1, b4, vb)");
        assertBinary(8, false, "IFF(n = 1, b4, b4 || b4)");
    }

    /** An aggregate that hands its argument back hands its TYPE back. */
    @Test
    public void anAggregateKeepsItsArgumentsType() {
        assertBinary(4, true, "MAX(b4)");
        assertBinary(100, true, "MIN(b100)");
        assertBinary(8, false, "MAX(b4 || b4)");
        assertBinary(4, true, "ANY_VALUE(b4)");
    }

    /** So does a window function — including the three siblings of the pair first measured. */
    @Test
    public void aWindowFunctionKeepsItsArgumentsType() {
        assertBinary(4, true, "FIRST_VALUE(b4) OVER (ORDER BY n)");
        assertBinary(4, true, "LAST_VALUE(b4) OVER (ORDER BY n)");
        assertBinary(4, true, "LAG(b4) OVER (ORDER BY n)");
        assertBinary(4, true, "LEAD(b4) OVER (ORDER BY n)");
        assertBinary(100, true, "FIRST_VALUE(b100) OVER (ORDER BY n)");
        assertBinary(8, false, "LAG(b4 || b4) OVER (ORDER BY n)");
    }

    /** CONCAT sums its arguments, exactly as the operator spelling does. */
    @Test
    public void concatSumsWhatItJoins() {
        assertBinary(8, false, "CONCAT(b4, b4)");
        assertBinary(108, false, "CONCAT(b4, b4, b100)");
        assertBinary(8, false, "b4 || b4");
    }

    /** SUBSTR and the trimming pair keep the INPUT's width — they do not narrow to what was asked. */
    @Test
    public void substringKeepsTheInputsWidth() {
        assertBinary(4, false, "SUBSTR(b4, 1, 2)");
        assertBinary(4, false, "SUBSTR(b4, 2)");
        assertBinary(100, false, "SUBSTR(b100, 1, 3)");
        assertBinary(4, false, "SUBSTRING(b4, 1, 2)");
        assertBinary(4, false, "LEFT(b4, 2)");
        assertBinary(4, false, "RIGHT(b4, 2)");
    }

    /** REVERSE is the one that keeps the fixed spelling as well as the width. */
    @Test
    public void reverseKeepsTheWholeType() {
        assertBinary(4, true, "REVERSE(b4)");
        assertBinary(100, true, "REVERSE(b100)");
    }
}
