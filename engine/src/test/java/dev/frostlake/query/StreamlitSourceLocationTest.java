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
 * The location a Streamlit app is created FROM. The app copies the location's files, so the location has to
 * name a stage, a named one that exists, with the app's main file among its files — streamlit_app.py when the
 * statement names none. A ROOT_LOCATION is taken as written. Every answer is live-verified.
 */
public class StreamlitSourceLocationTest extends MeasuredCellsTestSupport {

    @Test
    public void theLocationIsJudgedAsTheAppIsCreated() {
        try {
            engine.execute("CREATE OR REPLACE STAGE sl_st");
            engine.execute("CREATE OR REPLACE TABLE sl_t (a INT)");
            assertAnswer(
                "REFUSED SQL compilation error: Copy files not support stage kind: 'USER' for stage '~'.",
                "CREATE OR REPLACE STREAMLIT sl_a FROM '@~/app' MAIN_FILE = 'streamlit_app.py'");
            assertAnswer(
                "REFUSED SQL compilation error: Copy files not support stage kind: 'TABLE' for stage 'SL_T'.",
                "CREATE OR REPLACE STREAMLIT sl_b FROM '@%sl_t/app' MAIN_FILE = 'streamlit_app.py'");
            assertAnswer(
                "REFUSED SQL compilation error: | Missing MAIN_FILE.",
                "CREATE OR REPLACE STREAMLIT sl_c FROM '@sl_st/app' MAIN_FILE = 'streamlit_app.py'");
            assertAnswer(
                "REFUSED The specified stage SL_NOPE does not exist or the current role does not have access. "
                + "Owner of the Streamlit must have at least READ on the specified stage.",
                "CREATE OR REPLACE STREAMLIT sl_d FROM '@sl_nope/app' MAIN_FILE = 'streamlit_app.py'");
            assertAnswer(
                "Streamlit SL_E successfully created.",
                "CREATE OR REPLACE STREAMLIT sl_e ROOT_LOCATION = '@test_db.test_schema.sl_st/app' MAIN_FILE = "
                + "'streamlit_app.py'");
            assertAnswer(
                "1|17|17",
                "COPY INTO @sl_st/app/streamlit_app.py FROM (SELECT 'import streamlit') FILE_FORMAT = (TYPE = CSV "
                + "COMPRESSION = NONE) SINGLE = TRUE");
            assertAnswer(
                "Streamlit SL_F successfully created.",
                "CREATE OR REPLACE STREAMLIT sl_f FROM '@sl_st/app' MAIN_FILE = 'streamlit_app.py'");
            assertAnswer(
                "REFUSED SQL compilation error: | Missing MAIN_FILE.",
                "CREATE OR REPLACE STREAMLIT sl_g FROM '@sl_st/app' MAIN_FILE = 'other.py'");
            assertAnswer(
                "Streamlit SL_H successfully created.",
                "CREATE OR REPLACE STREAMLIT sl_h FROM '@sl_st/app'");
            assertAnswer(
                "Streamlit SL_I successfully created.",
                "CREATE OR REPLACE STREAMLIT sl_i FROM '@sl_st/app/' MAIN_FILE = 'streamlit_app.py'");
            assertAnswer(
                "Streamlit SL_J successfully created.",
                "CREATE OR REPLACE STREAMLIT sl_j FROM '@sl_st' MAIN_FILE = 'app/streamlit_app.py'");
            assertAnswer(
                "REFUSED SQL compilation error: | Missing MAIN_FILE.",
                "CREATE OR REPLACE STREAMLIT sl_k FROM '@sl_st' MAIN_FILE = 'streamlit_app.py'");
            assertAnswer(
                "Streamlit SL_L successfully created.",
                "CREATE OR REPLACE STREAMLIT sl_l FROM '@test_db.test_schema.sl_st/app' MAIN_FILE = 'streamlit_app.py'");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid URL prefix found in: 'sl_st/app'",
                "CREATE OR REPLACE STREAMLIT sl_m FROM 'sl_st/app' MAIN_FILE = 'streamlit_app.py'");
            engine.execute("SHOW STREAMLITS");
            assertAnswer(
                "SL_E ;; SL_F ;; SL_H ;; SL_I ;; SL_J ;; SL_L",
                "SELECT \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
        } finally {
            engine.execute("DROP STREAMLIT IF EXISTS sl_e");
            engine.execute("DROP STREAMLIT IF EXISTS sl_f");
            engine.execute("DROP STREAMLIT IF EXISTS sl_h");
            engine.execute("DROP STREAMLIT IF EXISTS sl_i");
            engine.execute("DROP STREAMLIT IF EXISTS sl_j");
            engine.execute("DROP STREAMLIT IF EXISTS sl_l");
        }
    }

    @Test
    public void theMainFileDefaultsAndTheStageNameFolds() {
        try {
            engine.execute("CREATE OR REPLACE STAGE sm_st");
            assertAnswer(
                "REFUSED SQL compilation error: | Missing MAIN_FILE.",
                "CREATE OR REPLACE STREAMLIT sm_a FROM '@sm_st/app'");
            assertAnswer(
                "1|17|17",
                "COPY INTO @sm_st/app/streamlit_app.py FROM (SELECT 'import streamlit') FILE_FORMAT = (TYPE = CSV "
                + "COMPRESSION = NONE) SINGLE = TRUE");
            assertAnswer(
                "Streamlit SM_B successfully created.",
                "CREATE OR REPLACE STREAMLIT sm_b FROM '@sm_st/app'");
            assertAnswer(
                "Streamlit SM_C successfully created.",
                "CREATE OR REPLACE STREAMLIT sm_c FROM '@\"SM_ST\"/app'");
            assertAnswer(
                "REFUSED The specified stage sm_st does not exist or the current role does not have access. Owner "
                + "of the Streamlit must have at least READ on the specified stage.",
                "CREATE OR REPLACE STREAMLIT sm_d FROM '@\"sm_st\"/app'");
        } finally {
            engine.execute("DROP STREAMLIT IF EXISTS sm_b");
            engine.execute("DROP STREAMLIT IF EXISTS sm_c");
        }
    }
}
