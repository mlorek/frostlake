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

package dev.frostlake.parser;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The lines stacked around the refusal of a name with an empty middle part: the dot line a three-part column
 * reference adds in a select item's call, the line a CAST operand repeats, the lines of a qualified star over
 * {@code db..t.c}, the faults found after such a name, and the lines a Snowflake Scripting block adds.
 */
public class EmptyPartStackedLinesTest extends BaseDatabaseTest {

    /** The refusal's lines, each as "position token" or "line:position token", separated by " / ". */
    private String lines(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        final StringBuilder out = new StringBuilder();
        for (final String line : String.valueOf(refused.getMessage()).split("\n")) {
            if (!line.startsWith("syntax error line ")) {
                continue;
            }
            final String place = line.substring("syntax error line ".length(), line.indexOf(" unexpected "));
            final String lineNumber = place.substring(0, place.indexOf(' '));
            final String position = place.substring(place.lastIndexOf(' ') + 1);
            final String token = line.substring(line.indexOf(" unexpected '") + " unexpected '".length(),
                line.length() - 2);
            out.append(out.length() > 0 ? " / " : "")
                .append("1".equals(lineNumber) ? "" : lineNumber + ":").append(position).append(' ').append(token);
        }
        return out.toString();
    }

    @Test
    public void aReferenceLeadingASelectItemsCallAddsItsDot() {
        assertEquals("17 ) / 14 .", lines("SELECT COUNT(T..x) FROM T"));
        assertEquals("17 ) / 14 .", lines("SELECT COUNT(T..x)"));
        assertEquals("17 , / 14 .", lines("SELECT COUNT(T..x, 1) FROM T"));
        assertEquals("18 ) / 15 .", lines("SELECT ABS(y, T..x) FROM T"));
        assertEquals("12 ) / 9 .", lines("SELECT (T..x) FROM T"));
        assertEquals("13 + / 9 .", lines("SELECT (T..x + 1) FROM T"));
        assertEquals("15 ] / 12 .", lines("SELECT [1, T..x] FROM T"));
        assertEquals("21 ) / 18 .", lines("SELECT 1 + COUNT(T..x) FROM T"));
        assertEquals("17 ) / 14 .", lines("SELECT COUNT(T..x) OVER () FROM T"));
        assertEquals("16 + / 12 .", lines("SELECT ABS(T..x + 1) FROM T"));
        assertEquals("15 ) / 12 .", lines("SELECT ABS(T..x) IS NULL FROM T"));
        assertEquals("16 ) / 13 .", lines("SELECT -ABS(T..x) FROM T"));
        assertEquals("29 ) / 26 .", lines("INSERT INTO T SELECT ABS(T..x), 1 FROM T"));
        assertEquals("45 ) / 42 .", lines("SELECT 1 FROM T WHERE EXISTS (SELECT ABS(T..x) FROM T)"));
        assertEquals("26 ) / 23 .", lines("WITH c AS (SELECT ABS(T..x) FROM T) SELECT 1"));
        assertEquals("30 ) / 27 . / 30 )", lines("SELECT * FROM (SELECT ABS(T..x) FROM T)"));
        assertEquals("17 ) / 14 .", lines("SELECT COUNT(T..x) FROM T WHERE"));
    }

    @Test
    public void elsewhereTheReferenceKeepsOneLine() {
        assertEquals("19 )", lines("SELECT ABS(ABS(T..x)) FROM T"));
        assertEquals("16 )", lines("SELECT (ABS(T..x)) FROM T"));
        assertEquals("19 )", lines("SELECT ABS(1 + T..x) FROM T"));
        assertEquals("32 )", lines("SELECT 1 FROM T WHERE COUNT(T..x) > 0"));
        assertEquals("33 )", lines("SELECT 1 FROM T ORDER BY ABS(T..x)"));
        assertEquals("11 ::", lines("SELECT T..x::INT FROM T"));
        assertEquals("12 FROM", lines("SELECT T..x FROM T"));
    }

    @Test
    public void aCastOperandRepeatsItsLine() {
        assertEquals("17 AS / 17 AS", lines("SELECT CAST(T..x AS INT) FROM T"));
        assertEquals("21 AS / 21 AS", lines("SELECT TRY_CAST(T..x AS INT) FROM T"));
        assertEquals("17 AS / 17 AS", lines("SELECT CAST(T..x AS INT) + 1 FROM T"));
        assertEquals("17 AS / 17 AS", lines("SELECT CAST(T..x AS INT) FROM T WHERE"));
    }

    @Test
    public void faultsAfterTheNameAreReportedAfterIt() {
        assertEquals("12 FROM / 16 <EOF>", lines("SELECT T..x FROM"));
        assertEquals("12 FROM / 24 <EOF>", lines("SELECT T..x FROM T WHERE"));
        assertEquals("12 FROM / 33 )", lines("SELECT T..x FROM T WHERE ABS(T..x) > 0"));
        assertEquals("12 FROM / 33 ) / 42 <EOF>", lines("SELECT T..x FROM T WHERE ABS(T..y) > 0 AND"));
        assertEquals("20 . / 27 <EOF>", lines("SELECT P440B_DB..T.x.y FROM"));
    }

    @Test
    public void aStarOverAFourPartNameIsRefusedAtItsDotAndAfterIt() {
        assertEquals("20 . / 23 FROM", lines("SELECT P440B_DB..T.x.* FROM P440B_DB..T"));
        assertEquals("20 . / 22 ,", lines("SELECT P440B_DB..T.x.*, 1 FROM P440B_DB..T"));
        assertEquals("20 . / 22 <EOF>", lines("SELECT P440B_DB..T.x.*"));
        assertEquals("20 . / 23 AS", lines("SELECT P440B_DB..T.x.* AS z FROM P440B_DB..T"));
        assertEquals("20 . / 29 'a'", lines("SELECT P440B_DB..T.x.* ILIKE 'a' FROM P440B_DB..T"));
        assertEquals("20 .", lines("SELECT P440B_DB..T.x.* EXCLUDE a FROM P440B_DB..T"));
        assertEquals("20 . / 22 .", lines("SELECT P440B_DB..T.x.y.* FROM P440B_DB..T"));
        assertEquals("26 . / 28 )", lines("SELECT COUNT(P440B_DB..T.x.*) FROM P440B_DB..T"));
        assertEquals("13 . / 16 FROM", lines("SELECT T..x.y.* FROM T"));
        assertEquals("20 . / 22 , / 37 . / 40 FROM",
            lines("SELECT P440B_DB..T.x.*, P440B_DB..T.y.* FROM P440B_DB..T"));
        assertEquals("20 . / 23 FROM / 27 <EOF>", lines("SELECT P440B_DB..T.x.* FROM"));
    }

    @Test
    public void aBlockStatementRepeatsTheFirstLine() {
        assertEquals("2:14 FROM / 2:14 FROM", lines("""
            BEGIN
              SELECT T..x FROM T;
            END"""));
        assertEquals("2:13 , / 2:13 ,", lines("""
            BEGIN
              SELECT T..x, T..y FROM T;
            END"""));
        assertEquals("2:14 FROM / 2:14 FROM", lines("""
            BEGIN
              SELECT T..x FROM T WHERE;
            END"""));
        assertEquals("4:14 FROM / 4:14 FROM", lines("""
            DECLARE
              c INT;
            BEGIN
              SELECT T..x FROM T;
            END"""));
        assertEquals("2:14 FROM / 2:14 FROM", lines("""
            BEGIN
              SELECT T..x FROM T;
            EXCEPTION WHEN OTHER THEN RETURN 1;
            END"""));
        assertEquals("2:34 FROM / 2:34 FROM", lines("""
            BEGIN
              LET r RESULTSET := (SELECT T..x FROM T);
              RETURN 1;
            END"""));
        assertEquals("2:17 ) / 2:14 . / 2:17 )", lines("""
            BEGIN
              SELECT ABS(T..x) FROM T;
              RETURN 1;
            END"""));
        assertEquals("2:19 AS / 2:19 AS / 2:25 )", lines("""
            BEGIN
              SELECT CAST(T..x AS INT) FROM T;
            END"""));
        assertEquals("2:13 ; / 3:2 RETURN", lines("""
            BEGIN
              SELECT T..x;
              RETURN 1;
            END"""));
        assertEquals("2:13 ; / 3:0 END", lines("""
            BEGIN
              SELECT T..x;
            END"""));
    }

    @Test
    public void aNestedBodyEndsWithTheTokenAfterTheStatement() {
        assertEquals("3:16 FROM / 3:16 FROM / 4:2 END", lines("""
            BEGIN
              WHILE (TRUE) DO
                SELECT T..x FROM T;
              END WHILE;
            END"""));
        assertEquals("3:16 FROM / 3:16 FROM / 4:2 END", lines("""
            BEGIN
              LOOP
                SELECT T..x FROM T;
              END LOOP;
            END"""));
        assertEquals("3:16 FROM / 3:16 FROM / 4:2 END", lines("""
            BEGIN
              BEGIN
                SELECT T..x FROM T;
              END;
            END"""));
        assertEquals("3:16 FROM / 4:2 END", lines("""
            BEGIN
              IF (TRUE) THEN
                SELECT T..x FROM T;
              END IF;
            END"""));
        assertEquals("3:16 FROM / 4:4 RETURN", lines("""
            BEGIN
              IF (TRUE) THEN
                SELECT T..x FROM T;
                RETURN 2;
              END IF;
            END"""));
        assertEquals("3:16 FROM / 4:2 UNTIL", lines("""
            BEGIN
              REPEAT
                SELECT T..x FROM T;
              UNTIL (TRUE)
              END REPEAT;
            END"""));
    }

    @Test
    public void aStatementRefusedAtItsSemicolonInABodyEndsPastTheConstruct() {
        assertEquals("3:15 ; / 5:0 END", lines("""
            BEGIN
              IF (TRUE) THEN
                SELECT T..x;
              END IF;
            END"""));
        assertEquals("3:15 ; / 4:6 FOR", lines("""
            BEGIN
              FOR i IN 1 TO 2 DO
                SELECT T..x;
              END FOR;
            END"""));
    }
}
