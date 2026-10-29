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
 * What a correlated IN, EXISTS or scalar aggregate answers when it reads the outer row through equalities: a
 * positive IN is two-valued, a NOT IN keeps the three values, an outer key that is NULL equals no inner one, and
 * TIMESTAMP_TZ keys equal by the instant they name, BINARY keys by their bytes, a collated member compares under
 * its collation, and a member's fault refuses the statement in an inner row of any key the outer table holds,
 * whichever outer rows the query keeps (all live-verified). The
 * engine answers these from the inner rows' members and keys computed once for the statement.
 */
public class CorrelatedInKeysTest extends MeasuredCellsTestSupport {

    @Test
    public void aCorrelatedMembershipKeepsItsNullRules() {
        assertAnswer("Table CA successfully created.",
            "CREATE OR REPLACE TABLE ca (id INT, v INT, s VARCHAR, t TIMESTAMP_TZ, bn BINARY)");
        assertAnswer("5",
            "INSERT INTO ca VALUES (1, 10, 'a', '2020-01-01 10:00:00 +00:00', TO_BINARY('0A', 'HEX')), (2, 20, 'B', "
                + "'2020-01-01 12:00:00 +02:00', TO_BINARY('0B', 'HEX')), (3, NULL, NULL, NULL, NULL), (NULL, 10, "
                + "'c', '2020-01-01 03:00:00 -07:00', TO_BINARY('0C', 'HEX')), (5, 30, 'e', NULL, NULL)");
        assertAnswer("Table CB successfully created.",
            "CREATE OR REPLACE TABLE cb (id INT, k INT, s VARCHAR COLLATE 'en-ci', t TIMESTAMP_TZ, bn BINARY)");
        assertAnswer("5",
            "INSERT INTO cb VALUES (1, 10, 'A', '2020-01-01 02:00:00 -08:00', TO_BINARY('0A', 'HEX')), (NULL, 20, "
                + "'b', '2020-01-01 10:00:00 +00:00', TO_BINARY('0B', 'HEX')), (2, 20, 'x', NULL, NULL), (7, 30, "
                + "NULL, NULL, TO_BINARY('0C', 'HEX')), (8, 40, 'E', NULL, NULL)");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 5|false ;; null|false",
            "SELECT ca.id, ca.id IN (SELECT cb.id FROM cb WHERE cb.k = ca.v) FROM ca ORDER BY ca.id NULLS LAST, ca.v");
        assertAnswer("1|false ;; 2|false ;; 3|true ;; 5|true ;; null|null",
            "SELECT ca.id, ca.id NOT IN (SELECT cb.id FROM cb WHERE cb.k = ca.v) FROM ca ORDER BY ca.id NULLS LAST, "
                + "ca.v");
        assertAnswer("1 ;; 2",
            "SELECT ca.id FROM ca WHERE ca.id IN (SELECT cb.id FROM cb WHERE cb.k = ca.v) ORDER BY 1");
        assertAnswer("3 ;; 5",
            "SELECT ca.id FROM ca WHERE ca.id NOT IN (SELECT cb.id FROM cb WHERE cb.k = ca.v) ORDER BY 1");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 5|false ;; null|false",
            "SELECT ca.id, (ca.id, ca.v) IN (SELECT cb.id, cb.k FROM cb WHERE cb.k = ca.v) FROM ca ORDER BY ca.id "
                + "NULLS LAST, ca.v");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 5|false ;; null|false",
            "SELECT ca.id, EXISTS (SELECT 1 FROM cb WHERE cb.id = ca.id AND cb.k > ca.v) FROM ca ORDER BY ca.id "
                + "NULLS LAST, ca.v");
        assertAnswer("1|0 ;; 2|0 ;; 3|0 ;; 5|0 ;; null|0",
            "SELECT ca.id, (SELECT COUNT(*) FROM cb WHERE cb.k = ca.v AND cb.id < ca.id) FROM ca ORDER BY ca.id "
                + "NULLS LAST, ca.v");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 5|false ;; null|true",
            "SELECT ca.id, EXISTS (SELECT 1 FROM cb WHERE cb.t = ca.t) FROM ca ORDER BY ca.id NULLS LAST, ca.v");
        assertAnswer("1|2 ;; 2|2 ;; 3|0 ;; 5|0 ;; null|2",
            "SELECT ca.id, (SELECT COUNT(*) FROM cb WHERE cb.t = ca.t) FROM ca ORDER BY ca.id NULLS LAST, ca.v");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 5|false ;; null|true",
            "SELECT ca.id, EXISTS (SELECT 1 FROM cb WHERE cb.bn = ca.bn) FROM ca ORDER BY ca.id NULLS LAST, ca.v");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 5|true ;; null|false",
            "SELECT ca.id, EXISTS (SELECT 1 FROM cb WHERE cb.s = ca.s) FROM ca ORDER BY ca.id NULLS LAST, ca.v");
        assertAnswer("1|10 ;; 2|20 ;; 3|null ;; 5|40 ;; null|null",
            "SELECT ca.id, (SELECT MAX(cb.k) FROM cb WHERE cb.s = ca.s) FROM ca ORDER BY ca.id NULLS LAST, ca.v");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 5|true ;; null|false",
            "SELECT ca.id, ca.s IN (SELECT cb.s FROM cb WHERE cb.k >= ca.v) FROM ca ORDER BY ca.id NULLS LAST, ca.v");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 5|false ;; null|false",
            "SELECT ca.id, EXISTS (SELECT 1 FROM cb WHERE cb.id = ca.id::VARCHAR) FROM ca ORDER BY ca.id NULLS LAST, "
                + "ca.v");
    }

    @Test
    public void aCorrelatedMembershipComparesEachMemberOfItsKeys() {
        assertAnswer("Table KA successfully created.",
            "CREATE OR REPLACE TABLE ka (id INT, v INT, w VARCHAR, s VARCHAR, d DATE, l TIMESTAMP_LTZ, n NUMBER(10,"
                + "2))");
        assertAnswer("6",
            "INSERT INTO ka VALUES (1, 10, 'x', 'A', '2020-01-01', '2020-01-01 10:00:00 +00:00', 1.50), (2, 10, 'y', "
                + "'b', '2020-01-02', '2020-01-01 11:00:00 +00:00', 2.00), (3, 20, 'x', NULL, NULL, NULL, NULL), (4, "
                + "NULL, 'x', 'c', '2020-01-03', NULL, 4), (NULL, 20, 'y', 'd', '2020-01-01', NULL, 5), (6, 30, NULL,"
                + " 'E', NULL, NULL, 6)");
        assertAnswer("Table KB successfully created.",
            "CREATE OR REPLACE TABLE kb (id INT, k INT, w VARCHAR, s VARCHAR COLLATE 'en-ci', d DATE, l TIMESTAMP_TZ,"
                + " m NUMBER(10,1), z INT, t VARCHAR)");
        assertAnswer("6",
            "INSERT INTO kb VALUES (1, 10, 'x', 'a', '2020-01-01', '2020-01-01 02:00:00 -08:00', 1.5, 1, '1'), (2, "
                + "10, 'y', 'B', '2020-01-02', '2020-01-01 13:00:00 +02:00', 2.0, 0, '2'), (NULL, 20, 'x', NULL, "
                + "'2020-01-01', NULL, NULL, 1, NULL), (3, 20, 'y', 'D', NULL, NULL, 4.0, 2, '03'), (5, 30, NULL, "
                + "'e', '2020-01-03', NULL, 6.0, 1, 'x6'), (7, 40, 'z', 'f', NULL, NULL, 7.0, 1, '7')");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 6|false ;; null|false",
            "SELECT ka.id, ka.id IN (SELECT kb.id FROM kb WHERE kb.k = ka.v AND kb.w = ka.w) FROM ka ORDER BY ka.id "
                + "NULLS LAST, ka.v");
        assertAnswer("1|false ;; 2|false ;; 3|null ;; 4|true ;; 6|true ;; null|null",
            "SELECT ka.id, ka.id NOT IN (SELECT kb.id FROM kb WHERE kb.k = ka.v AND kb.w = ka.w) FROM ka ORDER BY "
                + "ka.id NULLS LAST, ka.v");
        assertAnswer("1|true ;; 2|false ;; 3|true ;; 4|false ;; 6|false ;; null|false",
            "SELECT ka.id, ka.id IN (SELECT kb.id FROM kb WHERE kb.k = ka.v AND kb.z > 0) FROM ka ORDER BY ka.id "
                + "NULLS LAST, ka.v");
        assertAnswer("1|false ;; 2|true ;; 3|false ;; 4|true ;; 6|true ;; null|null",
            "SELECT ka.id, ka.id NOT IN (SELECT kb.id FROM kb WHERE kb.k = ka.v AND kb.z > 0) FROM ka ORDER BY ka.id "
                + "NULLS LAST, ka.v");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 6|true ;; null|true",
            "SELECT ka.id, ka.s IN (SELECT kb.s FROM kb WHERE kb.k = ka.v) FROM ka ORDER BY ka.id NULLS LAST, ka.v");
        assertAnswer("1|false ;; 2|false ;; 3|null ;; 4|true ;; 6|false ;; null|false",
            "SELECT ka.id, ka.s NOT IN (SELECT kb.s FROM kb WHERE kb.k = ka.v) FROM ka ORDER BY ka.id NULLS LAST, "
                + "ka.v");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 6|true ;; null|false",
            "SELECT ka.id, ka.n IN (SELECT kb.m FROM kb WHERE kb.k = ka.v) FROM ka ORDER BY ka.id NULLS LAST, ka.v");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|false ;; 6|false ;; null|false",
            "SELECT ka.id, ka.id IN (SELECT kb.t FROM kb WHERE kb.k = ka.v AND kb.k < 30) FROM ka ORDER BY ka.id "
                + "NULLS LAST, ka.v");
        assertAnswer("REFUSED Numeric value 'x6' is not recognized",
            "SELECT ka.id, ka.id IN (SELECT kb.t FROM kb WHERE kb.k = ka.v) FROM ka ORDER BY ka.id NULLS LAST, ka.v");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 6|false ;; null|false",
            "SELECT ka.id, ka.w IN (SELECT kb.t FROM kb WHERE kb.k = ka.v) FROM ka ORDER BY ka.id NULLS LAST, ka.v");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 6|false ;; null|false",
            "SELECT ka.id, ka.id IN (SELECT kb.id FROM kb WHERE kb.d = ka.d) FROM ka ORDER BY ka.id NULLS LAST, ka.v");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 6|false ;; null|false",
            "SELECT ka.id, ka.id IN (SELECT kb.id FROM kb WHERE kb.l = ka.l) FROM ka ORDER BY ka.id NULLS LAST, ka.v");
        assertAnswer("REFUSED Division by zero",
            "SELECT ka.id, ka.id IN (SELECT 10 / kb.z FROM kb WHERE kb.k = ka.v) FROM ka WHERE ka.v = 10 ORDER BY "
                + "ka.id NULLS LAST");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|false ;; 6|false ;; null|false",
            "SELECT ka.id, ka.id IN (SELECT kb.id + 0 FROM kb WHERE kb.k = ka.v + 0) FROM ka ORDER BY ka.id NULLS "
                + "LAST, ka.v");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 6|null ;; null|null",
            "SELECT ka.id, NULL IN (SELECT kb.id FROM kb WHERE kb.k = ka.v) FROM ka ORDER BY ka.id NULLS LAST, ka.v");
        assertAnswer("1|false ;; 2|false ;; 3|null ;; 4|false ;; 6|false ;; null|false",
            "SELECT ka.id, ka.id IN (SELECT kb.id FROM kb WHERE kb.k = ka.v) AND ka.id NOT IN (SELECT kb.id FROM kb "
                + "WHERE kb.w = ka.w) FROM ka ORDER BY ka.id NULLS LAST, ka.v");
        assertAnswer("3", "SELECT COUNT(*) FROM ka WHERE ka.id IN (SELECT kb.id FROM kb WHERE kb.k = ka.v)");
        assertAnswer("2", "SELECT COUNT(*) FROM ka WHERE ka.id NOT IN (SELECT kb.id FROM kb WHERE kb.k = ka.v)");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 6|false ;; null|false",
            "SELECT ka.id, (ka.id IN (SELECT kb.id FROM kb WHERE kb.k = ka.v)) IS NULL FROM ka ORDER BY ka.id NULLS "
                + "LAST, ka.v");
    }

    /** The members are computed over every inner row, so the fault of a row no outer key reads is raised too. */
    @Test
    public void aMemberFaultInAnotherKeysRowRefusesTheStatement() {
        assertAnswer("Table KB successfully created.", "CREATE OR REPLACE TABLE kb (k INT, z INT)");
        assertAnswer("2", "INSERT INTO kb VALUES (10, 0), (20, 1)");
        assertAnswer("Table KA successfully created.", "CREATE OR REPLACE TABLE ka (id INT, v INT)");
        assertAnswer("2", "INSERT INTO ka VALUES (1, 10), (3, 20)");
        // The key the fault's row has is one the outer table holds, whichever rows the outer query keeps.
        final String in = "ka.id IN (SELECT 10 / kb.z FROM kb WHERE kb.k = ka.v)";
        assertAnswer("REFUSED Division by zero", "SELECT ka.id, " + in + " FROM ka WHERE ka.v = 20");
        assertAnswer("REFUSED Division by zero", "SELECT ka.id, " + in + " FROM ka WHERE ka.id = 3");
        assertAnswer("REFUSED Division by zero", "SELECT ka.id, " + in + " FROM ka");
        assertAnswer("REFUSED Division by zero", "SELECT ka.id FROM ka WHERE ka.id = 3 AND " + in);
        assertAnswer("REFUSED Division by zero", "SELECT ka.id, " + in + " FROM (SELECT * FROM ka WHERE id = 3) ka");
        // A scalar aggregate reads the inner rows of the keys the outer rows it keeps have.
        assertAnswer("3|10.000000",
            "SELECT ka.id, (SELECT MAX(10 / kb.z) FROM kb WHERE kb.k = ka.v) FROM ka WHERE ka.v = 20");
        assertAnswer("REFUSED Division by zero",
            "SELECT ka.id, (SELECT MAX(10 / kb.z) FROM kb WHERE kb.k = ka.v) FROM ka");
    }
}
