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
 * A path, a subscript or a GET to a field a structured OBJECT does not declare is refused as the statement compiles,
 * whatever rows it reads (live-verified): over an empty table, a NULL object, a filtered table, and in a CREATE
 * VIEW alike; GET takes a structured OBJECT's key as a string literal alone.
 */
public class StructuredPathCompileRefusalTest extends MeasuredCellsTestSupport {

    /** Each statement and its answer. */
    @Test
    public void anUndeclaredFieldIsRefusedWhateverTheRows() {
        engine.execute("CREATE OR REPLACE TABLE sp_t (o OBJECT(a INT))");
        assertCells(new String[][] {
            {"SELECT o:nosuch FROM sp_t",
                "REFUSED Function GET: expected structured object to contain field nosuch but it did not."},
            {"CREATE OR REPLACE VIEW sp_vw AS SELECT o:nosuch AS z FROM sp_t",
                "REFUSED Function GET: expected structured object to contain field nosuch but it did not."},
            {"SELECT o['nosuch'] FROM sp_t",
                "REFUSED Function GET: expected structured object to contain field nosuch but it did not."},
            {"SELECT GET(o, 'nosuch') FROM sp_t",
                "REFUSED Function GET: expected structured object to contain field nosuch but it did not."},
            {"INSERT INTO sp_t SELECT NULL",
                "1"},
            {"SELECT o:nosuch, o['nosuch'], GET(o, 'nosuch') FROM sp_t",
                "REFUSED Function GET: expected structured object to contain field nosuch but it did not."},
            {"INSERT INTO sp_t SELECT {'a':1}::OBJECT(a INT)",
                "1"},
            {"SELECT o:nosuch FROM sp_t WHERE o:a = 5",
                "REFUSED Function GET: expected structured object to contain field nosuch but it did not."},
            {"SELECT o:nosuch FROM sp_t",
                "REFUSED Function GET: expected structured object to contain field nosuch but it did not."},
            {"SELECT o:a FROM sp_t ORDER BY 1",
                "1 ;; null"},
            {"SELECT o:A FROM sp_t",
                "REFUSED Function GET: expected structured object to contain field A but it did not."},
            {"SELECT o:nosuch::INT FROM sp_t WHERE 1 = 0",
                "REFUSED Function GET: expected structured object to contain field nosuch but it did not."},
            {"SELECT GET(o, LOWER('A')) FROM sp_t ORDER BY 1",
                "REFUSED SQL compilation error: | Argument number 1 for function 'GET' needs to be a string literal."},
        });
    }
}
