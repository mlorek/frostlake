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


package dev.frostlake.metastore;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The policy listings answer their rows by name, character for character — created in the order B, A, a, C, a
 * schema's join, masking, row access, aggregation and projection policies list A, B, C, then the quoted lower-case
 * name — and a rename moves a policy to its new name's place (live-verified).
 */
public class PolicyListingOrderTest extends BaseDatabaseTest {

    private static final String[][] KINDS = {
        {"JOIN", "JP", "AS () RETURNS JOIN_CONSTRAINT -> JOIN_CONSTRAINT(JOIN_REQUIRED => FALSE)"},
        {"MASKING", "MP", "AS (v VARCHAR) RETURNS VARCHAR -> v"},
        {"ROW ACCESS", "RP", "AS (v VARCHAR) RETURNS BOOLEAN -> TRUE"},
        {"AGGREGATION", "AP", "AS () RETURNS AGGREGATION_CONSTRAINT -> AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => 1)"},
        {"PROJECTION", "PP", "AS () RETURNS PROJECTION_CONSTRAINT -> PROJECTION_CONSTRAINT(ALLOW => TRUE)"},
    };

    /** The name column of a listing, in its order. */
    private List<String> names(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> names = new ArrayList<String>();
        for (final Row row : rs.getRows()) {
            names.add(cell(rs, row, "name"));
        }
        return names;
    }

    @Test
    public void everyPolicyKindListsByName() {
        for (final String[] kind : KINDS) {
            final String p = kind[1];
            final String lower = p.toLowerCase(Locale.ROOT) + "a";
            for (final String name : Arrays.asList(p + "B", p + "A", "\"" + lower + "\"", p + "C_" + p)) {
                engine.execute("CREATE OR REPLACE " + kind[0] + " POLICY " + name + " " + kind[2]);
            }
            final List<String> expected = Arrays.asList(p + "A", p + "B", p + "C_" + p, lower);
            assertEquals(expected, names("SHOW " + kind[0] + " POLICIES"), kind[0]);
            assertEquals(expected, names("SHOW " + kind[0] + " POLICIES IN SCHEMA"), kind[0] + " in schema");
        }
    }

    @Test
    public void aRenamedPolicyTakesItsNewNamesPlace() {
        for (final String name : Arrays.asList("JPB", "JPA", "\"jpa\"", "JPC_JP")) {
            engine.execute("CREATE OR REPLACE JOIN POLICY " + name + " " + KINDS[0][2]);
        }
        engine.execute("ALTER JOIN POLICY JPB RENAME TO JPZ");
        assertEquals(Arrays.asList("JPA", "JPC_JP", "JPZ", "jpa"), names("SHOW JOIN POLICIES"));
    }
}
