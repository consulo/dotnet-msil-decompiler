/*
 * Copyright 2013-2014 must-be.org
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

package consulo.internal.dotnet.msil.decompiler.textBuilder;

import consulo.internal.dotnet.asm.mbel.*;
import consulo.internal.dotnet.asm.signature.*;
import consulo.internal.dotnet.msil.decompiler.textBuilder.block.LineStubBlock;
import consulo.internal.dotnet.msil.decompiler.textBuilder.block.StubBlock;
import consulo.internal.dotnet.msil.decompiler.textBuilder.util.XStubUtil;
import consulo.internal.dotnet.msil.decompiler.util.MsilHelper;
import consulo.internal.dotnet.msil.decompiler.util.MsilUtil;
import consulo.util.lang.BitUtil;
import consulo.util.lang.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

/**
 * @author VISTALL
 * @since 21.05.14
 */
public class MsilSharedBuilder implements SignatureConstants
{
	private static final Logger LOG = LoggerFactory.getLogger(MsilSharedBuilder.class);

	private static final String[] KEYWORDS = new String[]{
			"virtual",
			"vararg",
			"nested",
			"bracket_opt",
			"char",
			"assembly",
			"uint32",
			"object",
			"float32",
			"private",
			"uint16",
			"protected",
			"bracket_in",
			"int64",
			"initonly",
			"value",
			"sealed",
			"float64",
			"serializable",
			"abstract",
			"specialname",
			"final",
			"static",
			"famorassembly",
			"int8",
			"extends",
			"int16",
			"int",
			"void",
			"class",
			"interface",
			"uint8",
			"int32",
			"bracket_out",
			"string",
			"rtspecialname",
			"implements",
			"bool",
			"hidebysig",
			"public",
			"uint",
			"literal",
			"uint64",
			"nullref",
			"type",
			"false",
			"true"
	};

	private static final char[] INVALID_CHARS = {
			'<',
			'/',
			'>',
			'-'
	};

	public static void appendValidName(StringBuilder builder, String name)
	{
		if(!isValidName(name))
		{
			builder.append('\'');
			builder.append(name);
			builder.append('\'');
		}
		else
		{
			builder.append(name);
		}
	}

	private static boolean isValidName(String name)
	{
		if(name.length() > 0 && Character.isDigit(name.charAt(0)))
		{
			return false;
		}

		for(char invalidChar : INVALID_CHARS)
		{
			if(name.indexOf(invalidChar) >= 0)
			{
				return false;
			}
		}

		for(String s : KEYWORDS)
		{
			if(name.equals(s))
			{
				return false;
			}
		}
		return true;
	}

	public static void processGenericParamAttribute(StubBlock parent, GenericParamOwner owner)
	{
		for(GenericParamDef genericParamDef : owner.getGenericParams())
		{
			List<CustomAttribute> customAttributes = genericParamDef.getCustomAttributes();
			if(!customAttributes.isEmpty())
			{
				parent.getBlocks().add(new LineStubBlock(".param type " + genericParamDef.getName() + "\n"));
				processAttributes(parent, genericParamDef);
			}
		}
	}

	public static void processAttributes(StubBlock parent, CustomAttributeOwner owner)
	{
		for(CustomAttribute customAttribute : owner.getCustomAttributes())
		{
			StringBuilder builder = new StringBuilder();
			builder.append(".custom ");

			MethodDefOrRef constructor = customAttribute.getConstructor();
			toStringFromDefRefSpec(builder, constructor.getParent(), null);
			builder.append("::");
			builder.append(constructor.getName());

			List<ParameterSignature> parameterSignatures = Collections.emptyList();
			if(constructor instanceof MethodRef)
			{
				MethodSignature callsiteSignature = ((MethodRef) constructor).getCallsiteSignature();
				parameterSignatures = callsiteSignature.getParameters();
			}
			else if(constructor instanceof MethodDef)
			{
				MethodSignature signature = ((MethodDef) constructor).getSignature();
				parameterSignatures = signature.getParameters();
			}

			MsilMethodBuilder.buildParameters(builder, parameterSignatures, null, false);

			builder.append(" = ");
			byte[] signature = customAttribute.getSignature();

			StubBlock block = new StubBlock(builder, null, StubBlock.PAR);
			StringBuilder lineBuilder = null;
			for(int i = 0; i < signature.length; i++)
			{
				byte b = signature[i];

				int pos = i % 16;

				if(pos == 0)
				{
					lineBuilder = new StringBuilder(40);
				}

				lineBuilder.append(String.format("%02X", b & 0xFF));

				boolean isLastByte = i == (signature.length - 1);
				if(pos == 15)
				{
					if(isLastByte)
					{
						lineBuilder.append('\n');
					}

					block.getBlocks().add(new LineStubBlock(lineBuilder));
				}
				else if(!isLastByte)
				{
					lineBuilder.append(' ');
				}
				else
				{
					lineBuilder.append('\n');
					block.getBlocks().add(new LineStubBlock(lineBuilder));
				}
			}

			parent.getBlocks().add(block);
		}
	}

	protected static void appendValue(StringBuilder builder, TypeSignature typeSignature, byte[] defaultValue)
	{
		if(defaultValue == null || typeSignature == null)
		{
			return;
		}

		builder.append(" = ");

		appendValueImpl(builder, typeSignature, defaultValue);
	}

	private static void appendValueImpl(StringBuilder builder, TypeSignature typeSignature, byte[] defaultValue)
	{
		try
		{
			byte type = typeSignature.getType();
			switch(type)
			{
				case ELEMENT_TYPE_BOOLEAN:
					builder.append("bool(").append(defaultValue[0] == 1).append(")");
					break;
				case ELEMENT_TYPE_I:
				case ELEMENT_TYPE_I1:
					builder.append("int8(").append(defaultValue[0]).append(")");
					break;
				case ELEMENT_TYPE_U:
				case ELEMENT_TYPE_U1:
					builder.append("uint8(").append(defaultValue[0] & 0xFF).append(")");
					break;
				case ELEMENT_TYPE_I2:
					builder.append("int16(").append(MsilUtil.getShort(defaultValue)).append(")");
					break;
				case ELEMENT_TYPE_U2:
					builder.append("uint16(").append(MsilUtil.getShort(defaultValue) & 0xFFFF).append(")");
					break;
				case ELEMENT_TYPE_I4:
					builder.append("int32(").append(MsilUtil.getInt(defaultValue)).append(")");
					break;
				case ELEMENT_TYPE_U4:
					builder.append("uint32(").append(MsilUtil.getInt(defaultValue) & 0xFFFFFFFFL).append(")");
					break;
				case ELEMENT_TYPE_I8:
					builder.append("int64(").append(MsilUtil.getLong(defaultValue)).append(")");
					break;
				case ELEMENT_TYPE_U8:
					BigInteger bigInteger = new BigInteger(defaultValue);
					builder.append("uint64(").append(bigInteger.toString()).append(")");
					break;
				case ELEMENT_TYPE_R4:
					builder.append("float32(").append(Float.intBitsToFloat(MsilUtil.getInt(defaultValue))).append(")");
					break;
				case ELEMENT_TYPE_OBJECT:
				case ELEMENT_TYPE_CLASS:
					if(MsilUtil.getInt(defaultValue) == 0)
					{
						builder.append("nullref");
					}
					break;
				case ELEMENT_TYPE_R8:
					builder.append("float64(").append(Double.longBitsToDouble(MsilUtil.getLong(defaultValue))).append(")");
					break;
				case ELEMENT_TYPE_CHAR:
					builder.append("char(");
					char aChar = MsilUtil.getChar(defaultValue);
					builder.append((int) aChar);
					builder.append(")");
					break;
				case ELEMENT_TYPE_STRING:
					String stringValue = new String(defaultValue, XStubUtil.STRING_CHARSET);
					builder.append("\"");
					builder.append(XStubUtil.escapeChars(stringValue));
					builder.append("\"");
					break;
				case ELEMENT_TYPE_VALUETYPE:
					if(!(typeSignature instanceof ValueTypeSignature))
					{
						builder.append(StringUtil.QUOTER.apply("valuetype is not ValueTypeSignature"));
						return;
					}
					AbstractTypeReference valueType = ((ValueTypeSignature) typeSignature).getValueType();
					if(valueType instanceof TypeDef)
					{
						Field field = ((TypeDef) valueType).getFieldByName(MsilHelper.ENUM_VALUE_FIEND_NAME);
						if(field == null)
						{
							builder.append(StringUtil.QUOTER.apply("\'" + MsilHelper.ENUM_VALUE_FIEND_NAME + "\' not found"));
							return;
						}
						appendValueImpl(builder, field.getSignature().getType(), defaultValue);
					}
					else
					{
						TypeSignature guessType = guessTypeFromByteArray(defaultValue);
						if(guessType == null)
						{
							builder.append(StringUtil.QUOTER.apply("no guess type: " + defaultValue.length));
							return;
						}

						appendValueImpl(builder, guessType, defaultValue);
					}
					break;
				default:
					builder.append(StringUtil.QUOTER.apply("unknown how read 0x" + String.format("%02X", type)));
					break;
			}
		}
		catch(Exception e)
		{
			builder.append(StringUtil.QUOTER.apply("error"));
		}
	}

	@Nullable
	private static TypeSignature guessTypeFromByteArray(byte[] bytes)
	{
		if(bytes.length == 1)
		{
			return TypeSignature.I1;
		}
		if(bytes.length == 2)
		{
			return TypeSignature.I2;
		}
		if(bytes.length == 4)
		{
			return TypeSignature.I4;
		}
		if(bytes.length == 8)
		{
			return TypeSignature.I8;
		}
		return null;
	}

	protected static void appendAccessor(String name, final TypeDef typeDef, MethodDef methodDef, StubBlock block)
	{
		StringBuilder builder = new StringBuilder();
		builder.append(name);
		builder.append(" ");

		typeToString(builder, methodDef.getSignature().getReturnType().getInnerType(), typeDef);

		builder.append(" ");

		toStringFromDefRefSpec(builder, methodDef.getParent(), typeDef);

		builder.append("::");

		appendValidName(builder, methodDef.getName());

		builder.append("(");
		join(builder, methodDef.getSignature().getParameters(), (t, v) ->
		{
			typeToString(t, v.getInnerType(), typeDef);
			return null;
		}, ", ");
		builder.append(")\n");

		block.getBlocks().add(new LineStubBlock(builder));
	}

	protected static void processGeneric(StringBuilder builder, @Nonnull final GenericParamOwner paramOwner, @Nullable final TypeDef typeDef)
	{
		List<GenericParamDef> genericParams = paramOwner.getGenericParams();
		if(genericParams.isEmpty())
		{
			return;
		}

		builder.append("<");
		join(builder, genericParams, (innerBuilder, genericParamDef) ->
		{
			int flags = genericParamDef.getFlags();

			int specialFlags = flags & GenericParamAttributes.SpecialConstraintMask;

			if(BitUtil.isSet(specialFlags, GenericParamAttributes.NotNullableValueTypeConstraint))
			{
				innerBuilder.append("valuetype ");
			}

			if(BitUtil.isSet(specialFlags, GenericParamAttributes.ReferenceTypeConstraint))
			{
				innerBuilder.append("class ");
			}

			if(BitUtil.isSet(specialFlags, GenericParamAttributes.DefaultConstructorConstraint))
			{
				innerBuilder.append(".ctor ");
			}

			List<GenericParamConstraintDef> constraints = genericParamDef.getConstraints();
			if(!constraints.isEmpty())
			{
				innerBuilder.append("(");
				join(innerBuilder, constraints, (t, v) ->
				{
					toStringFromDefRefSpec(t, v.getType(), typeDef);
					return null;
				}, ", ");
				innerBuilder.append(")");
			}

			int varianceMask = flags & GenericParamAttributes.VarianceMask;
			switch(varianceMask)
			{
				case GenericParamAttributes.Covariant:
					innerBuilder.append("+");
					break;
				case GenericParamAttributes.Contravariant:
					innerBuilder.append("-");
					break;
			}
			innerBuilder.append(genericParamDef.getName());
			return null;
		}, ", ");
		builder.append(">");
	}

	public static void typeToString(StringBuilder builder, TypeSignature signature, TypeDef typeDef)
	{
		if(signature == null)
		{
			builder.append("void");
			return;
		}
		byte type = signature.getType();
		switch(type)
		{
			case ELEMENT_TYPE_BOOLEAN:
				builder.append("bool");
				break;
			case ELEMENT_TYPE_VOID:
				builder.append("void");
				break;
			case ELEMENT_TYPE_CHAR:
				builder.append("char");
				break;
			case ELEMENT_TYPE_I1:
				builder.append("int8");
				break;
			case ELEMENT_TYPE_U1:
				builder.append("uint8");
				break;
			case ELEMENT_TYPE_I2:
				builder.append("int16");
				break;
			case ELEMENT_TYPE_U2:
				builder.append("uint16");
				break;
			case ELEMENT_TYPE_I4:
				builder.append("int32");
				break;
			case ELEMENT_TYPE_U4:
				builder.append("uint32");
				break;
			case ELEMENT_TYPE_I8:
				builder.append("int64");
				break;
			case ELEMENT_TYPE_U8:
				builder.append("uint64");
				break;
			case ELEMENT_TYPE_R4:
				builder.append("float32");
				break;
			case ELEMENT_TYPE_R8:
				builder.append("float64");
				break;
			case ELEMENT_TYPE_STRING:
				builder.append("string");
				break;
			case ELEMENT_TYPE_OBJECT:
				builder.append("object");
				break;
			case ELEMENT_TYPE_I:
				builder.append("int");
				break;
			case ELEMENT_TYPE_U:
				builder.append("uint");
				break;
			case ELEMENT_TYPE_BYREF:
				typeToString(builder, ((InnerTypeOwner) signature).getInnerType(), typeDef);
				builder.append("&");
				break;
			case ELEMENT_TYPE_TYPEDBYREF:
				builder.append("valuetype ").append("System.TypedReference");
				break;
			case ELEMENT_TYPE_PTR:
				PointerTypeSignature pointerTypeSignature = (PointerTypeSignature) signature;
				typeToString(builder, pointerTypeSignature.getPointerType(), typeDef);
				builder.append("*");
				break;
			case ELEMENT_TYPE_SZARRAY:
				SZArrayTypeSignature szArrayTypeSignature = (SZArrayTypeSignature) signature;
				typeToString(builder, szArrayTypeSignature.getElementType(), typeDef);
				builder.append("[]");
				break;
			case ELEMENT_TYPE_ARRAY:
				ArrayTypeSignature arrayTypeSignature = (ArrayTypeSignature) signature;
				typeToString(builder, arrayTypeSignature.getElementType(), typeDef);
				ArrayShapeSignature arrayShape = arrayTypeSignature.getArrayShape();
				builder.append("[");
				for(int i = 0; i < arrayShape.getRank(); i++)
				{
					if(i != 0)
					{
						builder.append(", ");
					}
					int low = MsilUtil.safeGet(arrayShape.getLowerBounds(), i);
					builder.append(low);
					builder.append("...");
				}
				builder.append("]");
				break;
			case ELEMENT_TYPE_CLASS:
				ClassTypeSignature typeSignature = (ClassTypeSignature) signature;
				builder.append("class ");
				toStringFromDefRefSpec(builder, typeSignature.getClassType(), typeDef);
				break;
			case ELEMENT_TYPE_GENERIC_INST:
				TypeSignatureWithGenericParameters mainTypeSignature = (TypeSignatureWithGenericParameters) signature;
				typeToString(builder, mainTypeSignature.getSignature(), typeDef);
				List<TypeSignature> genericArguments = mainTypeSignature.getGenericArguments();
				if(!genericArguments.isEmpty())
				{
					builder.append("<");
					for(int i = 0; i < genericArguments.size(); i++)
					{
						if(i != 0)
						{
							builder.append(", ");
						}
						typeToString(builder, genericArguments.get(i), typeDef);
					}
					builder.append(">");
				}
				break;
			case ELEMENT_TYPE_VAR:
				XGenericTypeSignature typeGenericTypeSignature = (XGenericTypeSignature) signature;
				if(typeDef == null)
				{
					LOG.error("TypeDef is null", new Exception());
					builder.append("GENERICERROR");
					return;
				}
				builder.append("!");
				GenericParamDef genericParamDef = MsilUtil.safeGet(typeDef.getGenericParams(), typeGenericTypeSignature.getIndex());
				if(genericParamDef == null)
				{
					LOG.error("Invalid generic index for type " + typeDef.getFullName() + ", index: " + typeGenericTypeSignature.getIndex());
					builder.append("UNK").append(typeGenericTypeSignature.getIndex());
				}
				else
				{
					builder.append(genericParamDef.getName());
				}
				break;
			case ELEMENT_TYPE_MVAR:
				XGenericTypeSignature methodGenericTypeSignature = (XGenericTypeSignature) signature;

				builder.append(methodGenericTypeSignature.getIndex());
				break;
			case ELEMENT_TYPE_VALUETYPE:
				builder.append("valuetype ");
				ValueTypeSignature valueTypeSignature = (ValueTypeSignature) signature;
				toStringFromDefRefSpec(builder, valueTypeSignature.getValueType(), typeDef);
				break;
			default:
				builder.append("UNK").append(Integer.toHexString(type).toUpperCase());
				break;
		}
	}

	public static void appendTypeRefFullName(@Nonnull StringBuilder builder, String namespace, @Nonnull String name)
	{
		if(!StringUtil.isEmpty(namespace))
		{
			appendValidName(builder, namespace + "." + name);
		}
		else
		{
			appendValidName(builder, name);
		}
	}

	public static <T> void join(StringBuilder builder, List<T> list, BiFunction<StringBuilder, T, Void> function, String dem)
	{
		for(int i = 0; i < list.size(); i++)
		{
			if(i != 0)
			{
				builder.append(dem);
			}

			T t = list.get(i);
			function.apply(builder, t);
		}
	}

	public static void toStringFromDefRefSpec(@Nonnull StringBuilder builder, @Nonnull AbstractTypeReference typeReference, @Nullable TypeDef typeDef)
	{
		if(typeReference instanceof TypeDef)
		{
			TypeDef parent = ((TypeDef) typeReference).getParent();
			if(parent != null)
			{
				toStringFromDefRefSpec(builder, parent, parent);
				builder.append(MsilHelper.NESTED_SEPARATOR_IN_NAME);
				appendValidName(builder, ((TypeDef) typeReference).getName());
			}
			else
			{
				appendTypeRefFullName(builder, ((TypeRef) typeReference).getNamespace(), ((TypeRef) typeReference).getName());
			}
		}
		else if(typeReference instanceof NestedTypeRef)
		{
			TypeRef enclosingTypeRef = ((NestedTypeRef) typeReference).getEnclosingTypeRef();
			if(enclosingTypeRef != null)
			{
				toStringFromDefRefSpec(builder, enclosingTypeRef, null);
				builder.append(MsilHelper.NESTED_SEPARATOR_IN_NAME);
				appendValidName(builder, ((TypeRef) typeReference).getName());
			}
			else
			{
				appendTypeRefFullName(builder, ((TypeRef) typeReference).getNamespace(), ((TypeRef) typeReference).getName());
			}
		}
		else if(typeReference instanceof TypeRef)
		{
			appendTypeRefFullName(builder, ((TypeRef) typeReference).getNamespace(), ((TypeRef) typeReference).getName());
		}
		else if(typeReference instanceof TypeSpec)
		{
			typeToString(builder, ((TypeSpec) typeReference).getSignature(), typeDef);
		}
		else
		{
			throw new IllegalArgumentException(typeReference.toString());
		}
	}
}
