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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * HASH_AGG declares the width HASH does — NUMBER(19,0), tagged [SB8] — and a table built over it stores that type
 * (live-verified). The hash values are this engine's own, so only their type is compared.
 */
public class HashAggTypeTest extends MeasuredCellsTestSupport {

    @Test
    public void hashAggDeclaresTheHashWidth() {
        try {
            assertCells(new String[][] {
                {"SELECT SYSTEM$TYPEOF(HASH_AGG(1)), SYSTEM$TYPEOF(HASH(1))", "NUMBER(19,0)[SB8]|NUMBER(19,0)[SB8]"},
                {"CREATE OR REPLACE TABLE ht AS SELECT HASH_AGG(column1) AS h, HASH(1) AS h1 FROM VALUES (1), (2)",
                    "Table HT successfully created."},
                {"DESCRIBE TABLE ht", "H|NUMBER(19,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                    + "H1|NUMBER(19,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null"},
            });
        } finally {
            engine.execute("DROP TABLE IF EXISTS ht");
        }
    }
}
