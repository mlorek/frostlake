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

package dev.frostlake.ddl;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * A SQL function body's name the body's typing refuses is placed in the body's frame - the frame's parenthesis and the
 * blanks before the name - as the body's compile places one (live-verified): a parameter named DEFAULT is
 * {@code invalid identifier 'DEFAULT'} at position 2 of {@code $$ default + 1 $$}.
 */
public class SqlFunctionKeywordParameterPlaceTest extends MeasuredCellsTestSupport {

    @Test
    public void aDefaultParameterIsPlacedInTheFrame() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT)");
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION fk54(default INT) RETURNS INT AS $$ default + 1 $$",
                "REFUSED SQL compilation error: error line 1 at position 2 | invalid identifier 'DEFAULT'"},
        });
    }
}
