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

package dev.frostlake.persistence;

import java.io.Serializable;
import java.util.Map;

/** Serializable snapshot of a PIPE (its COPY statement and ingest settings). */
public class PipeSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    public String name;
    public String copyStatement;
    public boolean autoIngest;
    public String notificationChannel;
    public boolean paused;
    public String errorIntegration;
    public String awsSnsTopicArn;
    public String integration;
    public int lastLoadedFileCount;
    public String lastLoadedTime;
    public String comment;
    public String owner;

    // The object's tag associations, tag name -> value. Null in a snapshot written before tags were
    // recorded, which reads back as an object carrying none.
    public Map<String, String> tags;
}
