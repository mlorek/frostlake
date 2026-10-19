/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.frostlake.metastore;

/**
 * The refusal of a create over a name its schema already holds. It reads as every such refusal does —
 * {@code Object 'T' already exists.} — and tells the statement that raised it which refusal it is, so that the
 * statement can name the object as it wrote it.
 */
public class NameTakenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message the refusal's sentence
     */
    public NameTakenException(final String message) {
        super(message);
    }
}
