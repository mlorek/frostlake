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

package dev.frostlake.stage;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

/**
 * A JVM that sets a dropped table's stage aside and exits without shutting its engine down, for
 * {@link DroppedObjectStagesTest}: the engine keeps nothing across a restart, so nothing of it may be left behind.
 */
public final class ExitWithoutShutdown {

    private ExitWithoutShutdown() {
    }

    /**
     * Run it.
     *
     * @param args the internal stage root
     */
    public static void main(final String[] args) {
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_STAGE_INTERNAL_LOCAL_ROOT, args[0]);
        final DatabaseEngine engine = new DatabaseEngine(cfg);
        engine.execute("CREATE DATABASE d");
        engine.execute("USE SCHEMA d.PUBLIC");
        engine.execute("CREATE TABLE t (a INT)");
        engine.execute("COPY INTO @%t/f FROM (SELECT 1 AS a) FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) SINGLE = TRUE");
        engine.execute("DROP TABLE t");
        // What was set aside before the exit, for the test to tell the hook's work from nothing set aside at all.
        final StringBuilder setAside = new StringBuilder();
        try (java.util.stream.Stream<Path> walk = Files.walk(Paths.get(args[0]))) {
            final Iterator<Path> paths = walk.iterator();
            while (paths.hasNext()) {
                final String name = paths.next().getFileName().toString();
                if (name.contains("@")) {
                    setAside.append(name, 0, name.indexOf('@'));
                }
            }
            Files.write(Paths.get(args[0], "set-aside.txt"), setAside.toString().getBytes(StandardCharsets.UTF_8));
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
        System.exit(0);
    }
}
