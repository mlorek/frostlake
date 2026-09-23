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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An unaliased braced star is named after its own text, upper-cased, with its modifier echoed AS
 * WRITTEN — a bare EXCLUDE list stays bare and a parenthesised one keeps its parentheses. A client
 * reading the column by name misses it otherwise.
 */
public class BracedStarLabelTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b VARCHAR(20))");
        engine.execute("INSERT INTO fz VALUES (5, 'x'), (7, 'y')");
    }

    /** The name of the first column. */
    private String label(final String sql) {
        return engine.executeQuery(sql).getColumns().get(0).getName();
    }

    /** A bare EXCLUDE list is echoed bare, a parenthesised one parenthesised. */
    @Test
    public void theModifierIsEchoedAsWritten() {
        assertEquals("{FZ.* EXCLUDE B}", label("SELECT {fz.* EXCLUDE b} FROM fz ORDER BY 1"));
        assertEquals("{FZ.* EXCLUDE (B)}", label("SELECT {fz.* EXCLUDE (b)} FROM fz ORDER BY 1"));
    }

    /** A braced star with no modifier is its own text. */
    @Test
    public void aPlainBracedStarIsItsOwnText() {
        assertEquals("{FZ.*}", label("SELECT {fz.*} FROM fz ORDER BY 1"));
    }

    /** The values are the row as an OBJECT, whichever spelling named it. */
    @Test
    public void theValuesAreUnchanged() {
        final ResultSet rs = engine.executeQuery("SELECT {fz.* EXCLUDE b} FROM fz ORDER BY 1");
        rs.next();
        assertEquals("{\"ID\":5}", String.valueOf(rs.getValue(0)));
    }
}
