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

package dev.frostlake.jdbc;

import java.sql.Connection;

/**
 * One concurrent client's work in {@code ConcurrentClientsDmlTest}: the client's ordinal and its
 * own JDBC connection, run on a dedicated thread. A custom interface (not {@code java.util.function.*}),
 * implemented with anonymous classes, per the project's callback convention.
 */
public interface ClientBody {

    void run(int clientId, Connection connection) throws Exception;
}
