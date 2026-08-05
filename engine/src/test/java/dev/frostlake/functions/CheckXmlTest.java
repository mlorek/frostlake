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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * CHECK_XML: NULL for NULL / empty / valid XML, the parse-error message (no {@code Error parsing
 * XML:} prefix) for malformed XML, and the optional second argument accepted — as in Snowflake.
 */
public class CheckXmlTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void validDocumentsAndNullishInputsReturnNull() {
        assertNull(scalar("SELECT CHECK_XML('<a>1</a>')"));
        assertNull(scalar("SELECT CHECK_XML(NULL)"));
        assertNull(scalar("SELECT CHECK_XML('')"));
        assertNull(scalar("SELECT CHECK_XML('<a attr=\"1\"><b/></a>', TRUE)"),
            "the optional disable_auto_convert argument is accepted");
    }

    @Test
    public void malformedXmlReturnsTheMessage() {
        final Object notXml = scalar("SELECT CHECK_XML('bad')");
        assertNotNull(notXml);
        assertFalse(notXml.toString().startsWith("Error parsing XML"),
            "CHECK_XML returns the bare message, without PARSE_XML's prefix");
        assertNotNull(scalar("SELECT CHECK_XML('<a><b></a>')"));
    }
}
