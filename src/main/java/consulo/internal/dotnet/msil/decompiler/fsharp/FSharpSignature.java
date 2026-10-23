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

import java.util.ArrayList;
import java.util.List;

/**
 * F# signature data of an assembly ({@code FSharpSignatureData} resource): the typed tree of its public API as the
 * F# compiler sees it - things IL metadata does not keep, like modules, unions, records, curried function types,
 * type abbreviations, units of measure and static member constraints.
 * <p>
 * The model mirrors the F# compiler typed tree ({@code TypedTree.fs} in dotnet/fsharp, MIT license) with only the
 * parts needed by an IDE.
 *
 * @author VISTALL
 * @since 2026-10-25
 */
public final class FSharpSignature
{
	/**
	 * Assemblies referenced by the signature data.
	 */
	public final List<String> ccuNames;
	/**
	 * The root namespace of the assembly: its entities are the top level namespaces and modules.
	 */
	public final Entity root;

	public FSharpSignature(List<String> ccuNames, Entity root)
	{
		this.ccuNames = ccuNames;
		this.root = root;
	}

	// Entities: namespaces, modules, types and exceptions

	public static final long ENTITY_IS_MODULE_OR_NAMESPACE = 0b1L;
	public static final long ENTITY_IS_PREFIX_DISPLAY = 0b10L;
	public static final long ENTITY_IS_STRUCT_RECORD_OR_UNION = 0b100000L;

	public enum ModuleKind
	{
		/**
		 * A module compiled with a {@code Module} suffix, because a type of the same name exists.
		 */
		MODULE_WITH_SUFFIX,
		MODULE_OR_TYPE,
		NAMESPACE
	}

	public static final class Entity
	{
		public List<Typar> typars = new ArrayList<>();
		public String logicalName = "";
		public String compiledName;
		public String[] publicPath;
		public boolean isPublic = true;
		public boolean isReprPublic = true;
		public List<Attrib> attribs = new ArrayList<>();
		public TyconRepr repr = TyconRepr.NONE;
		public Type abbreviation;
		public List<Type> interfaces = new ArrayList<>();
		public Type superType;
		public boolean isAbstract;
		public boolean isMeasure;
		public long flags;
		public ModuleKind moduleKind;
		public List<Val> vals = new ArrayList<>();
		public List<Entity> entities = new ArrayList<>();
		public ExceptionRepr exception;

		public boolean isModuleOrNamespace()
		{
			return (flags & ENTITY_IS_MODULE_OR_NAMESPACE) != 0;
		}

		public boolean isNamespace()
		{
			return isModuleOrNamespace() && moduleKind == ModuleKind.NAMESPACE;
		}

		public boolean isModule()
		{
			return isModuleOrNamespace() && moduleKind != ModuleKind.NAMESPACE;
		}

		public String getCompiledName()
		{
			return compiledName == null ? logicalName : compiledName;
		}

		@Override
		public String toString()
		{
			return logicalName;
		}
	}

	public enum TyconKind
	{
		NONE,
		RECORD,
		UNION,
		CLASS,
		INTERFACE,
		STRUCT,
		DELEGATE,
		ENUM,
		/**
		 * Inline IL representation: {@code type int = (# "int32" #)}
		 */
		ASM,
		MEASURE_ABBREVIATION
	}

	public static final class TyconRepr
	{
		public static final TyconRepr NONE = new TyconRepr(TyconKind.NONE);

		public final TyconKind kind;
		public List<UnionCase> cases = new ArrayList<>();
		public List<Field> fields = new ArrayList<>();
		public SlotSig delegateSignature;
		public String asmTypeName;
		public Type measureType;

		public TyconRepr(TyconKind kind)
		{
			this.kind = kind;
		}
	}

	public static final class UnionCase
	{
		public List<Field> fields = new ArrayList<>();
		public Type returnType;
		public String name;
		public List<Attrib> attribs = new ArrayList<>();
		public boolean isPublic;
	}

	public static final class Field
	{
		public boolean isMutable;
		public boolean isVolatile;
		public Type type;
		public boolean isStatic;
		public boolean isSecret;
		public Object literal;
		public String name;
		public List<Attrib> attribs = new ArrayList<>();
		public boolean isPublic;
	}

	public static final class ExceptionRepr
	{
		/**
		 * Exception abbreviation ({@code exception E = Other}), or null
		 */
		public TcRef abbreviation;
		/**
		 * Fields of a fresh exception, or null
		 */
		public List<Field> fields;
		/**
		 * IL type of an exception mapped to an IL class, or null
		 */
		public String asmTypeName;
	}

	// Values and members

	public static final long VAL_MUTABLE = 0b1L;
	public static final long VAL_COMPILER_GENERATED = 0b1000L;
	public static final long VAL_INLINE_MASK = 0b110000L;
	public static final long VAL_INLINE_ALWAYS = 0b010000L;
	public static final long VAL_IS_MEMBER_OR_MODULE_BINDING = 0b10000000L;
	public static final long VAL_IS_EXTENSION_MEMBER = 0b100000000L;
	public static final long VAL_IS_TYPE_FUNCTION = 0b10000000000L;

	public enum MemberKind
	{
		MEMBER,
		PROPERTY_GET,
		PROPERTY_SET,
		CONSTRUCTOR,
		CLASS_CONSTRUCTOR
	}

	public static final class MemberInfo
	{
		public TcRef apparentEntity;
		public boolean isInstance;
		public boolean isDispatchSlot;
		public boolean isOverrideOrExplicitImpl;
		public boolean isFinal;
		public MemberKind kind;
		public List<SlotSig> implementedSlots = new ArrayList<>();
	}

	public static final class Val
	{
		public String logicalName = "";
		public String compiledName;
		public Type type;
		public long flags;
		public MemberInfo memberInfo;
		public List<Attrib> attribs = new ArrayList<>();
		/**
		 * Arity of a top level function or member: the curried groups of tupled arguments.
		 */
		public ValReprInfo reprInfo;
		public boolean isPublic = true;
		public TcRef declaringEntity;
		public Object literal;

		public boolean isMutable()
		{
			return (flags & VAL_MUTABLE) != 0;
		}

		public boolean isInline()
		{
			return (flags & VAL_INLINE_MASK) == VAL_INLINE_ALWAYS;
		}

		public boolean isCompilerGenerated()
		{
			return (flags & VAL_COMPILER_GENERATED) != 0;
		}

		public boolean isExtensionMember()
		{
			return (flags & VAL_IS_EXTENSION_MEMBER) != 0;
		}

		public String getCompiledName()
		{
			return compiledName == null ? logicalName : compiledName;
		}

		@Override
		public String toString()
		{
			return logicalName;
		}
	}

	public static final class ValReprInfo
	{
		public List<String> typars = new ArrayList<>();
		public List<List<ArgReprInfo>> args = new ArrayList<>();
		public ArgReprInfo returnInfo;
	}

	public static final class ArgReprInfo
	{
		public String name;
		public List<Attrib> attribs = new ArrayList<>();
	}

	public static final class SlotSig
	{
		public String name;
		public Type declaringType;
		public List<Typar> classTypars = new ArrayList<>();
		public List<Typar> methodTypars = new ArrayList<>();
		public List<List<SlotParam>> params = new ArrayList<>();
		public Type returnType;
	}

	public static final class SlotParam
	{
		public String name;
		public Type type;
		public boolean isIn;
		public boolean isOut;
		public boolean isOptional;
	}

	// Attributes

	public static final class Attrib
	{
		public TcRef type;
		/**
		 * Positional arguments: constants or null for non constant expressions.
		 */
		public List<Object> args = new ArrayList<>();
		public List<NamedArg> namedArgs = new ArrayList<>();

		public String getTypeName()
		{
			return type == null ? "" : type.getName();
		}
	}

	public static final class NamedArg
	{
		public String name;
		public boolean isField;
		public Object value;
	}

	// Types

	public static final long TYPAR_STATIC_REQ = 0b1000L;
	public static final long TYPAR_KIND_MEASURE = 0b100000000L;

	public static final class Typar
	{
		public String name = "";
		public long flags;
		public List<Constraint> constraints = new ArrayList<>();
		public List<Attrib> attribs = new ArrayList<>();

		/**
		 * {@code ^T}: statically resolved type parameter
		 */
		public boolean isStaticallyResolved()
		{
			return (flags & TYPAR_STATIC_REQ) != 0;
		}

		public boolean isMeasure()
		{
			return (flags & TYPAR_KIND_MEASURE) != 0;
		}

		@Override
		public String toString()
		{
			return (isStaticallyResolved() ? "^" : "'") + name;
		}
	}

	public enum ConstraintKind
	{
		COERCES_TO,
		MAY_RESOLVE_MEMBER,
		DEFAULTS_TO,
		SUPPORTS_NULL,
		IS_NON_NULLABLE_STRUCT,
		IS_REFERENCE_TYPE,
		REQUIRES_DEFAULT_CONSTRUCTOR,
		SIMPLE_CHOICE,
		IS_ENUM,
		IS_DELEGATE,
		SUPPORTS_COMPARISON,
		SUPPORTS_EQUALITY,
		IS_UNMANAGED,
		NOT_SUPPORTS_NULL,
		ALLOWS_REF_STRUCT
	}

	public static final class Constraint
	{
		public final ConstraintKind kind;
		public List<Type> types = new ArrayList<>();
		/**
		 * Member constraint: {@code (static member (+): ^T * ^T -> ^T)}
		 */
		public Trait trait;

		public Constraint(ConstraintKind kind)
		{
			this.kind = kind;
		}
	}

	public static final class Trait
	{
		public List<Type> supportTypes = new ArrayList<>();
		public String memberName;
		public boolean isInstance;
		public MemberKind memberKind;
		public List<Type> argTypes = new ArrayList<>();
		public Type returnType;
	}

	/**
	 * Reference to an entity: of this assembly or, by path, of another one.
	 */
	public static final class TcRef
	{
		public final Entity local;
		public final String ccu;
		public final String[] path;

		public TcRef(Entity local)
		{
			this.local = local;
			this.ccu = null;
			this.path = null;
		}

		public TcRef(String ccu, String[] path)
		{
			this.local = null;
			this.ccu = ccu;
			this.path = path;
		}

		public String getName()
		{
			if(local != null)
			{
				return local.logicalName;
			}
			return path == null || path.length == 0 ? "" : path[path.length - 1];
		}

		@Override
		public String toString()
		{
			return local != null ? local.logicalName : ccu + ":" + String.join(".", path);
		}
	}

	public static abstract class Type
	{
	}

	public static final class TupleType extends Type
	{
		public final boolean isStruct;
		public final List<Type> elements;

		public TupleType(boolean isStruct, List<Type> elements)
		{
			this.isStruct = isStruct;
			this.elements = elements;
		}
	}

	public static final class AppType extends Type
	{
		public final TcRef tcref;
		public final List<Type> arguments;

		public AppType(TcRef tcref, List<Type> arguments)
		{
			this.tcref = tcref;
			this.arguments = arguments;
		}
	}

	public static final class FunType extends Type
	{
		public final Type domain;
		public final Type range;

		public FunType(Type domain, Type range)
		{
			this.domain = domain;
			this.range = range;
		}
	}

	public static final class VarType extends Type
	{
		public final Typar typar;

		public VarType(Typar typar)
		{
			this.typar = typar;
		}
	}

	public static final class ForallType extends Type
	{
		public final List<Typar> typars;
		public final Type body;

		public ForallType(List<Typar> typars, Type body)
		{
			this.typars = typars;
			this.body = body;
		}
	}

	public static final class MeasureType extends Type
	{
		public final Measure measure;

		public MeasureType(Measure measure)
		{
			this.measure = measure;
		}
	}

	public static final class UnionCaseType extends Type
	{
		public final TcRef unionType;
		public final String caseName;
		public final List<Type> arguments;

		public UnionCaseType(TcRef unionType, String caseName, List<Type> arguments)
		{
			this.unionType = unionType;
			this.caseName = caseName;
			this.arguments = arguments;
		}
	}

	public static final class AnonRecordType extends Type
	{
		public final boolean isStruct;
		public final List<String> fieldNames;
		public final List<Type> fieldTypes;

		public AnonRecordType(boolean isStruct, List<String> fieldNames, List<Type> fieldTypes)
		{
			this.isStruct = isStruct;
			this.fieldNames = fieldNames;
			this.fieldTypes = fieldTypes;
		}
	}

	// Units of measure

	public static abstract class Measure
	{
	}

	public static final class ConstMeasure extends Measure
	{
		public final TcRef tcref;

		public ConstMeasure(TcRef tcref)
		{
			this.tcref = tcref;
		}
	}

	public static final class InvMeasure extends Measure
	{
		public final Measure measure;

		public InvMeasure(Measure measure)
		{
			this.measure = measure;
		}
	}

	public static final class ProdMeasure extends Measure
	{
		public final Measure left;
		public final Measure right;

		public ProdMeasure(Measure left, Measure right)
		{
			this.left = left;
			this.right = right;
		}
	}

	public static final class VarMeasure extends Measure
	{
		public final Typar typar;

		public VarMeasure(Typar typar)
		{
			this.typar = typar;
		}
	}

	public static final class OneMeasure extends Measure
	{
		public static final OneMeasure INSTANCE = new OneMeasure();
	}

	public static final class PowerMeasure extends Measure
	{
		public final Measure measure;
		public final int numerator;
		public final int denominator;

		public PowerMeasure(Measure measure, int numerator, int denominator)
		{
			this.measure = measure;
			this.numerator = numerator;
			this.denominator = denominator;
		}
	}
}
