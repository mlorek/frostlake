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
 * A text beside a NUMBER in a set operation or in GREATEST and LEAST is held at the type the two fold to, and is
 * refused, as a cast of the text to that type is, when its digits do not fit: under the NUMBER(38,1) that a
 * NUMBER(38,0) and {@code '99999999999999999999999999999999999999.5'} fold to there is room for thirty-seven
 * integer digits, not thirty-eight. The text used to be measured against 10^38 alone, so a CTAS stored thirty-nine
 * digits in a NUMBER(38,1) and the next arithmetic over the column was refused.
 */
public class TextBesideNumberFoldWidthTest extends MeasuredCellsTestSupport {

    private static final String WIDE = "99999999999999999999999999999999999999.5";
    private static final String REFUSED = "REFUSED Numeric value '" + WIDE + "' is not recognized";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE tbn_t (id NUMBER(38,0))");
        engine.execute("INSERT INTO tbn_t VALUES (5)");
    }

    @Test
    public void aSetOperationRefusesATextPastItsUnifiedType() {
        assertAnswer(REFUSED, "SELECT id FROM tbn_t UNION ALL SELECT '" + WIDE + "'");
        assertAnswer(REFUSED, "SELECT id FROM tbn_t UNION SELECT '" + WIDE + "'");
        assertAnswer(REFUSED, "SELECT '" + WIDE + "' UNION ALL SELECT id FROM tbn_t");
        assertAnswer(REFUSED, "SELECT id FROM tbn_t INTERSECT SELECT '" + WIDE + "'");
        assertAnswer(REFUSED, "SELECT id FROM tbn_t EXCEPT SELECT '" + WIDE + "'");
        // A UNION ALL arm's value converts where the row is written, inside the write's DML sentence.
        assertAnswer("REFUSED DML operation to table TEST_DB.TEST_SCHEMA.TBN_W failed on column ID with error: "
            + REFUSED.substring("REFUSED ".length()),
            "CREATE TABLE tbn_w AS SELECT id FROM tbn_t UNION ALL SELECT '" + WIDE + "'");
        // A text past every NUMBER keeps its own sentence.
        assertAnswer("REFUSED Numeric value '1e39' is not recognized", "SELECT id FROM tbn_t UNION ALL SELECT '1e39'");
        assertAnswer("REFUSED Numeric value '1e38' is out of range", "SELECT id FROM tbn_t UNION ALL SELECT '1e38'");
    }

    @Test
    public void aSetOperationKeepsATextThatFitsItsUnifiedType() {
        assertAnswer("5.0 ;; 9999999999999999999999999999999999999.5",
            "SELECT id FROM tbn_t UNION ALL SELECT '9999999999999999999999999999999999999.5'");
        assertAnswer("5.0 ;; 123456789012.5", "SELECT id::NUMBER(10,0) FROM tbn_t UNION ALL SELECT '123456789012.5'");
    }

    @Test
    public void greatestAndLeastRefuseATextPastTheirFoldedType() {
        assertAnswer(REFUSED, "SELECT GREATEST(id, '" + WIDE + "') FROM tbn_t");
        assertAnswer(REFUSED, "SELECT GREATEST('" + WIDE + "', id) FROM tbn_t");
        assertAnswer(REFUSED, "SELECT LEAST(id, '" + WIDE + "') FROM tbn_t");
        assertAnswer(REFUSED, "SELECT GREATEST_IGNORE_NULLS(id, NULL, '" + WIDE + "') FROM tbn_t");
        assertAnswer(REFUSED, "SELECT LEAST_IGNORE_NULLS(id, NULL, '" + WIDE + "') FROM tbn_t");
        // GREATEST converts the text as it compares, inside the query, so a CTAS raises it bare.
        assertAnswer(REFUSED, "CREATE TABLE tbn_g AS SELECT GREATEST(id, '" + WIDE + "') g FROM tbn_t");
        // A text that fits, and one that spells no number, read as they did.
        assertAnswer("5.0|5.0", "SELECT GREATEST(id, '1.5'), LEAST(id, '9999999999999999999999999999999999999.5') "
            + "FROM tbn_t");
        assertAnswer("REFUSED Numeric value 'abc' is not recognized", "SELECT GREATEST(id, 'abc') FROM tbn_t");
    }
}
