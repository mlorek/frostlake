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
 * A DROP's or a DESCRIBE's signature and tail read by the account's tokens where this lexer reads the text
 * otherwise: a stage reference whole up to its delimiter, with the quotes, comment openings and operators
 * written inside it; '|', '^', '&' and a lone '$' as tokens of one character; a character the account has no
 * token for with the character after it; a backticked name; three runs of digits and a hexadecimal number as
 * one token. A text the account's lexer refuses - an unclosed string, quoted name or comment, a number that
 * stops after its exponent's letter or its second point - adds that lexer's line where the reading first
 * looks behind it. Every answer is live-verified.
 */
public class SignatureLexerReadingTest extends MeasuredCellsTestSupport {

    @Test
    public void aStageReferenceIsOneToken() {
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@\"s\"'.",
            "DESCRIBE TABLE t1 (@\"s\")");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@%\"t\"'.",
            "DESCRIBE TABLE t1 (@%\"t\")");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s--x'.",
            "DESCRIBE TABLE t1 (@s--x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s//x'.",
            "DESCRIBE TABLE t1 (@s//x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s/*x*/'.",
            "DESCRIBE TABLE t1 (@s/*x*/)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s!=x'.",
            "DESCRIBE TABLE t1 (@s!=x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s'. | syntax error "
                + "line 1 at position 24 unexpected ')'.",
            "DESCRIBE TABLE t1 (@s<>x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s='. | syntax error "
                + "line 1 at position 24 unexpected ')'.",
            "DESCRIBE TABLE t1 (@s=>x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s-'. | syntax error "
                + "line 1 at position 24 unexpected ')'.",
            "DESCRIBE TABLE t1 (@s->x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@S3://b/k'.",
            "DESCRIBE TABLE t1 (@S3://b/k)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@\"s'. | parse error "
                + "line 1 at position 26 near '<EOF>'. | syntax error line 1 at position 26 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (@\"s t\")");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@\"s'. | parse error "
                + "line 1 at position 26 near '<EOF>'. | syntax error line 1 at position 26 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (@\"s)t\")");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@\"s'. | parse error "
                + "line 1 at position 26 near '<EOF>'. | syntax error line 1 at position 26 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (@\"s,t\")");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s/*'. | syntax "
                + "error line 1 at position 26 unexpected '*'.",
            "DESCRIBE TABLE t1 (@s/* x */)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s/*x'.",
            "DESCRIBE TABLE t1 (@s/*x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s$$x$$'.",
            "DESCRIBE TABLE t1 (@s$$x$$)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s$$x'. | syntax "
                + "error line 1 at position 28 unexpected ')'.",
            "DESCRIBE TABLE t1 (@s$$x y$$)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s'. | syntax error "
                + "line 1 at position 23 unexpected ')'.",
            "DESCRIBE TABLE t1 (@s x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s'.",
            "DESCRIBE TABLE t1 (@s'x')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s\"x\"'.",
            "DESCRIBE TABLE t1 (@s\"x\")");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s.x'.",
            "DESCRIBE TABLE t1 (@s.x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s:x'.",
            "DESCRIBE TABLE t1 (@s:x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s[1]'.",
            "DESCRIBE TABLE t1 (@s[1])");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s{x}'.",
            "DESCRIBE TABLE t1 (@s{x})");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s=1'.",
            "DESCRIBE TABLE t1 (@s=1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s+x'.",
            "DESCRIBE TABLE t1 (@s+x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s-x'.",
            "DESCRIBE TABLE t1 (@s-x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s*x'.",
            "DESCRIBE TABLE t1 (@s*x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s%x'.",
            "DESCRIBE TABLE t1 (@s%x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s?x'.",
            "DESCRIBE TABLE t1 (@s?x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s#x'.",
            "DESCRIBE TABLE t1 (@s#x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s&x'.",
            "DESCRIBE TABLE t1 (@s&x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s^x'.",
            "DESCRIBE TABLE t1 (@s^x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s!x'.",
            "DESCRIBE TABLE t1 (@s!x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s\\x'.",
            "DESCRIBE TABLE t1 (@s\\x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s@x'.",
            "DESCRIBE TABLE t1 (@s@x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s'. | syntax error "
                + "line 1 at position 23 unexpected ')'.",
            "DESCRIBE TABLE t1 (@s|x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s'. | syntax error "
                + "line 1 at position 24 unexpected ')'.",
            "DESCRIBE TABLE t1 (@s||x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s'. | syntax error "
                + "line 1 at position 23 unexpected ')'.",
            "DESCRIBE TABLE t1 (@s~x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@~x'.",
            "DESCRIBE TABLE t1 (@~x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@~'. | syntax error "
                + "line 1 at position 23 unexpected ')'.",
            "DESCRIBE TABLE t1 (@~~x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s'. | syntax error "
                + "line 1 at position 23 unexpected ')'.",
            "DESCRIBE TABLE t1 (@s<x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s'. | syntax error "
                + "line 1 at position 23 unexpected ')'.",
            "DESCRIBE TABLE t1 (@s>x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s'. | syntax error "
                + "line 1 at position 24 unexpected ')'.",
            "DESCRIBE TABLE t1 (@s<=x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s'. | syntax error "
                + "line 1 at position 24 unexpected ')'.",
            "DESCRIBE TABLE t1 (@s>=x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@'.",
            "DESCRIBE TABLE t1 (@\u00e9)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s'.",
            "DESCRIBE TABLE t1 (@s\u00e9)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s'. | syntax error "
                + "line 1 at position 24 unexpected ')'.",
            "DESCRIBE TABLE t1 (@s`x`)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@'.",
            "DESCRIBE TABLE t1 (@)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@'. | syntax error "
                + "line 1 at position 22 unexpected ')'.",
            "DESCRIBE TABLE t1 (@ s)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@@s'.",
            "DESCRIBE TABLE t1 (@@s)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@'. | syntax error "
                + "line 1 at position 22 unexpected ')'.",
            "DESCRIBE TABLE t1 (@,s)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s-'. | syntax error "
                + "line 1 at position 25 unexpected ')'.",
            "DESCRIBE TABLE t1 (@s->>x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s='. | syntax error "
                + "line 1 at position 25 unexpected ')'.",
            "DESCRIBE TABLE t1 (@s=>>x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s:=x'.",
            "DESCRIBE TABLE t1 (@s:=x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s::x'.",
            "DESCRIBE TABLE t1 (@s::x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@X'.",
            "DESCRIBE TABLE t1 (@X'AB')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s1e'.",
            "DESCRIBE TABLE t1 (@s1e)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s1.5.6'.",
            "DESCRIBE TABLE t1 (@s1.5.6)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@file://x'.",
            "DESCRIBE TABLE t1 (@file://x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s3://b/k'.",
            "DESCRIBE TABLE t1 (@s3://b/k)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@azure://x/y'.",
            "DESCRIBE TABLE t1 (@azure://x/y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s/a'. | syntax "
                + "error line 1 at position 25 unexpected ')'.",
            "DESCRIBE TABLE t1 (@s/a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s/a/b.gz'.",
            "DESCRIBE TABLE t1 (@s/a/b.gz)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s!x!'.",
            "DESCRIBE TABLE t1 (@s!x!)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s'.",
            "DESCRIBE TABLE t1 (@s<>)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s='.",
            "DESCRIBE TABLE t1 (@s=>)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s-'.",
            "DESCRIBE TABLE t1 (@s->)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s!='.",
            "DESCRIBE TABLE t1 (@s!=)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s--'.",
            "DESCRIBE TABLE t1 (@s--)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s--x'. | syntax "
                + "error line 1 at position 27 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (@s--x) y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s//x'. | syntax "
                + "error line 1 at position 27 unexpected ')'.",
            "DESCRIBE TABLE t1 (@s//x, b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@\"s\".\"t\"'.",
            "DESCRIBE TABLE t1 (@\"s\".\"t\")");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@\"s\"x'.",
            "DESCRIBE TABLE t1 (@\"s\"x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s\"t\"'.",
            "DESCRIBE TABLE t1 (@s\"t\")");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s'. | syntax error "
                + "line 1 at position 23 unexpected ')'.",
            "DESCRIBE TABLE t1 (@s\tx)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s'. | syntax error "
                + "line 2 at position 1 unexpected ')'.",
            "DESCRIBE TABLE t1 (@s\nx)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '@\"s\"'.",
            "DESCRIBE TABLE t1 (a, @\"s\")");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '@\"s\"'.",
            "DESCRIBE TABLE t1 (a @\"s\")");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@\"s\"'. | syntax "
                + "error line 1 at position 25 unexpected ')'.",
            "DESCRIBE TABLE t1 (@\"s\" b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@\"s\"'. | syntax "
                + "error line 1 at position 26 unexpected ')'.",
            "DESCRIBE TABLE t1 (@\"s\", b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s'. | syntax error "
                + "line 1 at position 24 unexpected ','. | syntax error line 1 at position 27 unexpected ')'.",
            "DESCRIBE TABLE t1 (@s<>x, b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s='. | syntax error "
                + "line 1 at position 24 unexpected ','. | syntax error line 1 at position 27 unexpected ')'.",
            "DESCRIBE TABLE t1 (@s=>x, b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s-'. | syntax error "
                + "line 1 at position 24 unexpected ','. | syntax error line 1 at position 27 unexpected ')'.",
            "DESCRIBE TABLE t1 (@s->x, b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s'. | syntax error "
                + "line 1 at position 25 unexpected 'y'. | syntax error line 1 at position 26 unexpected ')'.",
            "DESCRIBE TABLE t1 (@s<>x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s!=x'. | syntax "
                + "error line 1 at position 26 unexpected ')'.",
            "DESCRIBE TABLE t1 (@s!=x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '@s!=x'. | syntax "
                + "error line 1 at position 27 unexpected ')'.",
            "DESCRIBE TABLE t1 (@s!=x, y)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 (a) x = @\"s\"");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 (a) x = @%\"t\"");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 (a) x = @s--x");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 (a) x = @s//x");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 (a) x = @s/*x*/");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '<>'.",
            "DESCRIBE TABLE t1 (a) x = @s<>x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '>'.",
            "DESCRIBE TABLE t1 (a) x = @s=>x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '>'.",
            "DESCRIBE TABLE t1 (a) x = @s->x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = @\"s\" y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = @s!=x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '<>'.",
            "DESCRIBE TABLE t1 (a) x = @s<>x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '>'.",
            "DESCRIBE TABLE t1 (a) x = @s=>x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '>'.",
            "DESCRIBE TABLE t1 (a) x = @s->x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = @s/*x*/ y");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 (a) x = @\"s\", y = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '@\"s\"'. | syntax "
                + "error line 1 at position 33 unexpected '1'.",
            "DESCRIBE TABLE t1 (a) x = (@\"s\", 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'b'.",
            "DESCRIBE TABLE t1 (a b) x = @\"s\"");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'b'.",
            "DESCRIBE TABLE t1 (a b) x = @s!=x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'b'. | syntax error "
                + "line 1 at position 30 unexpected '<>'.",
            "DESCRIBE TABLE t1 (a b) x = @s<>x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '@\"s\"'.",
            "DESCRIBE TABLE t1 (a) @\"s\"");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '@s!=x'.",
            "DESCRIBE TABLE t1 (a) @s!=x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '@s'.",
            "DESCRIBE TABLE t1 (a) @s<>x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '@s'.",
            "DESCRIBE TABLE t1 (a) x = 1, @s");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '@\"s\"'.",
            "DESCRIBE TABLE t1 (a) x = 1, @\"s\"");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '+'.",
            "DESCRIBE TABLE t1 (a) x = 1, +");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '2'.",
            "DESCRIBE TABLE t1 (a) x = 1, 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ''a''.",
            "DESCRIBE TABLE t1 (a) x = 1, 'a'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '('.",
            "DESCRIBE TABLE t1 (a) x = 1, (");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ')'.",
            "DESCRIBE TABLE t1 (a) x = 1, )");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '='. | syntax error "
                + "line 1 at position 30 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = 1, =");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ','.",
            "DESCRIBE TABLE t1 (a) x = 1, ,");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '?'.",
            "DESCRIBE TABLE t1 (a) x = 1, ?");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '-'.",
            "DESCRIBE TABLE t1 (a) x = 1, -");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '.'.",
            "DESCRIBE TABLE t1 (a) x = 1, .");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ':'.",
            "DESCRIBE TABLE t1 (a) x = 1, :");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ';'.",
            "DESCRIBE TABLE t1 (a) x = 1, ;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'NULL'.",
            "DESCRIBE TABLE t1 (a) x = 1, NULL");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = 1, TRUE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'SELECT'.",
            "DESCRIBE TABLE t1 (a) x = 1, SELECT");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = 1, CASE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = 1, WHEN");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'WITH'.",
            "DESCRIBE TABLE t1 (a) x = 1, WITH");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '['.",
            "DESCRIBE TABLE t1 (a) x = 1, [");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '@\"s\"'.",
            "DROP TABLE t9 (@\"s\")");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '@%\"t\"'.",
            "DROP TABLE t9 (@%\"t\")");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '@s--x'.",
            "DROP TABLE t9 (@s--x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '@s/*x*/'.",
            "DROP TABLE t9 (@s/*x*/)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '@s!=x'.",
            "DROP TABLE t9 (@s!=x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '@s'.",
            "DROP TABLE t9 (@s<>x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '@s='.",
            "DROP TABLE t9 (@s=>x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '@s-'.",
            "DROP TABLE t9 (@s->x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '@S3://b/k'.",
            "DROP TABLE t9 (@S3://b/k)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '@\"s\"'.",
            "DROP TABLE t9 (a) @\"s\"");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '@s!=x'.",
            "DROP TABLE t9 (a) @s!=x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'b'.",
            "DROP TABLE t9 (a b) @\"s\"");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '@s'.",
            "DROP TABLE t9 (a) @s<>x");
    }

    @Test
    public void aCharacterWithNoToken() {
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '('. | syntax error "
                + "line 1 at position 21 unexpected ')'. | syntax error line 1 at position 24 unexpected ')'. | "
                + "syntax error line 1 at position 29 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 ((a) b) | c");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '('. | syntax error "
                + "line 1 at position 21 unexpected ')'. | syntax error line 1 at position 24 unexpected ')'. | "
                + "syntax error line 1 at position 29 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 ((a) b) ^ c");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '('. | syntax error "
                + "line 1 at position 21 unexpected ')'. | syntax error line 1 at position 24 unexpected ')'. | "
                + "syntax error line 1 at position 29 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 ((a) b) & c");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '('. | syntax error "
                + "line 1 at position 21 unexpected ')'. | syntax error line 1 at position 24 unexpected '\u00e9)'.",
            "DESCRIBE TABLE t1 ((a), \u00e9)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '\u00e9 '. | syntax "
                + "error line 1 at position 22 unexpected ')'.",
            "DESCRIBE TABLE t1 (\u00e9 b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '\u00e9 '.",
            "DESCRIBE TABLE t1 (a) \u00e9 = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '\u00e9)'.",
            "DESCRIBE TABLE t1 (\u00e9)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '\u00e9a'. | syntax "
                + "error line 1 at position 23 unexpected ')'.",
            "DESCRIBE TABLE t1 (\u00e9a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '\u00e9 '. | syntax "
                + "error line 1 at position 23 unexpected ')'.",
            "DESCRIBE TABLE t1 (a\u00e9 b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '\u00e9)'.",
            "DESCRIBE TABLE t1 (a \u00e9)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '\u00e9e'. | syntax "
                + "error line 1 at position 23 unexpected ')'.",
            "DESCRIBE TABLE t1 (\u00e9e b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '\u00e9\u00e9'. | "
                + "syntax error line 1 at position 23 unexpected ')'.",
            "DESCRIBE TABLE t1 (\u00e9\u00e9 b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '\u00e9,'. | syntax "
                + "error line 1 at position 23 unexpected ')'.",
            "DESCRIBE TABLE t1 (\u00e9, b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '\u00e9('. | syntax "
                + "error line 1 at position 22 unexpected ')'.",
            "DESCRIBE TABLE t1 (\u00e9(b))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '\u00f1 '. | syntax "
                + "error line 1 at position 22 unexpected ')'.",
            "DESCRIBE TABLE t1 (\u00f1 b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '\u65e5 '. | syntax "
                + "error line 1 at position 22 unexpected ')'.",
            "DESCRIBE TABLE t1 (\u65e5 b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '\u65e5\u672c'. | "
                + "syntax error line 1 at position 23 unexpected ')'.",
            "DESCRIBE TABLE t1 (\u65e5\u672c b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '\u20ac '. | syntax "
                + "error line 1 at position 22 unexpected ')'.",
            "DESCRIBE TABLE t1 (\u20ac b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '|'. | syntax error "
                + "line 1 at position 22 unexpected ')'.",
            "DESCRIBE TABLE t1 (| b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '|'. | syntax error "
                + "line 1 at position 24 unexpected ')'.",
            "DESCRIBE TABLE t1 (a | b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '^'. | syntax error "
                + "line 1 at position 24 unexpected ')'.",
            "DESCRIBE TABLE t1 (a ^ b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '&'. | syntax error "
                + "line 1 at position 24 unexpected ')'.",
            "DESCRIBE TABLE t1 (a & b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '!'. | syntax error "
                + "line 1 at position 24 unexpected ')'.",
            "DESCRIBE TABLE t1 (a ! b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '# '. | syntax error "
                + "line 1 at position 24 unexpected ')'.",
            "DESCRIBE TABLE t1 (a # b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '\\ '. | syntax error "
                + "line 1 at position 24 unexpected ')'.",
            "DESCRIBE TABLE t1 (a \\ b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '` '. | syntax error "
                + "line 1 at position 24 unexpected ')'.",
            "DESCRIBE TABLE t1 (a ` b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '$'. | syntax error "
                + "line 1 at position 24 unexpected ')'.",
            "DESCRIBE TABLE t1 (a $ b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'b'.",
            "DESCRIBE TABLE t1 (a b) | c");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '|'.",
            "DESCRIBE TABLE t1 (a) x = |");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '\u00e9'.",
            "DESCRIBE TABLE t1 (a) x = \u00e9");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '|'.",
            "DESCRIBE TABLE t1 (a) x = 1 | y = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'b'.",
            "DESCRIBE TABLE t1 (a b) \u00e9");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'b'.",
            "DESCRIBE TABLE t1 (a b) \u00e9\u00e9");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'b'.",
            "DESCRIBE TABLE t1 (a b) \u00e9;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '\u00e9'.",
            "DESCRIBE TABLE t1 (a) \u00e9");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '+'. | syntax error "
                + "line 1 at position 22 unexpected ')'.",
            "DESCRIBE TABLE t1 (+ b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '+'. | syntax error "
                + "line 1 at position 24 unexpected ')'.",
            "DESCRIBE TABLE t1 (a + b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '+'.",
            "DESCRIBE TABLE t1 (a) x = +");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '|'.",
            "DESCRIBE TABLE t1 (a) | c");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '^'.",
            "DESCRIBE TABLE t1 (a) ^ c");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '&'.",
            "DESCRIBE TABLE t1 (a) & c");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '!'.",
            "DESCRIBE TABLE t1 (a) !");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '#'.",
            "DESCRIBE TABLE t1 (a) #");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '^'.",
            "DESCRIBE TABLE t1 (a) x = 1 ^ 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '&'.",
            "DESCRIBE TABLE t1 (a) x = 1 & 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '\u00e9,'.",
            "DESCRIBE TABLE t1 (a) x = \u00e9, y = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '\u00e9,'.",
            "DESCRIBE TABLE t1 (a) x = (\u00e9, 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '\u00e9)'.",
            "DESCRIBE TABLE t1 (a) x = (1, \u00e9)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '\u00e9)'.",
            "DESCRIBE TABLE t1 (a, \u00e9)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '\u00e9,'.",
            "DESCRIBE TABLE t1 (a, \u00e9, b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '|'.",
            "DESCRIBE TABLE t1 (a, | , b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '|'.",
            "DESCRIBE TABLE t1 (|)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '^'.",
            "DESCRIBE TABLE t1 (^)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '&'.",
            "DESCRIBE TABLE t1 (&)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '|'. | syntax error "
                + "line 1 at position 22 unexpected ')'.",
            "DESCRIBE TABLE t1 (a|b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '^'. | syntax error "
                + "line 1 at position 22 unexpected ')'.",
            "DESCRIBE TABLE t1 (a^b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '&'. | syntax error "
                + "line 1 at position 22 unexpected ')'.",
            "DESCRIBE TABLE t1 (a&b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '\u00e9)'.",
            "DESCRIBE TABLE t1 (a\u00e9)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '\u00e9a'.",
            "DESCRIBE TABLE t1 (\u00e9a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '\u00e9 '.",
            "DESCRIBE TABLE t1 (a) x\u00e9 = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '\u00e9'. | syntax "
                + "error line 1 at position 27 unexpected '\u00e9'.",
            "DESCRIBE TABLE t1 (a) x = y\u00e9");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '\u00e9'.",
            "DESCRIBE TABLE t1 (a) x = 1\u00e9");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '\u00c9 '. | syntax "
                + "error line 1 at position 22 unexpected ')'.",
            "DESCRIBE TABLE t1 (\u00c9 b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '\u00df '. | syntax "
                + "error line 1 at position 22 unexpected ')'.",
            "DESCRIBE TABLE t1 (\u00df b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '\u03b1 '. | syntax "
                + "error line 1 at position 22 unexpected ')'.",
            "DESCRIBE TABLE t1 (\u03b1 b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '\ud83d\ude00'. | "
                + "syntax error line 1 at position 23 unexpected ')'.",
            "DESCRIBE TABLE t1 (\ud83d\ude00 b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'b'.",
            "DESCRIBE TABLE t1 (a b) \ud83d\ude00");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '\u65e5'.",
            "DESCRIBE TABLE t1 (a) x = \u65e5");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'b'. | syntax error "
                + "line 1 at position 28 unexpected '\u00e9 '. | syntax error line 1 at position 31 unexpected "
                + "'<EOF>'.",
            "DESCRIBE TABLE t1 (a b) x = \u00e9 y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '\u00e9 '. | syntax "
                + "error line 1 at position 24 unexpected ')'.",
            "DESCRIBE TABLE t1 (a \u00e9 b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '\u00e9,'. | syntax "
                + "error line 1 at position 25 unexpected ')'.",
            "DESCRIBE TABLE t1 (a \u00e9, b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '\u00e9'.",
            "DESCRIBE TABLE t1 (a) x = 1 \u00e9");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '|'.",
            "DESCRIBE TABLE t1 (a) x = 1 |");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '|'.",
            "DESCRIBE TABLE t1 (a) x = 1 | 2 y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'b'.",
            "DESCRIBE TABLE t1 (a b) | c d");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '('. | syntax error "
                + "line 1 at position 21 unexpected ')'. | syntax error line 1 at position 24 unexpected ')'. | "
                + "syntax error line 1 at position 30 unexpected 'd'.",
            "DESCRIBE TABLE t1 ((a) b) | c d");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'b'. | syntax error "
                + "line 1 at position 23 unexpected 'c'. | syntax error line 1 at position 24 unexpected ')'.",
            "DESCRIBE TABLE t1 (a b c) |");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '|'.",
            "DESCRIBE TABLE t1 (a) x = (1 | 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '|'.",
            "DESCRIBE TABLE t1 (a) x = (1, | )");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '|'. | syntax error "
                + "line 1 at position 26 unexpected 'b'.",
            "DESCRIBE TABLE t1 (a (| ) b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'NUMBER'. | syntax "
                + "error line 1 at position 27 unexpected '('.",
            "DESCRIBE TABLE t1 (a NUMBER(|))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'NUMBER'. | syntax "
                + "error line 1 at position 27 unexpected '('. | syntax error line 1 at position 30 unexpected '|'.",
            "DESCRIBE TABLE t1 (a NUMBER(1 | 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'NUMBER'. | syntax "
                + "error line 1 at position 27 unexpected '('.",
            "DESCRIBE TABLE t1 (a NUMBER(\u00e9))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '\u00e9 '.",
            "DROP TABLE t9 (\u00e9 b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'b'.",
            "DROP TABLE t9 (a b) | c");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '\u00e9'.",
            "DROP TABLE t9 (a) \u00e9");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '|'.",
            "DROP TABLE t9 (a) | c");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '('.",
            "DROP TABLE t9 ((a), \u00e9)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '^'.",
            "DROP TABLE t9 (a) ^ c");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '&'.",
            "DROP TABLE t9 (a) & c");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '\u00e9)'.",
            "DROP TABLE t9 (\u00e9)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '\u00e9 '.",
            "DROP TABLE t9 (a) \u00e9 CASCADE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '|'.",
            "DROP TABLE t9 (a) | CASCADE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '|'.",
            "DROP TABLE t9 (a) CASCADE |");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '\u00e9'.",
            "DROP TABLE t9 (a) CASCADE \u00e9");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '|'. | syntax error "
                + "line 1 at position 28 unexpected '|'.",
            "DESCRIBE TABLE t1 (a) x = y |");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '^'. | syntax error "
                + "line 1 at position 28 unexpected '^'.",
            "DESCRIBE TABLE t1 (a) x = y ^");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '&'. | syntax error "
                + "line 1 at position 28 unexpected '&'.",
            "DESCRIBE TABLE t1 (a) x = y &");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '$'. | syntax error "
                + "line 1 at position 28 unexpected '$'.",
            "DESCRIBE TABLE t1 (a) x = y $");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '#'. | syntax error "
                + "line 1 at position 28 unexpected '#'.",
            "DESCRIBE TABLE t1 (a) x = y #");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '\u00e9'. | syntax "
                + "error line 1 at position 28 unexpected '\u00e9'.",
            "DESCRIBE TABLE t1 (a) x = y \u00e9");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '+'. | syntax error "
                + "line 1 at position 28 unexpected '+'.",
            "DESCRIBE TABLE t1 (a) x = y +");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '<EOF>'. | syntax "
                + "error line 1 at position 28 unexpected '!'.",
            "DESCRIBE TABLE t1 (a) x = y !");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '1.5.6'. | syntax "
                + "error line 1 at position 28 unexpected '1.5.6'.",
            "DESCRIBE TABLE t1 (a) x = y 1.5.6");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '0x1F'. | syntax "
                + "error line 1 at position 28 unexpected '0x1F'.",
            "DESCRIBE TABLE t1 (a) x = y 0x1F");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'X'AB''. | syntax "
                + "error line 1 at position 28 unexpected 'X'AB''.",
            "DESCRIBE TABLE t1 (a) x = y X'AB'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ''a''.",
            "DESCRIBE TABLE t1 (a) x = y 'a'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '1'. | syntax error "
                + "line 1 at position 28 unexpected '1'.",
            "DESCRIBE TABLE t1 (a) x = y 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '\u00e9 '. | syntax "
                + "error line 1 at position 28 unexpected '\u00e9 '.",
            "DESCRIBE TABLE t1 (a) x = y \u00e9 z = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '\u00e9'. | syntax "
                + "error line 1 at position 27 unexpected '.'.",
            "DESCRIBE TABLE t1 (a) x = y.\u00e9");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '\u00e9'. | syntax "
                + "error line 1 at position 27 unexpected '.'.",
            "DESCRIBE TABLE t1 (a) x = y. \u00e9");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '|'. | syntax error "
                + "line 1 at position 27 unexpected '|'.",
            "DESCRIBE TABLE t1 (a) x = y|");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '#'. | syntax error "
                + "line 1 at position 27 unexpected '#'.",
            "DESCRIBE TABLE t1 (a) x = y#");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 (a) x = y$");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '|'. | syntax error "
                + "line 1 at position 28 unexpected '|'.",
            "DESCRIBE TABLE t1 (a) x = y | z = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '\u00e9)'. | syntax "
                + "error line 1 at position 29 unexpected '\u00e9)'.",
            "DESCRIBE TABLE t1 (a) x = (y \u00e9)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '|'. | syntax error "
                + "line 1 at position 29 unexpected '|'.",
            "DESCRIBE TABLE t1 (a) x = (y |)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '\u00e9)'. | syntax "
                + "error line 1 at position 32 unexpected '\u00e9)'.",
            "DESCRIBE TABLE t1 (a) x = (1, y \u00e9)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '|'. | syntax error "
                + "line 1 at position 32 unexpected '|'.",
            "DESCRIBE TABLE t1 (a) x = (1, y |)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '\u00e9)'.",
            "DESCRIBE TABLE t1 (a) x = (y, \u00e9)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '\u00e9 '.",
            "DESCRIBE TABLE t1 (a) x = \u00e9 y = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '|'.",
            "DESCRIBE TABLE t1 (a) x = | y = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '\u00e9 '.",
            "DESCRIBE TABLE t1 (a) x \u00e9 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '|'.",
            "DESCRIBE TABLE t1 (a) x | 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '='.",
            "DESCRIBE TABLE t1 (a) x = y = \u00e9");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '='.",
            "DESCRIBE TABLE t1 (a) x = 1 = \u00e9");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '\u00e9)'.",
            "DESCRIBE TABLE t1 (a \u00e9)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '|'.",
            "DESCRIBE TABLE t1 (a |)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'INT'. | syntax error "
                + "line 1 at position 25 unexpected '\u00e9)'.",
            "DESCRIBE TABLE t1 (a INT \u00e9)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'INT'. | syntax error "
                + "line 1 at position 25 unexpected '|'.",
            "DESCRIBE TABLE t1 (a INT |)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'NUMBER'. | syntax "
                + "error line 1 at position 27 unexpected '('. | syntax error line 1 at position 31 unexpected "
                + "'\u00e9)'.",
            "DESCRIBE TABLE t1 (a NUMBER(1, \u00e9))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'NUMBER'. | syntax "
                + "error line 1 at position 27 unexpected '('. | syntax error line 1 at position 30 unexpected "
                + "'\u00e9)'.",
            "DESCRIBE TABLE t1 (a NUMBER(1 \u00e9))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '\u00e9)'. | syntax "
                + "error line 1 at position 28 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (TABLE(\u00e9))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '\u00e9)'.",
            "DESCRIBE TABLE t1 (TABLE(a \u00e9))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '\u00e9)'.",
            "DESCRIBE TABLE t1 (ARRAY(\u00e9))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '\u00e9)'.",
            "DESCRIBE TABLE t1 (a.\u00e9)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '\u00e9)'.",
            "DESCRIBE TABLE t1 (a. \u00e9)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '|'.",
            "DESCRIBE TABLE t1 (a.|)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '\u00e9.'. | syntax "
                + "error line 1 at position 22 unexpected ')'.",
            "DESCRIBE TABLE t1 (\u00e9.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '\u00e9'.",
            "DESCRIBE TABLE t1 (a) x = -\u00e9");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '\u00e9'.",
            "DESCRIBE TABLE t1 (a) x = - \u00e9");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '|'.",
            "DESCRIBE TABLE t1 (a) x = -|");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '\u00e9\u00e9'.",
            "DESCRIBE TABLE t1 (a) \u00e9\u00e9\u00e9");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '\u00e9\u00e9'. | "
                + "syntax error line 1 at position 24 unexpected ')'.",
            "DESCRIBE TABLE t1 (\u00e9\u00e9\u00e9 b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '\u00e9\u00e9'.",
            "DESCRIBE TABLE t1 (a) x = \u00e9\u00e9\u00e9");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '#a'. | syntax error "
                + "line 1 at position 23 unexpected ')'.",
            "DESCRIBE TABLE t1 (#a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '\\a'. | syntax error "
                + "line 1 at position 23 unexpected ')'.",
            "DESCRIBE TABLE t1 (\\a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '#y'.",
            "DESCRIBE TABLE t1 (a) x = #y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '\\y'.",
            "DESCRIBE TABLE t1 (a) x = \\y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '#z'. | syntax error "
                + "line 1 at position 27 unexpected '#z'.",
            "DESCRIBE TABLE t1 (a) x = y#z");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '$'.",
            "DESCRIBE TABLE t1 (a) x = $");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '$'.",
            "DESCRIBE TABLE t1 (a) x = $ y = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '$'.",
            "DESCRIBE TABLE t1 (a $)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '$'. | syntax error "
                + "line 1 at position 27 unexpected '.'.",
            "DESCRIBE TABLE t1 (a) x = y.$");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'b'.",
            "DESCRIBE TABLE t1 (`a` b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '`b`'.",
            "DESCRIBE TABLE t1 (a `b`)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = `b` y");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 28 near '<EOF>'. | syntax error line "
                + "1 at position 28 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = `b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '?'. | syntax error "
                + "line 1 at position 28 unexpected '?'.",
            "DESCRIBE TABLE t1 (a) x = y ?");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '-'. | syntax error "
                + "line 1 at position 28 unexpected '-'.",
            "DESCRIBE TABLE t1 (a) x = y -");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '@s'. | syntax error "
                + "line 1 at position 28 unexpected '@s'.",
            "DESCRIBE TABLE t1 (a) x = y @s");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '['. | syntax error "
                + "line 1 at position 28 unexpected '['.",
            "DESCRIBE TABLE t1 (a) x = y [");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '('.",
            "DESCRIBE TABLE t1 (a) x = y (");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ')'.",
            "DESCRIBE TABLE t1 (a) x = y )");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = y ,");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '<EOF>'. | syntax "
                + "error line 1 at position 28 unexpected '.'.",
            "DESCRIBE TABLE t1 (a) x = y .");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '='.",
            "DESCRIBE TABLE t1 (a) x = y =");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 (a) x = y ;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '\u00e9)'.",
            "DROP TABLE t9 (a \u00e9)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '|'.",
            "DROP TABLE t9 (a |)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '\u00e9)'.",
            "DROP TABLE t9 (a.\u00e9)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '\u00e9\u00e9'.",
            "DROP TABLE t9 (\u00e9\u00e9\u00e9)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '\u00e9\u00e9'.",
            "DROP TABLE t9 (a) \u00e9\u00e9\u00e9");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'b'.",
            "DROP TABLE t9 (`a` b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '`b`'.",
            "DROP TABLE t9 (a) `b`");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '#a'.",
            "DROP TABLE t9 (#a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '#b'.",
            "DROP TABLE t9 (a) #b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '$'.",
            "DROP TABLE t9 (a) $");
    }

    @Test
    public void numbersAndUnclosedLiterals() {
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '('. | syntax error "
                + "line 1 at position 21 unexpected ')'. | syntax error line 1 at position 25 unexpected ')'. | "
                + "parse error line 1 at position 35 near '<EOF>'.",
            "DESCRIBE TABLE t1 ((a), b) x = 'abc");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '('. | syntax error "
                + "line 1 at position 21 unexpected ')'. | syntax error line 1 at position 25 unexpected ')'. | "
                + "parse error line 1 at position 35 near '<EOF>'. | syntax error line 1 at position 35 unexpected "
                + "'<EOF>'.",
            "DESCRIBE TABLE t1 ((a), b) x = \"abc");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '('. | syntax error "
                + "line 1 at position 21 unexpected ')'. | parse error line 1 at position 31 near '<EOF>'. | syntax "
                + "error line 1 at position 25 unexpected ')'.",
            "DESCRIBE TABLE t1 ((a), b) /* x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'b'. | parse error "
                + "line 1 at position 31 near '<EOF>'.",
            "DESCRIBE TABLE t1 (a b) x = $$y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '1.5.6'.",
            "DESCRIBE TABLE t1 (a) x = 1.5.6");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '('. | syntax error "
                + "line 1 at position 21 unexpected ')'. | syntax error line 1 at position 25 unexpected ')'. | "
                + "parse error line 1 at position 33 near '<EOF>'. | syntax error line 1 at position 33 unexpected "
                + "'<EOF>'.",
            "DESCRIBE TABLE t1 ((a), b) x = 1e");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '('. | syntax error "
                + "line 1 at position 21 unexpected ')'. | syntax error line 1 at position 25 unexpected ')'.",
            "DESCRIBE TABLE t1 ((a), b) x = X'AB'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '1.5.6'.",
            "DESCRIBE TABLE t1 (a) x = 1.5.6.7");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '.2'.",
            "DESCRIBE TABLE t1 (a) x = 1..2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '.5'.",
            "DESCRIBE TABLE t1 (a) x = .5.5");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '.2'.",
            "DESCRIBE TABLE t1 (a) x = 1.5e3.2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = 1.a");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '.6'.",
            "DESCRIBE TABLE t1 (a) x = 1.5 .6");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '1.5.6'. | syntax "
                + "error line 1 at position 33 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = 1.5.6 y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '1.5.6'.",
            "DESCRIBE TABLE t1 (1.5.6)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'NUMBER'. | syntax "
                + "error line 1 at position 27 unexpected '('.",
            "DESCRIBE TABLE t1 (a NUMBER(1.5.6))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '1.5.6'. | syntax "
                + "error line 1 at position 34 unexpected '2'.",
            "DESCRIBE TABLE t1 (a) x = (1.5.6, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 (a) x = X'AB'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = X'AB' y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'X'AB''.",
            "DESCRIBE TABLE t1 (X'AB')");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 (a) x = x'ab'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '0x1F'.",
            "DESCRIBE TABLE t1 (a) x = 0x1F");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = 1_000");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 30 near '<EOF>'. | syntax error line "
                + "1 at position 30 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = 1.5.");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 (a) x = 1.");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '.3'.",
            "DESCRIBE TABLE t1 (a) x = 1.e2.3");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 28 near '<EOF>'. | syntax error line "
                + "1 at position 28 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = 1e");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 28 near '32'.",
            "DESCRIBE TABLE t1 (a) x = 1e y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'b'. | parse error "
                + "line 1 at position 30 near '<EOF>'. | syntax error line 1 at position 30 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a b) x = 1e");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 (a) x = 1e5");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '.6'.",
            "DESCRIBE TABLE t1 (a) x = 1e5.6");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 (a) x = .5");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 (a) x = 5.");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 30 near '<EOF>'. | syntax error line "
                + "1 at position 30 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = 1.2.");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 30 near '46'.",
            "DESCRIBE TABLE t1 (a) x = 1.2..3");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '1.2.3'.",
            "DESCRIBE TABLE t1 (a) x = 1.2.3.4.5");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 30 near '120'. | syntax error line 1 "
                + "at position 31 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = 1.2.x");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 30 near '<EOF>'. | syntax error line "
                + "1 at position 30 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = 1.2e");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 31 near '<EOF>'. | syntax error line "
                + "1 at position 31 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = 1.2e+");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = 12abc");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '1.5.6'.",
            "DESCRIBE TABLE t1 (a) x = 1.5.6, y = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'b'. | syntax error "
                + "line 1 at position 28 unexpected '1.5.6'.",
            "DESCRIBE TABLE t1 (a b) x = 1.5.6");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '1.5.6'.",
            "DESCRIBE TABLE t1 (a) x = -1.5.6");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '1.5.6'.",
            "DESCRIBE TABLE t1 (a) x = 1.5.6.");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 30 near '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = 'abc");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 30 near '<EOF>'. | syntax error line "
                + "1 at position 30 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = \"abc");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 26 near '<EOF>'.",
            "DESCRIBE TABLE t1 (a) /* x");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 29 near '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = $$y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'b'. | parse error "
                + "line 1 at position 32 near '<EOF>'.",
            "DESCRIBE TABLE t1 (a b) x = 'abc");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'b'. | parse error "
                + "line 1 at position 28 near '<EOF>'.",
            "DESCRIBE TABLE t1 (a b) /* x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'b'. | parse error "
                + "line 1 at position 32 near '<EOF>'. | syntax error line 1 at position 32 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a b) x = \"abc");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 30 near '<EOF>'. | syntax error line "
                + "1 at position 30 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = X'AB");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'b'. | parse error "
                + "line 1 at position 32 near '<EOF>'. | syntax error line 1 at position 32 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a b) x = X'AB");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '1.5.6'. | syntax "
                + "error line 1 at position 32 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = 1.5.6e");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'NUMBER'. | syntax "
                + "error line 1 at position 27 unexpected '('. | syntax error line 1 at position 32 unexpected ')'.",
            "DESCRIBE TABLE t1 (a NUMBER(1.5))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'NUMBER'. | syntax "
                + "error line 1 at position 27 unexpected '('. | syntax error line 1 at position 31 unexpected ')'.",
            "DESCRIBE TABLE t1 (a NUMBER(1.))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'NUMBER'. | syntax "
                + "error line 1 at position 27 unexpected '('. | syntax error line 1 at position 31 unexpected ')'.",
            "DESCRIBE TABLE t1 (a NUMBER(.5))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '1.5.6'.",
            "DESCRIBE TABLE t1 (a) x = (1.5.6)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '1.5.6'.",
            "DESCRIBE TABLE t1 (a) x = 1.5.6 = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '1.5.6'.",
            "DESCRIBE TABLE t1 (a) 1.5.6");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '1.5.6'.",
            "DESCRIBE TABLE t1 (a) 1.5.6 = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'b'.",
            "DESCRIBE TABLE t1 (a b) 1.5.6");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'b'. | parse error "
                + "line 1 at position 24 near '<EOF>'.",
            "DROP TABLE t9 (a b) /* x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'b'. | parse error "
                + "line 1 at position 24 near '<EOF>'.",
            "DROP TABLE t9 (a b) 'abc");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '1.5.6'.",
            "DROP TABLE t9 (a) 1.5.6");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'b'.",
            "DROP TABLE t9 (a b) 1.5.6");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '1.5.6'.",
            "DROP TABLE t9 (1.5.6)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'X'AB''.",
            "DROP TABLE t9 (X'AB')");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 22 near '<EOF>'.",
            "DROP TABLE t9 (a) /* x");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 22 near '<EOF>'. | syntax error line "
                + "1 at position 19 unexpected 'abc'.",
            "DROP TABLE t9 (a) 'abc");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'b'. | parse error "
                + "line 1 at position 24 near '<EOF>'.",
            "DROP TABLE t9 (a b) \"abc");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'b'. | parse error "
                + "line 1 at position 22 near '<EOF>'.",
            "DROP TABLE t9 (a b) 1e");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 20 near '<EOF>'.",
            "DROP TABLE t9 (a) 1e");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'X'AB''.",
            "DROP TABLE t9 (a) X'AB'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'b'.",
            "DROP TABLE t9 (a b) X'AB'");
    }

    @Test
    public void afterAPropertyListsComma() {
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '2'.",
            "DESCRIBE TABLE t1 (a) x = 1, 2, y = 3");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '2'.",
            "DESCRIBE TABLE t1 (a) x = 1, 2 y = 3");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '='.",
            "DESCRIBE TABLE t1 (a) x = 1, = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '='.",
            "DESCRIBE TABLE t1 (a) x = 1, = 2, y = 3");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '@s'.",
            "DESCRIBE TABLE t1 (a) x = 1, @s y = 3");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '@s'. | syntax error "
                + "line 1 at position 34 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = 1, @s, y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '+'.",
            "DESCRIBE TABLE t1 (a) x = 1, + y = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '('.",
            "DESCRIBE TABLE t1 (a) x = 1, (2), y = 3");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ''a''.",
            "DESCRIBE TABLE t1 (a) x = 1, 'a' = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '.'.",
            "DESCRIBE TABLE t1 (a) x = 1, . y = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ','.",
            "DESCRIBE TABLE t1 (a) x = 1,, y = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ';'.",
            "DESCRIBE TABLE t1 (a) x = 1, ; ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '='.",
            "DESCRIBE TABLE t1 (a) x = 1, = y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '='.",
            "DESCRIBE TABLE t1 (a) x = 1, = (2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '='. | syntax error "
                + "line 1 at position 31 unexpected ','. | syntax error line 1 at position 32 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = 1, = ,");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '='. | syntax error "
                + "line 1 at position 30 unexpected ','.",
            "DESCRIBE TABLE t1 (a) x = 1, =, y = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '2'.",
            "DESCRIBE TABLE t1 (a) x = 1, 2 = 3");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '2'. | syntax error "
                + "line 1 at position 32 unexpected '3'.",
            "DESCRIBE TABLE t1 (a) x = 1, 2, 3");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '\u00e9 '.",
            "DESCRIBE TABLE t1 (a) x = 1, \u00e9 y = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '|'.",
            "DESCRIBE TABLE t1 (a) x = 1, | y = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '\u00e9'.",
            "DESCRIBE TABLE t1 (a) x = 1, \u00e9");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '|'.",
            "DESCRIBE TABLE t1 (a) x = 1, |");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '1.5.6'.",
            "DESCRIBE TABLE t1 (a) x = 1, 1.5.6");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'NULL'.",
            "DESCRIBE TABLE t1 (a) x = 1, NULL = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '?'.",
            "DESCRIBE TABLE t1 (a) x = 1, ? = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '-'.",
            "DESCRIBE TABLE t1 (a) x = 1, - 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '('.",
            "DESCRIBE TABLE t1 (a) x = 1, (");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '('.",
            "DESCRIBE TABLE t1 (a) x = 1, ( y = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ')'.",
            "DESCRIBE TABLE t1 (a) x = 1, ) y = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '['.",
            "DESCRIBE TABLE t1 (a) x = 1, [ y = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '3'.",
            "DESCRIBE TABLE t1 (a) x = (1, 2), 3");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '='.",
            "DESCRIBE TABLE t1 (a) x = (1, 2), = 3");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'b'. | syntax error "
                + "line 1 at position 31 unexpected '2'.",
            "DESCRIBE TABLE t1 (a b) x = 1, 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'b'. | syntax error "
                + "line 1 at position 31 unexpected '='.",
            "DESCRIBE TABLE t1 (a b) x = 1, = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '2'.",
            "DESCRIBE TABLE t1 (a) x = y, 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '='.",
            "DESCRIBE TABLE t1 (a) x = y, = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '2'.",
            "DESCRIBE TABLE t1 (a) x = @s, 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '2'.",
            "DESCRIBE TABLE t1 (a) x = 'a', 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '2'. | syntax error "
                + "line 1 at position 31 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = 1, 2,");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '='. | syntax error "
                + "line 1 at position 30 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = 1, =");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '='.",
            "DESCRIBE TABLE t1 (a) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '='.",
            "DESCRIBE TABLE t1 (a) = 1, y = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '='.",
            "DESCRIBE TABLE t1 (a) =");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'b'.",
            "DESCRIBE TABLE t1 (a b) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '2'.",
            "DESCRIBE TABLE t1 x = 1, 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '='.",
            "DESCRIBE TABLE t1 x = 1, = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '+'.",
            "DESCRIBE TABLE t1 x = 1, +");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '='.",
            "DESCRIBE TABLE t1 = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '2'.",
            "DESCRIBE TABLE t1 x = 1, 2, y = 3");
    }

    @Test
    public void aTextTheLexerRefuses() {
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'b'. | parse error "
                + "line 1 at position 28 near '<EOF>'. | syntax error line 1 at position 28 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a b) 'abc");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 26 near '<EOF>'. | syntax error line "
                + "1 at position 26 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) 'abc");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 23 near '<EOF>'. | syntax error line "
                + "1 at position 23 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 ('abc");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 25 near '<EOF>'. | syntax error line "
                + "1 at position 22 unexpected 'abc'. | syntax error line 1 at position 25 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a 'abc");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 32 near '<EOF>'. | syntax error line "
                + "1 at position 32 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = 'abc y");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 32 near '<EOF>'. | syntax error line "
                + "1 at position 32 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = \"abc y");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 26 near '<EOF>'.",
            "DESCRIBE TABLE t1 (a) \"abc");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 25 near '<EOF>'. | syntax error line "
                + "1 at position 25 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a \"abc");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 23 near '<EOF>'. | syntax error line "
                + "1 at position 23 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (\"abc");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 34 near '<EOF>'. | syntax error line "
                + "1 at position 34 unexpected '<EOF>'. | syntax error line 1 at position 34 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = (1, 'abc");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 32 near '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = 1 /* y");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 25 near '<EOF>'. | syntax error line "
                + "1 at position 25 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a /* y");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 28 near '44'. | syntax error line 1 "
                + "at position 32 unexpected '='.",
            "DESCRIBE TABLE t1 (a) x = 1e, y = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 28 near '41'. | syntax error line 1 "
                + "at position 29 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = 1e)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'NUMBER'. | parse "
                + "error line 1 at position 30 near '41'. | syntax error line 1 at position 27 unexpected '('.",
            "DESCRIBE TABLE t1 (a NUMBER(1e))");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 30 near '121'. | syntax error line 1 "
                + "at position 31 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = 1.5.y");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 40 near '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = 'abc' y = 'def");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 25 near '<EOF>'. | syntax error line "
                + "1 at position 23 unexpected '$y'.",
            "DESCRIBE TABLE t1 (a) $$y");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 31 near '<EOF>'. | syntax error line "
                + "1 at position 31 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = $$y z");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 35 near '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = $$y z = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 34 near '<EOF>'. | syntax error line "
                + "1 at position 34 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = 'abc def");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 37 near '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = 'abc, y = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 37 near '<EOF>'. | syntax error line "
                + "1 at position 37 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = \"abc, y = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 28 near '32'. | syntax error line 1 "
                + "at position 31 unexpected '='.",
            "DESCRIBE TABLE t1 (a) x = 1e y = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 28 near '32'. | syntax error line 1 "
                + "at position 29 unexpected '='.",
            "DESCRIBE TABLE t1 (a) x = 1e = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 30 near '44'. | syntax error line 1 "
                + "at position 34 unexpected '='.",
            "DESCRIBE TABLE t1 (a) x = 1.5., y = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 30 near '41'. | syntax error line 1 "
                + "at position 31 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = 1.5.)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'b'. | parse error "
                + "line 1 at position 32 near '<EOF>'. | syntax error line 1 at position 32 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a b) x = 1.5.");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 29 near '44'.",
            "DESCRIBE TABLE t1 (a) x = (1e, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 32 near '41'. | syntax error line 1 "
                + "at position 33 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = (1, 1e)");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 21 near '41'. | syntax error line 1 "
                + "at position 22 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (1e)");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 24 near '<EOF>'.",
            "DESCRIBE TABLE t1 (a) 1e");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 24 near '32'. | syntax error line 1 "
                + "at position 25 unexpected '='.",
            "DESCRIBE TABLE t1 (a) 1e = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'b'. | parse error "
                + "line 1 at position 26 near '<EOF>'.",
            "DESCRIBE TABLE t1 (a b) 1e");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 23 near '<EOF>'. | syntax error line "
                + "1 at position 23 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (/* x");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 26 near '<EOF>'. | syntax error line "
                + "1 at position 26 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a, /* x");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 28 near '<EOF>'. | syntax error line "
                + "1 at position 28 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x /* y");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 30 near '<EOF>'. | syntax error line "
                + "1 at position 30 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = /* y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '''.",
            "DESCRIBE TABLE t1 (a) x = '");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '\"'.",
            "DESCRIBE TABLE t1 (a) x = \"");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '''.",
            "DESCRIBE TABLE t1 (a) '");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '\"'.",
            "DESCRIBE TABLE t1 (a) \"");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 31 near '<EOF>'. | syntax error line "
                + "1 at position 28 unexpected ''''.",
            "DESCRIBE TABLE t1 (a) x = 'a''b");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 31 near '<EOF>'. | syntax error line "
                + "1 at position 31 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = \"a\"\"b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'b'. | syntax error "
                + "line 1 at position 23 unexpected 'c'. | syntax error line 1 at position 24 unexpected ')'. | "
                + "parse error line 1 at position 34 near '<EOF>'.",
            "DESCRIBE TABLE t1 (a b c) x = \"abc");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '('. | syntax error "
                + "line 1 at position 21 unexpected ')'. | syntax error line 1 at position 24 unexpected ')'. | "
                + "parse error line 1 at position 34 near '<EOF>'. | syntax error line 1 at position 34 unexpected "
                + "'<EOF>'.",
            "DESCRIBE TABLE t1 ((a) b) x = \"abc");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '('. | syntax error "
                + "line 1 at position 21 unexpected ')'. | parse error line 1 at position 30 near '<EOF>'. | syntax "
                + "error line 1 at position 24 unexpected ')'. | syntax error line 1 at position 30 unexpected "
                + "'<EOF>'.",
            "DESCRIBE TABLE t1 ((a) b) 'abc");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = 1e5e");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 29 near '<EOF>'. | syntax error line "
                + "1 at position 29 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = 1.e");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 29 near '<EOF>'. | syntax error line "
                + "1 at position 29 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = .5e");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 29 near '<EOF>'. | syntax error line "
                + "1 at position 29 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = 1e+");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 29 near '32'.",
            "DESCRIBE TABLE t1 (a) x = 1e+ y");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 28 near '97'. | syntax error line 1 "
                + "at position 29 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = 1ea");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = 1e1e");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 32 near '<EOF>'. | syntax error line "
                + "1 at position 32 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = X'AB y");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 35 near '<EOF>'. | syntax error line "
                + "1 at position 35 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = X'AB' 'cd");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 28 near '<EOF>'. | syntax error line "
                + "1 at position 27 unexpected '$'.",
            "DESCRIBE TABLE t1 (a) x = $$");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 24 near '<EOF>'. | syntax error line "
                + "1 at position 23 unexpected '$'.",
            "DESCRIBE TABLE t1 (a) $$");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 30 near '<EOF>'. | syntax error line "
                + "1 at position 29 unexpected '$'.",
            "DESCRIBE TABLE t1 (a) x = $$y$");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'b'. | parse error "
                + "line 1 at position 27 near '<EOF>'.",
            "DESCRIBE TABLE t1 (a b) $$y");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 32 near '<EOF>'. | syntax error line "
                + "1 at position 32 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = y 'abc");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 32 near '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = y \"abc");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 32 near '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = y /* z");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 30 near '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = y 1e");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 32 near '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = y 1.5.");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 22 near '<EOF>'.",
            "DROP TABLE t9 (a) \"abc");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 19 near '<EOF>'. | syntax error line "
                + "1 at position 19 unexpected '<EOF>'.",
            "DROP TABLE t9 ('abc");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 21 near '<EOF>'. | syntax error line "
                + "1 at position 19 unexpected '$y'.",
            "DROP TABLE t9 (a) $$y");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 26 near '<EOF>'. | syntax error line "
                + "1 at position 19 unexpected 'abc'.",
            "DROP TABLE t9 (a) 'abc def");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 19 near '<EOF>'. | syntax error line "
                + "1 at position 19 unexpected '<EOF>'.",
            "DROP TABLE t9 (\"abc");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 21 near '<EOF>'. | syntax error line "
                + "1 at position 21 unexpected '<EOF>'.",
            "DROP TABLE t9 (a \"abc");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 21 near '<EOF>'. | syntax error line "
                + "1 at position 18 unexpected 'abc'.",
            "DROP TABLE t9 (a 'abc");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 19 near '<EOF>'. | syntax error line "
                + "1 at position 19 unexpected '<EOF>'.",
            "DROP TABLE t9 (/* x");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 20 near '32'. | syntax error line 1 "
                + "at position 21 unexpected 'y'.",
            "DROP TABLE t9 (a) 1e y");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 22 near '<EOF>'.",
            "DROP TABLE t9 (a) 1.5.");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 17 near '41'. | syntax error line 1 "
                + "at position 18 unexpected '<EOF>'.",
            "DROP TABLE t9 (1e)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'b'. | parse error "
                + "line 1 at position 23 near '<EOF>'.",
            "DROP TABLE t9 (a b) $$y");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 30 near '<EOF>'. | syntax error line "
                + "1 at position 19 unexpected 'abc'.",
            "DROP TABLE t9 (a) 'abc CASCADE");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 30 near '<EOF>'.",
            "DROP TABLE t9 (a) \"abc CASCADE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'y'.",
            "DROP TABLE t9 (a) /* x */ y");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 23 near '<EOF>'. | syntax error line "
                + "1 at position 19 unexpected 'a'.",
            "DROP TABLE t9 (a) 'a''b");
    }
}
