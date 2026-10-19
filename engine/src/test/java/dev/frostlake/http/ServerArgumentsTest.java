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

package dev.frostlake.http;

import dev.frostlake.config.EngineConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The server's command line, {@code [port] [configFile] [-f|--file <sqlFile>]}: the file named sets the
 * configuration the server AND its engine run on, and a port on the command line wins over the file's whichever
 * comes first.
 */
public class ServerArgumentsTest {

    @TempDir
    Path dir;

    private String configFile(final String... lines) throws IOException {
        final Path file = dir.resolve("server.properties");
        Files.write(file, String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
        return file.toString();
    }

    @Test
    public void thePortArgumentWinsOverTheFile() throws IOException {
        final String file = configFile("http.port=19999", "database.default=args_db");
        final EngineConfig config = ServerArguments.parse(new String[] {"22652", file}).config();
        assertEquals(22652, config.getHttpPort());
        assertEquals("args_db", config.getProperty(EngineConfig.PROP_DATABASE_DEFAULT, null));
    }

    @Test
    public void aFileWithoutAPortKeepsThePortArgument() throws IOException {
        final String file = configFile("database.default=args_db");
        assertEquals(22653, ServerArguments.parse(new String[] {"22653", file}).config().getHttpPort());
    }

    @Test
    public void aPortThatIsNoNumberLeavesTheFilesPort() throws IOException {
        final String file = configFile("http.port=19998");
        assertEquals(19998, ServerArguments.parse(new String[] {"not-a-port", file}).config().getHttpPort());
    }

    @Test
    public void theInitFileIsReadWhereverItStands() throws IOException {
        final String file = configFile("http.port=19997");
        final ServerArguments first = ServerArguments.parse(new String[] {"-f", "init.sql", "22654", file});
        assertEquals("init.sql", first.initFile());
        assertEquals(22654, first.config().getHttpPort());
        final ServerArguments last = ServerArguments.parse(new String[] {"22655", "--file", "boot.sql"});
        assertEquals("boot.sql", last.initFile());
        assertEquals(22655, last.config().getHttpPort());
        assertNull(ServerArguments.parse(new String[] {"22656"}).initFile());
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                ServerArguments.parse(new String[] {"22657", "-f"});
            }
        });
    }

    /** The engine of a server started on a file's configuration runs on it: it keeps what the file tells it to. */
    @Test
    public void theEngineRunsOnTheFilesConfiguration() throws IOException {
        final Path data = dir.resolve("data");
        final String file = configFile("persistence.enabled=true",
            "persistence.directory=" + data.toString().replace("\\", "/"), "persistence.autoSave=false");
        final DatabaseHttpServer server = new DatabaseHttpServer(ServerArguments.parse(new String[] {"0", file})
            .config());
        try {
            final SessionContext session = server.getEngine().getOrCreateSession("args");
            server.getEngine().execute("CREATE DATABASE args_keep", session);
        } finally {
            server.stop();
        }
        assertTrue(Files.exists(data.resolve("catalog.dat")), "the snapshot is written where the file says");
    }
}
