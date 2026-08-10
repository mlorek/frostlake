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
import dev.frostlake.functions.scalar.conditional.BoolAnd;
import dev.frostlake.functions.scalar.conditional.BoolNot;
import dev.frostlake.functions.scalar.conditional.BoolOr;
import dev.frostlake.functions.scalar.conditional.BoolXor;
import dev.frostlake.functions.scalar.conditional.Coalesce;
import dev.frostlake.functions.scalar.conditional.Decode;
import dev.frostlake.functions.scalar.conditional.EqualNull;
import dev.frostlake.functions.scalar.conditional.Greatest;
import dev.frostlake.functions.scalar.conditional.GreatestIgnoreNulls;
import dev.frostlake.functions.scalar.conditional.IfNull;
import dev.frostlake.functions.scalar.conditional.Iff;
import dev.frostlake.functions.scalar.conditional.Least;
import dev.frostlake.functions.scalar.conditional.LeastIgnoreNulls;
import dev.frostlake.functions.scalar.conditional.NullIf;
import dev.frostlake.functions.scalar.conditional.NullIfZero;
import dev.frostlake.functions.scalar.conditional.Nvl2;
import dev.frostlake.functions.scalar.conditional.Nvl;
import dev.frostlake.functions.scalar.conditional.RegrValx;
import dev.frostlake.functions.scalar.conditional.RegrValy;
import dev.frostlake.functions.scalar.conditional.ZeroIfNull;
import dev.frostlake.functions.scalar.context.AggregationConstraint;
import dev.frostlake.functions.scalar.context.CurrentAccountName;
import dev.frostlake.functions.scalar.context.CurrentAvailableRoles;
import dev.frostlake.functions.scalar.context.CurrentClient;
import dev.frostlake.functions.scalar.context.CurrentDatabase;
import dev.frostlake.functions.scalar.context.CurrentDate;
import dev.frostlake.functions.scalar.context.CurrentIpAddress;
import dev.frostlake.functions.scalar.context.CurrentOrganizationName;
import dev.frostlake.functions.scalar.context.CurrentRegion;
import dev.frostlake.functions.scalar.context.CurrentRole;
import dev.frostlake.functions.scalar.context.CurrentRoleType;
import dev.frostlake.functions.scalar.context.CurrentSchema;
import dev.frostlake.functions.scalar.context.CurrentSchemas;
import dev.frostlake.functions.scalar.context.CurrentSecondaryRoles;
import dev.frostlake.functions.scalar.context.CurrentSession;
import dev.frostlake.functions.scalar.context.CurrentStatement;
import dev.frostlake.functions.scalar.context.CurrentTime;
import dev.frostlake.functions.scalar.context.CurrentTimestamp;
import dev.frostlake.functions.scalar.context.CurrentTransaction;
import dev.frostlake.functions.scalar.context.CurrentUser;
import dev.frostlake.functions.scalar.context.CurrentVersion;
import dev.frostlake.functions.scalar.context.CurrentWarehouse;
import dev.frostlake.functions.scalar.context.GetDdl;
import dev.frostlake.functions.scalar.context.InvokerRole;
import dev.frostlake.functions.scalar.context.IsRoleInSession;
import dev.frostlake.functions.scalar.context.JoinConstraint;
import dev.frostlake.functions.scalar.context.LastTransaction;
import dev.frostlake.functions.scalar.context.NoAggregationConstraint;
import dev.frostlake.functions.scalar.context.ProjectionConstraint;
import dev.frostlake.functions.scalar.context.SeqFn;
import dev.frostlake.functions.scalar.context.UuidString;
import dev.frostlake.functions.scalar.conversion.BinaryAsString;
import dev.frostlake.functions.scalar.conversion.Cast;
import dev.frostlake.functions.scalar.conversion.DateFunction;
import dev.frostlake.functions.scalar.conversion.StringAsBinary;
import dev.frostlake.functions.scalar.conversion.ToBinary;
import dev.frostlake.functions.scalar.conversion.ToBoolean;
import dev.frostlake.functions.scalar.conversion.ToChar;
import dev.frostlake.functions.scalar.conversion.ToDate;
import dev.frostlake.functions.scalar.conversion.ToDouble;
import dev.frostlake.functions.scalar.conversion.ToNumber;
import dev.frostlake.functions.scalar.conversion.ToTime;
import dev.frostlake.functions.scalar.conversion.ToTimestamp;
import dev.frostlake.functions.scalar.conversion.ToTimestampNtz;
import dev.frostlake.functions.scalar.conversion.ToUuid;
import dev.frostlake.functions.scalar.conversion.TryCast;
import dev.frostlake.functions.scalar.conversion.TryToBinary;
import dev.frostlake.functions.scalar.conversion.TryToBoolean;
import dev.frostlake.functions.scalar.conversion.TryToDate;
import dev.frostlake.functions.scalar.conversion.TryToDouble;
import dev.frostlake.functions.scalar.conversion.TryToNumber;
import dev.frostlake.functions.scalar.conversion.TryToTime;
import dev.frostlake.functions.scalar.conversion.TryToTimestamp;
import dev.frostlake.functions.scalar.conversion.TryToUuid;
import dev.frostlake.functions.scalar.crypto.Decrypt;
import dev.frostlake.functions.scalar.crypto.DecryptRaw;
import dev.frostlake.functions.scalar.crypto.Encrypt;
import dev.frostlake.functions.scalar.crypto.EncryptRaw;
import dev.frostlake.functions.scalar.crypto.TryDecrypt;
import dev.frostlake.functions.scalar.crypto.TryDecryptRaw;
import dev.frostlake.functions.scalar.datetime.AddMonths;
import dev.frostlake.functions.scalar.datetime.ConvertTimezone;
import dev.frostlake.functions.scalar.datetime.DateAdd;
import dev.frostlake.functions.scalar.datetime.DateDiff;
import dev.frostlake.functions.scalar.datetime.DateFromParts;
import dev.frostlake.functions.scalar.datetime.DatePart;
import dev.frostlake.functions.scalar.datetime.DateTrunc;
import dev.frostlake.functions.scalar.datetime.DayFn;
import dev.frostlake.functions.scalar.datetime.DayName;
import dev.frostlake.functions.scalar.datetime.DayOfWeekFn;
import dev.frostlake.functions.scalar.datetime.DayOfWeekIso;
import dev.frostlake.functions.scalar.datetime.DayOfYear;
import dev.frostlake.functions.scalar.datetime.Extract;
import dev.frostlake.functions.scalar.datetime.Getdate;
import dev.frostlake.functions.scalar.datetime.HourFn;
import dev.frostlake.functions.scalar.datetime.LastDay;
import dev.frostlake.functions.scalar.datetime.MinuteFn;
import dev.frostlake.functions.scalar.datetime.MonthFn;
import dev.frostlake.functions.scalar.datetime.MonthName;
import dev.frostlake.functions.scalar.datetime.MonthsBetween;
import dev.frostlake.functions.scalar.datetime.NextDay;
import dev.frostlake.functions.scalar.datetime.NowFn;
import dev.frostlake.functions.scalar.datetime.PreviousDay;
import dev.frostlake.functions.scalar.datetime.QuarterFn;
import dev.frostlake.functions.scalar.datetime.SecondFn;
import dev.frostlake.functions.scalar.datetime.Sysdate;
import dev.frostlake.functions.scalar.datetime.TimeAdd;
import dev.frostlake.functions.scalar.datetime.TimeDiff;
import dev.frostlake.functions.scalar.datetime.TimeFromParts;
import dev.frostlake.functions.scalar.datetime.TimeSlice;
import dev.frostlake.functions.scalar.datetime.TimestampFromParts;
import dev.frostlake.functions.scalar.datetime.WeekFn;
import dev.frostlake.functions.scalar.datetime.WeekIso;
import dev.frostlake.functions.scalar.datetime.WeekOfYear;
import dev.frostlake.functions.scalar.datetime.YearFn;
import dev.frostlake.functions.scalar.datetime.YearOfWeek;
import dev.frostlake.functions.scalar.datetime.YearOfWeekIso;
import dev.frostlake.functions.scalar.encoding.Base64Decode;
import dev.frostlake.functions.scalar.encoding.Base64DecodeBinary;
import dev.frostlake.functions.scalar.encoding.Base64Encode;
import dev.frostlake.functions.scalar.encoding.Compress;
import dev.frostlake.functions.scalar.encoding.DecompressBinary;
import dev.frostlake.functions.scalar.encoding.DecompressString;
import dev.frostlake.functions.scalar.encoding.HexDecode;
import dev.frostlake.functions.scalar.encoding.HexDecodeBinary;
import dev.frostlake.functions.scalar.encoding.HexEncode;
import dev.frostlake.functions.scalar.encoding.TryBase64Decode;
import dev.frostlake.functions.scalar.encoding.TryBase64DecodeBinary;
import dev.frostlake.functions.scalar.encoding.TryHexDecode;
import dev.frostlake.functions.scalar.encoding.TryHexDecodeBinary;
import dev.frostlake.functions.scalar.file.FlGetContentType;
import dev.frostlake.functions.scalar.file.FlGetEtag;
import dev.frostlake.functions.scalar.file.FlGetFileType;
import dev.frostlake.functions.scalar.file.FlGetLastModified;
import dev.frostlake.functions.scalar.file.FlGetRelativePath;
import dev.frostlake.functions.scalar.file.FlGetScopedFileUrl;
import dev.frostlake.functions.scalar.file.FlGetSize;
import dev.frostlake.functions.scalar.file.FlGetStage;
import dev.frostlake.functions.scalar.file.FlGetStageFileUrl;
import dev.frostlake.functions.scalar.file.FlIsAudio;
import dev.frostlake.functions.scalar.file.FlIsCompressed;
import dev.frostlake.functions.scalar.file.FlIsDocument;
import dev.frostlake.functions.scalar.file.FlIsImage;
import dev.frostlake.functions.scalar.file.FlIsVideo;
import dev.frostlake.functions.scalar.file.ToFile;
import dev.frostlake.functions.scalar.file.TryToFile;
import dev.frostlake.functions.scalar.hash.HashFn;
import dev.frostlake.functions.scalar.hash.Md5;
import dev.frostlake.functions.scalar.hash.Md5Binary;
import dev.frostlake.functions.scalar.hash.Md5Hex;
import dev.frostlake.functions.scalar.hash.Md5NumberLower64;
import dev.frostlake.functions.scalar.hash.Md5NumberUpper64;
import dev.frostlake.functions.scalar.hash.Sha1;
import dev.frostlake.functions.scalar.hash.Sha1Binary;
import dev.frostlake.functions.scalar.hash.Sha1Hex;
import dev.frostlake.functions.scalar.hash.Sha2;
import dev.frostlake.functions.scalar.hash.Sha2Binary;
import dev.frostlake.functions.scalar.hash.Sha2Hex;
import dev.frostlake.functions.scalar.math.Abs;
import dev.frostlake.functions.scalar.math.Acos;
import dev.frostlake.functions.scalar.math.Acosh;
import dev.frostlake.functions.scalar.math.Asin;
import dev.frostlake.functions.scalar.math.Asinh;
import dev.frostlake.functions.scalar.math.Atan2;
import dev.frostlake.functions.scalar.math.Atan;
import dev.frostlake.functions.scalar.math.Atanh;
import dev.frostlake.functions.scalar.math.BitCount;
import dev.frostlake.functions.scalar.math.BitShiftLeft;
import dev.frostlake.functions.scalar.math.BitShiftRight;
import dev.frostlake.functions.scalar.math.Bitand;
import dev.frostlake.functions.scalar.math.Bitnot;
import dev.frostlake.functions.scalar.math.Bitor;
import dev.frostlake.functions.scalar.math.Bitxor;
import dev.frostlake.functions.scalar.math.Cbrt;
import dev.frostlake.functions.scalar.math.Ceil;
import dev.frostlake.functions.scalar.math.Cos;
import dev.frostlake.functions.scalar.math.Cosh;
import dev.frostlake.functions.scalar.math.Cot;
import dev.frostlake.functions.scalar.math.Degrees;
import dev.frostlake.functions.scalar.math.Div0;
import dev.frostlake.functions.scalar.math.Exp;
import dev.frostlake.functions.scalar.math.Factorial;
import dev.frostlake.functions.scalar.math.Floor;
import dev.frostlake.functions.scalar.math.Getbit;
import dev.frostlake.functions.scalar.math.Haversine;
import dev.frostlake.functions.scalar.math.Ln;
import dev.frostlake.functions.scalar.math.Log;
import dev.frostlake.functions.scalar.math.Mod;
import dev.frostlake.functions.scalar.math.Negate;
import dev.frostlake.functions.scalar.math.Normal;
import dev.frostlake.functions.scalar.math.Pi;
import dev.frostlake.functions.scalar.math.Power;
import dev.frostlake.functions.scalar.math.Radians;
import dev.frostlake.functions.scalar.math.Random;
import dev.frostlake.functions.scalar.math.Round;
import dev.frostlake.functions.scalar.math.Sign;
import dev.frostlake.functions.scalar.math.Sin;
import dev.frostlake.functions.scalar.math.Sinh;
import dev.frostlake.functions.scalar.math.Sqrt;
import dev.frostlake.functions.scalar.math.Square;
import dev.frostlake.functions.scalar.math.Tan;
import dev.frostlake.functions.scalar.math.Tanh;
import dev.frostlake.functions.scalar.math.Trunc;
import dev.frostlake.functions.scalar.math.Uniform;
import dev.frostlake.functions.scalar.math.WidthBucket;
import dev.frostlake.functions.scalar.semistructured.ArrayAppend;
import dev.frostlake.functions.scalar.semistructured.ArrayCat;
import dev.frostlake.functions.scalar.semistructured.ArrayCompact;
import dev.frostlake.functions.scalar.semistructured.ArrayConstruct;
import dev.frostlake.functions.scalar.semistructured.ArrayConstructCompact;
import dev.frostlake.functions.scalar.semistructured.ArrayContains;
import dev.frostlake.functions.scalar.semistructured.ArrayDistinct;
import dev.frostlake.functions.scalar.semistructured.ArrayExcept;
import dev.frostlake.functions.scalar.semistructured.ArrayFlatten;
import dev.frostlake.functions.scalar.semistructured.ArrayGenerateRange;
import dev.frostlake.functions.scalar.semistructured.ArrayInsert;
import dev.frostlake.functions.scalar.semistructured.ArrayIntersection;
import dev.frostlake.functions.scalar.semistructured.ArrayMax;
import dev.frostlake.functions.scalar.semistructured.ArrayMin;
import dev.frostlake.functions.scalar.semistructured.ArrayPosition;
import dev.frostlake.functions.scalar.semistructured.ArrayPrepend;
import dev.frostlake.functions.scalar.semistructured.ArrayRemove;
import dev.frostlake.functions.scalar.semistructured.ArrayRemoveAt;
import dev.frostlake.functions.scalar.semistructured.ArrayRepeat;
import dev.frostlake.functions.scalar.semistructured.ArrayReverse;
import dev.frostlake.functions.scalar.semistructured.ArraySize;
import dev.frostlake.functions.scalar.semistructured.ArraySlice;
import dev.frostlake.functions.scalar.semistructured.ArraySort;
import dev.frostlake.functions.scalar.semistructured.ArrayToString;
import dev.frostlake.functions.scalar.semistructured.ArraysOverlap;
import dev.frostlake.functions.scalar.semistructured.ArraysToObject;
import dev.frostlake.functions.scalar.semistructured.ArraysZip;
import dev.frostlake.functions.scalar.semistructured.AsArray;
import dev.frostlake.functions.scalar.semistructured.AsBinary;
import dev.frostlake.functions.scalar.semistructured.AsBoolean;
import dev.frostlake.functions.scalar.semistructured.AsDate;
import dev.frostlake.functions.scalar.semistructured.AsDecimal;
import dev.frostlake.functions.scalar.semistructured.AsDouble;
import dev.frostlake.functions.scalar.semistructured.AsInteger;
import dev.frostlake.functions.scalar.semistructured.AsObject;
import dev.frostlake.functions.scalar.semistructured.AsTime;
import dev.frostlake.functions.scalar.semistructured.AsTimestampNtz;
import dev.frostlake.functions.scalar.semistructured.AsVarchar;
import dev.frostlake.functions.scalar.semistructured.CheckJson;
import dev.frostlake.functions.scalar.semistructured.CheckXml;
import dev.frostlake.functions.scalar.semistructured.GetIgnoreCase;
import dev.frostlake.functions.scalar.semistructured.GetPath;
import dev.frostlake.functions.scalar.semistructured.IsArray;
import dev.frostlake.functions.scalar.semistructured.IsBinary;
import dev.frostlake.functions.scalar.semistructured.IsBoolean;
import dev.frostlake.functions.scalar.semistructured.IsDate;
import dev.frostlake.functions.scalar.semistructured.IsDecimal;
import dev.frostlake.functions.scalar.semistructured.IsDoubleFn;
import dev.frostlake.functions.scalar.semistructured.IsInteger;
import dev.frostlake.functions.scalar.semistructured.IsNullValue;
import dev.frostlake.functions.scalar.semistructured.IsObject;
import dev.frostlake.functions.scalar.semistructured.IsTime;
import dev.frostlake.functions.scalar.semistructured.IsTimestampNtz;
import dev.frostlake.functions.scalar.semistructured.IsVarchar;
import dev.frostlake.functions.scalar.semistructured.JsonExtractPathText;
import dev.frostlake.functions.scalar.semistructured.MapCat;
import dev.frostlake.functions.scalar.semistructured.MapConstruct;
import dev.frostlake.functions.scalar.semistructured.MapContainsKey;
import dev.frostlake.functions.scalar.semistructured.MapDelete;
import dev.frostlake.functions.scalar.semistructured.MapEntries;
import dev.frostlake.functions.scalar.semistructured.MapInsert;
import dev.frostlake.functions.scalar.semistructured.MapKeys;
import dev.frostlake.functions.scalar.semistructured.MapPick;
import dev.frostlake.functions.scalar.semistructured.MapSize;
import dev.frostlake.functions.scalar.semistructured.ObjectConstruct;
import dev.frostlake.functions.scalar.semistructured.ObjectConstructKeepNull;
import dev.frostlake.functions.scalar.semistructured.ObjectDelete;
import dev.frostlake.functions.scalar.semistructured.ObjectInsert;
import dev.frostlake.functions.scalar.semistructured.ObjectKeys;
import dev.frostlake.functions.scalar.semistructured.ObjectPick;
import dev.frostlake.functions.scalar.semistructured.ParseJson;
import dev.frostlake.functions.scalar.semistructured.ParseXml;
import dev.frostlake.functions.scalar.semistructured.StripNullValue;
import dev.frostlake.functions.scalar.semistructured.ToArray;
import dev.frostlake.functions.scalar.semistructured.ToJson;
import dev.frostlake.functions.scalar.semistructured.ToObject;
import dev.frostlake.functions.scalar.semistructured.ToVariant;
import dev.frostlake.functions.scalar.semistructured.ToXml;
import dev.frostlake.functions.scalar.semistructured.TryParseJson;
import dev.frostlake.functions.scalar.semistructured.TypeOf;
import dev.frostlake.functions.scalar.semistructured.XmlGet;
import dev.frostlake.functions.scalar.string.Ascii;
import dev.frostlake.functions.scalar.string.BitLength;
import dev.frostlake.functions.scalar.string.Char;
import dev.frostlake.functions.scalar.string.CharIndex;
import dev.frostlake.functions.scalar.string.Chr;
import dev.frostlake.functions.scalar.string.Concat;
import dev.frostlake.functions.scalar.string.ConcatWs;
import dev.frostlake.functions.scalar.string.Contains;
import dev.frostlake.functions.scalar.string.EditDistance;
import dev.frostlake.functions.scalar.string.EndsWith;
import dev.frostlake.functions.scalar.string.Ilike;
import dev.frostlake.functions.scalar.string.InitCap;
import dev.frostlake.functions.scalar.string.Insert;
import dev.frostlake.functions.scalar.string.JarowinklerSimilarity;
import dev.frostlake.functions.scalar.string.LPad;
import dev.frostlake.functions.scalar.string.LTrim;
import dev.frostlake.functions.scalar.string.Left;
import dev.frostlake.functions.scalar.string.Len;
import dev.frostlake.functions.scalar.string.Length;
import dev.frostlake.functions.scalar.string.Like;
import dev.frostlake.functions.scalar.string.Lower;
import dev.frostlake.functions.scalar.string.NormalizeFn;
import dev.frostlake.functions.scalar.string.OctetLength;
import dev.frostlake.functions.scalar.string.ParseIp;
import dev.frostlake.functions.scalar.string.ParseUrl;
import dev.frostlake.functions.scalar.string.Position;
import dev.frostlake.functions.scalar.string.RPad;
import dev.frostlake.functions.scalar.string.RTrim;
import dev.frostlake.functions.scalar.string.RandStr;
import dev.frostlake.functions.scalar.string.RegexpCount;
import dev.frostlake.functions.scalar.string.RegexpInstr;
import dev.frostlake.functions.scalar.string.RegexpLike;
import dev.frostlake.functions.scalar.string.RegexpReplace;
import dev.frostlake.functions.scalar.string.RegexpSubstr;
import dev.frostlake.functions.scalar.string.RegexpSubstrAll;
import dev.frostlake.functions.scalar.string.Repeat;
import dev.frostlake.functions.scalar.string.Replace;
import dev.frostlake.functions.scalar.string.Reverse;
import dev.frostlake.functions.scalar.string.Right;
import dev.frostlake.functions.scalar.string.RtrimmedLength;
import dev.frostlake.functions.scalar.string.Search;
import dev.frostlake.functions.scalar.string.Soundex;
import dev.frostlake.functions.scalar.string.Space;
import dev.frostlake.functions.scalar.string.Split;
import dev.frostlake.functions.scalar.string.SplitPart;
import dev.frostlake.functions.scalar.string.StartsWith;
import dev.frostlake.functions.scalar.string.Strtok;
import dev.frostlake.functions.scalar.string.StrtokToArray;
import dev.frostlake.functions.scalar.string.Substring;
import dev.frostlake.functions.scalar.string.Translate;
import dev.frostlake.functions.scalar.string.Trim;
import dev.frostlake.functions.scalar.string.TryParseIp;
import dev.frostlake.functions.scalar.string.TryValidateUtf8;
import dev.frostlake.functions.scalar.string.Unicode;
import dev.frostlake.functions.scalar.string.Upper;
import dev.frostlake.functions.scalar.vector.IsVector;
import dev.frostlake.functions.scalar.vector.VectorCosineSimilarity;
import dev.frostlake.functions.scalar.vector.VectorInnerProduct;
import dev.frostlake.functions.scalar.vector.VectorL1Distance;
import dev.frostlake.functions.scalar.vector.VectorL2Distance;
import dev.frostlake.functions.scalar.vector.VectorNormalize;
import dev.frostlake.functions.scalar.vector.VectorTrunc;
import dev.frostlake.functions.table.DataMetricReferencesFunction;
import dev.frostlake.functions.table.Flatten;
import dev.frostlake.functions.table.Generator;
import dev.frostlake.functions.table.PolicyReferencesFunction;
import dev.frostlake.functions.table.QueryRunner;
import dev.frostlake.functions.table.SplitToTable;
import dev.frostlake.functions.table.TagReferencesFunction;
import dev.frostlake.functions.table.TaskHistoryFunction;
import dev.frostlake.functions.table.UserTaskCancelFunction;
import dev.frostlake.functions.window.WindowFunctionNames;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.security.SessionContext;
import dev.frostlake.task.TaskScheduler;

import dev.frostlake.functions.aggregate.AnyValue;
import dev.frostlake.functions.aggregate.ApproxCountDistinct;
import dev.frostlake.functions.aggregate.ApproxPercentile;
import dev.frostlake.functions.aggregate.ArrayAgg;
import dev.frostlake.functions.aggregate.ArrayUnionAgg;
import dev.frostlake.functions.aggregate.ArrayUniqueAgg;
import dev.frostlake.functions.aggregate.Avg;
import dev.frostlake.functions.aggregate.BitAndAgg;
import dev.frostlake.functions.aggregate.BitOrAgg;
import dev.frostlake.functions.aggregate.BitXorAgg;
import dev.frostlake.functions.aggregate.BoolAndAgg;
import dev.frostlake.functions.aggregate.BoolOrAgg;
import dev.frostlake.functions.aggregate.BoolXorAgg;
import dev.frostlake.functions.aggregate.Corr;
import dev.frostlake.functions.aggregate.Count;
import dev.frostlake.functions.aggregate.CountIf;
import dev.frostlake.functions.aggregate.CovarPop;
import dev.frostlake.functions.aggregate.CovarSamp;
import dev.frostlake.functions.aggregate.HashAgg;
import dev.frostlake.functions.aggregate.Kurtosis;
import dev.frostlake.functions.aggregate.ListAgg;
import dev.frostlake.functions.aggregate.Max;
import dev.frostlake.functions.aggregate.MaxBy;
import dev.frostlake.functions.aggregate.Median;
import dev.frostlake.functions.aggregate.Min;
import dev.frostlake.functions.aggregate.MinBy;
import dev.frostlake.functions.aggregate.Mode;
import dev.frostlake.functions.aggregate.ObjectAgg;
import dev.frostlake.functions.aggregate.PercentileCont;
import dev.frostlake.functions.aggregate.PercentileDisc;
import dev.frostlake.functions.aggregate.RegrAvgx;
import dev.frostlake.functions.aggregate.RegrAvgy;
import dev.frostlake.functions.aggregate.RegrCount;
import dev.frostlake.functions.aggregate.RegrIntercept;
import dev.frostlake.functions.aggregate.RegrR2;
import dev.frostlake.functions.aggregate.RegrSlope;
import dev.frostlake.functions.aggregate.RegrSxx;
import dev.frostlake.functions.aggregate.RegrSxy;
import dev.frostlake.functions.aggregate.RegrSyy;
import dev.frostlake.functions.aggregate.Skew;
import dev.frostlake.functions.aggregate.StdDev;
import dev.frostlake.functions.aggregate.StdDevPop;
import dev.frostlake.functions.aggregate.StdDevSamp;
import dev.frostlake.functions.aggregate.Sum;
import dev.frostlake.functions.aggregate.VarPop;
import dev.frostlake.functions.aggregate.VarSamp;
import dev.frostlake.functions.aggregate.Variance;
import dev.frostlake.functions.aggregate.VectorAvg;
import dev.frostlake.functions.aggregate.VectorMax;
import dev.frostlake.functions.aggregate.VectorMin;
import dev.frostlake.functions.aggregate.VectorSum;
import dev.frostlake.functions.scalar.GroupingFn;
import dev.frostlake.functions.scalar.GroupingIdFn;

import java.util.Collection;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

public class FunctionRegistry {

    private final Map<String, BuiltInFunction> functions;
    private final Map<String, AggregateFunction> aggregateFunctions;
    private final Map<String, TableFunction> tableFunctions;
    private final Catalog catalog;
    /** Installed after construction by the executor; see {@link #setQueryRunner}. */
    private QueryRunner queryRunner;
    private final SessionContext sessionContext;
    private final EngineConfig config;
    /** Held so the task scheduler can be handed over once it exists — it is built after this. */
    private UserTaskCancelFunction userTaskCancel;

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
        register(new Len());
        register(new OctetLength());
        register(new BitLength());
        register(new Base64Encode());
        register(new Base64Decode());
        register(new Base64DecodeBinary());
        register(new HexEncode());
        register(new HexDecode());
        register(new HexDecodeBinary());
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
        register(new ProjectionConstraint());
        register(new AggregationConstraint());
        register(new NoAggregationConstraint());
        register(new JoinConstraint());
        register(new UuidString());
        register(new SeqFn("SEQ1", 1));
        register(new SeqFn("SEQ2", 2));
        register(new SeqFn("SEQ4", 4));
        register(new SeqFn("SEQ8", 8));
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
        register(new RegexpInstr());
        register(new TryBase64Decode());
        register(new TryBase64DecodeBinary());
        register(new TryHexDecode());
        register(new TryHexDecodeBinary());
        register(new Sha1());
        register(new Md5Hex());
        register(new Md5NumberLower64());
        register(new Md5NumberUpper64());
        register(new Sha1Hex());
        register(new Sha2Hex());
        register(new HashFn());
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
        // The MAP family (MAP is OBJECT-backed). Every one of these REQUIRES a MAP(k, v) argument and
        // refuses a plain OBJECT, so they are also listed in ExpressionEvaluatorVisitor's MAP_STRICT_ARGS
        // — MAP_CONSTRUCT excepted, which builds a map instead of reading one.
        register(new MapCat());
        register(new MapConstruct());
        register(new MapContainsKey());
        register(new MapDelete());
        register(new MapEntries());
        register(new MapInsert());
        register(new MapKeys());
        register(new MapPick());
        register(new MapSize());
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

        // FILE functions. TO_FILE builds the file-metadata object that IS a FILE value; the
        // fourteen FL_* accessors read one field of it each. TO_FILE / TRY_TO_FILE are re-registered by
        // QueryExecutor with a stage resolver — registered here too so the names always resolve and
        // SHOW FUNCTIONS lists them. Live, the accessors classify on CONTENT_TYPE alone.
        register(new ToFile());
        register(new TryToFile());
        register(new FlGetContentType());
        register(new FlGetEtag());
        register(new FlGetFileType());
        register(new FlGetLastModified());
        register(new FlGetRelativePath());
        register(new FlGetScopedFileUrl());
        register(new FlGetSize());
        register(new FlGetStage());
        register(new FlGetStageFileUrl());
        register(new FlIsAudio());
        register(new FlIsCompressed());
        register(new FlIsDocument());
        register(new FlIsImage());
        register(new FlIsVideo());

        // VECTOR functions. The distance/similarity pair returns a float64 FLOAT while vector RESULTS
        // carry 32-bit elements; the four aggregates reduce element-wise across a group's rows.
        register(new VectorCosineSimilarity());
        register(new VectorL1Distance());
        register(new VectorL2Distance());
        register(new VectorInnerProduct());
        register(new VectorNormalize());
        register(new VectorTrunc());
        register(new IsVector());
        registerAggregate(new VectorSum());
        registerAggregate(new VectorAvg());
        registerAggregate(new VectorMin());
        registerAggregate(new VectorMax());

        // Aliases — Snowflake alternate names mapped to already-implemented classes.
        functions.put("TRUNCATE", new Trunc());          // TRUNCATE == TRUNC
        functions.put("VECTOR_TRUNCATE", new VectorTrunc());   // VECTOR_TRUNCATE == VECTOR_TRUNC
        functions.put("POW", new Power());               // POW == POWER
        functions.put("DATE", new DateFunction());       // DATE == TO_DATE, but it also takes an epoch NUMBER
        functions.put("TIME", new ToTime());             // TIME == TO_TIME
        functions.put("TIMESTAMPADD", new DateAdd());    // TIMESTAMPADD == DATEADD (== TIMEADD)
        functions.put("TIMESTAMPDIFF", new DateDiff());  // TIMESTAMPDIFF == DATEDIFF (== TIMEDIFF)
        functions.put("DAYOFMONTH", new DayFn());        // DAYOFMONTH == DAY
        functions.put("LOCALTIME", new CurrentTime());   // LOCALTIME == CURRENT_TIME
        functions.put("SYSTIMESTAMP", new Sysdate());    // SYSTIMESTAMP == SYSDATE
        aggregateFunctions.put("VARIANCE_POP", new VarPop());    // VARIANCE_POP == VAR_POP
        aggregateFunctions.put("VARIANCE_SAMP", new VarSamp());  // VARIANCE_SAMP == VAR_SAMP

        // ── Live-catalog coverage batch: scalars newly present in SHOW BUILTIN FUNCTIONS ──
        register(new BitCount());
        register(new Negate());
        register(new RegrValx());
        register(new RegrValy());
        register(new RtrimmedLength());
        register(new RandStr());
        register(new NormalizeFn());
        register(new TryValidateUtf8());
        register(new ToUuid());
        register(new TryToUuid());
        register(new BinaryAsString());
        register(new StringAsBinary());
        register(new Md5Binary());
        register(new Sha1Binary());
        register(new Sha2Binary());
        register(new TryDecrypt());
        register(new TryDecryptRaw());
        register(new TryParseIp());
        register(new DayOfWeekIso());
        register(new WeekIso());
        register(new YearOfWeek());
        register(new YearOfWeekIso());
        register(new TimeSlice());
        register(new AsBinary());
        register(new AsDate());
        register(new AsTime());
        register(new AsTimestampNtz());
        register(new AsDecimal());
        register(new IsBinary());
        register(new IsDate());
        register(new IsTime());
        register(new IsTimestampNtz());
        register(new IsDecimal());
        register(new IsDoubleFn());
        register(new ArraysZip());
        register(new ArraysToObject());
        register(new ArrayRepeat());
        register(new JsonExtractPathText());
        register(new ParseXml());
        register(new CheckXml());
        register(new ToXml());
        register(new XmlGet());
        register(new CurrentIpAddress());
        register(new CurrentRoleType());
        register(new CurrentSecondaryRoles());
        register(new CurrentOrganizationName(config != null ? config : new EngineConfig()));
        register(new CurrentAccountName(config != null ? config : new EngineConfig()));
        register(new CurrentStatement(sessionContext));
        register(new CurrentTransaction());
        register(new LastTransaction(sessionContext));
        register(new CurrentSchemas(catalog));
        register(new InvokerRole(sessionContext));
        register(new IsRoleInSession(sessionContext));
        // Snowflake-name aliases over existing implementations.
        functions.put("AS_CHAR", new AsVarchar());
        functions.put("AS_NUMBER", new AsDecimal());
        functions.put("AS_REAL", new AsDouble());
        functions.put("AS_TIMESTAMP_LTZ", new AsTimestampNtz());
        functions.put("AS_TIMESTAMP_TZ", new AsTimestampNtz());
        functions.put("IS_CHAR", new IsVarchar());
        functions.put("IS_DATE_VALUE", new IsDate());
        functions.put("IS_REAL", new IsDoubleFn());
        functions.put("IS_TIMESTAMP_LTZ", new IsTimestampNtz());
        functions.put("IS_TIMESTAMP_TZ", new IsTimestampNtz());
        functions.put("BIT_AND", new Bitand());
        functions.put("BIT_OR", new Bitor());
        functions.put("BIT_XOR", new Bitxor());
        functions.put("BIT_NOT", new Bitnot());
        functions.put("BIT_SHIFTLEFT", new BitShiftLeft());
        functions.put("BIT_SHIFTRIGHT", new BitShiftRight());
        functions.put("DATEFROMPARTS", new DateFromParts());
        functions.put("TIMEFROMPARTS", new TimeFromParts());
        functions.put("TIMESTAMPFROMPARTS", new TimestampFromParts("TIMESTAMPFROMPARTS"));
        functions.put("TIMESTAMPNTZFROMPARTS", new TimestampFromParts("TIMESTAMPNTZFROMPARTS"));
        functions.put("TIMESTAMP_NTZ_FROM_PARTS", new TimestampFromParts("TIMESTAMP_NTZ_FROM_PARTS"));
        functions.put("TIMESTAMPLTZFROMPARTS", new TimestampFromParts("TIMESTAMPLTZFROMPARTS"));
        functions.put("TIMESTAMP_LTZ_FROM_PARTS", new TimestampFromParts("TIMESTAMP_LTZ_FROM_PARTS"));
        functions.put("TIMESTAMPTZFROMPARTS", new TimestampFromParts("TIMESTAMPTZFROMPARTS"));
        functions.put("TIMESTAMP_TZ_FROM_PARTS", new TimestampFromParts("TIMESTAMP_TZ_FROM_PARTS"));
        functions.put("TRY_TO_TIMESTAMP_NTZ", new TryToTimestamp());
        functions.put("TO_DECFLOAT", new ToDouble());
        functions.put("TRY_TO_DECFLOAT", new TryToDouble());
        aggregateFunctions.put("ARRAYAGG", new ArrayAgg());
        aggregateFunctions.put("OBJECTAGG", new ObjectAgg());
        aggregateFunctions.put("HLL", new ApproxCountDistinct());
        aggregateFunctions.put("APPROXIMATE_COUNT_DISTINCT", new ApproxCountDistinct());
        aggregateFunctions.put("BITANDAGG", new BitAndAgg());
        aggregateFunctions.put("BIT_ANDAGG", new BitAndAgg());
        aggregateFunctions.put("BIT_AND_AGG", new BitAndAgg());
        aggregateFunctions.put("BITORAGG", new BitOrAgg());
        aggregateFunctions.put("BIT_ORAGG", new BitOrAgg());
        aggregateFunctions.put("BIT_OR_AGG", new BitOrAgg());
        aggregateFunctions.put("BITXORAGG", new BitXorAgg());
        aggregateFunctions.put("BIT_XORAGG", new BitXorAgg());
        aggregateFunctions.put("BIT_XOR_AGG", new BitXorAgg());

        // Table functions
        registerTableFunction(new Generator());
        registerTableFunction(new SplitToTable());
        registerTableFunction(new Flatten());
        registerTableFunction(new TaskHistoryFunction(catalog));
        registerTableFunction(new TagReferencesFunction(catalog));
        registerTableFunction(new PolicyReferencesFunction(catalog));
        registerTableFunction(new DataMetricReferencesFunction(catalog));
        this.userTaskCancel = new UserTaskCancelFunction(catalog);
        registerTableFunction(this.userTaskCancel);
        // Optional function packs (e.g. frostlake-geo) contribute through the FunctionProvider
        // ServiceLoader SPI — discovered here, after the built-ins, so a pack could also override.
        for (final FunctionProvider provider : ServiceLoader.load(FunctionProvider.class)) {
            provider.contribute(this);
        }

    }

    /** Registers an alias name for an already-constructed function (used by FunctionProviders). */
    public void registerAlias(final String name, final BuiltInFunction function) {
        functions.put(name.toUpperCase(), function);
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

    /**
     * The catalog this registry's functions read metadata from, for a {@link FunctionProvider} whose
     * pack contributes a catalog-aware function. The engine's own such built-ins (GET_DDL, the
     * INFORMATION_SCHEMA-adjacent context functions) are handed it at construction; a pack sees the
     * registry and nothing else, so it asks here.
     */
    public Catalog getCatalog() {
        return catalog;
    }

    /** The session a pack's function answers for — the current role, database and schema. */
    public SessionContext getSessionContext() {
        return sessionContext;
    }

    /**
     * How a pack's function runs SQL of its own. Installed by the executor once it exists — the
     * registry is built first — so a function that needs it holds the REGISTRY and asks at call time
     * rather than capturing a runner it would have been handed as null.
     */
    public void setQueryRunner(final QueryRunner runner) {
        this.queryRunner = runner;
    }

    public QueryRunner getQueryRunner() {
        return queryRunner;
    }

    /**
     * Hands the task scheduler to the built-ins that need it. The scheduler is constructed after
     * this registry, so it arrives here rather than through a constructor.
     */
    public void setTaskScheduler(final TaskScheduler taskScheduler) {
        userTaskCancel.setTaskScheduler(taskScheduler);
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

    public boolean hasTableFunction(final String name) {
        return tableFunctions.containsKey(name.toUpperCase());
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

    /**
     * <strong>The</strong> list of built-in names this engine can dispatch — the single source
     * {@code SHOW FUNCTIONS} and {@code SHOW BUILTIN FUNCTIONS} enumerate, so a listing and a working
     * call can no longer disagree.
     *
     * <p>It is the union of six families, each contributed by the very structure the dispatcher consults:
     *
     * <ul>
     *   <li>the scalar map's <em>keys</em> — keys, not {@code getAllFunctions()} values, because an alias
     *       such as {@code SUBSTR} is a second key onto a {@code Substring} instance whose
     *       {@link BuiltInFunction#getName()} still answers {@code SUBSTRING}. Enumerating values listed
     *       {@code SUBSTRING} twice and {@code SUBSTR} never;</li>
     *   <li>the aggregate map's keys (same alias story: {@code ARRAYAGG}, {@code BIT_OR_AGG}, …);</li>
     *   <li>the table-function map's keys;</li>
     *   <li>{@link dev.frostlake.functions.window.WindowFunctionNames} — window functions live in
     *       {@code WindowFunctionEvaluator}, never in these maps;</li>
     *   <li>{@link HigherOrderFunctionNames} — TRANSFORM / FILTER / REDUCE are taken by
     *       {@code ExpressionEvaluatorVisitor} before the registry lookup;</li>
     *   <li>{@link SystemFunctionNames} and {@link OperatorFunctionNames} — evaluated by the
     *       {@code SYSTEM$} evaluators and by the grammar respectively.</li>
     * </ul>
     *
     * <p>The first three families cannot drift because the listing reads the same maps
     * {@code getFunction} / {@code getAggregateFunction} / {@code getTableFunction} resolve against; the
     * next three cannot drift because their dispatchers reject names those sets do not declare. Only
     * {@link OperatorFunctionNames} is curated, and its entries are re-verified by test.
     *
     * @return every dispatchable built-in name, upper-cased and sorted; never null
     */
    public SortedSet<String> allDispatchableNames() {
        final SortedSet<String> names = new TreeSet<>();
        names.addAll(functions.keySet());
        names.addAll(aggregateFunctions.keySet());
        names.addAll(tableFunctions.keySet());
        names.addAll(WindowFunctionNames.names());
        names.addAll(HigherOrderFunctionNames.names());
        names.addAll(SystemFunctionNames.names());
        names.addAll(OperatorFunctionNames.names());
        return names;
    }
}
