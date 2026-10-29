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
 * The QUERY_TAG a statement's QUERY_HISTORY row carries is the session's as the statement began (live-verified): empty,
 * never NULL, when none is set, the tag for each statement after it is set - the SET itself carries the one before it -
 * and empty again once it is unset or set empty.
 */
public class QueryHistoryQueryTagTest extends MeasuredCellsTestSupport {

    /** Statements run before, under and after a tag. */
    @Test
    public void tagsAsStatementsBegin() {
        assertCells(new String[][] {
            {"ALTER SESSION UNSET QUERY_TAG",
                "Statement executed successfully."},
            {"SELECT 804001",
                "804001"},
            {"SELECT query_tag IS NULL, LENGTH(query_tag), query_tag FROM "
                + "TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION()) "
                + "WHERE query_text = 'SELECT 804001'",
                "false|0|"},
            {"ALTER SESSION SET QUERY_TAG = 'tag804'",
                "Statement executed successfully."},
            {"SELECT 804002",
                "804002"},
            {"SELECT query_tag IS NULL, LENGTH(query_tag), query_tag FROM "
                + "TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION()) "
                + "WHERE query_text = 'SELECT 804002'",
                "false|6|tag804"},
            {"SELECT query_tag FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION()) WHERE query_text = 'SELECT "
                + "804002'",
                "tag804"},
            {"SELECT query_tag IS NULL, LENGTH(query_tag), query_tag FROM "
                + "TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION()) "
                + "WHERE query_text = 'ALTER SESSION SET QUERY_TAG = ''tag804'''",
                "false|0|"},
            {"ALTER SESSION SET QUERY_TAG = ''",
                "Statement executed successfully."},
            {"SELECT 804004",
                "804004"},
            {"SELECT query_tag IS NULL, LENGTH(query_tag), query_tag FROM "
                + "TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION()) "
                + "WHERE query_text = 'SELECT 804004'",
                "false|0|"},
            {"SELECT query_tag IS NULL, LENGTH(query_tag), query_tag FROM "
                + "TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION()) "
                + "WHERE query_text = 'ALTER SESSION SET QUERY_TAG = '''''",
                "false|6|tag804"},
            {"ALTER SESSION UNSET QUERY_TAG",
                "Statement executed successfully."},
        });
    }
}
