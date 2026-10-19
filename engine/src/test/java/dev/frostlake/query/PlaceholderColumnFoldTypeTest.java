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
 * The type a fold over one of INFORMATION_SCHEMA's placeholder columns takes. A column the views answer with
 * NULL alone - SELF_REFERENCING_COLUMN_NAME, DOMAIN_NAME, SQL_PATH and their like - is typed NULL where it is
 * read bare, and a text of full width as soon as a conditional folds it with one: {@code COALESCE(sql_path,
 * 'x')} is VARCHAR(134217728). Folded with a number it takes the number's type. Every answer is live-verified.
 */
public class PlaceholderColumnFoldTypeTest extends MeasuredCellsTestSupport {

    @Test
    public void aPlaceholderFoldsAsAFullWidthText() {
        answer(
            "CREATE OR REPLACE TABLE it (a INT)");
        answer(
            "CREATE OR REPLACE VIEW iv AS SELECT a FROM it");
        assertAnswer(
            "NULL[LOB]",
            "SELECT SYSTEM$TYPEOF(self_referencing_column_name) FROM information_schema.tables WHERE table_schema "
                + "= 'TEST_SCHEMA' AND table_name = 'IT'");
        assertAnswer(
            "VARCHAR(134217728)[LOB]",
            "SELECT SYSTEM$TYPEOF(COALESCE(self_referencing_column_name, 'x')) FROM information_schema.tables "
                + "WHERE table_schema = 'TEST_SCHEMA' AND table_name = 'IT'");
        assertAnswer(
            "VARCHAR(134217728)[LOB]|VARCHAR(134217728)[LOB]",
            "SELECT SYSTEM$TYPEOF(COALESCE(reference_generation, 'x')), SYSTEM$TYPEOF(NVL(user_defined_type_name, "
                + "'x')) FROM information_schema.tables WHERE table_schema = 'TEST_SCHEMA' AND table_name = 'IT'");
        assertAnswer(
            "VARCHAR(134217728)[LOB]|VARCHAR(1)[LOB]",
            "SELECT SYSTEM$TYPEOF(IFNULL(self_referencing_column_name, 'x')), "
                + "SYSTEM$TYPEOF(NVL2(self_referencing_column_name, 'a', 'b')) FROM information_schema.tables WHERE "
                + "table_schema = 'TEST_SCHEMA' AND table_name = 'IT'");
        assertAnswer(
            "VARCHAR(134217728)[LOB]|VARCHAR(134217728)[LOB]",
            "SELECT SYSTEM$TYPEOF(self_referencing_column_name || 'x'), "
                + "SYSTEM$TYPEOF(CONCAT(self_referencing_column_name, 'x')) FROM information_schema.tables WHERE "
                + "table_schema = 'TEST_SCHEMA' AND table_name = 'IT'");
        assertAnswer(
            "VARCHAR(134217728)[LOB]",
            "SELECT SYSTEM$TYPEOF(IFF(self_referencing_column_name IS NULL, 'x', self_referencing_column_name)) "
                + "FROM information_schema.tables WHERE table_schema = 'TEST_SCHEMA' AND table_name = 'IT'");
        assertAnswer(
            "VARCHAR(134217728)[LOB]",
            "SELECT SYSTEM$TYPEOF(CASE WHEN self_referencing_column_name IS NULL THEN 'x' ELSE "
                + "self_referencing_column_name END) FROM information_schema.tables WHERE table_schema = "
                + "'TEST_SCHEMA' AND table_name = 'IT'");
        assertAnswer(
            "VARCHAR(134217728)[LOB]|VARCHAR(134217728)[LOB]|VARCHAR[LOB]",
            "SELECT SYSTEM$TYPEOF(GREATEST(self_referencing_column_name, 'x')), "
                + "SYSTEM$TYPEOF(UPPER(self_referencing_column_name)), "
                + "SYSTEM$TYPEOF(self_referencing_column_name::VARCHAR) FROM information_schema.tables WHERE "
                + "table_schema = 'TEST_SCHEMA' AND table_name = 'IT'");
        assertAnswer(
            "NUMBER(1,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(COALESCE(self_referencing_column_name, 1)) FROM information_schema.tables WHERE "
                + "table_schema = 'TEST_SCHEMA' AND table_name = 'IT'");
        assertAnswer(
            "NULL[LOB]|NUMBER(18,0)[SB1]|NULL[LOB]",
            "SELECT SYSTEM$TYPEOF(COALESCE(self_referencing_column_name, self_referencing_column_name)), "
                + "SYSTEM$TYPEOF(LENGTH(self_referencing_column_name)), "
                + "SYSTEM$TYPEOF(NULLIF(self_referencing_column_name, 'x')) FROM information_schema.tables WHERE "
                + "table_schema = 'TEST_SCHEMA' AND table_name = 'IT'");
        assertAnswer(
            "VARCHAR(134217728)[LOB]|VARCHAR(5)[LOB]|VARCHAR(134217728)[LOB]",
            "SELECT SYSTEM$TYPEOF(DECODE(self_referencing_column_name, NULL, 'x')), "
                + "SYSTEM$TYPEOF(self_referencing_column_name::VARCHAR(5)), "
                + "SYSTEM$TYPEOF(TRIM(self_referencing_column_name)) FROM information_schema.tables WHERE "
                + "table_schema = 'TEST_SCHEMA' AND table_name = 'IT'");
        assertAnswer(
            "x|null|y",
            "SELECT COALESCE(self_referencing_column_name, 'x'), self_referencing_column_name, "
                + "COALESCE(reference_generation, 'y') FROM information_schema.tables WHERE table_schema = "
                + "'TEST_SCHEMA' AND table_name = 'IT'");
        assertShape(
            "SELF_REFERENCING_COLUMN_NAME:VARCHAR(0), C2:VARCHAR(134217728), TABLE_NAME:VARCHAR(134217728)",
            "SELECT self_referencing_column_name, COALESCE(self_referencing_column_name, 'x') c2, table_name FROM "
                + "information_schema.tables WHERE table_schema = 'TEST_SCHEMA' AND table_name = 'IT'");
        assertAnswer(
            "VARCHAR[LOB]|NUMBER(38,"
                + "0)[SB16]|VARCHAR(134217728)[LOB]|VARCHAR[LOB]|VARCHAR(134217728)[LOB]|VARCHAR(3)[LOB]",
            "SELECT SYSTEM$TYPEOF(table_name), SYSTEM$TYPEOF(row_count), SYSTEM$TYPEOF(is_transient), "
                + "SYSTEM$TYPEOF(comment), SYSTEM$TYPEOF(clustering_key), SYSTEM$TYPEOF(auto_clustering_on) FROM "
                + "information_schema.tables WHERE table_schema = 'TEST_SCHEMA' AND table_name = 'IT'");
        assertAnswer(
            "VARCHAR[LOB]|VARCHAR(134217728)[LOB]|VARCHAR[LOB]",
            "SELECT SYSTEM$TYPEOF(COALESCE(comment, 'x')), SYSTEM$TYPEOF(COALESCE(clustering_key, 'x')), "
                + "SYSTEM$TYPEOF(COALESCE(table_name, 'x')) FROM information_schema.tables WHERE table_schema = "
                + "'TEST_SCHEMA' AND table_name = 'IT'");
        assertAnswer(
            "NULL[LOB]|VARCHAR(134217728)[LOB]|NULL[LOB]|VARCHAR(134217728)[LOB]|NULL[LOB]|NUMBER(1,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(domain_name), SYSTEM$TYPEOF(COALESCE(domain_name, 'x')), "
                + "SYSTEM$TYPEOF(interval_type), SYSTEM$TYPEOF(COALESCE(interval_type, 'x')), "
                + "SYSTEM$TYPEOF(maximum_cardinality), SYSTEM$TYPEOF(COALESCE(maximum_cardinality, 1)) FROM "
                + "information_schema.columns WHERE table_schema = 'TEST_SCHEMA' AND table_name = 'IT'");
        assertAnswer(
            "NULL[LOB]|VARCHAR(134217728)[LOB]|VARCHAR[LOB]|VARCHAR(134217728)[LOB]|VARCHAR(134217728)[LOB]|NULL[LOB]|NULL[LOB]",
            "SELECT SYSTEM$TYPEOF(character_set_name), SYSTEM$TYPEOF(COALESCE(character_set_name, 'x')), "
                + "SYSTEM$TYPEOF(collation_name), SYSTEM$TYPEOF(dtd_identifier), "
                + "SYSTEM$TYPEOF(COALESCE(dtd_identifier, 'x')), SYSTEM$TYPEOF(scope_name), SYSTEM$TYPEOF(udt_name) "
                + "FROM information_schema.columns WHERE table_schema = 'TEST_SCHEMA' AND table_name = 'IT'");
        assertAnswer(
            "NULL[LOB]|VARCHAR(134217728)[LOB]|NULL[LOB]|VARCHAR(134217728)[LOB]",
            "SELECT SYSTEM$TYPEOF(sql_path), SYSTEM$TYPEOF(COALESCE(sql_path, 'x')), "
                + "SYSTEM$TYPEOF(default_character_set_name), SYSTEM$TYPEOF(COALESCE(default_character_set_catalog, "
                + "'x')) FROM information_schema.schemata WHERE schema_name = 'TEST_SCHEMA'");
        assertAnswer(
            "VARCHAR(4)[LOB]|VARCHAR(4)[LOB]|VARCHAR(2)[LOB]|VARCHAR(2)[LOB]|VARCHAR(2)[LOB]",
            "SELECT SYSTEM$TYPEOF(check_option), SYSTEM$TYPEOF(COALESCE(check_option, 'x')), "
                + "SYSTEM$TYPEOF(is_updatable), SYSTEM$TYPEOF(insertable_into), "
                + "SYSTEM$TYPEOF(COALESCE(insertable_into, 'x')) FROM information_schema.views WHERE table_schema = "
                + "'TEST_SCHEMA' AND table_name = 'IV'");
        assertAnswer(
            "Table IT2 successfully created.",
            "CREATE OR REPLACE TABLE it2 AS SELECT COALESCE(self_referencing_column_name, 'x') d, table_name n "
                + "FROM information_schema.tables WHERE table_schema = 'TEST_SCHEMA' AND table_name = 'IT'");
        assertAnswer(
            "D|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "N|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE it2");
        assertAnswer(
            "Table IT3 successfully created.",
            "CREATE OR REPLACE TABLE it3 AS SELECT self_referencing_column_name c FROM information_schema.tables "
                + "WHERE table_schema = 'TEST_SCHEMA' AND table_name = 'IT'");
        assertAnswer(
            "null|3",
            "SELECT self_referencing_column_name || 'x', LENGTH(COALESCE(self_referencing_column_name, 'abc')) "
                + "FROM information_schema.tables WHERE table_schema = 'TEST_SCHEMA' AND table_name = 'IT'");
        assertAnswer(
            "4",
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'TEST_SCHEMA' AND "
                + "self_referencing_column_name IS NULL");
        assertAnswer(
            "VARCHAR(134217728)[LOB]",
            "SELECT SYSTEM$TYPEOF(x.c) FROM (SELECT COALESCE(self_referencing_column_name, 'x') c FROM "
                + "information_schema.tables WHERE table_schema = 'TEST_SCHEMA' AND table_name = 'IT') x");
        assertAnswer(
            "NULL[LOB]|VARCHAR(134217728)[LOB]",
            "SELECT SYSTEM$TYPEOF(MAX(self_referencing_column_name)), "
                + "SYSTEM$TYPEOF(MAX(COALESCE(self_referencing_column_name, 'x'))) FROM information_schema.tables "
                + "WHERE table_schema = 'TEST_SCHEMA' AND table_name = 'IT'");
    }
}
