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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Backslash escape sequences inside single-quoted string literals, matching Snowflake. A {@code '…'}
 * literal accepts both quote doubling ({@code ''} &rarr; {@code '}) and backslash escapes
 * ({@code \'} &rarr; {@code '}, {@code \\} &rarr; {@code \}, {@code \n}/{@code \t}/{@code \r}); any other
 * backslash is kept verbatim. All decode sites share {@code SqlStringLiterals}, and the lexer treats
 * backslash as always beginning an escape so the boundaries agree.
 *
 * <p>The headline case is a stored procedure whose {@code '}-quoted body builds dynamic SQL with
 * backslash-escaped quotes (a shape Snowflake exports produce). The body is decoded once at CREATE and
 * re-parsed at CALL, so both layers must agree — previously the body was left with stray backslashes that
 * failed to re-lex (<em>token recognition error at: '\'</em>).
 */
public class BackslashStringEscapeTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE SCHEMA test_schema");
        engine.execute("USE SCHEMA test_schema");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private String scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final Object value = rs.getRows().get(0).getValue(0);
        return value == null ? null : value.toString();
    }

    // ─────────────────── canonical literal decode (direct) ───────────────────

    @Test
    public void backslashEscapedQuoteYieldsQuote() {
        // SQL: SELECT '\''  ->  '
        assertEquals("'", scalar("SELECT '\\''"));
    }

    @Test
    public void doubledQuoteYieldsQuote() {
        // SQL: SELECT ''''  ->  '
        assertEquals("'", scalar("SELECT ''''"));
    }

    @Test
    public void escapedBackslashYieldsSingleBackslash() {
        // SQL: SELECT '\\'  ->  \
        assertEquals("\\", scalar("SELECT '\\\\'"));
    }

    @Test
    public void backslashInWindowsPathPreserved() {
        // SQL: SELECT 'C:\\Users'  ->  C:\Users
        assertEquals("C:\\Users", scalar("SELECT 'C:\\\\Users'"));
    }

    @Test
    public void quoteEmbeddedWithBackslash() {
        // SQL: SELECT 'It\'s'  ->  It's
        assertEquals("It's", scalar("SELECT 'It\\'s'"));
    }

    @Test
    public void unrecognizedBackslashKeptVerbatim() {
        // SQL: SELECT 'a\%b'  ->  a\%b   (\% is not an escape, backslash preserved)
        assertEquals("a\\%b", scalar("SELECT 'a\\%b'"));
    }

    @Test
    public void backslashNewlineYieldsNewline() {
        // SQL: SELECT 'a\nb'  ->  a<newline>b
        assertEquals("a\nb", scalar("SELECT 'a\\nb'"));
    }

    // ─────────────── procedure body: build dynamic SQL with \' quoting ───────────────

    @Test
    public void procedureBodyBuildsCleanQuotedSqlFromBackslashEscapes() {
        // The '-quoted body embeds quotes as \\'''' (a real backslash, then a doubled quote). Decoding the
        // body once gives \'' , which re-parses at CALL as the escaped quote — so the built statement uses
        // clean single quotes, not stray backslashes. (Text block \\\\ => two real backslashes.)
        engine.execute("""
            CREATE OR REPLACE PROCEDURE build_stmt("COL" VARCHAR, "VAL" VARCHAR)
            RETURNS VARCHAR
            LANGUAGE SQL
            AS 'BEGIN
                LET stmt VARCHAR := ''UPDATE t SET '' || :col || '' = \\\\'''' || :val || ''\\\\'';'';
                RETURN stmt;
            END;
            ';
            """);
        assertEquals(
            "UPDATE t SET label = 'active';",
            scalar("CALL build_stmt('label', 'active')"));
    }

    @Test
    public void executeImmediateRunsBackslashBuiltStatement() {
        // Prove the built SQL not only looks clean but actually executes: the body builds
        // INSERT INTO tvals VALUES ('<v>') via \\'''' … ''\\'' and runs it.
        engine.execute("CREATE TABLE tvals (v VARCHAR)");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE ins_val("V" VARCHAR)
            RETURNS VARCHAR
            LANGUAGE SQL
            AS 'BEGIN
                LET stmt VARCHAR := ''INSERT INTO tvals VALUES (\\\\'''' || :v || ''\\\\'')'';
                EXECUTE IMMEDIATE stmt;
                RETURN ''ok'';
            END;
            ';
            """);
        assertEquals("ok", scalar("CALL ins_val('hello')"));
        assertEquals("hello", scalar("SELECT v FROM tvals"));
    }
}
