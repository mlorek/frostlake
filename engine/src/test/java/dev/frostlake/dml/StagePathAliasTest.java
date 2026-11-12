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
 * A stage path ends at a blank, and the word after it is the stage's alias: {@code FROM @st/data/ t} reads every file
 * beneath data/ under the alias t, and the COPY transformation examples write {@code FROM @mystage/sales.csv.gz t}
 * (documented). The alias was read as the path's last characters, so a file path matched no file and a directory path
 * matched only the files whose names began with the alias, with no error.
 */
public class StagePathAliasTest extends MeasuredCellsTestSupport {

    @Test
    public void anAliasAfterAFilePathIsTheStagesAlias() {
        engine.execute("CREATE STAGE spa_st FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)");
        engine.execute("COPY INTO @spa_st/f1.csv.gz FROM (SELECT * FROM VALUES ('1', 'one'), ('2', 'two') ORDER BY 1) "
            + "FILE_FORMAT = (TYPE = CSV COMPRESSION = GZIP) HEADER = TRUE SINGLE = TRUE");
        assertAnswer("1|one ;; 2|two", "SELECT t.$1, t.$2 FROM @spa_st/f1.csv.gz t ORDER BY 1");
        engine.execute("CREATE TABLE spa_home (id INT, name VARCHAR)");
        engine.execute("COPY INTO spa_home (id, name) FROM (SELECT t.$1, t.$2 FROM @spa_st/f1.csv.gz t)");
        assertAnswer("1|one ;; 2|two", "SELECT id, name FROM spa_home ORDER BY id");
    }

    @Test
    public void anAliasAfterADirectoryPathReadsEveryFileBeneathIt() {
        engine.execute("CREATE STAGE spa_dir FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)");
        engine.execute("COPY INTO @spa_dir/data/t_jan.csv FROM (SELECT 1 AS a) "
            + "FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) HEADER = TRUE SINGLE = TRUE");
        engine.execute("COPY INTO @spa_dir/data/u_feb.csv FROM (SELECT 2 AS a) "
            + "FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) HEADER = TRUE SINGLE = TRUE");
        assertAnswer("1|data/t_jan.csv ;; 2|data/u_feb.csv",
            "SELECT t.$1, METADATA$FILENAME FROM @spa_dir/data/ t ORDER BY 1");
        engine.execute("CREATE TABLE spa_x (a INT)");
        engine.execute("COPY INTO spa_x (a) FROM (SELECT t.$1 FROM @spa_dir/data/ t)");
        assertAnswer("1 ;; 2", "SELECT a FROM spa_x ORDER BY a");
    }
}
