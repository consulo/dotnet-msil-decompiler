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

import java.util.List;

import consulo.internal.dotnet.asm.mbel.Event;
import consulo.internal.dotnet.asm.mbel.Field;
import consulo.internal.dotnet.asm.mbel.InterfaceImplementation;
import consulo.internal.dotnet.asm.mbel.MethodDef;
import consulo.internal.dotnet.asm.mbel.Property;
import consulo.internal.dotnet.asm.mbel.TypeDef;
import consulo.internal.dotnet.asm.signature.TypeAttributes;
import consulo.internal.dotnet.msil.decompiler.textBuilder.block.StubBlock;
import consulo.internal.dotnet.msil.decompiler.textBuilder.util.XStubUtil;

/**
 * @author VISTALL
 * @since 21.05.14
 */
public class MsilTypeBuilder extends MsilSharedBuilder implements TypeAttributes
{
	public static StubBlock processTypeDef(final TypeDef typeDef)
	{
		StringBuilder builder = new StringBuilder();
		builder.append(".class ");

		if(XStubUtil.isSet(typeDef.getFlags(), TypeAttributes.VisibilityMask, TypeAttributes.Public))
		{
			builder.append("public ");
		}
		else if(XStubUtil.isSet(typeDef.getFlags(), TypeAttributes.VisibilityMask, 
				TypeAttributes.NestedPrivate))
		{
			builder.append("nested private ");
		}
		else if(XStubUtil.isSet(typeDef.getFlags(), TypeAttributes.VisibilityMask, 
				TypeAttributes.NestedPublic))
		{
			builder.append("nested public ");
		}
		else if(XStubUtil.isSet(typeDef.getFlags(), TypeAttributes.VisibilityMask, 
				TypeAttributes.NestedFamily))
		{
			builder.append("nested protected ");
		}
		else if(XStubUtil.isSet(typeDef.getFlags(), TypeAttributes.VisibilityMask, 
				TypeAttributes.NestedAssembly))
		{
			builder.append("nested assembly ");
		}

		if(XStubUtil.isSet(typeDef.getFlags(), TypeAttributes.ClassSemanticsMask, 
				TypeAttributes.Interface))
		{
			builder.append("interface ");
		}

		if(XStubUtil.isSet(typeDef.getFlags(), TypeAttributes.Abstract))
		{
			builder.append("abstract ");
		}

		if(XStubUtil.isSet(typeDef.getFlags(), TypeAttributes.Sealed))
		{
			builder.append("sealed ");
		}

		if(XStubUtil.isSet(typeDef.getFlags(), TypeAttributes.SpecialName))
		{
			builder.append("specialname ");
		}

		if(XStubUtil.isSet(typeDef.getFlags(), TypeAttributes.Serializable))
		{
			builder.append("serializable ");
		}

		appendTypeRefFullName(builder, typeDef.getNamespace(), typeDef.getName());

		processGeneric(builder, typeDef, typeDef);

		Object superClass = typeDef.getSuperClass();
		if(superClass != null)
		{
			builder.append(" extends ");
			toStringFromDefRefSpec(builder, superClass, typeDef);
		}

		List<InterfaceImplementation> interfaceImplementations = typeDef.getInterfaceImplementations();
		if(!interfaceImplementations.isEmpty())
		{
			builder.append(" implements ");

			join(builder, interfaceImplementations, (builder1, o) ->
			{
				toStringFromDefRefSpec(builder1, o.getInterface(), typeDef);
				return null;
			}, ", ");
		}

		StubBlock e = new StubBlock(builder, null, StubBlock.BRACES);
		processAttributes(e, typeDef);
		processGenericParamAttribute(e, typeDef);

		for(int k = 0; k < typeDef.getNestedClasses().size(); k++)
		{
			TypeDef def = typeDef.getNestedClasses().get(k);
			if(XStubUtil.isInvisibleMember(def.getName()))
			{
				continue;
			}
			e.getBlocks().add(processTypeDef(def));
		}

		for(int j = 0; j < typeDef.getFields().size(); j++)
		{
			Field field = typeDef.getFields().get(j);

			//processAttributes(e, field);
			MsilFieldBuilder.processField(field, typeDef, e);
		}

		for(int k = 0; k < typeDef.getEvents().size(); k++)
		{
			Event event = typeDef.getEvents().get(k);
			MsilEventBuilder.processEvent(event, typeDef, e);
		}

		for(int k = 0; k < typeDef.getProperties().size(); k++)
		{
			Property property = typeDef.getProperties().get(k);
			MsilPropertyBuilder.processProperty(property, typeDef, e);
		}

		for(int k = 0; k < typeDef.getMethods().size(); k++)
		{
			MethodDef methodDef = typeDef.getMethods().get(k);

			MsilMethodBuilder.processMethod(methodDef, typeDef, e);
		}

		return e;
	}
}
