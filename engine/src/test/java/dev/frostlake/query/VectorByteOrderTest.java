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
 * VECTOR comparisons are byte-wise lexicographic over the elements' 32-bit encodings (documented: "Direct vector
 * comparisons (for example, v1 &lt; v2) are byte-wise lexicographic and, although deterministic, won't produce the
 * results that you might expect from number comparisons"), so an element with its sign bit set sorts after every
 * element without it: [-1] &lt; [1] is FALSE, in comparisons, in ORDER BY and in range filters alike. The engine
 * compared the elements as numbers. The order of the bytes within an element is unmeasured on live; values of
 * different signs order the same either way.
 */
public class VectorByteOrderTest extends MeasuredCellsTestSupport {

    private void setUpVectors() {
        engine.execute("CREATE OR REPLACE TABLE vbo (id INT, v VECTOR(INT, 1), f VECTOR(FLOAT, 1))");
        engine.execute("INSERT INTO vbo SELECT 1, [1]::VECTOR(INT, 1), [1]::VECTOR(FLOAT, 1)");
        engine.execute("INSERT INTO vbo SELECT 2, [-1]::VECTOR(INT, 1), [-1]::VECTOR(FLOAT, 1)");
        engine.execute("INSERT INTO vbo SELECT 3, [0]::VECTOR(INT, 1), [0]::VECTOR(FLOAT, 1)");
    }

    @Test
    public void aNegativeElementSortsAfterThePositiveOnes() {
        setUpVectors();
        assertAnswer("false|false", """
            SELECT [-1]::VECTOR(INT, 1) < [1]::VECTOR(INT, 1), [-1]::VECTOR(FLOAT, 1) < [1]::VECTOR(FLOAT, 1)""");
        assertAnswer("3 ;; 1 ;; 2", "SELECT id FROM vbo ORDER BY v");
        assertAnswer("3 ;; 1 ;; 2", "SELECT id FROM vbo ORDER BY f");
        assertAnswer("1 ;; 2", "SELECT id FROM vbo WHERE f > [0]::VECTOR(FLOAT, 1) ORDER BY id");
    }

    @Test
    public void elementsOfOneSignKeepTheirOrder() {
        assertAnswer("true|true", """
            SELECT [1, 5]::VECTOR(INT, 2) < [2, 0]::VECTOR(INT, 2), [0.5]::VECTOR(FLOAT, 1) < [2]::VECTOR(FLOAT, 1)""");
    }
}
