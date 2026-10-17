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

import org.junit.jupiter.api.Test;

/**
 * A base64 text decodes only as whole groups of four characters, its line breaks dropped — {@code 'QQ'} and
 * {@code 'QQ =='} are not legal base64 where {@code 'QUJD\nRA=='} decodes — and a DATE has no epoch finer than a
 * second: {@code DATE_PART(epoch_millisecond, d)} is refused as an invalid date/time part while the statement
 * compiles, as every epoch of a TIME is (live-verified).
 */
public class EpochPartAndBase64Test extends MeasuredCellsTestSupport {

    @Test
    public void wholeGroupsDecodeAndADateHasNoFineEpoch() {
        engine.execute("CREATE OR REPLACE TABLE dd (d DATE, t TIME)");
        engine.execute("INSERT INTO dd VALUES ('2024-03-15', '10:20:30')");
        assertCells(new String[][] {
            {"SELECT BASE64_DECODE_STRING('QQ')",
                "REFUSED The following string is not a legal base64-encoded value: 'QQ'"},
            {"SELECT BASE64_DECODE_STRING('QQ=')",
                "REFUSED The following string is not a legal base64-encoded value: 'QQ='"},
            {"SELECT BASE64_DECODE_STRING('QUJDRA')",
                "REFUSED The following string is not a legal base64-encoded value: 'QUJDRA'"},
            {"SELECT BASE64_DECODE_BINARY('QQ')",
                "REFUSED The following string is not a legal base64-encoded value: 'QQ'"},
            {"SELECT TRY_BASE64_DECODE_BINARY('QQ')", "null"},
            {"SELECT BASE64_DECODE_STRING('QQ ==')",
                "REFUSED The following string is not a legal base64-encoded value: 'QQ =='"},
            {"SELECT BASE64_DECODE_STRING('Q Q==')",
                "REFUSED The following string is not a legal base64-encoded value: 'Q Q=='"},
            {"SELECT BASE64_DECODE_STRING('QQ==\\\n')", "A"},
            {"SELECT BASE64_DECODE_STRING('QUJD\\\nRA==')", "ABCD"},
            {"SELECT BASE64_DECODE_STRING('QQ=====')",
                "REFUSED The following string is not a legal base64-encoded value: 'QQ====='"},
            {"SELECT BASE64_DECODE_STRING('QUJDRA==QQ==')",
                "REFUSED The following string is not a legal base64-encoded value: 'QUJDRA==QQ=='"},
            {"SELECT TRY_BASE64_DECODE_STRING('QQ ==')", "null"},
            {"SELECT TRY_BASE64_DECODE_STRING('QUJDR')", "null"},
            {"SELECT BASE64_DECODE_STRING('QUJDRA', '+/=')",
                "REFUSED The following string is not a legal base64-encoded value: 'QUJDRA'"},
            {"SELECT BASE64_DECODE_STRING('QUJDRA==', '+/=')", "ABCD"},
            {"SELECT BASE64_DECODE_STRING('QUJDRA', '$')",
                "REFUSED The following string is not a legal base64-encoded value: 'QUJDRA'"},
            {"SELECT DATE_PART(epoch_microsecond, d) FROM dd",
                "REFUSED SQL compilation error: | invalid value [EPOCH_MICROSECOND] for parameter 'DATE_PART "
                + "date/time part'"},
            {"SELECT DATE_PART(epoch_nanosecond, d) FROM dd",
                "REFUSED SQL compilation error: | invalid value [EPOCH_NANOSECOND] for parameter 'DATE_PART "
                + "date/time part'"},
            {"SELECT EXTRACT(epoch_millisecond FROM d) FROM dd",
                "REFUSED SQL compilation error: | invalid value [epoch_millisecond] for parameter 'EXTRACT "
                + "date/time part'"},
            {"SELECT DATE_PART(epoch_second, d) FROM dd", "1710460800"},
            {"SELECT DATE_PART(epoch, d) FROM dd", "1710460800"},
            {"SELECT DATE_PART('epoch_millisecond', d) FROM dd",
                "REFUSED SQL compilation error: | invalid value [epoch_millisecond] for parameter 'DATE_PART "
                + "date/time part'"},
            {"SELECT DATE_PART(epoch_millisecond, '2024-03-15')",
                "REFUSED SQL compilation error: | Function EXTRACT does not support VARCHAR(10) argument type"},
            {"SELECT DATE_PART(epoch_millisecond, t) FROM dd",
                "REFUSED SQL compilation error: | invalid value [EPOCH_MILLISECOND] for parameter 'DATE_PART "
                + "date/time part'"},
            {"SELECT DATE_PART(epoch_second, t) FROM dd",
                "REFUSED SQL compilation error: | invalid value [EPOCH_SECOND] for parameter 'DATE_PART date/time "
                + "part'"},
            {"SELECT DATE_PART(epoch_millisecond, NULL::DATE)",
                "REFUSED SQL compilation error: | invalid value [EPOCH_MILLISECOND] for parameter 'DATE_PART "
                + "date/time part'"},
            {"SELECT DATE_PART(epoch_milliseconds, d) FROM dd",
                "REFUSED SQL compilation error: | invalid value [EPOCH_MILLISECONDS] for parameter 'DATE_PART "
                + "date/time part'"},
            {"SELECT DATE_PART(epoch_ms, d) FROM dd",
                "REFUSED SQL compilation error: | invalid value [EPOCH_MS] for parameter 'DATE_PART date/time part'"},
            {"SELECT 1 FROM dd WHERE DATE_PART(epoch_millisecond, d) > 0",
                "REFUSED SQL compilation error: | invalid value [EPOCH_MILLISECOND] for parameter 'DATE_PART "
                + "date/time part'"},
            {"SELECT DATE_PART(epoch_millisecond, d) FROM dd WHERE FALSE",
                "REFUSED SQL compilation error: | invalid value [EPOCH_MILLISECOND] for parameter 'DATE_PART "
                + "date/time part'"},
            {"CREATE OR REPLACE TABLE cj AS SELECT CHECK_JSON('{a') c, CHECK_XML('<a') x",
                "Table CJ successfully created."},
            {"DESCRIBE TABLE cj",
                "C|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "X|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null"},
            {"CREATE OR REPLACE TABLE lb AS SELECT LPAD(TO_BINARY('AB', 'HEX'), 3, TO_BINARY('00', 'HEX')) b",
                "Table LB successfully created."},
            {"DESCRIBE TABLE lb", "B|BINARY(8388608)|COLUMN|Y|null|N|N|null|null|null|null|null|null"},
        });
    }
}
