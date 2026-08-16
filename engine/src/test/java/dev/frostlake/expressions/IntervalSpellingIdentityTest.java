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
 * The two INTERVAL spellings are DIFFERENT EXPRESSIONS, and anything that identifies an expression has
 * to keep them apart.
 *
 * <p>{@code d + INTERVAL '1' DAY} and {@code d + INTERVAL '1 day'} shift a DATE by the same amount to
 * different types — a timestamp and a date. Frostlake keys aggregate values inside one select item by
 * the expression's canonical print, and that print used to drop which spelling carried the unit, so the
 * two aggregates below collided and the second one's value was rendered for both.
 */
public class IntervalSpellingIdentityTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE isp (d DATE, k NUMBER)");
        engine.execute("INSERT INTO isp VALUES ('2026-01-01', 1)");
    }

    private String first(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    /** Side by side as plain items, which never collided: one promotes, one does not. */
    @Test
    public void theTwoSpellingsProduceDifferentTypes() {
        final ResultSet rs = engine.executeQuery(
            "SELECT d + INTERVAL '1' DAY AS a, d + INTERVAL '1 day' AS b FROM isp");
        rs.next();
        assertEquals("2026-01-02T00:00", String.valueOf(rs.getValue("a")), "the keyword form promotes");
        assertEquals("2026-01-02", String.valueOf(rs.getValue("b")), "the in-string form stays a DATE");
    }

    /**
     * Both spellings inside ONE select item, which is where the collision was reachable: the aggregates
     * are keyed by canonical print and looked up by the same key while the surrounding expression is
     * evaluated. The rendered halves must differ — a timestamp beside a date.
     */
    @Test
    public void twoAggregatesOverTheSameUnitDoNotShareOneValue() {
        assertEquals("2026-01-02 00:00:00.000/2026-01-02",
            first("SELECT MAX(d + INTERVAL '1' DAY) || '/' || MAX(d + INTERVAL '1 day') AS c"
                + " FROM isp GROUP BY k"));
    }

    /** The same pair as separate items has always worked, and must keep working. */
    @Test
    public void thePairAsSeparateItemsStillDiffers() {
        final ResultSet rs = engine.executeQuery(
            "SELECT MAX(d + INTERVAL '1' DAY) AS a, MAX(d + INTERVAL '1 day') AS b FROM isp GROUP BY k");
        rs.next();
        assertEquals("2026-01-02T00:00", String.valueOf(rs.getValue("a")));
        assertEquals("2026-01-02", String.valueOf(rs.getValue("b")));
    }

    /** And the same expression written twice still shares its value — the key stayed coarse enough. */
    @Test
    public void theSameSpellingTwiceStillSharesOneValue() {
        assertEquals("2026-01-02/2026-01-02",
            first("SELECT MAX(d + INTERVAL '1 day') || '/' || MAX(d + INTERVAL '1 day') AS c"
                + " FROM isp GROUP BY k"));
    }
}
