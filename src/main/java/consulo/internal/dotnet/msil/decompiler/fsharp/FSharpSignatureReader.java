/*
 * Copyright 2013-2026 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package consulo.internal.dotnet.msil.decompiler.fsharp;

import consulo.internal.dotnet.asm.mbel.LocalManifestResource;
import consulo.internal.dotnet.asm.mbel.ManifestResource;
import consulo.internal.dotnet.asm.mbel.ModuleParser;
import consulo.internal.dotnet.msil.decompiler.fsharp.FSharpSignature.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Reads the F# signature data of an assembly: the {@code FSharpSignatureData.<Assembly>} resource (or its deflated
 * {@code FSharpSignatureCompressedData} variant) with the optional secondary {@code ...DataB} stream of F# 5+.
 * <p>
 * Port of the reader half of {@code TypedTreePickle.fs} from dotnet/fsharp (MIT license): the format is read in the
 * exact order of the F# compiler, keeping only what {@link FSharpSignature} needs. Expressions (attribute arguments,
 * literal values) are read and dropped, except constants.
 *
 * @author VISTALL
 * @since 2026-10-25
 */
public final class FSharpSignatureReader
{
	public static final String SIGNATURE_DATA = "FSharpSignatureData.";
	public static final String SIGNATURE_COMPRESSED_DATA = "FSharpSignatureCompressedData.";
	public static final String SIGNATURE_DATA_B = "FSharpSignatureDataB.";
	public static final String SIGNATURE_COMPRESSED_DATA_B = "FSharpSignatureCompressedDataB.";

	public static class FormatException extends RuntimeException
	{
		public FormatException(String message)
		{
			super(message);
		}
	}

	/**
	 * @return signature data of an F# assembly, or null if the assembly has no F# signature data
	 */
	public static FSharpSignature read(ModuleParser parser)
	{
		byte[] data = null;
		byte[] dataB = null;
		for(ManifestResource resource : parser.getManifestResources())
		{
			if(!(resource instanceof LocalManifestResource local) || resource.getName() == null)
			{
				continue;
			}
			String name = resource.getName();
			if(name.startsWith(SIGNATURE_DATA))
			{
				data = local.getResourceData();
			}
			else if(name.startsWith(SIGNATURE_COMPRESSED_DATA))
			{
				data = inflate(local.getResourceData());
			}
			else if(name.startsWith(SIGNATURE_DATA_B))
			{
				dataB = local.getResourceData();
			}
			else if(name.startsWith(SIGNATURE_COMPRESSED_DATA_B))
			{
				dataB = inflate(local.getResourceData());
			}
		}
		if(data == null)
		{
			return null;
		}
		return read(data, dataB == null ? new byte[0] : dataB);
	}

	/**
	 * .NET {@code DeflateStream} writes raw deflate data.
	 */
	public static byte[] inflate(byte[] data)
	{
		Inflater inflater = new Inflater(true);
		inflater.setInput(data);
		ByteArrayOutputStream out = new ByteArrayOutputStream(data.length * 4);
		byte[] buffer = new byte[64 * 1024];
		try
		{
			while(!inflater.finished())
			{
				int count = inflater.inflate(buffer);
				if(count == 0 && (inflater.needsInput() || inflater.needsDictionary()))
				{
					break;
				}
				out.write(buffer, 0, count);
			}
		}
		catch(DataFormatException e)
		{
			throw new FormatException("bad compressed signature data: " + e.getMessage());
		}
		finally
		{
			inflater.end();
		}
		return out.toByteArray();
	}

	public static FSharpSignature read(byte[] data, byte[] dataB)
	{
		// phase 2: tables
		Stream phase2 = new Stream(data, 0, data.length);
		int ccuCount = phase2.readInt();
		List<String> ccuNames = new ArrayList<>(ccuCount);
		for(int i = 0; i < ccuCount; i++)
		{
			if(phase2.readByte() != 0)
			{
				throw new FormatException("u_encoded_ccuref");
			}
			ccuNames.add(phase2.readPrimString());
		}
		int z1 = phase2.readInt();
		int entityCount = z1 < 0 ? -z1 - 1 : z1;
		int typarCount = phase2.readInt();
		int valCount = phase2.readInt();
		int anonCount = z1 < 0 ? phase2.readInt() : 0;

		String[] strings = new String[phase2.readInt()];
		for(int i = 0; i < strings.length; i++)
		{
			strings[i] = phase2.readPrimString();
		}

		int[][] pubpaths = new int[phase2.readInt()][];
		for(int i = 0; i < pubpaths.length; i++)
		{
			pubpaths[i] = phase2.readIntArray();
		}

		int[] nlerefCcus = new int[phase2.readInt()];
		int[][] nlerefPaths = new int[nlerefCcus.length][];
		for(int i = 0; i < nlerefCcus.length; i++)
		{
			nlerefCcus[i] = phase2.readInt();
			nlerefPaths[i] = phase2.readIntArray();
		}

		int[] simpleTypes = phase2.readIntArray();

		int phase1Length = phase2.readInt();
		int phase1Start = phase2.position;

		FSharpSignatureReader reader = new FSharpSignatureReader(new Stream(data, phase1Start, phase1Start + phase1Length),
				new Stream(dataB, 0, dataB.length), entityCount, typarCount, valCount, anonCount);

		reader.myStrings = strings;
		reader.myPubpaths = new String[pubpaths.length][];
		for(int i = 0; i < pubpaths.length; i++)
		{
			reader.myPubpaths[i] = reader.strings(pubpaths[i]);
		}
		reader.myNlerefs = new TcRef[nlerefCcus.length];
		for(int i = 0; i < nlerefCcus.length; i++)
		{
			reader.myNlerefs[i] = new TcRef(ccuNames.get(nlerefCcus[i]), reader.strings(nlerefPaths[i]));
		}
		reader.mySimpleTypes = new Type[simpleTypes.length];
		for(int i = 0; i < simpleTypes.length; i++)
		{
			reader.mySimpleTypes[i] = new AppType(reader.myNlerefs[simpleTypes[i]], List.of());
		}

		// unpickleCcuInfo: module spec, compile time working dir, uses quotations, 3 bytes of space
		Entity root = reader.entitySpec();
		reader.string();
		reader.bool();
		reader.space(3);
		return new FSharpSignature(ccuNames, root);
	}

	private final Stream myIn;
	private final Stream myInB;

	private final Entity[] myEntities;
	private final Typar[] myTypars;
	private final Val[] myVals;
	private final AnonInfo[] myAnonInfos;

	private String[] myStrings;
	private String[][] myPubpaths;
	private TcRef[] myNlerefs;
	private Type[] mySimpleTypes;

	private FSharpSignatureReader(Stream in, Stream inB, int entityCount, int typarCount, int valCount, int anonCount)
	{
		myIn = in;
		myInB = inB;
		myEntities = new Entity[entityCount];
		for(int i = 0; i < entityCount; i++)
		{
			myEntities[i] = new Entity();
		}
		myTypars = new Typar[typarCount];
		for(int i = 0; i < typarCount; i++)
		{
			myTypars[i] = new Typar();
		}
		myVals = new Val[valCount];
		for(int i = 0; i < valCount; i++)
		{
			myVals[i] = new Val();
		}
		myAnonInfos = new AnonInfo[anonCount];
		for(int i = 0; i < anonCount; i++)
		{
			myAnonInfos[i] = new AnonInfo();
		}
	}

	private static final class AnonInfo
	{
		boolean isStruct;
		List<String> names = new ArrayList<>();
	}

	/**
	 * A constant of an expression, so attribute arguments and literals keep their values.
	 */
	private record ConstExpr(Object value)
	{
	}

	private static final Object NOT_CONSTANT = new Object();

	// Primitives

	private int byteValue()
	{
		return myIn.readByte();
	}

	private int byteB()
	{
		return myInB.isEof() ? 0 : myInB.readByte();
	}

	private boolean bool()
	{
		return byteValue() == 1;
	}

	private int intValue()
	{
		return myIn.readInt();
	}

	private int intB()
	{
		int b0 = byteB();
		if(b0 <= 0x7F)
		{
			return b0;
		}
		if(b0 <= 0xBF)
		{
			return ((b0 & 0x7F) << 8) | byteB();
		}
		return byteB() | (byteB() << 8) | (byteB() << 16) | (byteB() << 24);
	}

	private long int64()
	{
		long b1 = intValue() & 0xFFFFFFFFL;
		long b2 = intValue();
		return b1 | (b2 << 32);
	}

	private byte[] bytes()
	{
		int length = intValue();
		return myIn.readBytes(length);
	}

	private void space(int n)
	{
		for(int i = 0; i < n; i++)
		{
			byteValue();
		}
	}

	private <T> T usedSpace1(Supplier<T> reader)
	{
		int tag = byteValue();
		if(tag == 0)
		{
			return null;
		}
		if(tag == 1)
		{
			T value = reader.get();
			space(1);
			return value;
		}
		return null;
	}

	private <T> List<T> list(Supplier<T> reader)
	{
		int n = intValue();
		List<T> result = new ArrayList<>(Math.max(0, n));
		for(int i = 0; i < n; i++)
		{
			result.add(reader.get());
		}
		return result;
	}

	private <T> List<T> listB(Supplier<T> reader)
	{
		int n = intB();
		List<T> result = new ArrayList<>(Math.max(0, n));
		for(int i = 0; i < n; i++)
		{
			result.add(reader.get());
		}
		return result;
	}

	private <T> T option(Supplier<T> reader)
	{
		int tag = byteValue();
		if(tag == 0)
		{
			return null;
		}
		if(tag == 1)
		{
			return reader.get();
		}
		throw new FormatException("u_option: " + tag);
	}

	private boolean optionPresent()
	{
		int tag = byteValue();
		if(tag > 1)
		{
			throw new FormatException("u_option: " + tag);
		}
		return tag == 1;
	}

	private <T> T lazy(Supplier<T> reader)
	{
		// length and ranges of the nodes defined inside
		for(int i = 0; i < 7; i++)
		{
			myIn.readPrimInt();
		}
		return reader.get();
	}

	// Interned data

	private String string()
	{
		return myStrings[checkIndex(intValue(), myStrings.length, "strings")];
	}

	private List<String> stringList()
	{
		return list(this::string);
	}

	private String[] strings(int[] indexes)
	{
		String[] result = new String[indexes.length];
		for(int i = 0; i < indexes.length; i++)
		{
			result[i] = myStrings[indexes[i]];
		}
		return result;
	}

	private String[] pubpath()
	{
		return myPubpaths[checkIndex(intValue(), myPubpaths.length, "pubpaths")];
	}

	private void ccuref()
	{
		intValue();
	}

	private static int checkIndex(int index, int size, String table)
	{
		if(index < 0 || index >= size)
		{
			throw new FormatException("index " + index + " out of range of table " + table + " (" + size + ")");
		}
		return index;
	}

	// Ranges and identifiers

	private void range()
	{
		string();
		intValue();
		intValue();
		intValue();
		intValue();
	}

	private String ident()
	{
		String text = string();
		range();
		return text;
	}

	private void xmlDoc()
	{
		int n = intValue();
		for(int i = 0; i < n; i++)
		{
			string();
		}
	}

	// References

	private TcRef tcref()
	{
		int tag = byteValue();
		if(tag == 0)
		{
			return new TcRef(myEntities[checkIndex(intValue(), myEntities.length, "entities")]);
		}
		if(tag == 1)
		{
			return myNlerefs[checkIndex(intValue(), myNlerefs.length, "nlerefs")];
		}
		throw new FormatException("u_tcref: " + tag);
	}

	private Typar tpref()
	{
		return myTypars[checkIndex(intValue(), myTypars.length, "typars")];
	}

	private void vref()
	{
		int tag = byteValue();
		if(tag == 0)
		{
			checkIndex(intValue(), myVals.length, "vals");
		}
		else if(tag == 1)
		{
			// non local val ref
			tcref();
			option(this::string);
			bool();
			string();
			intValue();
			option(this::type);
		}
		else
		{
			throw new FormatException("u_vref: " + tag);
		}
	}

	// IL

	private void ilScopeRef()
	{
		int tag = byteValue();
		if(tag == 0)
		{
			return;
		}
		if(tag == 1)
		{
			// module ref
			string();
			bool();
			option(this::bytes);
			return;
		}
		if(tag == 2)
		{
			// assembly ref
			if(byteValue() != 0)
			{
				throw new FormatException("u_ILAssemblyRef");
			}
			string();
			option(this::bytes);
			option(() ->
			{
				byteValue();
				return bytes();
			});
			bool();
			option(() ->
			{
				intValue();
				intValue();
				intValue();
				intValue();
				return Boolean.TRUE;
			});
			option(this::string);
			return;
		}
		throw new FormatException("u_ILScopeRef: " + tag);
	}

	private String ilTypeRef()
	{
		ilScopeRef();
		List<String> enclosing = stringList();
		String name = string();
		if(enclosing.isEmpty())
		{
			return name;
		}
		return String.join("/", enclosing) + "/" + name;
	}

	private void ilArrayShape()
	{
		int n = intValue();
		for(int i = 0; i < n; i++)
		{
			option(this::intValue);
			option(this::intValue);
		}
	}

	/**
	 * @return name of a type spec, or null for other IL types
	 */
	private String ilType()
	{
		int tag = byteValue();
		switch(tag)
		{
			case 0:
				return "void";
			case 1:
				ilArrayShape();
				ilType();
				return null;
			case 2:
			case 3:
				return ilTypeSpec();
			case 4:
			case 5:
				ilType();
				return null;
			case 6:
				ilCallSig();
				return null;
			case 7:
				intValue();
				return null;
			case 8:
				bool();
				ilTypeRef();
				ilType();
				return null;
			default:
				throw new FormatException("u_ILType: " + tag);
		}
	}

	private void ilTypes()
	{
		int n = intValue();
		for(int i = 0; i < n; i++)
		{
			ilType();
		}
	}

	private String ilTypeSpec()
	{
		String name = ilTypeRef();
		ilTypes();
		return name;
	}

	private void ilCallConv()
	{
		byteValue();
		byteValue();
	}

	private void ilCallSig()
	{
		ilCallConv();
		ilTypes();
		ilType();
	}

	private String ilMethodRef()
	{
		ilTypeRef();
		ilCallConv();
		intValue();
		String name = string();
		ilTypes();
		ilType();
		return name;
	}

	private void ilFieldSpec()
	{
		ilTypeRef();
		string();
		ilType();
		ilType();
	}

	private void ilMethodSpec()
	{
		ilMethodRef();
		ilType();
		ilTypes();
	}

	private void ilInstr()
	{
		int tag = byteValue();
		switch(tag)
		{
			// itag_ldarg
			case 1:
				intValue();
				break;
			// itag_call, itag_callvirt, itag_ldvirtftn
			case 4:
			case 24:
			case 55:
				ilMethodSpec();
				break;
			// itag_conv, itag_conv_ovf, itag_conv_ovf_un
			case 20:
			case 22:
			case 23:
				intValue();
				break;
			// itag_ldfld, itag_ldsfld, itag_stfld, itag_stsfld
			case 31:
			case 34:
			case 33:
			case 36:
				intValue();
				ilFieldSpec();
				break;
			// itag_ldflda, itag_ldsflda
			case 32:
			case 35:
				ilFieldSpec();
				break;
			// itag_ldtoken, itag_box, itag_unbox, itag_unbox_any, itag_castclass, itag_isinst, itag_ldobj, itag_stobj, itag_sizeof,
			// itag_ilzero, itag_initobj, itag_cpobj
			case 43:
			case 38:
			case 29:
			case 61:
			case 27:
			case 28:
			case 25:
			case 37:
			case 58:
			case 3:
			case 63:
			case 65:
				ilType();
				break;
			// itag_ldstr
			case 26:
				string();
				break;
			// itag_newarr, itag_stelem_any, itag_ldelem_any
			case 39:
			case 60:
			case 59:
				ilArrayShape();
				ilType();
				break;
			// itag_ldelema
			case 41:
				intValue();
				ilArrayShape();
				ilType();
				break;
			// itag_ldlen_multi
			case 62:
				intValue();
				intValue();
				break;
			// instructions without arguments
			case 0:
			case 2:
			case 5:
			case 6:
			case 7:
			case 8:
			case 9:
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
			case 16:
			case 17:
			case 18:
			case 19:
			case 30:
			case 40:
			case 42:
			case 44:
			case 45:
			case 46:
			case 47:
			case 48:
			case 49:
			case 50:
			case 51:
			case 52:
			case 53:
			case 54:
			case 56:
			case 57:
			case 64:
			case 66:
				break;
			default:
				throw new FormatException("no decoder for instruction " + tag);
		}
	}

	// Types

	private List<Type> types()
	{
		return list(this::type);
	}

	private Type type()
	{
		int tag = byteValue();
		switch(tag)
		{
			case 0:
				return new TupleType(false, types());
			case 1:
			{
				byteB();
				return mySimpleTypes[checkIndex(intValue(), mySimpleTypes.length, "simpletypes")];
			}
			case 2:
			{
				byteB();
				TcRef tcref = tcref();
				return new AppType(tcref, types());
			}
			case 3:
			{
				byteB();
				Type domain = type();
				Type range = type();
				return new FunType(domain, range);
			}
			case 4:
			{
				byteB();
				return new VarType(tpref());
			}
			case 5:
			{
				List<Typar> typars = typarSpecs();
				return new ForallType(typars, type());
			}
			case 6:
				return new MeasureType(measure());
			case 7:
			{
				TcRef tcref = tcref();
				String caseName = string();
				return new UnionCaseType(tcref, caseName, types());
			}
			case 8:
				return new TupleType(true, types());
			case 9:
			{
				AnonInfo info = anonInfo();
				List<Type> elements = types();
				return new AnonRecordType(info.isStruct, info.names, elements);
			}
			default:
				throw new FormatException("u_typ: " + tag);
		}
	}

	private AnonInfo anonInfo()
	{
		int index = checkIndex(intValue(), myAnonInfos.length, "anoninfos");
		AnonInfo info = myAnonInfos[index];
		ccuref();
		info.isStruct = bool();
		int n = intValue();
		info.names = new ArrayList<>(n);
		for(int i = 0; i < n; i++)
		{
			info.names.add(ident());
		}
		return info;
	}

	private Measure measure()
	{
		int tag = byteValue();
		switch(tag)
		{
			case 0:
				return new ConstMeasure(tcref());
			case 1:
				return new InvMeasure(measure());
			case 2:
			{
				Measure left = measure();
				Measure right = measure();
				return new ProdMeasure(left, right);
			}
			case 3:
				return new VarMeasure(tpref());
			case 4:
				return OneMeasure.INSTANCE;
			case 5:
			{
				Measure measure = measure();
				int numerator = intValue();
				int denominator = intValue();
				return new PowerMeasure(measure, numerator, denominator);
			}
			default:
				throw new FormatException("u_measure_expr: " + tag);
		}
	}

	// Type parameters

	private List<Typar> typarSpecs()
	{
		return list(this::typarSpec);
	}

	private Typar typarSpec()
	{
		Typar typar = myTypars[checkIndex(intValue(), myTypars.length, "typars")];
		typar.name = ident();
		typar.attribs = attribs();
		typar.flags = int64();
		typar.constraints = typarConstraints();
		xmlDoc();
		return typar;
	}

	private List<Constraint> typarConstraints()
	{
		List<Constraint> constraints = list(this::typarConstraint);
		constraints.addAll(listB(this::typarConstraintB));
		return constraints;
	}

	private Constraint typarConstraint()
	{
		int tag = byteValue();
		Constraint constraint;
		switch(tag)
		{
			case 0:
				constraint = new Constraint(ConstraintKind.COERCES_TO);
				constraint.types.add(type());
				break;
			case 1:
				constraint = new Constraint(ConstraintKind.MAY_RESOLVE_MEMBER);
				constraint.trait = trait();
				break;
			case 2:
				constraint = new Constraint(ConstraintKind.DEFAULTS_TO);
				constraint.types.add(type());
				break;
			case 3:
				constraint = new Constraint(ConstraintKind.SUPPORTS_NULL);
				break;
			case 4:
				constraint = new Constraint(ConstraintKind.IS_NON_NULLABLE_STRUCT);
				break;
			case 5:
				constraint = new Constraint(ConstraintKind.IS_REFERENCE_TYPE);
				break;
			case 6:
				constraint = new Constraint(ConstraintKind.REQUIRES_DEFAULT_CONSTRUCTOR);
				break;
			case 7:
				constraint = new Constraint(ConstraintKind.SIMPLE_CHOICE);
				constraint.types.addAll(types());
				break;
			case 8:
				constraint = new Constraint(ConstraintKind.IS_ENUM);
				constraint.types.add(type());
				break;
			case 9:
				constraint = new Constraint(ConstraintKind.IS_DELEGATE);
				constraint.types.add(type());
				constraint.types.add(type());
				break;
			case 10:
				constraint = new Constraint(ConstraintKind.SUPPORTS_COMPARISON);
				break;
			case 11:
				constraint = new Constraint(ConstraintKind.SUPPORTS_EQUALITY);
				break;
			case 12:
				constraint = new Constraint(ConstraintKind.IS_UNMANAGED);
				break;
			default:
				throw new FormatException("u_tyar_constraint: " + tag);
		}
		return constraint;
	}

	private Constraint typarConstraintB()
	{
		int tag = byteB();
		if(tag == 1)
		{
			return new Constraint(ConstraintKind.NOT_SUPPORTS_NULL);
		}
		if(tag == 2)
		{
			return new Constraint(ConstraintKind.ALLOWS_REF_STRUCT);
		}
		throw new FormatException("u_tyar_constraintB: " + tag);
	}

	private Trait trait()
	{
		Trait trait = new Trait();
		trait.supportTypes = types();
		trait.memberName = string();
		MemberInfo flags = new MemberInfo();
		memberFlags(flags);
		trait.isInstance = flags.isInstance;
		trait.memberKind = flags.kind;
		trait.argTypes = types();
		trait.returnType = option(this::type);
		option(() ->
		{
			traitSolution();
			return Boolean.TRUE;
		});
		return trait;
	}

	private void traitSolution()
	{
		int tag = byteValue();
		switch(tag)
		{
			case 0:
				type();
				option(this::ilTypeRef);
				ilMethodRef();
				types();
				break;
			case 1:
				type();
				vref();
				types();
				break;
			case 2:
				break;
			case 3:
				expr();
				break;
			case 4:
				types();
				rfref();
				bool();
				break;
			case 5:
				anonInfo();
				types();
				intValue();
				break;
			case 6:
				type();
				option(this::ilTypeRef);
				ilMethodRef();
				types();
				type();
				break;
			case 7:
				type();
				vref();
				types();
				type();
				break;
			default:
				throw new FormatException("u_trait_sln: " + tag);
		}
	}

	private void memberFlags(MemberInfo info)
	{
		info.isInstance = bool();
		bool();
		info.isDispatchSlot = bool();
		info.isOverrideOrExplicitImpl = bool();
		info.isFinal = bool();
		info.kind = memberKind();
	}

	private MemberKind memberKind()
	{
		int tag = byteValue();
		return switch(tag)
		{
			case 0 -> MemberKind.MEMBER;
			case 1 -> MemberKind.PROPERTY_GET;
			case 2 -> MemberKind.PROPERTY_SET;
			case 3 -> MemberKind.CONSTRUCTOR;
			case 4 -> MemberKind.CLASS_CONSTRUCTOR;
			default -> throw new FormatException("u_member_kind: " + tag);
		};
	}

	private void ucref()
	{
		tcref();
		string();
	}

	private void rfref()
	{
		tcref();
		string();
	}

	// Attributes

	private List<Attrib> attribs()
	{
		return list(this::attrib);
	}

	private Attrib attrib()
	{
		Attrib attrib = new Attrib();
		attrib.type = tcref();
		// attribute kind
		int kind = byteValue();
		if(kind == 0)
		{
			ilMethodRef();
		}
		else if(kind == 1)
		{
			vref();
		}
		else
		{
			throw new FormatException("u_attribkind: " + kind);
		}
		int argCount = intValue();
		for(int i = 0; i < argCount; i++)
		{
			attrib.args.add(attribExpr());
		}
		int namedCount = intValue();
		for(int i = 0; i < namedCount; i++)
		{
			NamedArg named = new NamedArg();
			named.name = string();
			type();
			named.isField = bool();
			named.value = attribExpr();
			attrib.namedArgs.add(named);
		}
		// AppliedToAGetterOrSetter, range is not stored
		bool();
		return attrib;
	}

	/**
	 * @return the constant of the evaluated expression, or null
	 */
	private Object attribExpr()
	{
		expr();
		Object evaluated = expr();
		return evaluated instanceof ConstExpr constant ? constant.value() : null;
	}

	// Entities

	private Entity entitySpec()
	{
		Entity entity = myEntities[checkIndex(intValue(), myEntities.length, "entities")];
		entity.typars = typarSpecs();
		entity.logicalName = string();
		entity.compiledName = option(this::string);
		range();
		entity.publicPath = option(this::pubpath);
		entity.isPublic = access();
		entity.isReprPublic = access();
		entity.attribs = attribs();
		TyconReprReader repr = tyconRepr();
		entity.abbreviation = option(this::type);
		tcaug(entity);
		string();
		entity.isMeasure = byteValue() == 1;
		long flags = int64();
		boolean flagBit = (flags & 0b10000L) != 0;
		entity.flags = flags & ~0b10000L;
		entity.repr = repr.build(flagBit);
		option(this::cpath);
		lazy(() ->
		{
			moduleType(entity);
			return Boolean.TRUE;
		});
		entity.exception = exceptionRepr();
		usedSpace1(() ->
		{
			xmlDoc();
			return Boolean.TRUE;
		});
		return entity;
	}

	private interface TyconReprReader
	{
		TyconRepr build(boolean flagBit);
	}

	private TyconReprReader tyconRepr()
	{
		int tag1 = byteValue();
		if(tag1 == 0)
		{
			return flagBit -> TyconRepr.NONE;
		}
		if(tag1 == 1)
		{
			int tag2 = byteValue();
			switch(tag2)
			{
				case 0:
				{
					TyconRepr repr = new TyconRepr(TyconKind.RECORD);
					repr.fields = fieldTable();
					return flagBit -> repr;
				}
				case 1:
				{
					TyconRepr repr = new TyconRepr(TyconKind.UNION);
					repr.cases = list(this::unionCase);
					return flagBit -> repr;
				}
				case 2:
				{
					String name = ilType();
					return flagBit ->
					{
						// provider generated types are not supported
						if(flagBit)
						{
							return TyconRepr.NONE;
						}
						TyconRepr repr = new TyconRepr(TyconKind.ASM);
						repr.asmTypeName = name;
						return repr;
					};
				}
				case 3:
				{
					TyconRepr repr = objectModelData();
					return flagBit -> repr;
				}
				case 4:
				{
					TyconRepr repr = new TyconRepr(TyconKind.MEASURE_ABBREVIATION);
					repr.measureType = type();
					return flagBit -> repr;
				}
				default:
					throw new FormatException("u_tycon_repr: " + tag2);
			}
		}
		if(tag1 == 2)
		{
			List<UnionCase> cases = new ArrayList<>();
			int n = intValue();
			for(int i = 0; i < n; i++)
			{
				cases.add(unionCase());
			}
			TyconRepr data = objectModelData();
			TyconRepr repr = new TyconRepr(TyconKind.UNION);
			repr.cases = cases;
			repr.fields = data.fields;
			return flagBit -> repr;
		}
		throw new FormatException("u_tycon_repr: " + tag1);
	}

	private TyconRepr objectModelData()
	{
		int tag = byteValue();
		TyconRepr repr;
		switch(tag)
		{
			case 0:
				repr = new TyconRepr(TyconKind.CLASS);
				break;
			case 1:
				repr = new TyconRepr(TyconKind.INTERFACE);
				break;
			case 2:
				repr = new TyconRepr(TyconKind.STRUCT);
				break;
			case 3:
				repr = new TyconRepr(TyconKind.DELEGATE);
				repr.delegateSignature = slotSig();
				break;
			case 4:
				repr = new TyconRepr(TyconKind.ENUM);
				break;
			case 5:
				repr = new TyconRepr(TyconKind.UNION);
				break;
			case 6:
				repr = new TyconRepr(TyconKind.RECORD);
				break;
			default:
				throw new FormatException("u_tycon_objmodel_kind: " + tag);
		}
		// virtual slots
		list(() ->
		{
			vref();
			return Boolean.TRUE;
		});
		repr.fields = fieldTable();
		return repr;
	}

	private UnionCase unionCase()
	{
		UnionCase unionCase = new UnionCase();
		unionCase.fields = fieldTable();
		unionCase.returnType = type();
		string();
		unionCase.name = ident();
		unionCase.attribs = attribsExt();
		string();
		unionCase.isPublic = access();
		return unionCase;
	}

	/**
	 * Attribute list whose length has a marker bit for an extra xml doc.
	 */
	private List<Attrib> attribsExt()
	{
		int n = intValue();
		if((n & 0x80000000) == 0x80000000)
		{
			xmlDoc();
		}
		int count = n & 0x7FFFFFFF;
		List<Attrib> result = new ArrayList<>(count);
		for(int i = 0; i < count; i++)
		{
			result.add(attrib());
		}
		return result;
	}

	private List<Field> fieldTable()
	{
		return list(this::field);
	}

	private Field field()
	{
		Field field = new Field();
		field.isMutable = bool();
		field.isVolatile = bool();
		field.type = type();
		field.isStatic = bool();
		field.isSecret = bool();
		field.literal = option(this::constant);
		field.name = ident();
		field.attribs = attribsExt();
		field.attribs.addAll(attribs());
		string();
		field.isPublic = access();
		return field;
	}

	private ExceptionRepr exceptionRepr()
	{
		int tag = byteValue();
		switch(tag)
		{
			case 0:
			{
				ExceptionRepr repr = new ExceptionRepr();
				repr.abbreviation = tcref();
				return repr;
			}
			case 1:
			{
				ExceptionRepr repr = new ExceptionRepr();
				repr.asmTypeName = ilTypeRef();
				return repr;
			}
			case 2:
			{
				ExceptionRepr repr = new ExceptionRepr();
				repr.fields = fieldTable();
				return repr;
			}
			case 3:
				return null;
			default:
				throw new FormatException("u_exnc_repr: " + tag);
		}
	}

	/**
	 * @return whether the access is public: an empty list of compilation paths
	 */
	private boolean access()
	{
		List<Boolean> paths = list(() ->
		{
			cpath();
			return Boolean.TRUE;
		});
		return paths.isEmpty();
	}

	private Boolean cpath()
	{
		ilScopeRef();
		int n = intValue();
		for(int i = 0; i < n; i++)
		{
			string();
			int kind = byteValue();
			if(kind > 2)
			{
				throw new FormatException("u_istype: " + kind);
			}
		}
		return Boolean.TRUE;
	}

	private void tcaug(Entity entity)
	{
		option(() ->
		{
			vref();
			vref();
			return Boolean.TRUE;
		});
		option(() ->
		{
			vref();
			return Boolean.TRUE;
		});
		option(() ->
		{
			vref();
			vref();
			vref();
			return Boolean.TRUE;
		});
		option(() ->
		{
			vref();
			vref();
			return Boolean.TRUE;
		});
		// adhoc members
		list(() ->
		{
			string();
			vref();
			return Boolean.TRUE;
		});
		// interfaces: type, is from abbreviation, dummy range
		entity.interfaces = list(() ->
		{
			Type type = type();
			bool();
			return type;
		});
		entity.superType = option(this::type);
		entity.isAbstract = bool();
		space(1);
	}

	private void moduleType(Entity entity)
	{
		int kind = byteValue();
		entity.moduleKind = switch(kind)
		{
			case 0 -> ModuleKind.MODULE_WITH_SUFFIX;
			case 1 -> ModuleKind.MODULE_OR_TYPE;
			case 2 -> ModuleKind.NAMESPACE;
			default -> throw new FormatException("u_istype: " + kind);
		};
		entity.vals = list(this::valSpec);
		entity.entities = list(this::entitySpec);
	}

	// Values

	private List<Val> vals()
	{
		return list(this::valSpec);
	}

	private Val valSpec()
	{
		Val val = myVals[checkIndex(intValue(), myVals.length, "vals")];
		val.logicalName = string();
		val.compiledName = option(this::string);
		option(() ->
		{
			range();
			range();
			return Boolean.TRUE;
		});
		val.type = type();
		val.flags = int64();
		val.memberInfo = option(this::memberInfo);
		val.attribs = attribs();
		val.reprInfo = option(this::valReprInfo);
		string();
		val.isPublic = access();
		// parent
		int parentTag = byteValue();
		if(parentTag == 1)
		{
			val.declaringEntity = tcref();
		}
		else if(parentTag != 0)
		{
			throw new FormatException("u_parentref: " + parentTag);
		}
		val.literal = option(this::constant);
		usedSpace1(() ->
		{
			xmlDoc();
			return Boolean.TRUE;
		});
		return val;
	}

	private MemberInfo memberInfo()
	{
		MemberInfo info = new MemberInfo();
		info.apparentEntity = tcref();
		memberFlags(info);
		info.implementedSlots = list(this::slotSig);
		bool();
		return info;
	}

	private ValReprInfo valReprInfo()
	{
		ValReprInfo info = new ValReprInfo();
		info.typars = list(() ->
		{
			String name = ident();
			byteValue();
			return name;
		});
		info.args = list(() -> list(this::argReprInfo));
		info.returnInfo = argReprInfo();
		return info;
	}

	private ArgReprInfo argReprInfo()
	{
		ArgReprInfo info = new ArgReprInfo();
		info.attribs = attribs();
		info.name = option(this::ident);
		return info;
	}

	private SlotSig slotSig()
	{
		SlotSig sig = new SlotSig();
		sig.name = string();
		sig.declaringType = type();
		sig.classTypars = typarSpecs();
		sig.methodTypars = typarSpecs();
		sig.params = list(() -> list(this::slotParam));
		sig.returnType = option(this::type);
		return sig;
	}

	private SlotParam slotParam()
	{
		SlotParam param = new SlotParam();
		param.name = option(this::string);
		param.type = type();
		param.isIn = bool();
		param.isOut = bool();
		param.isOptional = bool();
		attribs();
		return param;
	}

	// Expressions: read to keep the stream position, only constants are kept

	private Object constant()
	{
		int tag = byteValue();
		switch(tag)
		{
			case 0:
				return bool();
			case 1:
				return (byte) intValue();
			case 2:
				return (short) (byteValue() & 0xFF);
			case 3:
				return (short) intValue();
			case 4:
				return intValue() & 0xFFFF;
			case 5:
				return intValue();
			case 6:
				return intValue() & 0xFFFFFFFFL;
			case 7:
			case 9:
				return int64();
			case 8:
			case 10:
				return int64();
			case 11:
				return Float.intBitsToFloat(intValue());
			case 12:
				return Double.longBitsToDouble(int64());
			case 13:
				return (char) (intValue() & 0xFFFF);
			case 14:
				return string();
			case 15:
			case 16:
				return null;
			case 17:
			{
				int n = intValue();
				int[] bits = new int[n];
				for(int i = 0; i < n; i++)
				{
					bits[i] = intValue();
				}
				return bits;
			}
			default:
				throw new FormatException("u_const: " + tag);
		}
	}

	private List<Object> exprs()
	{
		return list(this::expr);
	}

	private Object expr()
	{
		int tag = byteValue();
		switch(tag)
		{
			case 0:
			{
				Object value = constant();
				type();
				return new ConstExpr(value);
			}
			case 1:
				vref();
				vrefFlags();
				return NOT_CONSTANT;
			case 2:
				op();
				types();
				exprs();
				return NOT_CONSTANT;
			case 3:
				expr();
				expr();
				intValue();
				return NOT_CONSTANT;
			case 4:
				option(this::valSpec);
				option(this::valSpec);
				vals();
				expr();
				type();
				return NOT_CONSTANT;
			case 5:
				typarSpecs();
				expr();
				type();
				return NOT_CONSTANT;
			case 6:
				expr();
				type();
				types();
				exprs();
				return NOT_CONSTANT;
			case 7:
				list(this::bind);
				expr();
				return NOT_CONSTANT;
			case 8:
				bind();
				expr();
				return NOT_CONSTANT;
			case 9:
				decisionTree();
				// targets
				list(() ->
				{
					vals();
					expr();
					return Boolean.TRUE;
				});
				type();
				return NOT_CONSTANT;
			case 10:
				type();
				option(this::valSpec);
				expr();
				methods();
				// interfaces
				list(() ->
				{
					type();
					methods();
					return Boolean.TRUE;
				});
				return NOT_CONSTANT;
			case 11:
				// static optimization constraints
				list(() ->
				{
					int constraintTag = byteValue();
					if(constraintTag == 0)
					{
						type();
						type();
					}
					else if(constraintTag == 1)
					{
						type();
					}
					else
					{
						throw new FormatException("u_static_optimization_constraint: " + constraintTag);
					}
					return Boolean.TRUE;
				});
				expr();
				expr();
				return NOT_CONSTANT;
			case 12:
				typarSpecs();
				expr();
				return NOT_CONSTANT;
			case 13:
				expr();
				type();
				return NOT_CONSTANT;
			case 14:
				trait();
				return NOT_CONSTANT;
			default:
				throw new FormatException("u_expr: " + tag);
		}
	}

	private Boolean bind()
	{
		valSpec();
		expr();
		return Boolean.TRUE;
	}

	private void methods()
	{
		list(() ->
		{
			slotSig();
			attribs();
			typarSpecs();
			list(this::vals);
			expr();
			return Boolean.TRUE;
		});
	}

	private void vrefFlags()
	{
		int tag = byteValue();
		if(tag == 3)
		{
			type();
		}
		else if(tag > 4)
		{
			throw new FormatException("u_vrefFlags: " + tag);
		}
	}

	private Boolean decisionTree()
	{
		int tag = byteValue();
		switch(tag)
		{
			case 0:
				expr();
				// cases
				list(() ->
				{
					decisionTreeTest();
					decisionTree();
					return Boolean.TRUE;
				});
				option(this::decisionTree);
				return Boolean.TRUE;
			case 1:
				exprs();
				intValue();
				return Boolean.TRUE;
			case 2:
				bind();
				decisionTree();
				return Boolean.TRUE;
			default:
				throw new FormatException("u_dtree: " + tag);
		}
	}

	private void decisionTreeTest()
	{
		int tag = byteValue();
		switch(tag)
		{
			case 0:
				ucref();
				types();
				break;
			case 1:
				constant();
				break;
			case 2:
				break;
			case 3:
				type();
				type();
				break;
			case 4:
				intValue();
				type();
				break;
			default:
				throw new FormatException("u_dtree_discrim: " + tag);
		}
	}

	private void op()
	{
		int tag = byteValue();
		switch(tag)
		{
			case 0:
			case 14:
				ucref();
				break;
			case 1:
			case 3:
			case 6:
				tcref();
				break;
			case 2:
			case 13:
			case 15:
			case 19:
			case 20:
			case 23:
			case 24:
			case 27:
			case 29:
				break;
			case 4:
			case 5:
			case 25:
				rfref();
				break;
			case 7:
			case 8:
			case 28:
				ucref();
				intValue();
				break;
			case 9:
			case 10:
				tcref();
				intValue();
				break;
			case 11:
			case 30:
				intValue();
				break;
			case 12:
				list(() ->
				{
					ilInstr();
					return Boolean.TRUE;
				});
				types();
				break;
			case 16:
				trait();
				break;
			case 17:
				byteValue();
				vref();
				break;
			case 18:
				bool();
				bool();
				bool();
				bool();
				vrefFlags();
				bool();
				bool();
				ilMethodRef();
				types();
				types();
				types();
				break;
			case 21:
				intValue();
				break;
			case 22:
				bytes();
				break;
			case 26:
			{
				int n = intValue();
				for(int i = 0; i < n; i++)
				{
					intValue();
				}
				break;
			}
			case 31:
				anonInfo();
				break;
			case 32:
				anonInfo();
				intValue();
				break;
			default:
				throw new FormatException("u_op: " + tag);
		}
	}

	/**
	 * Byte stream with the compressed integers of CLR metadata.
	 */
	private static final class Stream
	{
		private final byte[] myBytes;
		private final int myEnd;
		private int position;

		Stream(byte[] bytes, int start, int end)
		{
			myBytes = bytes;
			position = start;
			myEnd = end;
		}

		boolean isEof()
		{
			return position >= myEnd;
		}

		int readByte()
		{
			if(position >= myEnd)
			{
				throw new FormatException("unexpected end of signature data");
			}
			return myBytes[position++] & 0xFF;
		}

		int readPrimInt()
		{
			int b0 = readByte();
			int b1 = readByte();
			int b2 = readByte();
			int b3 = readByte();
			return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
		}

		int readInt()
		{
			int b0 = readByte();
			if(b0 <= 0x7F)
			{
				return b0;
			}
			if(b0 <= 0xBF)
			{
				return ((b0 & 0x7F) << 8) | readByte();
			}
			return readPrimInt();
		}

		int[] readIntArray()
		{
			int n = readInt();
			int[] result = new int[n];
			for(int i = 0; i < n; i++)
			{
				result[i] = readInt();
			}
			return result;
		}

		byte[] readBytes(int length)
		{
			if(position + length > myEnd)
			{
				throw new FormatException("unexpected end of signature data");
			}
			byte[] result = new byte[length];
			System.arraycopy(myBytes, position, result, 0, length);
			position += length;
			return result;
		}

		String readPrimString()
		{
			int length = readInt();
			String value = new String(myBytes, position, length, StandardCharsets.UTF_8);
			position += length;
			return value;
		}
	}
}
