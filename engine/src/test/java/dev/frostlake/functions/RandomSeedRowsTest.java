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
package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A seeded RANDOM answers a different value on each row and the same value for every call with that seed in one
 * row, as its page documents ("RANDOM returns a different value for each row, even though the seed is the same"):
 * a seeded generator varies UNIFORM, NORMAL and RANDSTR row by row, a seeded filter keeps about half of the rows,
 * and each UNION ALL arm draws the same sequence again (live-verified).
 */
public class RandomSeedRowsTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t AS SELECT SEQ4() AS i FROM TABLE(GENERATOR(ROWCOUNT => 200))");
    }

    /** Every cell of the first row, joined. */
    private String row(final String sql) {
        final Row row = engine.executeQuery(sql).getRows().get(0);
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < row.getValues().size(); i++) {
            out.append(i > 0 ? "," : "").append(row.getValue(i));
        }
        return out.toString();
    }

    @Test
    public void eachRowDrawsItsOwnValue() {
        assertEquals("3,3", row("SELECT COUNT(DISTINCT r1), SUM(IFF(r1 = r2, 1, 0)) FROM (SELECT RANDOM(4711) AS r1,"
            + " RANDOM(4711) AS r2 FROM TABLE(GENERATOR(ROWCOUNT => 3)))"));
        assertEquals("200,200",
            row("SELECT COUNT(DISTINCT RANDOM(7)), COUNT(DISTINCT NORMAL(0, 1, RANDOM(7))) FROM t"));
        assertEquals("1", row("SELECT COUNT(DISTINCT RANDOM(7) = RANDOM(7)) FROM t"));
        assertEquals("true", row("SELECT COUNT(DISTINCT RANDOM(7) - RANDOM(8)) > 1 FROM t"));
        assertEquals("true", row("SELECT COUNT(DISTINCT UNIFORM(1, 1000000, RANDOM(3))) > 150 FROM t"));
        assertEquals("true", row("SELECT COUNT(DISTINCT RANDSTR(10, RANDOM(3))) > 150 FROM t"));
    }

    @Test
    public void aSeededFilterKeepsAboutHalf() {
        assertEquals("true", row("SELECT COUNT(*) BETWEEN 60 AND 140 FROM t WHERE UNIFORM(0, 9, RANDOM(42)) < 5"));
    }

    @Test
    public void eachUnionArmDrawsTheSameSequence() {
        assertEquals("200", row("SELECT COUNT(DISTINCT x) FROM (SELECT RANDOM(5) x FROM t UNION ALL"
            + " SELECT RANDOM(5) FROM t)"));
    }

    @Test
    public void aSeedMustBeAConstant() {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT COUNT(DISTINCT RANDOM(i)) FROM t");
            }
        });
        assertTrue(String.valueOf(refused.getMessage())
            .contains("argument 1 to function RANDOM needs to be constant, found 'T.I'"), refused.getMessage());
    }
}
