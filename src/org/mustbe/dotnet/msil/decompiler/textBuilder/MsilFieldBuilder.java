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

import org.mustbe.dotnet.msil.decompiler.textBuilder.block.LineStubBlock;
import org.mustbe.dotnet.msil.decompiler.textBuilder.block.StubBlock;
import org.mustbe.dotnet.msil.decompiler.textBuilder.util.XStubUtil;
import edu.arizona.cs.mbel.mbel.Field;
import edu.arizona.cs.mbel.mbel.TypeDef;
import edu.arizona.cs.mbel.signature.FieldAttributes;

/**
 * @author VISTALL
 * @since 21.05.14
 */
public class MsilFieldBuilder extends MsilSharedBuilder implements FieldAttributes
{
	public static void processField(Field field, TypeDef typeDef, StubBlock block)
	{
		StringBuilder builder = new StringBuilder();
		builder.append(".field ");
		if(XStubUtil.isSet(field.getFlags(), FieldAccessMask, Public))
		{
			builder.append("public ");
		}
		else if(XStubUtil.isSet(field.getFlags(), FieldAccessMask, Assembly))
		{
			builder.append("assembly ");
		}
		else if(XStubUtil.isSet(field.getFlags(), FieldAccessMask, FamANDAssem))
		{
			builder.append("famorassem ");
		}
		else if(XStubUtil.isSet(field.getFlags(), FieldAccessMask, Family))
		{
			builder.append("protected ");
		}
		else if(XStubUtil.isSet(field.getFlags(), FieldAccessMask, Private))
		{
			builder.append("private ");
		}

		if(XStubUtil.isSet(field.getFlags(), Static))
		{
			builder.append("static ");
		}

		if(XStubUtil.isSet(field.getFlags(), Literal))
		{
			builder.append("literal ");
		}

		if(XStubUtil.isSet(field.getFlags(), InitOnly))
		{
			builder.append("initonly ");
		}

		typeToString(builder, field.getSignature().getType(), typeDef);
		builder.append(" ");
		appendValidName(builder, field.getName());
		appendValue(builder, field.getSignature().getType(), field.getDefaultValue());
		builder.append("\n");

		block.getBlocks().add(new LineStubBlock(builder));

		processAttributes(block, field);
	}
}
