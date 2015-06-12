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

package org.mustbe.dotnet.msil.decompiler.textBuilder;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mustbe.dotnet.msil.decompiler.textBuilder.block.LineStubBlock;
import org.mustbe.dotnet.msil.decompiler.textBuilder.block.StubBlock;
import org.mustbe.dotnet.msil.decompiler.textBuilder.util.XStubUtil;
import com.intellij.util.BitUtil;
import com.intellij.util.PairFunction;
import edu.arizona.cs.mbel.mbel.CustomAttribute;
import edu.arizona.cs.mbel.mbel.MethodDef;
import edu.arizona.cs.mbel.mbel.TypeDef;
import edu.arizona.cs.mbel.signature.CallingConvention;
import edu.arizona.cs.mbel.signature.MethodAttributes;
import edu.arizona.cs.mbel.signature.MethodSignature;
import edu.arizona.cs.mbel.signature.ParamAttributes;
import edu.arizona.cs.mbel.signature.ParameterInfo;
import edu.arizona.cs.mbel.signature.ParameterSignature;
import edu.arizona.cs.mbel.signature.SignatureConstants;
import edu.arizona.cs.mbel.signature.TypeSignature;

/**
 * @author VISTALL
 * @since 21.05.14
 */
public class MsilMethodBuilder extends MsilSharedBuilder implements MethodAttributes
{
	public static void processMethod(MethodDef methodDef, final TypeDef typeDef, StubBlock block)
	{
		StringBuilder builder = new StringBuilder();
		builder.append(".method ");
		if(XStubUtil.isSet(methodDef.getFlags(), MemberAccessMask, Public))
		{
			builder.append("public ");
		}
		else if(XStubUtil.isSet(methodDef.getFlags(), MemberAccessMask, Assem))
		{
			builder.append("assembly ");
		}
		else if(XStubUtil.isSet(methodDef.getFlags(), MemberAccessMask, Private))
		{
			builder.append("private ");
		}
		else if(XStubUtil.isSet(methodDef.getFlags(), MemberAccessMask, Family))
		{
			builder.append("protected ");
		}
		else if(XStubUtil.isSet(methodDef.getFlags(), MemberAccessMask, FamORAssem))
		{
			builder.append("famorassem ");
		}

		if(XStubUtil.isSet(methodDef.getFlags(), Static))
		{
			builder.append("static ");
		}

		if(XStubUtil.isSet(methodDef.getFlags(), Final))
		{
			builder.append("final ");
		}

		if(XStubUtil.isSet(methodDef.getFlags(), HideBySig))
		{
			builder.append("hidebysig ");
		}

		if(XStubUtil.isSet(methodDef.getFlags(), Abstract))
		{
			builder.append("abstract ");
		}

		if(XStubUtil.isSet(methodDef.getFlags(), Virtual))
		{
			builder.append("virtual ");
		}

		if(XStubUtil.isSet(methodDef.getFlags(), SpecialName))
		{
			builder.append("specialname ");
		}

		if(XStubUtil.isSet(methodDef.getFlags(), RTSpecialName))
		{
			builder.append("rtspecialname ");
		}

		MethodSignature signature = methodDef.getSignature();

		if(signature.getCallingConvention() == CallingConvention.VARARG)
		{
			builder.append("vararg ");
		}

		typeToString(builder, signature.getReturnType().getInnerType(), typeDef);
		builder.append(" ");
		appendValidName(builder, methodDef.getName());
		processGeneric(builder, methodDef, typeDef);

		List<ParameterSignature> parameters = signature.getParameters();

		buildParameters(builder, parameters, typeDef, true);

		StubBlock e = new StubBlock(builder, null, StubBlock.BRACES);

		processAttributes(e, methodDef);

		processGenericParamAttribute(e, methodDef);

		for(int i = 0; i < parameters.size(); i++)
		{
			ParameterSignature parameterSignature = parameters.get(i);

			ParameterInfo parameterInfo = parameterSignature.getParameterInfo();
			if(parameterInfo == null)
			{
				continue;
			}

			byte[] defaultValue = parameterInfo.getDefaultValue();
			List<CustomAttribute> customAttributes = parameterInfo.getCustomAttributes();

			if(defaultValue != null || !customAttributes.isEmpty())
			{
				StringBuilder lineBuilder = new StringBuilder().append(".param [").append(i + 1).append("]");

				TypeSignature typeSignature = parameterSignature;
				if(parameterSignature.getType() == SignatureConstants.ELEMENT_TYPE_TYPEONLY)
				{
					typeSignature = parameterSignature.getInnerType();
				}

				appendValue(lineBuilder, typeSignature, defaultValue);
				lineBuilder.append("\n");

				e.getBlocks().add(new LineStubBlock(lineBuilder));
				processAttributes(e, parameterInfo);
			}
		}

		block.getBlocks().add(e);
	}

	public static void buildParameters(@NotNull StringBuilder builder,
			@NotNull final List<ParameterSignature> parameters,
			@Nullable final TypeDef typeDef,
			final boolean appendName)
	{
		builder.append("(");

		join(builder, parameters, new PairFunction<StringBuilder, ParameterSignature, Void>()
		{
			@Override
			public Void fun(StringBuilder builder, ParameterSignature parameterSignature)
			{
				ParameterInfo parameterInfo = parameterSignature.getParameterInfo();
				if(parameterInfo != null)
				{
					if(BitUtil.isSet(parameterInfo.getFlags(), ParamAttributes.HasDefault))
					{
						builder.append("[opt] ");
					}

					if(BitUtil.isSet(parameterInfo.getFlags(), ParamAttributes.In))
					{
						builder.append("[in] ");
					}

					if(BitUtil.isSet(parameterInfo.getFlags(), ParamAttributes.Out))
					{
						builder.append("[out] ");
					}
				}

				TypeSignature typeSignature = parameterSignature;
				if(parameterSignature.getType() == SignatureConstants.ELEMENT_TYPE_TYPEONLY)
				{
					typeSignature = parameterSignature.getInnerType();
				}

				typeToString(builder, typeSignature, typeDef);

				if(appendName && parameterInfo != null)
				{
					builder.append(" ");
					appendValidName(builder, parameterInfo.getName());
				}
				return null;
			}
		}, ", ");
		builder.append(")");
	}
}
