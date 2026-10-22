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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * A staged XML file read by a query: Snowflake's XML model, as PARSE_XML builds it - every top-level element a
 * record that prints as XML and that XMLGET reads, every element those hold under STRIP_OUTER_ELEMENT, text
 * converted unless DISABLE_AUTO_CONVERT and trimmed unless PRESERVE_SPACE - and a byte that is no UTF-8
 * refused in the account's words unless REPLACE_INVALID_CHARACTERS or IGNORE_UTF8_ERRORS reads it as U+FFFD
 * (all live-verified). The files are written to a temporary directory and PUT on a stage.
 */
public class StagedXmlRecordTest extends MeasuredCellsTestSupport {

    /** Each file the stages hold, by name. */
    private static final Map<String, byte[]> FILES = new HashMap<>();

    static {
        FILES.put("good.xml", "<root><a>caf\u00e9</a></root>".getBytes(StandardCharsets.UTF_8));
        FILES.put("bad.xml", "<root><a>caf\u00e9</a></root>".getBytes(StandardCharsets.ISO_8859_1));
        FILES.put("bad2.xml", "<root><a>x\u00ff\u00fey</a><b>1</b></root>".getBytes(StandardCharsets.ISO_8859_1));
        FILES.put("attr.xml", "<root id=\"7\"><a>1</a><a>x</a><b/></root>".getBytes(StandardCharsets.UTF_8));
        FILES.put("ws.xml", "<root><a>  x  </a></root>".getBytes(StandardCharsets.UTF_8));
        FILES.put("multi.xml", "<a>1</a><a>2</a>".getBytes(StandardCharsets.UTF_8));
        FILES.put("decl.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><!-- c --><root><a>1</a></root>"
            .getBytes(StandardCharsets.UTF_8));
        FILES.put("nested.xml", "<root><p><q>1</q></p><p><q>2</q></p></root>".getBytes(StandardCharsets.UTF_8));
        FILES.put("bad.json", "{\"a\":\"caf\u00e9\",\"n\":1}\n".getBytes(StandardCharsets.ISO_8859_1));
    }

    private Path dir;

    @BeforeEach
    public void makeDirectory() throws IOException {
        dir = Files.createTempDirectory("staged_xml_");
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

    /** Writes a file of {@link #FILES} and PUTs it on a stage, uncompressed. */
    private void put(final String stage, final String name) throws IOException {
        final Path file = dir.resolve(name);
        Files.write(file, FILES.get(name));
        engine.execute("PUT file://" + file + " @" + stage + " AUTO_COMPRESS=FALSE");
    }

    /** A byte that is no UTF-8: refused, or read as U+FFFD under either option. */
    @Test
    public void invalidUtf8() throws IOException {
        engine.execute("CREATE OR REPLACE STAGE enc2_st");
        put("enc2_st", "bad.xml");
        put("enc2_st", "bad2.xml");
        put("enc2_st", "good.xml");
        engine.execute("CREATE OR REPLACE FILE FORMAT fx2071 TYPE = XML");
        engine.execute("CREATE OR REPLACE FILE FORMAT fx2071_rep TYPE = XML REPLACE_INVALID_CHARACTERS = TRUE");
        engine.execute("CREATE OR REPLACE FILE FORMAT fx2071_ign TYPE = XML IGNORE_UTF8_ERRORS = TRUE");
        engine.execute("CREATE OR REPLACE FILE FORMAT fa2071 TYPE = AVRO");
        engine.execute("CREATE OR REPLACE FILE FORMAT fa2071_rep TYPE = AVRO REPLACE_INVALID_CHARACTERS = TRUE");
        assertAnswer("<root><a>caf\u00e9</a></root>", "SELECT $1 FROM @enc2_st/good.xml (FILE_FORMAT => 'fx2071')");
        assertShapedAnswer("REFUSED Error parsing XML: invalid UTF-8 sequence: 0xE9 |   File 'stages/<QID>/bad.xml', "
                + "line 1, character 13 |   Row 0, column $1",
            "SELECT $1 FROM @enc2_st/bad.xml (FILE_FORMAT => 'fx2071')");
        assertAnswer("<root><a>caf\ufffd</a></root>", "SELECT $1 FROM @enc2_st/bad.xml (FILE_FORMAT => 'fx2071_rep')");
        assertAnswer("caf\ufffd|4",
            "SELECT XMLGET($1, 'a'):\"$\"::VARCHAR, LENGTH(XMLGET($1, 'a'):\"$\"::VARCHAR) FROM @enc2_st/bad.xml "
                + "(FILE_FORMAT => 'fx2071_rep')");
        assertAnswer("636166EFBFBD",
            "SELECT HEX_ENCODE(XMLGET($1, 'a'):\"$\"::VARCHAR) FROM @enc2_st/bad.xml (FILE_FORMAT => 'fx2071_rep')");
        assertAnswer("<root><a>caf\ufffd</a></root>", "SELECT $1 FROM @enc2_st/bad.xml (FILE_FORMAT => 'fx2071_ign')");
        assertShapedAnswer("REFUSED Error parsing XML: invalid UTF-8 sequence: 0xFF |   File 'stages/<QID>/bad2.xml', "
                + "line 1, character 11 |   Row 0, column $1",
            "SELECT $1 FROM @enc2_st/bad2.xml (FILE_FORMAT => 'fx2071')");
        assertAnswer("78EFBFBDEFBFBD79|1",
            "SELECT HEX_ENCODE(XMLGET($1, 'a'):\"$\"::VARCHAR), XMLGET($1, 'b'):\"$\" FROM @enc2_st/bad2.xml "
                + "(FILE_FORMAT => 'fx2071_rep')");
    }

    /** The XML model, its options, and several top-level elements. */
    @Test
    public void recordModel() throws IOException {
        engine.execute("CREATE OR REPLACE STAGE enc3_st");
        put("enc3_st", "good.xml");
        put("enc3_st", "attr.xml");
        put("enc3_st", "ws.xml");
        put("enc3_st", "multi.xml");
        put("enc3_st", "decl.xml");
        put("enc3_st", "nested.xml");
        put("enc3_st", "bad.json");
        engine.execute("CREATE OR REPLACE FILE FORMAT fx2071 TYPE = XML");
        engine.execute("CREATE OR REPLACE FILE FORMAT fx2071_strip TYPE = XML STRIP_OUTER_ELEMENT = TRUE");
        engine.execute("CREATE OR REPLACE FILE FORMAT fx2071_noconv TYPE = XML DISABLE_AUTO_CONVERT = TRUE");
        engine.execute("CREATE OR REPLACE FILE FORMAT fx2071_pres TYPE = XML PRESERVE_SPACE = TRUE");
        engine.execute("CREATE OR REPLACE FILE FORMAT fj2071 TYPE = JSON");
        assertAnswer("<root><a>caf\u00e9</a></root>", "SELECT PARSE_XML('<root><a>caf\u00e9</a></root>')");
        assertAnswer("<root><a>caf\u00e9</a></root>", "SELECT $1 FROM @enc3_st/good.xml (FILE_FORMAT => 'fx2071')");
        assertAnswer("XML", "SELECT TYPEOF($1) FROM @enc3_st/good.xml (FILE_FORMAT => 'fx2071')");
        assertAnswer("<root><a>caf\u00e9</a></root>",
            "SELECT $1::VARCHAR FROM @enc3_st/good.xml (FILE_FORMAT => 'fx2071')");
        assertAnswer("<a>caf\u00e9</a>", "SELECT XMLGET($1, 'a') FROM @enc3_st/good.xml (FILE_FORMAT => 'fx2071')");
        assertAnswer("root", "SELECT GET($1, '@') FROM @enc3_st/good.xml (FILE_FORMAT => 'fx2071')");
        assertAnswer("<root id=\"7\"><a>1</a><a>x</a><b></b></root>",
            "SELECT $1 FROM @enc3_st/attr.xml (FILE_FORMAT => 'fx2071')");
        assertAnswer("7|INTEGER",
            "SELECT GET($1, '@id'), TYPEOF(GET($1, '@id')) FROM @enc3_st/attr.xml (FILE_FORMAT => 'fx2071')");
        assertAnswer("x|INTEGER",
            "SELECT XMLGET($1, 'a', 1):\"$\", TYPEOF(XMLGET($1, 'a', 0):\"$\") FROM @enc3_st/attr.xml (FILE_FORMAT "
                + "=> 'fx2071')");
        assertAnswer("1|<a>1</a> ;; 2|<a>x</a> ;; 3|<b></b>",
            "SELECT METADATA$FILE_ROW_NUMBER, $1 FROM @enc3_st/attr.xml (FILE_FORMAT => 'fx2071_strip') ORDER BY 1");
        assertAnswer("7|VARCHAR|VARCHAR",
            "SELECT GET($1, '@id'), TYPEOF(GET($1, '@id')), TYPEOF(XMLGET($1, 'a'):\"$\") FROM @enc3_st/attr.xml "
                + "(FILE_FORMAT => 'fx2071_noconv')");
        assertAnswer("<root><a>x</a></root>|1",
            "SELECT $1, LENGTH(XMLGET($1, 'a'):\"$\"::VARCHAR) FROM @enc3_st/ws.xml (FILE_FORMAT => 'fx2071')");
        assertAnswer("<root><a xml:space=\"preserve\">  x  </a></root>|5",
            "SELECT $1, LENGTH(XMLGET($1, 'a'):\"$\"::VARCHAR) FROM @enc3_st/ws.xml (FILE_FORMAT => 'fx2071_pres')");
        assertAnswer("1|<a>1</a> ;; 2|<a>2</a>",
            "SELECT METADATA$FILE_ROW_NUMBER, $1 FROM @enc3_st/multi.xml (FILE_FORMAT => 'fx2071') ORDER BY 1");
        assertAnswer("2", "SELECT COUNT(*) FROM @enc3_st/multi.xml (FILE_FORMAT => 'fx2071')");
        assertAnswer("<root><a>1</a></root>", "SELECT $1 FROM @enc3_st/decl.xml (FILE_FORMAT => 'fx2071')");
        assertAnswer("<root><p><q>1</q></p><p><q>2</q></p></root>",
            "SELECT $1 FROM @enc3_st/nested.xml (FILE_FORMAT => 'fx2071')");
        assertAnswer("1|<p><q>1</q></p> ;; 2|<p><q>2</q></p>",
            "SELECT METADATA$FILE_ROW_NUMBER, $1 FROM @enc3_st/nested.xml (FILE_FORMAT => 'fx2071_strip') ORDER BY 1");
        assertAnswer("1|<root><a>caf\u00e9</a></root>",
            "SELECT METADATA$FILE_ROW_NUMBER, $1 FROM @enc3_st/good.xml (FILE_FORMAT => 'fx2071')");
        assertAnswer("<a>caf\u00e9</a>", "SELECT $1 FROM @enc3_st/good.xml (FILE_FORMAT => 'fx2071_strip')");
        assertShapedAnswer("REFUSED Error parsing JSON: invalid UTF-8 sequence: 0xE9 |   File 'stages/<QID>/bad.json',"
                + " line 1, character 10 |   Row 0, column $1",
            "SELECT $1:a FROM @enc3_st/bad.json (FILE_FORMAT => 'fj2071')");
    }
}
