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

import java.io.IOException;
import java.net.ServerSocket;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The server turns TCP no-delay on for its sockets before the first one exists: without it a kept-alive
 * connection waits on the client's delayed ACK for every response body, about 40 ms a statement on Linux
 * and macOS. The JDK reads the property once, when its server configuration loads, so what is pinned is
 * that constructing the server sets it; the latency itself is timing and is not asserted.
 */
public class ServerNoDelayTest {

    @Test
    public void theServerAsksForNoDelay() throws IOException {
        final int port;
        try (final ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        final DatabaseHttpServer server = new DatabaseHttpServer(port);
        server.start();
        try {
            assertEquals("true", System.getProperty("sun.net.httpserver.nodelay"));
        } finally {
            server.stop();
        }
    }
}
