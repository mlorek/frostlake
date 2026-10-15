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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A signature after the name a DROP names, kind by kind: the account reads one after every kind but a stage
 * and a routine — its own kinds, a warehouse, a user and a role among them — and reports a faulty one alike
 * after all of them (live-verified). Every name here is one that cannot exist, under IF EXISTS.
 */
public class DropSignatureEveryKindTest extends MeasuredCellsTestSupport {

    @Test
    public void aSignatureThatIsRead() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        answer(
            "CREATE OR REPLACE TABLE t3 (x INT)");
        answer(
            "CREATE OR REPLACE TABLE t7 (x INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected ''x''. | syntax error "
                + "line 1 at position 32 unexpected 'y'.",
            "DROP SECRET s ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '('.",
            "DROP SECRET IF EXISTS nosuch_sec ((a), b)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_SEC already dropped).",
            "DROP SECRET IF EXISTS nosuch_sec (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '1'.",
            "DROP SECRET IF EXISTS nosuch_sec (1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'b'.",
            "DROP SECRET IF EXISTS nosuch_sec (a b)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_NR already dropped).",
            "DROP NETWORK RULE IF EXISTS nosuch_nr (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected '1'.",
            "DROP NETWORK RULE IF EXISTS nosuch_nr (1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected '('.",
            "DROP NETWORK RULE IF EXISTS nosuch_nr ((a), b)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_PP already dropped).",
            "DROP PASSWORD POLICY IF EXISTS nosuch_pp (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected '1'.",
            "DROP PASSWORD POLICY IF EXISTS nosuch_pp (1)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_NB already dropped).",
            "DROP NOTEBOOK IF EXISTS nosuch_nb (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected '1'.",
            "DROP NOTEBOOK IF EXISTS nosuch_nb (1)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_SL already dropped).",
            "DROP STREAMLIT IF EXISTS nosuch_sl (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '1'.",
            "DROP STREAMLIT IF EXISTS nosuch_sl (1)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_AL already dropped).",
            "DROP ALERT IF EXISTS nosuch_al (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'.",
            "DROP ALERT IF EXISTS nosuch_al (1)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_SIG_ZQ_7F3A91 already dropped).",
            "DROP WAREHOUSE IF EXISTS nosuch_sig_zq_7f3a91 (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected '1'.",
            "DROP WAREHOUSE IF EXISTS nosuch_sig_zq_7f3a91 (1)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_SIG_ZQ_7F3A91 already dropped).",
            "DROP COMPUTE POOL IF EXISTS nosuch_sig_zq_7f3a91 (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected '1'.",
            "DROP COMPUTE POOL IF EXISTS nosuch_sig_zq_7f3a91 (1)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_SIG_ZQ_7F3A91 already dropped).",
            "DROP USER IF EXISTS nosuch_sig_zq_7f3a91 (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected '1'.",
            "DROP USER IF EXISTS nosuch_sig_zq_7f3a91 (1)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_SIG_ZQ_7F3A91 already dropped).",
            "DROP ROLE IF EXISTS nosuch_sig_zq_7f3a91 (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected '1'.",
            "DROP ROLE IF EXISTS nosuch_sig_zq_7f3a91 (1)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_SIG_ZQ_7F3A91 already dropped).",
            "DROP CONTACT IF EXISTS nosuch_sig_zq_7f3a91 (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected '1'.",
            "DROP CONTACT IF EXISTS nosuch_sig_zq_7f3a91 (1)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_SIG_ZQ_7F3A91 already dropped).",
            "DROP PROJECTION POLICY IF EXISTS nosuch_sig_zq_7f3a91 (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected '1'.",
            "DROP PROJECTION POLICY IF EXISTS nosuch_sig_zq_7f3a91 (1)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_SIG_ZQ_7F3A91 already dropped).",
            "DROP AGGREGATION POLICY IF EXISTS nosuch_sig_zq_7f3a91 (a)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_SIG_ZQ_7F3A91 already dropped).",
            "DROP JOIN POLICY IF EXISTS nosuch_sig_zq_7f3a91 (a)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_SIG_ZQ_7F3A91 already dropped).",
            "DROP DATABASE ROLE IF EXISTS nosuch_sig_zq_7f3a91 (a)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_SIG_ZQ_7F3A91 already dropped).",
            "DROP NETWORK POLICY IF EXISTS nosuch_sig_zq_7f3a91 (a)");
    }

    @Test
    public void tablesSecretsAndApps() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        answer(
            "CREATE OR REPLACE TABLE t7 (x INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected '('.",
            "DROP TABLE IF EXISTS nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected '1'. | syntax error "
                + "line 1 at position 51 unexpected ')'.",
            "DROP TABLE IF EXISTS nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected ''x''. | syntax error "
                + "line 1 at position 60 unexpected 'y'.",
            "DROP TABLE IF EXISTS nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected 'x'.",
            "DROP TABLE IF EXISTS nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'b'.",
            "DROP TABLE IF EXISTS nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected '.'.",
            "DROP TABLE IF EXISTS nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected '('.",
            "DROP SECRET IF EXISTS nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected '1'. | syntax error "
                + "line 1 at position 52 unexpected ')'.",
            "DROP SECRET IF EXISTS nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected ''x''. | syntax error "
                + "line 1 at position 61 unexpected 'y'.",
            "DROP SECRET IF EXISTS nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected 'x'.",
            "DROP SECRET IF EXISTS nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected 'b'.",
            "DROP SECRET IF EXISTS nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected '.'.",
            "DROP SECRET IF EXISTS nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected '('.",
            "DROP NETWORK RULE IF EXISTS nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected '1'. | syntax error "
                + "line 1 at position 58 unexpected ')'.",
            "DROP NETWORK RULE IF EXISTS nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected ''x''. | syntax error "
                + "line 1 at position 67 unexpected 'y'.",
            "DROP NETWORK RULE IF EXISTS nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected 'x'.",
            "DROP NETWORK RULE IF EXISTS nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'b'.",
            "DROP NETWORK RULE IF EXISTS nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected '.'.",
            "DROP NETWORK RULE IF EXISTS nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected '('.",
            "DROP PASSWORD POLICY IF EXISTS nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected '1'. | syntax error "
                + "line 1 at position 61 unexpected ')'.",
            "DROP PASSWORD POLICY IF EXISTS nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected ''x''. | syntax error "
                + "line 1 at position 70 unexpected 'y'.",
            "DROP PASSWORD POLICY IF EXISTS nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected 'x'.",
            "DROP PASSWORD POLICY IF EXISTS nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected 'b'.",
            "DROP PASSWORD POLICY IF EXISTS nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected '.'.",
            "DROP PASSWORD POLICY IF EXISTS nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected '('.",
            "DROP NOTEBOOK IF EXISTS nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected '1'. | syntax error "
                + "line 1 at position 54 unexpected ')'.",
            "DROP NOTEBOOK IF EXISTS nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected ''x''. | syntax error "
                + "line 1 at position 63 unexpected 'y'.",
            "DROP NOTEBOOK IF EXISTS nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected 'x'.",
            "DROP NOTEBOOK IF EXISTS nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected 'b'.",
            "DROP NOTEBOOK IF EXISTS nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected '.'.",
            "DROP NOTEBOOK IF EXISTS nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected '('.",
            "DROP STREAMLIT IF EXISTS nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected '1'. | syntax error "
                + "line 1 at position 55 unexpected ')'.",
            "DROP STREAMLIT IF EXISTS nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected ''x''. | syntax error "
                + "line 1 at position 64 unexpected 'y'.",
            "DROP STREAMLIT IF EXISTS nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected 'x'.",
            "DROP STREAMLIT IF EXISTS nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected 'b'.",
            "DROP STREAMLIT IF EXISTS nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected '.'.",
            "DROP STREAMLIT IF EXISTS nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected '('.",
            "DROP WAREHOUSE IF EXISTS nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected '1'. | syntax error "
                + "line 1 at position 55 unexpected ')'.",
            "DROP WAREHOUSE IF EXISTS nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected ''x''. | syntax error "
                + "line 1 at position 64 unexpected 'y'.",
            "DROP WAREHOUSE IF EXISTS nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected 'x'.",
            "DROP WAREHOUSE IF EXISTS nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected 'b'.",
            "DROP WAREHOUSE IF EXISTS nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected '.'.",
            "DROP WAREHOUSE IF EXISTS nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected '('.",
            "DROP COMPUTE POOL IF EXISTS nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected '1'. | syntax error "
                + "line 1 at position 58 unexpected ')'.",
            "DROP COMPUTE POOL IF EXISTS nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected ''x''. | syntax error "
                + "line 1 at position 67 unexpected 'y'.",
            "DROP COMPUTE POOL IF EXISTS nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected 'x'.",
            "DROP COMPUTE POOL IF EXISTS nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'b'.",
            "DROP COMPUTE POOL IF EXISTS nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected '.'.",
            "DROP COMPUTE POOL IF EXISTS nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
    }

    @Test
    public void theAccountsOwnKinds() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        answer(
            "CREATE OR REPLACE TABLE t7 (x INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected '('.",
            "DROP USER IF EXISTS nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected '1'. | syntax error "
                + "line 1 at position 50 unexpected ')'.",
            "DROP USER IF EXISTS nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected ''x''. | syntax error "
                + "line 1 at position 59 unexpected 'y'.",
            "DROP USER IF EXISTS nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'x'.",
            "DROP USER IF EXISTS nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'b'.",
            "DROP USER IF EXISTS nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected '.'.",
            "DROP USER IF EXISTS nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected '('.",
            "DROP ROLE IF EXISTS nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected '1'. | syntax error "
                + "line 1 at position 50 unexpected ')'.",
            "DROP ROLE IF EXISTS nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected ''x''. | syntax error "
                + "line 1 at position 59 unexpected 'y'.",
            "DROP ROLE IF EXISTS nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'x'.",
            "DROP ROLE IF EXISTS nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'b'.",
            "DROP ROLE IF EXISTS nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected '.'.",
            "DROP ROLE IF EXISTS nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected '('.",
            "DROP CONTACT IF EXISTS nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected '1'. | syntax error "
                + "line 1 at position 53 unexpected ')'.",
            "DROP CONTACT IF EXISTS nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected ''x''. | syntax error "
                + "line 1 at position 62 unexpected 'y'.",
            "DROP CONTACT IF EXISTS nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected 'x'.",
            "DROP CONTACT IF EXISTS nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected 'b'.",
            "DROP CONTACT IF EXISTS nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 54 unexpected '.'.",
            "DROP CONTACT IF EXISTS nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected '('.",
            "DROP PROJECTION POLICY IF EXISTS nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected '1'. | syntax error "
                + "line 1 at position 63 unexpected ')'.",
            "DROP PROJECTION POLICY IF EXISTS nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected ''x''. | syntax error "
                + "line 1 at position 72 unexpected 'y'.",
            "DROP PROJECTION POLICY IF EXISTS nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected 'x'.",
            "DROP PROJECTION POLICY IF EXISTS nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected 'b'.",
            "DROP PROJECTION POLICY IF EXISTS nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 64 unexpected '.'.",
            "DROP PROJECTION POLICY IF EXISTS nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected '('.",
            "DROP AGGREGATION POLICY IF EXISTS nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected '1'. | syntax error "
                + "line 1 at position 64 unexpected ')'.",
            "DROP AGGREGATION POLICY IF EXISTS nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected ''x''. | syntax error "
                + "line 1 at position 73 unexpected 'y'.",
            "DROP AGGREGATION POLICY IF EXISTS nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected 'x'.",
            "DROP AGGREGATION POLICY IF EXISTS nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected 'b'.",
            "DROP AGGREGATION POLICY IF EXISTS nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected '.'.",
            "DROP AGGREGATION POLICY IF EXISTS nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected '('.",
            "DROP JOIN POLICY IF EXISTS nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected '1'. | syntax error "
                + "line 1 at position 57 unexpected ')'.",
            "DROP JOIN POLICY IF EXISTS nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected ''x''. | syntax error "
                + "line 1 at position 66 unexpected 'y'.",
            "DROP JOIN POLICY IF EXISTS nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'x'.",
            "DROP JOIN POLICY IF EXISTS nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected 'b'.",
            "DROP JOIN POLICY IF EXISTS nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected '.'.",
            "DROP JOIN POLICY IF EXISTS nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected '('.",
            "DROP DATABASE ROLE IF EXISTS nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected '1'. | syntax error "
                + "line 1 at position 59 unexpected ')'.",
            "DROP DATABASE ROLE IF EXISTS nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected ''x''. | syntax error "
                + "line 1 at position 68 unexpected 'y'.",
            "DROP DATABASE ROLE IF EXISTS nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 54 unexpected 'x'.",
            "DROP DATABASE ROLE IF EXISTS nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected 'b'.",
            "DROP DATABASE ROLE IF EXISTS nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected '.'.",
            "DROP DATABASE ROLE IF EXISTS nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected '('.",
            "DROP NETWORK POLICY IF EXISTS nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected '1'. | syntax error "
                + "line 1 at position 60 unexpected ')'.",
            "DROP NETWORK POLICY IF EXISTS nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected ''x''. | syntax error "
                + "line 1 at position 69 unexpected 'y'.",
            "DROP NETWORK POLICY IF EXISTS nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected 'x'.",
            "DROP NETWORK POLICY IF EXISTS nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 54 unexpected 'b'.",
            "DROP NETWORK POLICY IF EXISTS nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected '.'.",
            "DROP NETWORK POLICY IF EXISTS nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
    }

    @Test
    public void containersAndPolicies() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        answer(
            "CREATE OR REPLACE TABLE t7 (x INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected '('.",
            "DROP DATABASE IF EXISTS nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected '1'. | syntax error "
                + "line 1 at position 54 unexpected ')'.",
            "DROP DATABASE IF EXISTS nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected ''x''. | syntax error "
                + "line 1 at position 63 unexpected 'y'.",
            "DROP DATABASE IF EXISTS nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected 'x'.",
            "DROP DATABASE IF EXISTS nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected 'b'.",
            "DROP DATABASE IF EXISTS nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected '.'.",
            "DROP DATABASE IF EXISTS nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected '('.",
            "DROP SCHEMA IF EXISTS nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected '1'. | syntax error "
                + "line 1 at position 52 unexpected ')'.",
            "DROP SCHEMA IF EXISTS nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected ''x''. | syntax error "
                + "line 1 at position 61 unexpected 'y'.",
            "DROP SCHEMA IF EXISTS nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected 'x'.",
            "DROP SCHEMA IF EXISTS nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected 'b'.",
            "DROP SCHEMA IF EXISTS nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected '.'.",
            "DROP SCHEMA IF EXISTS nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected '('.",
            "DROP FILE FORMAT IF EXISTS nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected '1'. | syntax error "
                + "line 1 at position 57 unexpected ')'.",
            "DROP FILE FORMAT IF EXISTS nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected ''x''. | syntax error "
                + "line 1 at position 66 unexpected 'y'.",
            "DROP FILE FORMAT IF EXISTS nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'x'.",
            "DROP FILE FORMAT IF EXISTS nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected 'b'.",
            "DROP FILE FORMAT IF EXISTS nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected '.'.",
            "DROP FILE FORMAT IF EXISTS nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected '('.",
            "DROP TAG IF EXISTS nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected '1'. | syntax error "
                + "line 1 at position 49 unexpected ')'.",
            "DROP TAG IF EXISTS nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ''x''. | syntax error "
                + "line 1 at position 58 unexpected 'y'.",
            "DROP TAG IF EXISTS nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'x'.",
            "DROP TAG IF EXISTS nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'b'.",
            "DROP TAG IF EXISTS nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected '.'.",
            "DROP TAG IF EXISTS nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected '('.",
            "DROP ALERT IF EXISTS nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected '1'. | syntax error "
                + "line 1 at position 51 unexpected ')'.",
            "DROP ALERT IF EXISTS nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected ''x''. | syntax error "
                + "line 1 at position 60 unexpected 'y'.",
            "DROP ALERT IF EXISTS nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected 'x'.",
            "DROP ALERT IF EXISTS nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'b'.",
            "DROP ALERT IF EXISTS nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected '.'.",
            "DROP ALERT IF EXISTS nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected '('.",
            "DROP MASKING POLICY IF EXISTS nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected '1'. | syntax error "
                + "line 1 at position 60 unexpected ')'.",
            "DROP MASKING POLICY IF EXISTS nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected ''x''. | syntax error "
                + "line 1 at position 69 unexpected 'y'.",
            "DROP MASKING POLICY IF EXISTS nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected 'x'.",
            "DROP MASKING POLICY IF EXISTS nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 54 unexpected 'b'.",
            "DROP MASKING POLICY IF EXISTS nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected '.'.",
            "DROP MASKING POLICY IF EXISTS nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected '('.",
            "DROP ROW ACCESS POLICY IF EXISTS nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected '1'. | syntax error "
                + "line 1 at position 63 unexpected ')'.",
            "DROP ROW ACCESS POLICY IF EXISTS nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected ''x''. | syntax error "
                + "line 1 at position 72 unexpected 'y'.",
            "DROP ROW ACCESS POLICY IF EXISTS nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected 'x'.",
            "DROP ROW ACCESS POLICY IF EXISTS nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected 'b'.",
            "DROP ROW ACCESS POLICY IF EXISTS nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 64 unexpected '.'.",
            "DROP ROW ACCESS POLICY IF EXISTS nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected '('.",
            "DROP CORTEX SEARCH SERVICE IF EXISTS nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected '1'. | syntax error "
                + "line 1 at position 67 unexpected ')'.",
            "DROP CORTEX SEARCH SERVICE IF EXISTS nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected ''x''. | syntax error "
                + "line 1 at position 76 unexpected 'y'.",
            "DROP CORTEX SEARCH SERVICE IF EXISTS nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'x'.",
            "DROP CORTEX SEARCH SERVICE IF EXISTS nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'b'.",
            "DROP CORTEX SEARCH SERVICE IF EXISTS nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 68 unexpected '.'.",
            "DROP CORTEX SEARCH SERVICE IF EXISTS nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
    }

    @Test
    public void tableKindsAndOtherPolicies() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        answer(
            "CREATE OR REPLACE TABLE t7 (x INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected '('.",
            "DROP EVENT TABLE IF EXISTS nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected '1'. | syntax error "
                + "line 1 at position 57 unexpected ')'.",
            "DROP EVENT TABLE IF EXISTS nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected ''x''. | syntax error "
                + "line 1 at position 66 unexpected 'y'.",
            "DROP EVENT TABLE IF EXISTS nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'x'.",
            "DROP EVENT TABLE IF EXISTS nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected 'b'.",
            "DROP EVENT TABLE IF EXISTS nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected '.'.",
            "DROP EVENT TABLE IF EXISTS nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected '('.",
            "DROP ICEBERG TABLE IF EXISTS nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected '1'. | syntax error "
                + "line 1 at position 59 unexpected ')'.",
            "DROP ICEBERG TABLE IF EXISTS nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected ''x''. | syntax error "
                + "line 1 at position 68 unexpected 'y'.",
            "DROP ICEBERG TABLE IF EXISTS nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 54 unexpected 'x'.",
            "DROP ICEBERG TABLE IF EXISTS nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected 'b'.",
            "DROP ICEBERG TABLE IF EXISTS nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected '.'.",
            "DROP ICEBERG TABLE IF EXISTS nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected '('.",
            "DROP EXTERNAL TABLE IF EXISTS nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected '1'. | syntax error "
                + "line 1 at position 60 unexpected ')'.",
            "DROP EXTERNAL TABLE IF EXISTS nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected ''x''. | syntax error "
                + "line 1 at position 69 unexpected 'y'.",
            "DROP EXTERNAL TABLE IF EXISTS nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected 'x'.",
            "DROP EXTERNAL TABLE IF EXISTS nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 54 unexpected 'b'.",
            "DROP EXTERNAL TABLE IF EXISTS nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected '.'.",
            "DROP EXTERNAL TABLE IF EXISTS nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected '('.",
            "DROP MATERIALIZED VIEW IF EXISTS nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected '1'. | syntax error "
                + "line 1 at position 63 unexpected ')'.",
            "DROP MATERIALIZED VIEW IF EXISTS nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected ''x''. | syntax error "
                + "line 1 at position 72 unexpected 'y'.",
            "DROP MATERIALIZED VIEW IF EXISTS nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected 'x'.",
            "DROP MATERIALIZED VIEW IF EXISTS nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected 'b'.",
            "DROP MATERIALIZED VIEW IF EXISTS nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 64 unexpected '.'.",
            "DROP MATERIALIZED VIEW IF EXISTS nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected '('.",
            "DROP DYNAMIC TABLE IF EXISTS nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected '1'. | syntax error "
                + "line 1 at position 59 unexpected ')'.",
            "DROP DYNAMIC TABLE IF EXISTS nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected ''x''. | syntax error "
                + "line 1 at position 68 unexpected 'y'.",
            "DROP DYNAMIC TABLE IF EXISTS nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 54 unexpected 'x'.",
            "DROP DYNAMIC TABLE IF EXISTS nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected 'b'.",
            "DROP DYNAMIC TABLE IF EXISTS nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected '.'.",
            "DROP DYNAMIC TABLE IF EXISTS nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected '('.",
            "DROP SESSION POLICY IF EXISTS nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected '1'. | syntax error "
                + "line 1 at position 60 unexpected ')'.",
            "DROP SESSION POLICY IF EXISTS nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected ''x''. | syntax error "
                + "line 1 at position 69 unexpected 'y'.",
            "DROP SESSION POLICY IF EXISTS nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected 'x'.",
            "DROP SESSION POLICY IF EXISTS nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 54 unexpected 'b'.",
            "DROP SESSION POLICY IF EXISTS nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected '.'.",
            "DROP SESSION POLICY IF EXISTS nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected '('.",
            "DROP AUTHENTICATION POLICY IF EXISTS nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected '1'. | syntax error "
                + "line 1 at position 67 unexpected ')'.",
            "DROP AUTHENTICATION POLICY IF EXISTS nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected ''x''. | syntax error "
                + "line 1 at position 76 unexpected 'y'.",
            "DROP AUTHENTICATION POLICY IF EXISTS nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'x'.",
            "DROP AUTHENTICATION POLICY IF EXISTS nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'b'.",
            "DROP AUTHENTICATION POLICY IF EXISTS nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 68 unexpected '.'.",
            "DROP AUTHENTICATION POLICY IF EXISTS nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected '('.",
            "DROP SEQUENCE IF EXISTS nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected '1'. | syntax error "
                + "line 1 at position 54 unexpected ')'.",
            "DROP SEQUENCE IF EXISTS nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected ''x''. | syntax error "
                + "line 1 at position 63 unexpected 'y'.",
            "DROP SEQUENCE IF EXISTS nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected 'x'.",
            "DROP SEQUENCE IF EXISTS nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected 'b'.",
            "DROP SEQUENCE IF EXISTS nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected '.'.",
            "DROP SEQUENCE IF EXISTS nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
    }
}
