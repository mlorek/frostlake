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
package dev.frostlake.features;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * An image repository's database, schema and name must each read as an unquoted identifier would. SHOW IMAGE
 * REPOSITORIES lists repositories by name: STARTS WITH and LIMIT ... FROM match the name with its case, FROM from the
 * name it gives on, LIKE ignoring case, and a page size of 0 is refused (live-verified).
 */
public class ImageRepositoryListingTest extends MeasuredCellsTestSupport {

    /**
     * A quoted name that reads as an unquoted one is taken, any other refused; FROM starts at the name it gives,
     * whether or not a repository holds it.
     */
    @Test
    public void aRepositoryIsNamedAsAnUnquotedIdentifierWould() {
        engine.execute("CREATE OR REPLACE IMAGE REPOSITORY ir784c");
        engine.execute("CREATE OR REPLACE IMAGE REPOSITORY ir784e");
        assertCells(new String[][] {
            {"CREATE OR REPLACE IMAGE REPOSITORY \"ir783c\"",
                "REFUSED SQL compilation error: |  Invalid identifier: TEST_DB.TEST_SCHEMA.ir783c  |  db, schema and "
                + "repo names for an image repo must be unquoted identifiers"},
            {"CREATE OR REPLACE IMAGE REPOSITORY \"IR784A\"",
                "Image Repository IR784A successfully created."},
            {"CREATE OR REPLACE IMAGE REPOSITORY test_db.\"TEST_SCHEMA\".ir784b",
                "Image Repository IR784B successfully created."},
            {"CREATE OR REPLACE IMAGE REPOSITORY \"TEST_DB\".test_schema.ir784b",
                "Image Repository IR784B successfully created."},
            {"DROP IMAGE REPOSITORY \"IR784C\"",
                "IR784C successfully dropped."},
            {"SHOW IMAGE REPOSITORIES LIKE 'IR784%' LIMIT 5 FROM 'IR784C' ->> SELECT \"name\" FROM $1",
                "IR784E"},
            {"SHOW IMAGE REPOSITORIES LIKE 'IR784%' LIMIT 5 FROM 'IR784D' ->> SELECT \"name\" FROM $1",
                "IR784E"},
            {"SHOW IMAGE REPOSITORIES LIKE 'IR784%' LIMIT 5 FROM 'IR784' ->> SELECT \"name\" FROM $1",
                "IR784A ;; IR784B ;; IR784E"},
            {"SHOW IMAGE REPOSITORIES LIKE 'IR784%' LIMIT 5 FROM 'IR784E' ->> SELECT \"name\" FROM $1",
                "IR784E"},
            {"SHOW IMAGE REPOSITORIES LIKE 'IR784%' LIMIT 5 FROM 'IR784F' ->> SELECT \"name\" FROM $1",
                ""},
            {"SHOW IMAGE REPOSITORIES LIMIT 0",
                "REFUSED page size \"0\" must be greater than 0 in limit clause"},
        });
    }

    /** The listing is in name order, and STARTS WITH and FROM match the name with its case. */
    @Test
    public void repositoriesAreListedByName() {
        engine.execute("CREATE OR REPLACE IMAGE REPOSITORY ir783b");
        engine.execute("CREATE OR REPLACE IMAGE REPOSITORY ir783a");
        engine.execute("CREATE OR REPLACE IMAGE REPOSITORY ir783d");
        assertCells(new String[][] {
            {"SHOW IMAGE REPOSITORIES IN SCHEMA test_db.test_schema ->> SELECT \"name\" FROM $1",
                "IR783A ;; IR783B ;; IR783D"},
            {"SHOW IMAGE REPOSITORIES IN SCHEMA test_db.test_schema STARTS WITH 'IR783' ->> SELECT \"name\" FROM $1",
                "IR783A ;; IR783B ;; IR783D"},
            {"SHOW IMAGE REPOSITORIES IN SCHEMA test_db.test_schema STARTS WITH 'ir783' ->> SELECT \"name\" FROM $1",
                ""},
            {"SHOW IMAGE REPOSITORIES LIKE 'ir783%' IN SCHEMA test_db.test_schema ->> SELECT \"name\" FROM $1",
                "IR783A ;; IR783B ;; IR783D"},
            {"SHOW IMAGE REPOSITORIES IN SCHEMA test_db.test_schema LIMIT 2 ->> SELECT \"name\" FROM $1",
                "IR783A ;; IR783B"},
            {"SHOW IMAGE REPOSITORIES IN SCHEMA test_db.test_schema LIMIT 2 FROM 'IR783B' ->> SELECT \"name\" FROM $1",
                "IR783B ;; IR783D"},
            {"SHOW IMAGE REPOSITORIES IN SCHEMA test_db.test_schema LIMIT 2 FROM 'ir783b' ->> SELECT \"name\" FROM $1",
                ""},
            {"SHOW IMAGE REPOSITORIES IN SCHEMA test_db.test_schema STARTS WITH 'IR783' LIMIT 1 FROM 'IR783A' ->> "
                + "SELECT \"name\" FROM $1",
                "IR783A"},
            {"SHOW IMAGE REPOSITORIES LIKE '%783%' IN SCHEMA test_db.test_schema STARTS WITH 'IR' LIMIT 10 ->> SELECT "
                + "\"name\" FROM $1",
                "IR783A ;; IR783B ;; IR783D"},
            {"SHOW IMAGE REPOSITORIES IN SCHEMA test_db.test_schema LIMIT 0 ->> SELECT \"name\" FROM $1",
                "REFUSED page size \"0\" must be greater than 0 in limit clause"},
            {"SHOW IMAGE REPOSITORIES IN SCHEMA test_db.test_schema LIMIT 1 FROM 'zzz' ->> SELECT \"name\" FROM $1",
                ""},
        });
    }
}
