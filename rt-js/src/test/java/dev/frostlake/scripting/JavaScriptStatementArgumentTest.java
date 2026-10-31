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

package dev.frostlake.scripting;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * The procedure API's arguments and a result set's members: a {@code sqlText} that is no text is refused by
 * createStatement and by execute with its JavaScript type, {@code Argument with invalid type number passed}, a
 * {@code binds} that is no array {@code Invalid binds argument}; past the last row a column's member keeps the last
 * row's value while getColumnValue reads null; and a column named like a method replaces the method once a row is
 * read, so {@code rs.getColumnValue(1)} is no function (live-verified).
 */
public class JavaScriptStatementArgumentTest extends MeasuredCellsTestSupport {

    /** {@code snowflake.execute} binds a {@code binds} array's values to the statement's markers (documented). */
    @Test
    public void executeBindsAnArray() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE PROCEDURE pb() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ var rs = snowflake.execute("
                + "{sqlText: 'SELECT ? + ?', binds: [40, 2]}); rs.next(); return rs.getColumnValue(1); $$",
                "Function PB successfully created."},
            {"CALL pb()", "42.0"},
        });
    }

    @Test
    public void theStatementApisArgumentsAndRowMembers() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE PROCEDURE pa() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ try { "
                + "snowflake.createStatement({sqlText: 1}); return 'ran'; } catch (e) { return e.message + ' | ' + "
                + "e.stack; } $$",
                "Function PA successfully created."},
            {"CALL pa()", "Argument with invalid type number passed | Snowflake.createStatement, line 1 position 17"},
            {"CREATE OR REPLACE PROCEDURE pa() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ try { "
                + "snowflake.createStatement({sqlText: true}); return 'ran'; } catch (e) { return e.message + ' | ' "
                + "+ e.stack; } $$",
                "Function PA successfully created."},
            {"CALL pa()", "Argument with invalid type boolean passed | Snowflake.createStatement, line 1 position 17"},
            {"CREATE OR REPLACE PROCEDURE pa() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ try { "
                + "snowflake.createStatement({sqlText: null}); return 'ran'; } catch (e) { return e.message + ' | ' "
                + "+ e.stack; } $$",
                "Function PA successfully created."},
            {"CALL pa()", "empty argument passed | Snowflake.createStatement, line 1 position 17"},
            {"CREATE OR REPLACE PROCEDURE pa() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ try { "
                + "snowflake.createStatement({sqlText: {}}); return 'ran'; } catch (e) { return e.message + ' | ' + "
                + "e.stack; } $$",
                "Function PA successfully created."},
            {"CALL pa()", "Argument with invalid type object passed | Snowflake.createStatement, line 1 position 17"},
            {"CREATE OR REPLACE PROCEDURE pa() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ try { "
                + "snowflake.createStatement({sqlText: ['SELECT 1']}); return 'ran'; } catch (e) { return e.message "
                + "+ ' | ' + e.stack; } $$",
                "Function PA successfully created."},
            {"CALL pa()", "Argument with invalid type object passed | Snowflake.createStatement, line 1 position 17"},
            {"CREATE OR REPLACE PROCEDURE pa() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ try { "
                + "snowflake.execute({sqlText: 1}); return 'ran'; } catch (e) { return e.message + ' | ' + e.stack; "
                + "} $$",
                "Function PA successfully created."},
            {"CALL pa()", "Argument with invalid type number passed | Snowflake.execute, line 1 position 17"},
            {"CREATE OR REPLACE PROCEDURE pa() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ try { "
                + "snowflake.createStatement({sqlText: 'SELECT 1', binds: 5}); return 'ran'; } catch (e) { return "
                + "e.message + ' | ' + e.stack; } $$",
                "Function PA successfully created."},
            {"CALL pa()", "Invalid binds argument | Snowflake.createStatement, line 1 position 17"},
            {"CREATE OR REPLACE PROCEDURE pa() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ try { "
                + "snowflake.createStatement({sqlText: 1.5}); return 'ran'; } catch (e) { return e.message + ' | ' + "
                + "e.stack; } $$",
                "Function PA successfully created."},
            {"CALL pa()", "Argument with invalid type number passed | Snowflake.createStatement, line 1 position 17"},
            {"CREATE OR REPLACE PROCEDURE pa() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var rs = "
                + "snowflake.execute({sqlText: 'SELECT 1 AS A'}); rs.next(); var b = rs.next(); return b + ':' + "
                + "String(rs.A); $$",
                "Function PA successfully created."},
            {"CALL pa()", "false:1"},
            {"CREATE OR REPLACE PROCEDURE pa() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var rs = "
                + "snowflake.execute({sqlText: 'SELECT 1 AS A UNION ALL SELECT 2 ORDER BY 1'}); rs.next(); "
                + "rs.next(); var b = rs.next(); return b + ':' + String(rs.A) + ':' + rs.getColumnValue(1); $$",
                "Function PA successfully created."},
            {"CALL pa()", "false:2:null"},
            {"CREATE OR REPLACE PROCEDURE pa() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var rs = "
                + "snowflake.execute({sqlText: 'SELECT 1 AS A WHERE FALSE'}); var b = rs.next(); return b + ':' + "
                + "String(rs.A); $$",
                "Function PA successfully created."},
            {"CALL pa()", "false:undefined"},
            {"CREATE OR REPLACE PROCEDURE pa() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var rs = "
                + "snowflake.execute({sqlText: 'SELECT 1 AS A'}); rs.next(); rs.next(); var c = rs.next(); return c "
                + "+ ':' + String(rs.A) + ':' + rs.getColumnValueAsString(1); $$",
                "Function PA successfully created."},
            {"CALL pa()", "false:1:null"},
            {"CREATE OR REPLACE PROCEDURE pa() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var rs = "
                + "snowflake.execute({sqlText: 'SELECT 5 AS \"next\", 6 AS \"getColumnValue\"'}); rs.next(); return "
                + "typeof rs.next + ':' + typeof rs.getColumnValue + ':' + rs.getColumnValue(1) + ':' + "
                + "rs.getColumnValue(2); $$",
                "Function PA successfully created."},
            {"CALL pa()",
                "REFUSED JavaScript execution error: Uncaught TypeError: rs.getColumnValue is not a function in PA "
                + "at ' var rs = snowflake.execute({sqlText: 'SELECT 5 AS \"next\", 6 AS \"getColumnValue\"'}); "
                + "rs.next(); return typeof rs.next + ':' + typeof rs.getColumnValue + ':' + rs.getColumnValue(1) + "
                + "':' + rs.getColumnValue(2); ' position 162 | stackstrace:  | PA line: 1"},
            {"CREATE OR REPLACE PROCEDURE pa() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var rs = "
                + "snowflake.execute({sqlText: 'SELECT 5 AS \"next\"'}); var a = typeof rs.next; rs.next(); return a "
                + "+ ':' + typeof rs.next + ':' + String(rs.next); $$",
                "Function PA successfully created."},
            {"CALL pa()", "function:number:5"},
            {"CREATE OR REPLACE PROCEDURE pa() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var rs = "
                + "snowflake.execute({sqlText: 'SELECT 5 AS \"getColumnCount\"'}); rs.next(); return typeof "
                + "rs.getColumnCount + ':' + String(rs.getColumnCount); $$",
                "Function PA successfully created."},
            {"CALL pa()", "number:5"},
            {"CREATE OR REPLACE PROCEDURE pa() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var rs = "
                + "snowflake.execute({sqlText: 'SELECT 5 AS \"toString\"'}); rs.next(); return typeof rs.toString + "
                + "':' + String(rs.toString); $$",
                "Function PA successfully created."},
            {"CALL pa()", "number:5"},
            {"CREATE OR REPLACE PROCEDURE pa() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var rs = "
                + "snowflake.execute({sqlText: 'SELECT 5 AS \"getQueryId\"'}); rs.next(); return typeof "
                + "rs.getQueryId; $$",
                "Function PA successfully created."},
            {"CALL pa()", "number"},
            {"CREATE OR REPLACE PROCEDURE pa() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var rs = "
                + "snowflake.execute({sqlText: 'SELECT 5 AS \"next\", 6 AS b'}); rs.next(); try { rs.next(); return "
                + "'called'; } catch (e) { return e.message; } $$",
                "Function PA successfully created."},
            {"CALL pa()", "rs.next is not a function"},
            {"CREATE OR REPLACE PROCEDURE pa() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var rs = "
                + "snowflake.execute({sqlText: 'SELECT 5 AS \"getColumnValue\"'}); return typeof rs.getColumnValue + "
                + "':' + rs.next() + ':' + typeof rs.getColumnValue; $$",
                "Function PA successfully created."},
            {"CALL pa()", "function:true:number"},
        });
    }
}
