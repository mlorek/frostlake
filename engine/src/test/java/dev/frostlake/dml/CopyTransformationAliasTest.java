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

package dev.frostlake.dml;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A COPY transformation's stage takes an alias, with AS or without, which qualifies its fields:
 * {@code FROM (SELECT t.$1, t.$2 FROM @mystage t)} (documented). The alias was a syntax error.
 */
public class CopyTransformationAliasTest extends MeasuredCellsTestSupport {

    @Test
    public void theStagesAliasQualifiesItsFields() {
        engine.execute("CREATE STAGE cta_st FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)");
        engine.execute("COPY INTO @cta_st/f1.csv.gz FROM (SELECT * FROM VALUES ('1', 'one'), ('2', 'two') ORDER BY 1) "
            + "FILE_FORMAT = (TYPE = CSV COMPRESSION = GZIP) HEADER = TRUE SINGLE = TRUE");
        engine.execute("CREATE TABLE cta_home (id INT, name VARCHAR)");
        engine.execute("CREATE TABLE cta_home2 (id INT, name VARCHAR)");
        engine.execute("COPY INTO cta_home (id, name) FROM (SELECT t.$1, t.$2 FROM @cta_st t)");
        engine.execute("COPY INTO cta_home2 (id, name) FROM (SELECT t.$1, SUBSTR(t.$2, 1, 2) FROM @cta_st AS t)");
        assertAnswer("1|one ;; 2|two", "SELECT id, name FROM cta_home ORDER BY id");
        assertAnswer("1|on ;; 2|tw", "SELECT id, name FROM cta_home2 ORDER BY id");
    }
}
