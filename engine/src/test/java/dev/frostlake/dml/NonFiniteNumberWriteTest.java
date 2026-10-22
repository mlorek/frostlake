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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A FLOAT NaN or infinity written into an exact NUMBER column is refused, as a finite FLOAT past the column's
 * range is, in the sentence the cast spells a non-finite with ({@code nan}, {@code inf}, {@code -inf}) and with the
 * column's own type in it. INSERT … VALUES, INSERT … SELECT and UPDATE used to store the Double as it was, so an
 * INT column answered NaN and its SUM did too, while CTAS and the cast refused the same values.
 */
public class NonFiniteNumberWriteTest extends MeasuredCellsTestSupport {

    private static final String NI_REFUSED = "REFUSED DML operation to table NFW_NI failed on column N with error: "
        + "Number out of representable range: type FIXED[SB16](38,0){nullable}, value ";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE nfw_fl (f FLOAT)");
        engine.execute("INSERT INTO nfw_fl VALUES ('NaN')");
        engine.execute("CREATE TABLE nfw_ni (n INT)");
    }

    @Test
    public void anInsertRefusesANonFiniteFloatInANumberColumn() {
        assertAnswer(NI_REFUSED + "nan", "INSERT INTO nfw_ni SELECT f FROM nfw_fl");
        assertAnswer(NI_REFUSED + "nan", "INSERT INTO nfw_ni VALUES ('NaN'::FLOAT)");
        assertAnswer(NI_REFUSED + "inf", "INSERT INTO nfw_ni SELECT 'inf'::FLOAT");
        assertAnswer(NI_REFUSED + "-inf", "INSERT INTO nfw_ni VALUES ('-inf'::FLOAT)");
        assertAnswer("0|null|null", "SELECT COUNT(*), SUM(n), MAX(n) FROM nfw_ni");
        // The column's own width and scale are what the sentence prints, nullable when the column or the value's
        // source is: a nullable FLOAT column's NaN is {nullable} in a NOT NULL column, a literal's {not null}.
        engine.execute("CREATE TABLE nfw_nd (n NUMBER(10,2) NOT NULL)");
        assertAnswer("REFUSED DML operation to table NFW_ND failed on column N with error: "
            + "Number out of representable range: type FIXED[SB8](10,2){nullable}, value nan",
            "INSERT INTO nfw_nd SELECT f FROM nfw_fl");
        assertAnswer("REFUSED DML operation to table NFW_ND failed on column N with error: "
            + "Number out of representable range: type FIXED[SB8](10,2){not null}, value inf",
            "INSERT INTO nfw_nd SELECT 'inf'::FLOAT");
        // A FLOAT column takes them as before.
        engine.execute("CREATE TABLE nfw_f2 (f FLOAT)");
        assertAnswer("1", "INSERT INTO nfw_f2 SELECT 'inf'::FLOAT");
    }

    @Test
    public void anUpdateRefusesANonFiniteFloatInANumberColumn() {
        engine.execute("INSERT INTO nfw_ni VALUES (1)");
        assertAnswer(NI_REFUSED + "nan", "UPDATE nfw_ni SET n = 'NaN'::FLOAT");
        assertAnswer(NI_REFUSED + "inf", "UPDATE nfw_ni SET n = (SELECT 'inf'::FLOAT)");
        assertAnswer("1", "SELECT n FROM nfw_ni");
    }

    @Test
    public void aMergeRefusesANonFiniteFloatInANumberColumn() {
        engine.execute("INSERT INTO nfw_ni VALUES (1)");
        assertAnswer(NI_REFUSED + "nan", "MERGE INTO nfw_ni USING nfw_fl ON TRUE WHEN MATCHED THEN UPDATE SET n = f");
        assertAnswer(NI_REFUSED + "nan",
            "MERGE INTO nfw_ni USING nfw_fl ON FALSE WHEN NOT MATCHED THEN INSERT (n) VALUES (f)");
        assertAnswer("1", "SELECT n FROM nfw_ni");
    }
}
