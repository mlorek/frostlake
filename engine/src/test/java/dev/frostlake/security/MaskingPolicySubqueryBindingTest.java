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
 * A masking policy's parameter is bound inside a subquery of its body ahead of the subquery's own columns, as it is
 * everywhere in the body (live-verified): MAX(val) over a table VAL with a column VAL reads the masked value, a derived
 * column of the parameter's name gives way to it, and a function or a table spelled like the parameter stays what it
 * is.
 */
public class MaskingPolicySubqueryBindingTest extends MeasuredCellsTestSupport {

    /** Policies whose subqueries read the parameter beside a table, a function and a derived column of its name. */
    @Test
    public void aSubqueryReadsTheMaskedValue() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE mp_t (val VARCHAR)",
                "Table MP_T successfully created."},
            {"INSERT INTO mp_t VALUES ('secret')",
                "1"},
            {"CREATE OR REPLACE TABLE val (val VARCHAR)",
                "Table VAL successfully created."},
            {"INSERT INTO val VALUES ('from table')",
                "1"},
            {"CREATE OR REPLACE FUNCTION val() RETURNS VARCHAR AS $$ 'fn' $$",
                "Function VAL successfully created."},
            {"CREATE OR REPLACE MASKING POLICY mpv AS (val VARCHAR) RETURNS VARCHAR -> val || '/' || val() || '/' || "
                + "(SELECT MAX(val) FROM val)",
                "Masking policy MPV successfully created."},
            {"ALTER TABLE mp_t MODIFY COLUMN val SET MASKING POLICY mpv",
                "Statement executed successfully."},
            {"SELECT val FROM mp_t",
                "secret/fn/secret"},
            {"CREATE OR REPLACE TABLE mq_t (v VARCHAR)",
                "Table MQ_T successfully created."},
            {"INSERT INTO mq_t VALUES ('s')",
                "1"},
            {"CREATE OR REPLACE MASKING POLICY mqv AS (v VARCHAR) RETURNS VARCHAR -> v || '/' || (SELECT MAX(v) FROM "
                + "(SELECT 'inner' v))",
                "Masking policy MQV successfully created."},
            {"ALTER TABLE mq_t MODIFY COLUMN v SET MASKING POLICY mqv",
                "Statement executed successfully."},
            {"SELECT v FROM mq_t",
                "s/s"},
        });
    }

    /** A body that writes the parameter quoted and its name as a text. */
    @Test
    public void aQuotedOrTextNameStaysItself() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE mr_t (val VARCHAR)",
                "Table MR_T successfully created."},
            {"INSERT INTO mr_t VALUES ('x')",
                "1"},
            {"CREATE OR REPLACE MASKING POLICY mrv AS (val VARCHAR) RETURNS VARCHAR -> 'val' || val || \"VAL\"",
                "Masking policy MRV successfully created."},
            {"ALTER TABLE mr_t MODIFY COLUMN val SET MASKING POLICY mrv",
                "Statement executed successfully."},
            {"SELECT val FROM mr_t",
                "valxx"},
        });
    }
}
