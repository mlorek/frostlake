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

import dev.frostlake.config.EngineConfig;
import dev.frostlake.functions.scalar.conditional.*;
import dev.frostlake.functions.scalar.context.*;
import dev.frostlake.functions.scalar.conversion.*;
import dev.frostlake.functions.scalar.crypto.*;
import dev.frostlake.functions.scalar.datetime.*;
import dev.frostlake.functions.scalar.encoding.*;
import dev.frostlake.functions.scalar.hash.*;
import dev.frostlake.functions.scalar.math.*;
import dev.frostlake.functions.scalar.semistructured.*;
import dev.frostlake.functions.scalar.string.*;
import dev.frostlake.functions.table.Flatten;
import dev.frostlake.functions.table.Generator;
import dev.frostlake.functions.table.SplitToTable;
import dev.frostlake.functions.table.TaskHistoryFunction;
import dev.frostlake.functions.table.UserTaskCancelFunction;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.security.SessionContext;

import dev.frostlake.functions.aggregate.*;
import dev.frostlake.functions.scalar.GroupingFn;
import dev.frostlake.functions.scalar.GroupingIdFn;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FunctionRegistry {

    private final Map<String, BuiltInFunction> functions;
    private final Map<String, AggregateFunction> aggregateFunctions;
    private final Map<String, TableFunction> tableFunctions;
    private final Catalog catalog;
    private final SessionContext sessionContext;
    private final EngineConfig config;

    public FunctionRegistry(final Catalog catalog) {
        this(catalog, null, null);
    }

    public FunctionRegistry(final Catalog catalog, final SessionContext sessionContext) {
        this(catalog, sessionContext, null);
    }

    public FunctionRegistry(final Catalog catalog, final SessionContext sessionContext, final EngineConfig config) {
        this.functions = new ConcurrentHashMap<>();
        this.aggregateFunctions = new ConcurrentHashMap<>();
        this.tableFunctions = new ConcurrentHashMap<>();
        this.catalog = catalog;
        this.sessionContext = sessionContext;
        this.config = config;
        registerBuiltInFunctions();
    }

    private void registerBuiltInFunctions() {
        // String functions
        register(new Upper());
        register(new Lower());
        register(new Length());
        register(new Substring());
        functions.put("SUBSTR", new Substring()); // SUBSTR is an alias for SUBSTRING
        register(new Concat());
        register(new Trim());
        register(new Replace());
        register(new LTrim());
        register(new RTrim());
        register(new Left());
        register(new Right());
        register(new Insert());
        register(new Char());
        register(new Reverse());
        register(new InitCap());
        register(new LPad());
        register(new RPad());
        register(new CharIndex());
        register(new Position());
        register(new Chr());
        register(new Ascii());
        register(new Unicode());
        register(new Repeat());
        register(new Space());
        register(new SplitPart());
        register(new Split());
        register(new Strtok());
        register(new Contains());
        register(new StartsWith());
        register(new EndsWith());
        register(new Translate());
        register(new RegexpReplace());
        register(new RegexpLike());
        register(new Search());
        functions.put("RLIKE", new RegexpLike());   // RLIKE(subject, pattern) is a synonym for REGEXP_LIKE
        register(new Like());                        // LIKE(subject, pattern) — function-call form of the LIKE operator
        register(new Ilike());                       // ILIKE(subject, pattern) — function-call form of the ILIKE operator
        register(new RegexpSubstr());
        register(new RegexpSubstrAll());
        functions.put("REGEXP_EXTRACT_ALL", new RegexpSubstrAll()); // REGEXP_EXTRACT_ALL == REGEXP_SUBSTR_ALL
        register(new RegexpCount());
        register(new EditDistance());
        register(new JarowinklerSimilarity());
        register(new ParseUrl());
        register(new ParseIp());
        register(new StrtokToArray());
        register(new Soundex());
        register(new ConcatWs());
        register(new CharLength());
        register(new Len());
        register(new OctetLength());
        register(new BitLength());
        register(new Base64Encode());
        register(new Base64Decode());
        register(new HexEncode());
        register(new HexDecode());
        register(new Md5());
        register(new Sha2());

        // Numeric functions
        register(new Div0(false));   // DIV0
        register(new Div0(true));    // DIV0NULL
        register(new ZeroIfNull());
        register(new NullIfZero());
        register(new Abs());
        register(new Round());
        register(new Ceil());
        register(new Floor());
        register(new Power());
        register(new Sqrt());
        register(new Mod());
        register(new Sign());
        register(new Trunc());
        register(new Exp());
        register(new Ln());
        register(new Log());
        register(new Pi());
        register(new Cbrt());
        register(new Square());
        register(new Factorial());
        register(new Sin());
        register(new Cos());
        register(new Tan());
        register(new Cot());
        register(new Asin());
        register(new Acos());
        register(new Atan());
        register(new Atan2());
        register(new Sinh());
        register(new Cosh());
        register(new Tanh());
        register(new Acosh());
        register(new Asinh());
        register(new Atanh());
        register(new Degrees());
        register(new Radians());
        register(new Bitand());
        register(new Bitor());
        register(new Bitxor());
        register(new Bitnot());
        register(new BitShiftLeft());
        register(new BitShiftRight());
        register(new Getbit());
        register(new WidthBucket());
        register(new Haversine());
        register(new Random());
        register(new Uniform());
        register(new Normal());

        // Date/Time functions
        register(new CurrentDate());
        register(new CurrentTimestamp());
        register(new Sysdate());
        register(new DateAdd());
        register(new DateDiff());
        register(new DatePart());
        register(new Extract());
        register(new DateTrunc());
        register(new LastDay());
        register(new NextDay());
        register(new PreviousDay());
        register(new AddMonths());
        register(new MonthsBetween());
        register(new DateFromParts());
        register(new TimeFromParts());
        register(new TimestampFromParts());
        register(new ToTime());
        register(new YearFn());
        register(new MonthFn());
        register(new DayFn());
        register(new HourFn());
        register(new MinuteFn());
        register(new SecondFn());
        register(new QuarterFn());
        register(new WeekFn());
        register(new WeekOfYear());
        register(new DayOfWeekFn());
        register(new DayOfYear());
        register(new CurrentTime());
        register(new NowFn("NOW"));
        register(new NowFn("LOCALTIMESTAMP"));
        register(new TimeAdd());
        register(new TimeDiff());

        // Grouping functions (return 0 outside ROLLUP/CUBE; the per-group bitmask is resolved in
        // GroupByAggregateEvaluator for super-group rows). GROUPING_ID(e1,…,en) is the multi-arg bitmask.
        register(new GroupingFn());
        register(new GroupingIdFn());

        // Context functions
        register(new CurrentDatabase(catalog));
        register(new GetDdl(catalog));
        register(new UuidString());
        register(new CurrentSchema(catalog));
        register(new CurrentWarehouse(catalog));
        register(new CurrentRegion(config != null ? config : new EngineConfig()));
        register(new CurrentVersion());
        register(new CurrentClient());
        register(new CurrentSession(sessionContext));
        if (sessionContext != null) {
            register(new CurrentUser(sessionContext));
            register(new CurrentRole(sessionContext));
            register(new CurrentAvailableRoles(catalog, sessionContext));
        }

        // Conditional functions
        register(new Coalesce());
        register(new Nvl());
        register(new Nvl2());
        register(new IfNull());
        register(new NullIf());
        register(new Iff());
        register(new Greatest());
        register(new GreatestIgnoreNulls());
        register(new Least());
        register(new LeastIgnoreNulls());
        register(new EqualNull());
        register(new Decode());
        register(new BoolAnd());
        register(new BoolOr());
        register(new BoolNot());
        register(new BoolXor());

        // Conversion functions
        register(new Cast());
        register(new TryCast());
        register(new ToChar());
        register(new ToBinary());
        functions.put("TO_VARCHAR", new ToChar()); // TO_VARCHAR is a synonym of TO_CHAR
        register(new ToNumber());
        functions.put("TO_DECIMAL", new ToNumber()); // TO_DECIMAL / TO_NUMERIC are synonyms of TO_NUMBER
        functions.put("TO_NUMERIC", new ToNumber());
        register(new TryToNumber());
        functions.put("TRY_TO_DECIMAL", new TryToNumber()); // TRY_TO_DECIMAL / TRY_TO_NUMERIC are synonyms of TRY_TO_NUMBER
        functions.put("TRY_TO_NUMERIC", new TryToNumber());
        register(new ToDouble());
        register(new TryToDouble());
        register(new ToInteger());
        register(new ToBoolean());
        register(new TryToBoolean());
        register(new ToDate());
        register(new TryToDate());
        register(new ToTimestampNtz());
        register(new ToTimestamp("TO_TIMESTAMP"));
        register(new ToTimestamp("TO_TIMESTAMP_LTZ"));
        register(new ToTimestamp("TO_TIMESTAMP_TZ"));
        register(new TryToTimestamp());
        register(new TryToTimestamp("TRY_TO_TIMESTAMP"));
        register(new TryToTimestamp("TRY_TO_TIMESTAMP_LTZ"));
        register(new TryToTimestamp("TRY_TO_TIMESTAMP_TZ"));
        register(new TryToTime());
        register(new TryToBinary());
        register(new ToVariant());
        register(new ToJson());
        register(new ToArray());
        register(new ToObject());

        // String extras
        register(new Instr());
        register(new RegexpInstr());
        register(new TryBase64Decode());
        register(new TryHexDecode());
        register(new Sha1());
        register(new Md5Hex());
        register(new Md5NumberLower64());
        register(new Md5NumberUpper64());
        register(new Sha1Hex());
        register(new Sha2Hex());
        register(new HashFn());
        register(new Hmac());
        register(new HmacHex());
        register(new Encrypt());
        register(new Decrypt());
        register(new EncryptRaw());
        register(new DecryptRaw());
        register(new Compress());
        register(new DecompressString());
        register(new DecompressBinary());

        // Numeric extras
        register(new GreatestIgnoreNulls());
        register(new LeastIgnoreNulls());

        // Date/time extras
        register(new Getdate());
        register(new DayName());
        register(new MonthName());
        register(new ConvertTimezone());

        // Semi-structured / JSON
        register(new ParseJson());
        register(new TryParseJson());
        register(new CheckJson());
        register(new GetIgnoreCase());
        register(new TypeOf());
        register(new IsObject());
        register(new IsArray());
        register(new IsNullValue());
        register(new IsInteger());
        register(new IsVarchar());
        register(new IsBoolean());
        register(new StripNullValue());
        register(new AsVarchar());
        register(new AsDouble());
        register(new AsInteger());
        register(new AsBoolean());
        register(new AsObject());
        register(new AsArray());
        register(new ArrayConstruct());
        register(new ArrayConstructCompact());
        register(new ArrayAppend());
        register(new ArrayToString());
        register(new ArraySize());
        register(new ArrayContains());
        register(new ArrayPrepend());
        register(new ArrayCat());
        register(new ArraySlice());
        register(new ArrayDistinct());
        register(new ArrayRemove());
        register(new ArrayRemoveAt());
        register(new ArrayFlatten());
        register(new ArraysOverlap());
        register(new ArrayIntersection());
        register(new ArrayExcept());
        register(new ArrayPosition());
        register(new ArraySort());
        register(new ArrayMin());
        register(new ArrayMax());
        register(new ArrayCompact());
        register(new ArrayReverse());
        register(new ArrayInsert());
        register(new ArrayGenerateRange());
        register(new ObjectConstruct());
        register(new ObjectConstructKeepNull());
        register(new ObjectInsert());
        register(new MapCat());   // MAP_CAT — merge two MAPs (MAP is OBJECT-backed)
        register(new ObjectDelete());
        register(new ObjectPick());
        register(new ObjectKeys());
        register(new GetPath(false)); // GET
        register(new GetPath(true));  // GET_PATH

        // Aggregate functions
        registerAggregate(new Count());
        registerAggregate(new Sum());
        registerAggregate(new Avg());
        registerAggregate(new Min());
        registerAggregate(new Max());
        registerAggregate(new StdDev());
        registerAggregate(new Variance());
        registerAggregate(new ArrayAgg());
        registerAggregate(new ListAgg());
        registerAggregate(new StdDevSamp());
        registerAggregate(new StdDevPop());
        registerAggregate(new VarSamp());
        registerAggregate(new VarPop());
        registerAggregate(new BoolOrAgg());
        registerAggregate(new BoolAndAgg());
        registerAggregate(new BoolXorAgg());
        registerAggregate(new BitAndAgg());
        registerAggregate(new BitOrAgg());
        registerAggregate(new BitXorAgg());
        registerAggregate(new CountIf());
        registerAggregate(new Median());
        registerAggregate(new Mode());
        registerAggregate(new AnyValue());
        registerAggregate(new ObjectAgg());
        registerAggregate(new ApproxCountDistinct());
        registerAggregate(new Corr());
        registerAggregate(new CovarPop());
        registerAggregate(new CovarSamp());
        registerAggregate(new Kurtosis());
        registerAggregate(new Skew());
        registerAggregate(new PercentileCont());
        registerAggregate(new PercentileDisc());
        registerAggregate(new ApproxPercentile());
        registerAggregate(new RegrSlope());
        registerAggregate(new RegrIntercept());
        registerAggregate(new RegrR2());
        registerAggregate(new RegrCount());
        registerAggregate(new RegrAvgx());
        registerAggregate(new RegrAvgy());
        registerAggregate(new RegrSxx());
        registerAggregate(new RegrSyy());
        registerAggregate(new RegrSxy());
        registerAggregate(new ArrayUnionAgg());
        registerAggregate(new ArrayUniqueAgg());
        registerAggregate(new HashAgg());
        registerAggregate(new MaxBy());
        registerAggregate(new MinBy());

        // Aliases — Snowflake alternate names mapped to already-implemented classes.
        functions.put("TRUNCATE", new Trunc());          // TRUNCATE == TRUNC
        functions.put("POW", new Power());               // POW == POWER
        functions.put("DATE", new ToDate());             // DATE == TO_DATE
        functions.put("TIME", new ToTime());             // TIME == TO_TIME
        functions.put("TIMESTAMPADD", new DateAdd());    // TIMESTAMPADD == DATEADD (== TIMEADD)
        functions.put("TIMESTAMPDIFF", new DateDiff());  // TIMESTAMPDIFF == DATEDIFF (== TIMEDIFF)
        functions.put("DAYOFMONTH", new DayFn());        // DAYOFMONTH == DAY
        functions.put("LOCALTIME", new CurrentTime());   // LOCALTIME == CURRENT_TIME
        functions.put("SYSTIMESTAMP", new Sysdate());    // SYSTIMESTAMP == SYSDATE
        aggregateFunctions.put("VARIANCE_POP", new VarPop());    // VARIANCE_POP == VAR_POP
        aggregateFunctions.put("VARIANCE_SAMP", new VarSamp());  // VARIANCE_SAMP == VAR_SAMP

        // Table functions
        registerTableFunction(new Generator());
        registerTableFunction(new SplitToTable());
        registerTableFunction(new Flatten());
        registerTableFunction(new TaskHistoryFunction(catalog));
        registerTableFunction(new UserTaskCancelFunction(catalog));
    }

    public void register(final BuiltInFunction function) {
        functions.put(function.getName().toUpperCase(), function);
    }

    public void registerAggregate(final AggregateFunction function) {
        aggregateFunctions.put(function.getName().toUpperCase(), function);
    }

    public void registerTableFunction(final TableFunction function) {
        tableFunctions.put(function.getName().toUpperCase(), function);
    }

    public BuiltInFunction getFunction(final String name) {
        return functions.get(name.toUpperCase());
    }

    public AggregateFunction getAggregateFunction(final String name) {
        return aggregateFunctions.get(name.toUpperCase());
    }

    public TableFunction getTableFunction(final String name) {
        return tableFunctions.get(name.toUpperCase());
    }

    public boolean hasFunction(final String name) {
        return functions.containsKey(name.toUpperCase());
    }

    public boolean hasAggregateFunction(final String name) {
        return aggregateFunctions.containsKey(name.toUpperCase());
    }

    public Collection<BuiltInFunction> getAllFunctions() {
        return functions.values();
    }

    public Collection<AggregateFunction> getAllAggregateFunctions() {
        return aggregateFunctions.values();
    }

    public Collection<TableFunction> getAllTableFunctions() {
        return tableFunctions.values();
    }
}
