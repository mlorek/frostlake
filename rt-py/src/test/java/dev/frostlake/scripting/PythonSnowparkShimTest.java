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

package dev.frostlake.scripting;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pure-Python {@code snowflake.snowpark} emulation handed to Python procedure handlers as
 * {@code session}: snowpark imports resolve locally, {@code session.sql} runs DML/DDL as well as
 * queries, and the DataFrame surface the vendor loaders use (select over col/parse_json/cast/alias,
 * a UDTF applied via select, joins including leftanti, unionAll, drop_duplicates and
 * {@code df.write.save_as_table}) operates on materialized rows.
 */
public class PythonSnowparkShimTest extends BaseDatabaseTest {

    private String callString(final String procCall) {
        final ResultSet rs = engine.executeQuery("CALL " + procCall);
        return String.valueOf(rs.getRows().get(0).getValue(0));
    }

    @Test
    public void snowparkImportsResolve() {
        engine.execute("""
            CREATE OR REPLACE PROCEDURE shim_imports()
            RETURNS STRING
            LANGUAGE PYTHON
            RUNTIME_VERSION='3.11'
            PACKAGES=('snowflake-snowpark-python')
            HANDLER='main'
            AS $$
            import snowflake.snowpark as sp
            from snowflake.snowpark.functions import udtf, parse_json, col, coalesce, lit, array_construct, current_timestamp, array_max
            from snowflake.snowpark.types import StringType, ArrayType, TimestampType, StructField, StructType, IntegerType, LongType
            from snowflake.snowpark.session import Session
            from snowflake.snowpark.exceptions import SnowparkSQLException

            def main(session):
                return sp.__version__
            $$""");
        assertEquals("1.30.0", callString("shim_imports()"));
    }

    @Test
    public void sessionSqlRunsDmlAndQueries() {
        engine.execute("CREATE TABLE shim_dml (id INT, name VARCHAR)");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE shim_dml_proc()
            RETURNS STRING
            LANGUAGE PYTHON
            RUNTIME_VERSION='3.11'
            HANDLER='main'
            AS $$
            def main(session):
                session.sql("INSERT INTO shim_dml VALUES (1, 'a'), (2, 'b')").collect()
                session.sql("DELETE FROM shim_dml WHERE id = 2").collect()
                count = session.sql("SELECT COUNT(*) AS cnt FROM shim_dml").collect()[0][0]
                by_name = session.sql("SELECT name FROM shim_dml").collect()[0]
                return str(count) + ':' + by_name['NAME'] + ':' + by_name.NAME
            $$""");
        assertEquals("1:a:a", callString("shim_dml_proc()"));
    }

    @Test
    public void dataFrameSelectCastAliasAndVariantField() {
        engine.execute("CREATE TABLE shim_src (id INT, payload VARIANT)");
        engine.execute("INSERT INTO shim_src SELECT 7, PARSE_JSON('{\"name\": \"x\", \"score\": 41}')");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE shim_select_proc()
            RETURNS STRING
            LANGUAGE PYTHON
            RUNTIME_VERSION='3.11'
            HANDLER='main'
            AS $$
            from snowflake.snowpark.functions import col
            from snowflake.snowpark.types import IntegerType, StringType

            def main(session):
                df = session.table('shim_src').select(
                    col('payload')['name'].cast(StringType()).as_('n'),
                    col('payload')['score'].cast(IntegerType()).as_('s'),
                    col('id').cast(StringType()).as_('i')
                )
                row = df.collect()[0]
                return '%s/%s/%s/%s' % (row['N'], row['S'] + 1, row['I'], ','.join(df.columns))
            $$""");
        assertEquals("x/42/7/N,S,I", callString("shim_select_proc()"));
    }

    @Test
    public void udtfAppliedThroughSelectAndWrittenBack() {
        engine.execute("CREATE TABLE shim_in (grp_id INT, items VARIANT)");
        engine.execute("INSERT INTO shim_in SELECT 1, PARSE_JSON('[{\"v\": 2}, {\"v\": 3}]')");
        engine.execute("INSERT INTO shim_in SELECT 2, PARSE_JSON('[{\"v\": 10}]')");
        engine.execute("CREATE TABLE shim_out (grp_id NUMBER, total NUMBER)");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE shim_udtf_proc()
            RETURNS STRING
            LANGUAGE PYTHON
            RUNTIME_VERSION='3.11'
            HANDLER='main'
            AS $$
            import json
            from snowflake.snowpark.functions import udtf, parse_json, col
            from snowflake.snowpark.types import StructType, StructField, StringType, ArrayType, LongType, IntegerType

            def main(session):
                class SumHandler:
                    def process(self, grp_id, items):
                        total = 0
                        for item in items:
                            total += item['v']
                        yield (json.dumps({'grp_id': grp_id, 'total': total}),)

                sum_udtf = udtf(
                    SumHandler,
                    output_schema=StructType([StructField('result_json', StringType())]),
                    input_types=[LongType(), ArrayType()]
                )
                df = (
                    session.table('shim_in')
                    .select(sum_udtf('grp_id', 'items'))
                    .select(parse_json('result_json').alias('r'))
                    .select(
                        col('r')['grp_id'].cast(LongType()).as_('grp_id'),
                        col('r')['total'].cast(IntegerType()).as_('total')
                    )
                )
                df.write.mode('append').save_as_table('shim_out')
                return 'rows=%d' % df.count()
            $$""");
        assertEquals("rows=2", callString("shim_udtf_proc()"));
        final ResultSet rs = engine.executeQuery("SELECT grp_id, total FROM shim_out ORDER BY grp_id");
        assertEquals(2, rs.getRowCount());
        assertEquals(5L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
        assertEquals(10L, ((Number) rs.getRows().get(1).getValue(1)).longValue());
    }

    @Test
    public void joinsLeftInnerAndLeftAnti() {
        engine.execute("CREATE TABLE shim_left (k INT, l VARCHAR)");
        engine.execute("CREATE TABLE shim_right (k INT, r VARCHAR)");
        engine.execute("INSERT INTO shim_left VALUES (1, 'l1'), (2, 'l2'), (3, 'l3')");
        engine.execute("INSERT INTO shim_right VALUES (1, 'r1'), (3, 'r3')");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE shim_join_proc()
            RETURNS STRING
            LANGUAGE PYTHON
            RUNTIME_VERSION='3.11'
            HANDLER='main'
            AS $$
            def main(session):
                left = session.table('shim_left')
                right = session.table('shim_right')
                inner = left.join(right, 'k', 'inner')
                left_join = left.join(right, on='k', how='left')
                anti = left.join(right, 'k', 'leftanti').select(left.col('*'))
                missing = left_join.collect()[1]['R'] if left_join.count() == 3 else 'bad'
                return '%d/%d/%d/%s/%s' % (
                    inner.count(), left_join.count(), anti.count(),
                    str(missing), anti.collect()[0]['L'])
            $$""");
        assertEquals("2/3/1/None/l2", callString("shim_join_proc()"));
    }

    @Test
    public void unionAllDropDuplicatesAndFunctions() {
        engine.execute("""
            CREATE OR REPLACE PROCEDURE shim_union_proc()
            RETURNS STRING
            LANGUAGE PYTHON
            RUNTIME_VERSION='3.11'
            HANDLER='main'
            AS $$
            from snowflake.snowpark.functions import col, coalesce, lit, array_construct, current_timestamp

            def main(session):
                a = session.sql("SELECT 1 AS k, NULL AS v")
                b = session.sql("SELECT 1 AS k, NULL AS v")
                unioned = a.union_all(b)
                deduped = unioned.drop_duplicates('k')
                filled = deduped.select(
                    col('k'),
                    coalesce(col('v'), lit('DEFAULT')).as_('v'),
                    array_construct(lit('UNDEFINED')).as_('arr'),
                    current_timestamp().as_('ts')
                )
                row = filled.collect()[0]
                has_ts = row['TS'] is not None
                return '%d/%d/%s/%s/%s' % (unioned.count(), deduped.count(), row['V'], row['ARR'][0], has_ts)
            $$""");
        assertEquals("2/1/DEFAULT/UNDEFINED/True", callString("shim_union_proc()"));
    }

    @Test
    public void writerOverwriteTruncatesExistingTable() {
        engine.execute("CREATE TABLE shim_target (k NUMBER, v VARCHAR)");
        engine.execute("INSERT INTO shim_target VALUES (99, 'stale')");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE shim_overwrite_proc()
            RETURNS STRING
            LANGUAGE PYTHON
            RUNTIME_VERSION='3.11'
            HANDLER='main'
            AS $$
            def main(session):
                df = session.sql("SELECT 1 AS k, 'fresh' AS v")
                df.write.mode('overwrite').saveAsTable('shim_target', column_order='name', table_type='transient')
                return 'ok'
            $$""");
        assertEquals("ok", callString("shim_overwrite_proc()"));
        final ResultSet rs = engine.executeQuery("SELECT k, v FROM shim_target");
        assertEquals(1, rs.getRowCount());
        assertEquals("fresh", rs.getRows().get(0).getValue(1));
    }

    @Test
    public void schemaAndFilterAndLimit() {
        engine.execute("CREATE TABLE shim_meta (id NUMBER, label VARCHAR, flag BOOLEAN)");
        engine.execute("INSERT INTO shim_meta VALUES (1, 'keep', TRUE), (2, 'drop', FALSE), (3, 'keep', TRUE)");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE shim_meta_proc()
            RETURNS STRING
            LANGUAGE PYTHON
            RUNTIME_VERSION='3.11'
            HANDLER='main'
            AS $$
            from snowflake.snowpark.functions import col

            def main(session):
                df = session.table('shim_meta')
                names = [f.datatype.__class__.__name__ for f in df.schema]
                kept = df.filter(col('label') == 'keep')
                empty = session.table('shim_meta').limit(0)
                return '%s/%d/%d/%d' % (','.join(names), kept.count(), empty.count(), len(empty.columns))
            $$""");
        assertEquals("LongType,StringType,BooleanType/2/0/3", callString("shim_meta_proc()"));
    }

    @Test
    public void variantColumnsArriveAsPythonContainers() {
        engine.execute("CREATE TABLE shim_var (id INT, arr VARIANT, obj VARIANT)");
        engine.execute("INSERT INTO shim_var SELECT 1, PARSE_JSON('[1, 2, 3]'), PARSE_JSON('{\"a\": {\"b\": 4}}')");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE shim_var_proc()
            RETURNS STRING
            LANGUAGE PYTHON
            RUNTIME_VERSION='3.11'
            HANDLER='main'
            AS $$
            def main(session):
                row = session.table('shim_var').collect()[0]
                arr = row['ARR']
                obj = row['OBJ']
                return '%s/%d/%d' % (type(arr).__name__, sum(arr), obj['a']['b'])
            $$""");
        assertEquals("list/6/4", callString("shim_var_proc()"));
    }

    @Test
    public void withColumnUnionByNameAndGroupByAgg() {
        engine.execute("CREATE TABLE shim_grp (g VARCHAR, v NUMBER, ts NUMBER)");
        engine.execute("INSERT INTO shim_grp VALUES ('a', 1, 10), ('a', 5, 30), ('b', 2, 20)");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE shim_grp_proc()
            RETURNS STRING
            LANGUAGE PYTHON
            RUNTIME_VERSION='3.11'
            HANDLER='main'
            AS $$
            from snowflake.snowpark.functions import col, lit, max

            def main(session):
                df = session.table('shim_grp')
                agg = df.group_by("G").agg(max(df["TS"]).alias("MAX_TS"))
                by_a = [r for r in agg.collect() if r['G'] == 'a'][0]
                extended = df.with_column("FLAG", lit('x'))
                reordered = extended.select(col('FLAG'), col('G'), col('V'), col('TS'))
                unioned = extended.union_by_name(reordered)
                return '%d/%d/%d/%d' % (agg.count(), by_a['MAX_TS'], len(extended.columns), unioned.count())
            $$""");
        assertEquals("2/30/4/6", callString("shim_grp_proc()"));
    }

    @Test
    public void windowRowNumberDedupPattern() {
        engine.execute("CREATE TABLE shim_win (g VARCHAR, k VARCHAR, ord VARCHAR)");
        engine.execute("INSERT INTO shim_win VALUES ('g1', 'k1', 'b'), ('g1', 'k1', 'a'), ('g1', 'k2', 'z'), ('g2', 'k1', 'm')");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE shim_win_proc()
            RETURNS STRING
            LANGUAGE PYTHON
            RUNTIME_VERSION='3.11'
            HANDLER='main'
            AS $$
            from snowflake.snowpark import Window
            from snowflake.snowpark import functions as sf

            def main(session):
                deduped = (
                    session.table('shim_win')
                    .with_column('row_number',
                        sf.row_number().over(Window.partition_by('g', 'k').order_by('ord')))
                    .where('row_number = 1')
                    .drop('row_number')
                )
                rows = sorted((r['G'], r['K'], r['ORD']) for r in deduped.collect())
                return ';'.join('%s-%s-%s' % r for r in rows)
            $$""");
        assertEquals("g1-k1-a;g1-k2-z;g2-k1-m", callString("shim_win_proc()"));
    }

    @Test
    public void sessionErrorsRaiseSnowparkSqlException() {
        engine.execute("""
            CREATE OR REPLACE PROCEDURE shim_error_proc()
            RETURNS STRING
            LANGUAGE PYTHON
            RUNTIME_VERSION='3.11'
            HANDLER='main'
            AS $$
            from snowflake.snowpark.exceptions import SnowparkSQLException

            def main(session):
                try:
                    session.sql('SELECT * FROM shim_no_such_table').collect()
                    return 'no error'
                except SnowparkSQLException as e:
                    return 'caught:' + ('yes' if 'SHIM_NO_SUCH_TABLE' in str(e).upper() else str(e))
            $$""");
        final String result = callString("shim_error_proc()");
        assertTrue(result.startsWith("caught:"), "expected SnowparkSQLException, got: " + result);
        assertEquals("caught:yes", result);
    }

    @Test
    public void aBareFlagConditionFiltersLocalRowsEvenWithNoValueToTypeIt() {
        // An empty frame spills its columns as VARIANT, which is no predicate: the flag is read over the rows.
        engine.execute("CREATE TABLE shim_flags (id INT, status VARCHAR)");
        engine.execute("INSERT INTO shim_flags VALUES (1, 'ACTIVE'), (2, 'DEACTIVATED'), (3, NULL)");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE shim_flag_filter()
            RETURNS STRING
            LANGUAGE PYTHON
            RUNTIME_VERSION='3.11'
            PACKAGES=('snowflake-snowpark-python')
            HANDLER='main'
            AS $$
            from snowflake.snowpark.functions import col

            def main(session):
                df = session.table('shim_flags').with_column('active', col('status') == 'ACTIVE')
                kept = df.where('active').count()
                empty = df.filter(col('id') > 10).with_column('flag', col('status') == 'ACTIVE')
                return str(kept) + ':' + str(empty.where('flag').count())
            $$""");
        assertEquals("1:0", callString("shim_flag_filter()"));
    }
}
