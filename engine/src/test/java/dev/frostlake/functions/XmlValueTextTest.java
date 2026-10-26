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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * An XML value read as text is its XML: TO_VARCHAR, TO_CHAR and a cast of a staged record or of PARSE_XML
 * spell the element, its attributes and its children as XML, where TO_JSON, an ARRAY and an OBJECT holding
 * the value keep its JSON form; || and TRY_CAST refuse it, and TO_VARCHAR refuses a format over it (all
 * live-verified). The files are written to a temporary directory and PUT on a stage.
 */
public class XmlValueTextTest extends MeasuredCellsTestSupport {

    private Path dir;

    @BeforeEach
    public void makeDirectory() throws IOException {
        dir = Files.createTempDirectory("xml_text_");
    }

    @AfterEach
    public void removeDirectory() {
        final File[] children = dir.toFile().listFiles();
        if (children != null) {
            for (final File child : children) {
                child.delete();
            }
        }
        dir.toFile().delete();
    }

    /** Writes a file and answers the PUT that stages it, uncompressed. */
    private String put(final String stage, final String name, final String content) throws IOException {
        final Path file = dir.resolve(name);
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return "PUT file://" + file + " @" + stage + " AUTO_COMPRESS=FALSE";
    }

    @Test
    public void anXmlValueReadsAsItsXmlText() throws IOException {
        assertAnswer("Stage area STX737 successfully created.",
            "CREATE OR REPLACE STAGE stx737 FILE_FORMAT = (TYPE = XML)");
        assertAnswer("r1.xml|r1.xml|15|15|NONE|NONE|UPLOADED|ENCRYPTED|",
            put("stx737", "r1.xml", "<r><a>1</a></r>"));
        assertAnswer("r2.xml|r2.xml|54|54|NONE|NONE|UPLOADED|ENCRYPTED|",
            put("stx737", "r2.xml", "<r id=\"5\"><a>1</a><b>x</b></r>\n<r id=\"6\"><a>2</a></r>\n"));
        assertAnswer("<r><a>1</a></r>", "SELECT $1 FROM @stx737/r1.xml");
        assertAnswer("<r><a>1</a></r>", "SELECT TO_VARCHAR($1) FROM @stx737/r1.xml");
        assertAnswer("<r><a>1</a></r>", "SELECT $1::VARCHAR FROM @stx737/r1.xml");
        assertAnswer("XML", "SELECT TYPEOF($1) FROM @stx737/r1.xml");
        assertAnswer("{\"$\":{\"$\":1,\"@\":\"a\"},\"@\":\"r\",\"a\":0}", "SELECT TO_JSON($1) FROM @stx737/r1.xml");
        assertAnswer("<r id=\"5\"><a>1</a><b>x</b></r> ;; <r id=\"6\"><a>2</a></r>",
            "SELECT TO_VARCHAR($1) FROM @stx737/r2.xml ORDER BY 1");
        assertAnswer("<r><a>1</a></r>", "SELECT TO_VARCHAR(PARSE_XML('<r><a>1</a></r>'))");
        assertAnswer("{\"$\":{\"$\":1,\"@\":\"a\"},\"@\":\"r\",\"a\":0}",
            "SELECT TO_JSON(PARSE_XML('<r><a>1</a></r>'))");
        assertAnswer("<a>1</a>", "SELECT TO_VARCHAR(XMLGET(PARSE_XML('<r><a>1</a></r>'), 'a'))");
        assertAnswer("[{\"$\":{\"$\":1,\"@\":\"a\"},\"@\":\"r\",\"a\":0}]",
            "SELECT ARRAY_CONSTRUCT(PARSE_XML('<r><a>1</a></r>'))");
        assertAnswer("{\"k\":{\"$\":{\"$\":1,\"@\":\"a\"},\"@\":\"r\",\"a\":0}}",
            "SELECT OBJECT_CONSTRUCT('k', PARSE_XML('<r><a>1</a></r>'))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 36 | Invalid argument types for "
                + "function '||': (OBJECT, VARCHAR(1))",
            "SELECT PARSE_XML('<r><a>1</a></r>') || ''");
        assertAnswer("<r id=\"5\"><a>1</a><b>x</b></r>",
            "SELECT TO_VARCHAR(PARSE_XML('<r id=\"5\"><a>1</a><b>x</b></r>'))");
        assertAnswer("15", "SELECT LENGTH(TO_VARCHAR(PARSE_XML('<r><a>1</a></r>')))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[TO_VARCHAR(PARSE_XML('<r><a>1</a></r>'), 'X')] expected 1, got 2",
            "SELECT TO_VARCHAR(PARSE_XML('<r><a>1</a></r>'), 'X')");
        assertAnswer("REFUSED SQL compilation error: | Function TRY_CAST cannot be used with arguments of types "
                + "OBJECT and VARCHAR(134217728)",
            "SELECT TRY_CAST(PARSE_XML('<r><a>1</a></r>') AS VARCHAR)");
        assertAnswer("<r><a>1</a></r>", "SELECT TO_CHAR(PARSE_XML('<r><a>1</a></r>'))");
    }
}
