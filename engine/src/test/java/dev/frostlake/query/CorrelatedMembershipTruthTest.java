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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a membership over a query answers where nothing matches. A query that reads the row around it is matched
 * row against row, as a join is: its {@code IN} and its {@code = ANY} are TRUE for a match and FALSE for
 * everything else, a NULL probe and a NULL in the set included, while its {@code NOT IN} and its {@code ALL}
 * keep the three values. A query that reads no outer row keeps them throughout. An empty set holds nothing —
 * FALSE for IN, TRUE for NOT IN — whatever the probe read from a row; a NULL probe that reads no column is
 * folded with its membership and is UNKNOWN whatever the set. Every row is live-verified.
 */
public class CorrelatedMembershipTruthTest extends BaseDatabaseTest {

    /** A statement, and its answer or the words it is refused in. */
    private static final String[][] MEMBERSHIPS = {
        {"SELECT id, id IN (SELECT MAX(g.id) FROM g WHERE g.id = fz.id) FROM fz ORDER BY id", "5|true ;; 7|false"},
        {"SELECT id, id NOT IN (SELECT MAX(g.id) FROM g WHERE g.id = fz.id) FROM fz ORDER BY id", "5|false ;; 7|null"},
        {"SELECT id, id IN (SELECT COUNT(*) FROM g WHERE g.id = fz.id) FROM fz ORDER BY id", "5|false ;; 7|false"},
        {"SELECT id, id IN (SELECT MAX(g.id) FROM g WHERE g.id = fn.id) FROM fn ORDER BY id",
            "0|false ;; 2|false ;; 5|true"},
        {"SELECT id, n IN (SELECT MAX(g.id) FROM g WHERE g.id = fn.id) FROM fn ORDER BY id",
            "0|false ;; 2|false ;; 5|false"},
        {"SELECT id, id IN (SELECT SUM(g.id) FROM g WHERE g.id = fz.id) FROM fz ORDER BY id", "5|true ;; 7|false"},
        {"SELECT id, id IN (SELECT MAX(g.id) + 0 FROM g WHERE g.id = fz.id) FROM fz ORDER BY id", "5|true ;; 7|false"},
        {"SELECT id, id IN (SELECT g.id FROM g WHERE g.id = fz.id) FROM fz ORDER BY id", "5|true ;; 7|false"},
        {"SELECT id, id IN (SELECT NULL FROM g WHERE g.id = fz.id) FROM fz ORDER BY id", "5|false ;; 7|false"},
        {"SELECT id, id IN (SELECT MAX(g.id) FROM g WHERE g.id = fz.id GROUP BY g.s) FROM fz ORDER BY id",
            "5|true ;; 7|false"},
        {"SELECT id, id IN (SELECT MAX(g.id) FROM g WHERE g.id = fz.id HAVING COUNT(*) >= 0) FROM fz ORDER BY id",
            "5|true ;; 7|false"},
        {"SELECT id FROM fz WHERE id NOT IN (SELECT MAX(g.id) FROM g WHERE g.id = fz.id) ORDER BY id", ""},
        {"SELECT id, id IN (SELECT MAX(g.id) FROM g WHERE g.id = 99) FROM fz ORDER BY id", "5|null ;; 7|null"},
        {"SELECT id, id IN (SELECT MAX(g.id) FROM g WHERE FALSE) FROM fz ORDER BY id", "5|null ;; 7|null"},
        {"SELECT id, k, id IN (SELECT gn.id FROM gn WHERE gn.k = fo.k) FROM fo ORDER BY k, id",
            "5|a|true ;; null|a|false ;; 7|b|false ;; null|b|false ;; 9|d|false"},
        {"SELECT id, k, id NOT IN (SELECT gn.id FROM gn WHERE gn.k = fo.k) FROM fo ORDER BY k, id",
            "5|a|false ;; null|a|null ;; 7|b|true ;; null|b|true ;; 9|d|null"},
        {"SELECT id, k, id IN (SELECT gn.id FROM gn WHERE gn.k = 'a') FROM fo ORDER BY k, id",
            "5|a|true ;; null|a|null ;; 7|b|null ;; null|b|null ;; 9|d|null"},
        {"SELECT id, k, id IN (SELECT gn.id FROM gn WHERE gn.k = 'd') FROM fo ORDER BY k, id",
            "5|a|null ;; null|a|null ;; 7|b|null ;; null|b|null ;; 9|d|null"},
        {"SELECT id, k, id IN (SELECT gn.id FROM gn WHERE gn.k = 'zz') FROM fo ORDER BY k, id",
            "5|a|false ;; null|a|false ;; 7|b|false ;; null|b|false ;; 9|d|false"},
        {"SELECT id, k, id IN (SELECT gn.id FROM gn WHERE gn.k > fo.k) FROM fo ORDER BY k, id",
            "5|a|false ;; null|a|false ;; 7|b|false ;; null|b|false ;; 9|d|false"},
        {"SELECT id, k, id IN (SELECT gn.id FROM gn WHERE gn.k = fo.k AND gn.id IS NOT NULL) FROM fo ORDER BY k, id",
            "5|a|true ;; null|a|false ;; 7|b|false ;; null|b|false ;; 9|d|false"},
        {"SELECT id, k, id IN (SELECT gn.id + 0 FROM gn WHERE gn.k = fo.k) FROM fo ORDER BY k, id",
            "5|a|true ;; null|a|false ;; 7|b|false ;; null|b|false ;; 9|d|false"},
        {"SELECT id, k, id IN (SELECT NULL FROM gn WHERE gn.k = fo.k) FROM fo ORDER BY k, id",
            "5|a|false ;; null|a|false ;; 7|b|false ;; null|b|false ;; 9|d|false"},
        {"SELECT id, k, id IN (SELECT fo.id FROM gn WHERE gn.k = fo.k) FROM fo ORDER BY k, id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 21"},
        {"SELECT id, k, EXISTS (SELECT 1 FROM gn WHERE gn.k = fo.k AND gn.id = fo.id) FROM fo ORDER BY k, id",
            "5|a|true ;; null|a|false ;; 7|b|false ;; null|b|false ;; 9|d|false"},
        {"SELECT id, k, CASE WHEN id IN (SELECT gn.id FROM gn WHERE gn.k = fo.k) THEN 'in' WHEN NOT (id IN "
            + "(SELECT gn.id FROM gn WHERE gn.k = fo.k)) THEN 'out' ELSE 'unknown' END FROM fo ORDER BY k, id",
            "5|a|in ;; null|a|out ;; 7|b|out ;; null|b|out ;; 9|d|out"},
        {"SELECT id, k, (id IN (SELECT gn.id FROM gn WHERE gn.k = fo.k)) IS NULL FROM fo ORDER BY k, id",
            "5|a|false ;; null|a|false ;; 7|b|false ;; null|b|false ;; 9|d|false"},
        {"SELECT id, k, NOT (id IN (SELECT gn.id FROM gn WHERE gn.k = fo.k)) FROM fo ORDER BY k, id",
            "5|a|false ;; null|a|true ;; 7|b|true ;; null|b|true ;; 9|d|true"},
        {"SELECT id, k FROM fo WHERE id IN (SELECT gn.id FROM gn WHERE gn.k = fo.k) ORDER BY k, id", "5|a"},
        {"SELECT id, k FROM fo WHERE NOT (id IN (SELECT gn.id FROM gn WHERE gn.k = fo.k)) ORDER BY k, id",
            "null|a ;; 7|b ;; null|b ;; 9|d"},
        {"SELECT id, k FROM fo WHERE id NOT IN (SELECT gn.id FROM gn WHERE gn.k = fo.k) ORDER BY k, id",
            "7|b ;; null|b"},
        {"SELECT id, k FROM fo WHERE (id IN (SELECT gn.id FROM gn WHERE gn.k = fo.k)) IS NULL ORDER BY k, id", ""},
        {"SELECT id, k FROM fo WHERE id IN (SELECT gn.id FROM gn WHERE gn.k = fo.k) OR k = 'd' ORDER BY k, id",
            "5|a ;; 9|d"},
        {"SELECT id, k FROM fo WHERE NOT (id IN (SELECT gn.id FROM gn WHERE gn.k = fo.k) OR k = 'zz') ORDER BY k, id",
            "null|a ;; 7|b ;; null|b ;; 9|d"},
        {"SELECT id FROM fn WHERE id IN (SELECT COUNT(*) FROM g WHERE g.id = fn.id) ORDER BY id", "0"},
        {"SELECT id FROM fn WHERE id NOT IN (SELECT COUNT(*) FROM g WHERE g.id = fn.id) ORDER BY id", "2 ;; 5"},
        {"SELECT id FROM fn WHERE 0 = (SELECT COUNT(*) FROM g WHERE g.id = fn.id) ORDER BY id", "0 ;; 2"},
        {"SELECT id, 0 = (SELECT COUNT(*) FROM g WHERE g.id = fn.id) FROM fn ORDER BY id",
            "0|true ;; 2|true ;; 5|false"},
        {"SELECT id, 1 IN (SELECT COUNT(*) FROM g WHERE g.id = fn.id) FROM fn ORDER BY id",
            "0|false ;; 2|false ;; 5|true"},
        {"SELECT id, id NOT IN (SELECT MAX(g.id) FROM g WHERE g.id = fn.id) FROM fn ORDER BY id",
            "0|null ;; 2|null ;; 5|false"},
        {"SELECT id, id NOT IN (SELECT SUM(g.id) FROM g WHERE g.id = fn.id) FROM fn ORDER BY id",
            "0|null ;; 2|null ;; 5|false"},
        {"SELECT id, id IN (SELECT MIN(g.id) FROM g WHERE g.id = fn.id) FROM fn ORDER BY id",
            "0|false ;; 2|false ;; 5|true"},
        {"SELECT id, id IN (SELECT ANY_VALUE(g.id) FROM g WHERE g.id = fn.id) FROM fn ORDER BY id",
            "0|false ;; 2|false ;; 5|true"},
        {"SELECT id, id IN (SELECT MAX(g.id) FROM g WHERE g.id = fn.id AND g.v > 0) FROM fn ORDER BY id",
            "0|false ;; 2|false ;; 5|true"},
        {"SELECT id, id IN (SELECT MAX(g.id) FROM g JOIN gn ON g.id = gn.id WHERE g.id = fn.id) FROM fn ORDER BY id",
            "0|false ;; 2|false ;; 5|true"},
        {"SELECT id, n IN (SELECT MAX(g.id) FROM g WHERE g.id = fn.id) IS NULL FROM fn ORDER BY id",
            "REFUSED syntax error line 1 at position 61 unexpected 'IS'."},
        {"SELECT id, id IN (SELECT MAX(g.id) FROM g WHERE g.id = fn.id) IS NULL FROM fn ORDER BY id",
            "REFUSED syntax error line 1 at position 62 unexpected 'IS'."},
        {"SELECT id, NOT (id IN (SELECT MAX(g.id) FROM g WHERE g.id = fn.id)) FROM fn ORDER BY id",
            "0|true ;; 2|true ;; 5|false"},
        {"SELECT id FROM fn WHERE NOT (id IN (SELECT MAX(g.id) FROM g WHERE g.id = fn.id)) ORDER BY id", "0 ;; 2"},
        {"SELECT id FROM fn WHERE id NOT IN (SELECT MAX(g.id) FROM g WHERE g.id = fn.id) ORDER BY id", ""},
        {"SELECT id FROM fn WHERE (id IN (SELECT MAX(g.id) FROM g WHERE g.id = fn.id)) IS NULL ORDER BY id", ""},
        {"SELECT id, EXISTS (SELECT MAX(g.id) FROM g WHERE g.id = fn.id) FROM fn ORDER BY id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 11"},
        {"SELECT id, EXISTS (SELECT COUNT(*) FROM g WHERE g.id = fn.id) FROM fn ORDER BY id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 11"},
        {"SELECT id, (SELECT MAX(g.id) FROM g WHERE g.id = fn.id) IS NULL FROM fn ORDER BY id",
            "0|true ;; 2|true ;; 5|false"},
        {"SELECT NULL IN (SELECT gn.id FROM gn WHERE gn.k = 'zz')", "null"},
        {"SELECT 1 IN (SELECT gn.id FROM gn WHERE gn.k = 'zz')", "false"},
        {"SELECT 2 IN (SELECT gn.id FROM gn WHERE gn.k = 'd')", "null"},
        {"SELECT id, id IN (SELECT 1 WHERE FALSE) FROM fo ORDER BY k, id",
            "5|false ;; null|false ;; 7|false ;; null|false ;; 9|false"},
        {"SELECT id, id IN (SELECT gn.id FROM gn WHERE FALSE) FROM fo ORDER BY k, id",
            "5|false ;; null|false ;; 7|false ;; null|false ;; 9|false"},
        {"SELECT id, id IN (SELECT gn.id FROM gn WHERE gn.k = 'zz') FROM fo ORDER BY k, id",
            "5|false ;; null|false ;; 7|false ;; null|false ;; 9|false"},
        {"SELECT id, id + 0 IN (SELECT gn.id FROM gn WHERE gn.k = 'zz') FROM fo ORDER BY k, id",
            "5|false ;; null|false ;; 7|false ;; null|false ;; 9|false"},
        {"SELECT id, NULLIF(id, id) IN (SELECT gn.id FROM gn WHERE gn.k = 'zz') FROM fo ORDER BY k, id",
            "5|false ;; null|false ;; 7|false ;; null|false ;; 9|false"},
        {"SELECT id, id NOT IN (SELECT gn.id FROM gn WHERE gn.k = 'zz') FROM fo ORDER BY k, id",
            "5|true ;; null|true ;; 7|true ;; null|true ;; 9|true"},
        {"SELECT id, k FROM fo WHERE NOT (id IN (SELECT gn.id FROM gn WHERE gn.k = 'zz')) ORDER BY k, id",
            "5|a ;; null|a ;; 7|b ;; null|b ;; 9|d"},
        {"SELECT id, k FROM fo WHERE id NOT IN (SELECT gn.id FROM gn WHERE gn.k = 'zz') ORDER BY k, id",
            "5|a ;; null|a ;; 7|b ;; null|b ;; 9|d"},
        {"SELECT id, k FROM fo WHERE id NOT IN (SELECT gn.id FROM gn WHERE gn.k = 'a') ORDER BY k, id", ""},
        {"SELECT id, k FROM fo WHERE id NOT IN (SELECT gn.id FROM gn WHERE gn.k = 'c') ORDER BY k, id",
            "5|a ;; 7|b ;; 9|d"},
        {"SELECT id, id IN (SELECT gn.id FROM gn WHERE gn.k = 'c') FROM fo ORDER BY k, id",
            "5|false ;; null|null ;; 7|false ;; null|null ;; 9|false"},
        {"SELECT id, k, (id, k) IN (SELECT gn.id, gn.k FROM gn WHERE gn.k = fo.k) FROM fo ORDER BY k, id",
            "5|a|true ;; null|a|false ;; 7|b|false ;; null|b|false ;; 9|d|false"},
        {"SELECT id, k, id IN (SELECT gn.id FROM gn WHERE gn.k = fo.k) AND TRUE FROM fo ORDER BY k, id",
            "5|a|true ;; null|a|false ;; 7|b|false ;; null|b|false ;; 9|d|false"},
        {"SELECT id, k, COALESCE(id IN (SELECT gn.id FROM gn WHERE gn.k = fo.k), TRUE) FROM fo ORDER BY k, id",
            "5|a|true ;; null|a|false ;; 7|b|false ;; null|b|false ;; 9|d|false"},
        {"SELECT id, k, IFF(id IN (SELECT gn.id FROM gn WHERE gn.k = fo.k), 'y', 'n') FROM fo ORDER BY k, id",
            "5|a|y ;; null|a|n ;; 7|b|n ;; null|b|n ;; 9|d|n"},
        {"SELECT id, k, id IN (SELECT gn.id FROM gn WHERE gn.k = fo.k UNION ALL SELECT 7) FROM fo ORDER BY k, id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 21"},
        {"SELECT id, k, id IN (SELECT gn.id FROM gn WHERE gn.k = fo.k LIMIT 5) FROM fo ORDER BY k, id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 21"},
        {"SELECT id, k, id IN (SELECT DISTINCT gn.id FROM gn WHERE gn.k = fo.k) FROM fo ORDER BY k, id",
            "5|a|true ;; null|a|false ;; 7|b|false ;; null|b|false ;; 9|d|false"},
        {"SELECT id, k, id IN (SELECT MAX(gn.id) FROM gn WHERE gn.k = fo.k GROUP BY gn.k) FROM fo ORDER BY k, id",
            "5|a|true ;; null|a|false ;; 7|b|false ;; null|b|false ;; 9|d|false"},
        {"SELECT id, k, id IN (SELECT x.id FROM gn x JOIN gn y ON x.k = y.k WHERE x.k = fo.k) FROM fo ORDER BY k, id",
            "5|a|true ;; null|a|false ;; 7|b|false ;; null|b|false ;; 9|d|false"},
        {"SELECT id, k, k IN (SELECT gn.k FROM gn WHERE gn.id = fo.id) FROM fo ORDER BY k, id",
            "5|a|true ;; null|a|false ;; 7|b|false ;; null|b|false ;; 9|d|false"},
        {"SELECT id, k, id IN (SELECT gn.id FROM gn WHERE gn.id = fo.id) FROM fo ORDER BY k, id",
            "5|a|true ;; null|a|false ;; 7|b|false ;; null|b|false ;; 9|d|false"},
        {"SELECT id, k, id NOT IN (SELECT gn.id FROM gn WHERE gn.id = fo.id) FROM fo ORDER BY k, id",
            "5|a|false ;; null|a|true ;; 7|b|true ;; null|b|true ;; 9|d|true"},
        {"SELECT id, k, id NOT IN (SELECT gn.id FROM gn WHERE gn.k = fo.k AND gn.id IS NOT NULL) FROM fo "
            + "ORDER BY k, id",
            "5|a|false ;; null|a|null ;; 7|b|true ;; null|b|true ;; 9|d|true"},
        {"SELECT id, k, NOT (id NOT IN (SELECT gn.id FROM gn WHERE gn.k = fo.k)) FROM fo ORDER BY k, id",
            "5|a|true ;; null|a|null ;; 7|b|false ;; null|b|false ;; 9|d|null"},
        {"SELECT id, k, (SELECT COUNT(*) FROM gn WHERE gn.k = fo.k AND fo.id IN (SELECT g2.id FROM gn g2 "
            + "WHERE g2.k = gn.k)) FROM fo ORDER BY k, id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 15"},
        {"SELECT id, NULL IN (SELECT gn.id FROM gn WHERE gn.k = 'a') FROM fo ORDER BY k, id",
            "5|null ;; null|null ;; 7|null ;; null|null ;; 9|null"},
        {"SELECT id, 5 IN (SELECT gn.id FROM gn WHERE gn.k = 'a') FROM fo ORDER BY k, id",
            "5|true ;; null|true ;; 7|true ;; null|true ;; 9|true"},
        {"SELECT id, 99 IN (SELECT gn.id FROM gn WHERE gn.k = 'a') FROM fo ORDER BY k, id",
            "5|null ;; null|null ;; 7|null ;; null|null ;; 9|null"},
        {"SELECT id, NULL IN (SELECT gn.id FROM gn WHERE gn.k = fo.k) FROM fo ORDER BY k, id",
            "5|null ;; null|null ;; 7|null ;; null|null ;; 9|null"},
        {"SELECT id, 99 IN (SELECT gn.id FROM gn WHERE gn.k = fo.k) FROM fo ORDER BY k, id",
            "5|false ;; null|false ;; 7|false ;; null|false ;; 9|false"},
        {"SELECT id, NULL NOT IN (SELECT gn.id FROM gn WHERE gn.k = fo.k) FROM fo ORDER BY k, id",
            "5|null ;; null|null ;; 7|null ;; null|null ;; 9|null"},
        {"SELECT x, x IN (SELECT 1 WHERE FALSE) FROM (VALUES (NULL), (1)) t(x) ORDER BY x", "1|false ;; null|false"},
        {"SELECT x, x NOT IN (SELECT 1 WHERE FALSE) FROM (VALUES (NULL), (1)) t(x) ORDER BY x", "1|true ;; null|true"},
        {"SELECT x, x IN (SELECT gn.id FROM gn WHERE gn.k = 'a') FROM (VALUES (NULL), (5), (9)) t(x) ORDER BY x",
            "5|true ;; 9|null ;; null|null"},
        {"SELECT x, x IN (SELECT gn.id FROM gn WHERE gn.k = t.y) FROM (VALUES (NULL, 'a'), (5, 'a'), (9, "
            + "'a'), (9, 'zz')) t(x, y) ORDER BY x, y",
            "5|true ;; 9|false ;; 9|false ;; null|false"},
        {"SELECT id, k FROM fo WHERE id IN (SELECT gn.id FROM gn WHERE gn.k = fo.k) IS NULL ORDER BY k, id",
            "REFUSED syntax error line 1 at position 74 unexpected 'IS'."},
        {"SELECT id, k FROM fo WHERE (id IN (SELECT gn.id FROM gn WHERE gn.k = 'a')) IS NULL ORDER BY k, id",
            "null|a ;; 7|b ;; null|b ;; 9|d"},
        {"SELECT id, k, (id IN (SELECT gn.id FROM gn WHERE gn.k = 'a')) IS NULL FROM fo ORDER BY k, id",
            "5|a|false ;; null|a|true ;; 7|b|true ;; null|b|true ;; 9|d|true"},
        {"SELECT COUNT(*) FROM fo WHERE id IN (SELECT gn.id FROM gn WHERE gn.k = 'a') OR k = 'd'", "2"},
        {"SELECT id, NULLIF(1, 1) IN (SELECT gn.id FROM gn WHERE gn.k = fo.k) FROM fo ORDER BY k, id",
            "5|null ;; null|null ;; 7|null ;; null|null ;; 9|null"},
        {"SELECT id, k, (NULL, 'a') IN (SELECT gn.id, gn.k FROM gn WHERE gn.k = fo.k) FROM fo ORDER BY k, id",
            "5|a|false ;; null|a|false ;; 7|b|false ;; null|b|false ;; 9|d|false"},
        {"SELECT id, k, (5, 'a') IN (SELECT gn.id, gn.k FROM gn WHERE gn.k = fo.k) FROM fo ORDER BY k, id",
            "5|a|true ;; null|a|true ;; 7|b|false ;; null|b|false ;; 9|d|false"},
        {"SELECT id, k FROM fo WHERE NULL IN (SELECT gn.id FROM gn WHERE gn.k = fo.k) ORDER BY k, id", ""},
        {"SELECT id, k FROM fo WHERE (id IN (SELECT 1 WHERE FALSE)) IS NULL ORDER BY k, id", ""},
        {"SELECT id, k, id IN (SELECT gn.id FROM gn WHERE FALSE) FROM fo ORDER BY k, id",
            "5|a|false ;; null|a|false ;; 7|b|false ;; null|b|false ;; 9|d|false"},
        {"SELECT id, k, id NOT IN (SELECT gn.id FROM gn WHERE FALSE) FROM fo ORDER BY k, id",
            "5|a|true ;; null|a|true ;; 7|b|true ;; null|b|true ;; 9|d|true"},
    };

    private static final String[][] QUANTIFIED = {
        {"SELECT id, id = ANY (SELECT MAX(g.id) FROM g WHERE g.id = fz.id) FROM fz ORDER BY id", "5|true ;; 7|false"},
        {"SELECT id, k, id = ANY (SELECT gn.id FROM gn WHERE gn.k = fo.k) FROM fo ORDER BY k, id",
            "5|a|true ;; null|a|false ;; 7|b|false ;; null|b|false ;; 9|d|false"},
        {"SELECT id, k, id <> ALL (SELECT gn.id FROM gn WHERE gn.k = fo.k) FROM fo ORDER BY k, id",
            "5|a|false ;; null|a|null ;; 7|b|true ;; null|b|true ;; 9|d|null"},
        {"SELECT id, k, id > ANY (SELECT gn.id FROM gn WHERE gn.k = fo.k) FROM fo ORDER BY k, id",
            "5|a|false ;; null|a|false ;; 7|b|false ;; null|b|false ;; 9|d|false"},
        {"SELECT id, id <> ALL (SELECT MAX(g.id) FROM g WHERE g.id = fn.id) FROM fn ORDER BY id",
            "0|null ;; 2|null ;; 5|false"},
        {"SELECT 2 = ANY (SELECT gn.id FROM gn WHERE gn.k = 'd')", "null"},
        {"SELECT id, id = ANY (SELECT gn.id FROM gn WHERE gn.k = 'zz') FROM fo ORDER BY k, id",
            "5|false ;; null|false ;; 7|false ;; null|false ;; 9|false"},
        {"SELECT id, id <> ALL (SELECT gn.id FROM gn WHERE gn.k = 'zz') FROM fo ORDER BY k, id",
            "5|true ;; null|true ;; 7|true ;; null|true ;; 9|true"},
        {"SELECT id, k, id < ALL (SELECT gn.id FROM gn WHERE gn.k = fo.k) FROM fo ORDER BY k, id",
            "5|a|false ;; null|a|null ;; 7|b|true ;; null|b|true ;; 9|d|null"},
        {"SELECT id, k, id >= ALL (SELECT gn.id FROM gn WHERE gn.k = fo.k) FROM fo ORDER BY k, id",
            "5|a|null ;; null|a|null ;; 7|b|true ;; null|b|true ;; 9|d|null"},
        {"SELECT id, k, id < ANY (SELECT gn.id FROM gn WHERE gn.k = fo.k) FROM fo ORDER BY k, id",
            "5|a|false ;; null|a|false ;; 7|b|false ;; null|b|false ;; 9|d|false"},
        {"SELECT id, k, id <> ANY (SELECT gn.id FROM gn WHERE gn.k = fo.k) FROM fo ORDER BY k, id",
            "5|a|false ;; null|a|false ;; 7|b|false ;; null|b|false ;; 9|d|false"},
        {"SELECT id, k, id = ALL (SELECT gn.id FROM gn WHERE gn.k = fo.k) FROM fo ORDER BY k, id",
            "5|a|null ;; null|a|null ;; 7|b|true ;; null|b|true ;; 9|d|null"},
        {"SELECT id, k FROM fo WHERE id < ALL (SELECT gn.id FROM gn WHERE gn.k = fo.k) ORDER BY k, id",
            "7|b ;; null|b"},
        {"SELECT id, k FROM fo WHERE NOT (id < ANY (SELECT gn.id FROM gn WHERE gn.k = fo.k)) ORDER BY k, id",
            "5|a ;; null|a ;; 7|b ;; null|b ;; 9|d"},
        {"SELECT id, NULL = ANY (SELECT gn.id FROM gn WHERE gn.k = fo.k) FROM fo ORDER BY k, id",
            "5|null ;; null|null ;; 7|null ;; null|null ;; 9|null"},
        {"SELECT id, k FROM fo WHERE NOT (id = ANY (SELECT gn.id FROM gn WHERE gn.k = fo.k)) ORDER BY k, id",
            "null|a ;; 7|b ;; null|b ;; 9|d"},
        {"SELECT id, k FROM fo WHERE id <> ALL (SELECT gn.id FROM gn WHERE gn.k = fo.k) ORDER BY k, id",
            "7|b ;; null|b"},
        {"SELECT id, NULL <> ALL (SELECT gn.id FROM gn WHERE gn.k = fo.k) FROM fo ORDER BY k, id",
            "5|null ;; null|null ;; 7|null ;; null|null ;; 9|null"},
    };

    private static final String[][] CONSTANT_PROBES = {
        {"SELECT NULL IN (SELECT 1 WHERE FALSE)", "null"},
        {"SELECT CAST(NULL AS INT) IN (SELECT 1 WHERE FALSE)", "null"},
        {"SELECT NULL NOT IN (SELECT 1 WHERE FALSE)", "null"},
        {"SELECT NULL IN (SELECT 1)", "null"},
        {"SELECT 2 IN (SELECT NULL)", "null"},
        {"SELECT id, NULL IN (SELECT gn.id FROM gn WHERE FALSE) FROM fo ORDER BY k, id",
            "5|null ;; null|null ;; 7|null ;; null|null ;; 9|null"},
        {"SELECT id, NULL IN (SELECT 1 WHERE FALSE) FROM fo ORDER BY k, id",
            "5|null ;; null|null ;; 7|null ;; null|null ;; 9|null"},
        {"SELECT * FROM (SELECT NULL IN (SELECT 1 WHERE FALSE) AS y)", "null"},
        {"SELECT id, CAST(NULL AS INT) IN (SELECT gn.id FROM gn WHERE FALSE) FROM fo ORDER BY k, id",
            "5|null ;; null|null ;; 7|null ;; null|null ;; 9|null"},
        {"SELECT id, NULL = ANY (SELECT 1 WHERE FALSE) FROM fo ORDER BY k, id",
            "5|null ;; null|null ;; 7|null ;; null|null ;; 9|null"},
        {"SELECT id, NULL < ALL (SELECT 1 WHERE FALSE) FROM fo ORDER BY k, id",
            "5|null ;; null|null ;; 7|null ;; null|null ;; 9|null"},
        {"SELECT NULL = ANY (SELECT 1 WHERE FALSE)", "null"},
        {"SELECT NULL <> ALL (SELECT 1 WHERE FALSE)", "null"},
        {"SELECT 1 = ANY (SELECT 1 WHERE FALSE)", "false"},
        {"SELECT 1 <> ALL (SELECT 1 WHERE FALSE)", "true"},
        {"SELECT id, k FROM fo WHERE NOT (NULL IN (SELECT 1 WHERE FALSE)) ORDER BY k, id", ""},
        {"SELECT id, k FROM fo WHERE (NULL IN (SELECT 1 WHERE FALSE)) IS NULL ORDER BY k, id",
            "5|a ;; null|a ;; 7|b ;; null|b ;; 9|d"},
    };

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE fo (id INT, k VARCHAR)");
        engine.execute("INSERT INTO fo VALUES (5, 'a'), (7, 'b'), (9, 'd'), (NULL, 'a'), (NULL, 'b')");
        engine.execute("CREATE OR REPLACE TABLE gn (id INT, k VARCHAR)");
        engine.execute("INSERT INTO gn VALUES (NULL, 'a'), (5, 'a'), (6, 'c'), (NULL, 'd')");
        engine.execute("CREATE OR REPLACE TABLE fn (id INT, n INT)");
        engine.execute("INSERT INTO fn VALUES (0, NULL), (2, 1), (5, NULL)");
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN, s VARCHAR)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE, 'a'), (7, FALSE, 'b')");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT, s VARCHAR)");
        engine.execute("INSERT INTO g VALUES (5, 50, 'a'), (6, 60, 'c')");
    }

    /** A statement's answer as one text: a bar between cells, " ;; " between rows; a refusal as REFUSED and its words. */
    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder out = new StringBuilder();
            boolean firstRow = true;
            while (rs.next()) {
                out.append(firstRow ? "" : " ;; ");
                firstRow = false;
                for (int c = 0; c < rs.getColumnCount(); c++) {
                    out.append(c == 0 ? "" : "|").append(String.valueOf(rs.getValue(c)));
                }
            }
            return out.toString();
        } catch (final RuntimeException refused) {
            return "REFUSED " + refused.getMessage();
        }
    }

    /** Each statement answers the given text, or is refused in the given words. */
    private void assertAnswers(final String[][] cases) {
        for (final String[] one : cases) {
            final String answered = answer(one[0]);
            if (one[1].startsWith("REFUSED ")) {
                assertTrue(answered.startsWith("REFUSED ") && answered.contains(one[1].substring("REFUSED ".length())),
                    one[0] + " -> " + answered);
            } else {
                assertEquals(one[1], answered, one[0]);
            }
        }
    }

    @Test
    public void aMembershipOverTheRowAroundItIsTwoValued() {
        assertAnswers(MEMBERSHIPS);
    }

    @Test
    public void aQuantifiedComparisonFollowsItsMembership() {
        assertAnswers(QUANTIFIED);
    }

    @Test
    public void aNullProbeThatReadsNoColumnIsUnknown() {
        assertAnswers(CONSTANT_PROBES);
    }
}
