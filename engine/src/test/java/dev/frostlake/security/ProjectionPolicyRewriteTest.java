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

package dev.frostlake.security;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A projection policy that denies projection, read through the FROM clause's rewrites (live-verified): UNPIVOT's
 * value column, PIVOT's pivoted columns over an aggregate that hands out values, and a MATCH_RECOGNIZE measure
 * over the column come under the policy, and so does the column itself where a rewrite passes it through; a
 * counting aggregate and a measure that reads no such column do not.
 */
public class ProjectionPolicyRewriteTest extends MeasuredCellsTestSupport {

    @Test
    public void theRewrittenColumnsComeUnderThePolicy() {
        engine.execute("CREATE OR REPLACE TABLE tpp (id INT, ssn STRING, other STRING)");
        engine.execute("INSERT INTO tpp VALUES (1, '111', 'o1'), (2, '222', 'o2')");
        engine.execute("CREATE OR REPLACE PROJECTION POLICY ppz AS () RETURNS PROJECTION_CONSTRAINT -> "
            + "PROJECTION_CONSTRAINT(ALLOW => false)");
        engine.execute("ALTER TABLE tpp MODIFY COLUMN ssn SET PROJECTION POLICY ppz");
        assertCells(new String[][] {
            {"SELECT MAX(ssn) FROM tpp",
                "REFUSED SQL compilation error: The following columns are restricted by a Projection Policy. Please "
                + "remove them from the list of projected columns: |  |  MAX(SSN)"},
            {"SELECT * FROM tpp UNPIVOT (val FOR col IN (ssn))",
                "REFUSED SQL compilation error: The following columns are restricted by a Projection Policy. Please "
                + "remove them from the list of projected columns: |  |  VAL"},
            {"SELECT * FROM tpp PIVOT (MAX(ssn) FOR id IN (1, 2))",
                "REFUSED SQL compilation error: The following columns are restricted by a Projection Policy. Please "
                + "remove them from the list of projected columns: |  |  1 |  2"},
            {"SELECT * FROM tpp MATCH_RECOGNIZE (ORDER BY id MEASURES FIRST(ssn) AS s ONE ROW PER MATCH PATTERN (a) "
                + "DEFINE a AS TRUE)",
                "REFUSED SQL compilation error: The following columns are restricted by a Projection Policy. Please "
                + "remove them from the list of projected columns: |  |  S"},
            {"SELECT * FROM tpp MATCH_RECOGNIZE (ORDER BY id MEASURES FIRST(ssn) AS s ALL ROWS PER MATCH PATTERN (a) "
                + "DEFINE a AS TRUE)",
                "REFUSED SQL compilation error: The following columns are restricted by a Projection Policy. Please "
                + "remove them from the list of projected columns: |  |  SSN |  S"},
            {"SELECT id FROM tpp UNPIVOT (val FOR col IN (ssn, other))", "1 ;; 1 ;; 2 ;; 2"},
            {"SELECT col FROM tpp UNPIVOT (val FOR col IN (ssn))", "SSN ;; SSN"},
            {"SELECT id FROM tpp UNPIVOT (val FOR col IN (ssn))", "1 ;; 2"},
            {"SELECT * FROM tpp PIVOT (COUNT(ssn) FOR id IN (1, 2))", "o1|1|0 ;; o2|0|1"},
            {"SELECT * FROM tpp MATCH_RECOGNIZE (ORDER BY id MEASURES COUNT(*) AS c ONE ROW PER MATCH PATTERN (a) "
                + "DEFINE a AS ssn = '111')",
                "1"},
            {"SELECT * FROM tpp MATCH_RECOGNIZE (PARTITION BY ssn ORDER BY id MEASURES COUNT(*) AS c ONE ROW PER "
                + "MATCH PATTERN (a) DEFINE a AS TRUE)",
                "REFUSED SQL compilation error: The following columns are restricted by a Projection Policy. Please "
                + "remove them from the list of projected columns: |  |  SSN"},
            {"SELECT * FROM tpp MATCH_RECOGNIZE (ORDER BY id MEASURES LAST(ssn) AS s, COUNT(*) AS c ONE ROW PER MATCH "
                + "PATTERN (a) DEFINE a AS TRUE)",
                "REFUSED SQL compilation error: The following columns are restricted by a Projection Policy. Please "
                + "remove them from the list of projected columns: |  |  S"},
            {"SELECT val FROM tpp UNPIVOT (val FOR col IN (ssn))",
                "REFUSED SQL compilation error: The following columns are restricted by a Projection Policy. Please "
                + "remove them from the list of projected columns: |  |  VAL"},
            {"SELECT id, other FROM tpp UNPIVOT (val FOR col IN (ssn))", "1|o1 ;; 2|o2"},
            {"SELECT COUNT(*) FROM tpp UNPIVOT (val FOR col IN (ssn))", "2"},
            {"SELECT \"1\" FROM tpp PIVOT (MAX(ssn) FOR id IN (1, 2))",
                "REFUSED SQL compilation error: The following columns are restricted by a Projection Policy. Please "
                + "remove them from the list of projected columns: |  |  1"},
            {"SELECT other FROM tpp PIVOT (MAX(ssn) FOR id IN (1, 2))", "o1 ;; o2"},
            {"SELECT * FROM tpp UNPIVOT (val FOR col IN (other))",
                "REFUSED SQL compilation error: The following columns are restricted by a Projection Policy. Please "
                + "remove them from the list of projected columns: |  |  SSN"},
            {"SELECT * FROM tpp MATCH_RECOGNIZE (ORDER BY id MEASURES MATCH_NUMBER() AS m ONE ROW PER MATCH PATTERN "
                + "(a) DEFINE a AS TRUE)",
                "1 ;; 2"},
        });
    }
}
