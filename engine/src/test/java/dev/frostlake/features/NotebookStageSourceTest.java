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
 * CREATE NOTEBOOK … FROM a stage copies the stage's files as CREATE STREAMLIT does and is refused as it is: a user
 * or a table stage is no stage it copies from, a stage that is not there and a location that is no stage URL are
 * refused, and a MAIN_FILE the stage does not hold - the notebook_app.ipynb a notebook defaults to included - is
 * "Notebook MAIN_FILE cannot be found." (all live-verified).
 */
public class NotebookStageSourceTest extends MeasuredCellsTestSupport {

    @Test
    public void notebookSources() {
        engine.execute("CREATE OR REPLACE STAGE nb_st");
        engine.execute("CREATE OR REPLACE TABLE nb_t (a INT)");
        assertAnswer("REFUSED SQL compilation error: Copy files not support stage kind: 'USER' for stage '~'.",
            "CREATE OR REPLACE NOTEBOOK nb_a FROM '@~/nb' MAIN_FILE = 'nb.ipynb'");
        assertAnswer("REFUSED SQL compilation error: Copy files not support stage kind: 'TABLE' for stage 'NB_T'.",
            "CREATE OR REPLACE NOTEBOOK nb_b FROM '@%nb_t/nb' MAIN_FILE = 'nb.ipynb'");
        assertAnswer("REFUSED Notebook MAIN_FILE cannot be found.",
            "CREATE OR REPLACE NOTEBOOK nb_c FROM '@nb_st/nb' MAIN_FILE = 'nb.ipynb'");
        assertAnswer("REFUSED The specified stage NB_NOPE does not exist or the current role does not have access. "
                + "Owner of the Streamlit must have at least READ on the specified stage.",
            "CREATE OR REPLACE NOTEBOOK nb_d FROM '@nb_nope/nb' MAIN_FILE = 'nb.ipynb'");
        assertAnswer("REFUSED SQL compilation error: | invalid URL prefix found in: 'nb_st/nb'",
            "CREATE OR REPLACE NOTEBOOK nb_e FROM 'nb_st/nb' MAIN_FILE = 'nb.ipynb'");
        assertAnswer("REFUSED The specified stage NB_MISSING does not exist or the current role does not have access. "
                + "Owner of the Streamlit must have at least READ on the specified stage.",
            "CREATE OR REPLACE NOTEBOOK nb_f FROM '@test_db.test_schema.nb_missing/nb' MAIN_FILE = 'nb.ipynb'");
        assertAnswer("REFUSED The specified stage NB_MISSING does not exist or the current role does not have access. "
                + "Owner of the Streamlit must have at least READ on the specified stage.",
            "CREATE OR REPLACE STREAMLIT nb_s FROM '@test_db.test_schema.nb_missing/app' MAIN_FILE = 'x.py'");
        assertAnswer("REFUSED Notebook MAIN_FILE cannot be found.", "CREATE OR REPLACE NOTEBOOK nb_h FROM '@nb_st/nb'");
        assertAnswer("Notebook NB_G successfully created.", "CREATE OR REPLACE NOTEBOOK nb_g");
        assertAnswer("REFUSED The specified stage NB_NOPE does not exist or the current role does not have access. "
                + "Owner of the Streamlit must have at least READ on the specified stage.",
            "ALTER NOTEBOOK nb_g ADD VERSION v2 FROM '@nb_nope/x'");
        assertAnswer("REFUSED SQL compilation error: Copy files not support stage kind: 'USER' for stage '~'.",
            "ALTER NOTEBOOK nb_g ADD VERSION v3 FROM '@~/x'");
        assertAnswer("Version V4 successfully created.", "ALTER NOTEBOOK nb_g ADD VERSION v4 FROM '@nb_st/x'");
        assertAnswer("REFUSED SQL compilation error: | invalid URL prefix found in: 'nb_st/x'",
            "ALTER NOTEBOOK nb_g ADD VERSION v5 FROM 'nb_st/x'");
        assertAnswer("Version successfully created.", "ALTER NOTEBOOK nb_g ADD VERSION FROM '@nb_st/x'");
        assertAnswer("Streamlit NB_SA successfully created.",
            "CREATE OR REPLACE STREAMLIT nb_sa ROOT_LOCATION = '@test_db.test_schema.nb_st/app' MAIN_FILE = "
                + "'streamlit_app.py'");
        assertAnswer("REFUSED Attached stage not exists.", "ALTER STREAMLIT nb_sa ADD VERSION v2 FROM '@nb_nope/app'");
        assertAnswer("REFUSED Attached stage not exists.", "ALTER STREAMLIT nb_sa ADD VERSION v3 FROM '@~/app'");
        assertAnswer("REFUSED Attached stage not exists.", "ALTER STREAMLIT nb_sa ADD VERSION v4 FROM '@nb_st/app'");
        assertAnswer("REFUSED SQL compilation error: | invalid URL prefix found in: 'nb_st/app'",
            "ALTER STREAMLIT nb_sa ADD VERSION v5 FROM 'nb_st/app'");
        assertAnswer("REFUSED Attached stage not exists.", "ALTER STREAMLIT nb_sa ADD VERSION FROM '@nb_st/app'");
    }
}
