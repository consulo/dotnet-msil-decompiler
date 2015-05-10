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

import org.mustbe.dotnet.msil.decompiler.textBuilder.block.StubBlock;
import edu.arizona.cs.mbel.mbel.MethodDef;
import edu.arizona.cs.mbel.mbel.Property;
import edu.arizona.cs.mbel.mbel.TypeDef;

/**
 * @author VISTALL
 * @since 21.05.14
 */
public class MsilPropertyBuilder extends MsilSharedBuilder
{
	public static void processProperty(Property property, TypeDef typeDef, StubBlock e)
	{
		StringBuilder builder = new StringBuilder();
		builder.append(".property ");
		typeToString(builder, property.getSignature().getType(), typeDef);
		builder.append(" ");
		appendValidName(builder, property.getName());

		StubBlock e1 = new StubBlock(builder, null, StubBlock.BRACES);
		processAttributes(e1, property);

		MethodDef getter = property.getGetter();
		if(getter != null)
		{
			appendAccessor(".get", typeDef, getter, e1);
		}

		MethodDef setter = property.getSetter();
		if(setter != null)
		{
			appendAccessor(".set", typeDef, setter, e1);
		}
		e.getBlocks().add(e1);
	}
}
