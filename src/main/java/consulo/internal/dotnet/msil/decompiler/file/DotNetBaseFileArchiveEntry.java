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

package consulo.internal.dotnet.msil.decompiler.file;

import consulo.internal.dotnet.asm.mbel.ModuleParser;
import consulo.internal.dotnet.asm.mbel.TypeDef;
import consulo.internal.dotnet.msil.decompiler.textBuilder.MsilStubBuilder;
import consulo.internal.dotnet.msil.decompiler.textBuilder.block.StubBlock;
import consulo.util.collection.SmartList;
import consulo.util.lang.ref.SimpleReference;

import jakarta.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

/**
 * @author VISTALL
 * @since 11.12.13.
 */
public class DotNetBaseFileArchiveEntry extends DotNetAbstractFileArchiveEntry
{
	private List<TypeDef> myTypeDefs;

	private final String myNamespace;

	public DotNetBaseFileArchiveEntry(String originalFilePath, SimpleReference<ModuleParser> moduleParserRef, TypeDef typeDef, String name, long lastModified)
	{
		super(originalFilePath, moduleParserRef, name, lastModified);
		myTypeDefs = new SmartList<>(typeDef);
		myNamespace = typeDef.getNamespace();
	}

	public void addTypeDef(@Nonnull TypeDef typeDef)
	{
		myTypeDefs.add(typeDef);
	}

	@Override
	@Nonnull
	public String getNamespace()
	{
		return myNamespace;
	}

	@Nonnull
	@Override
	public List<? extends StubBlock> build()
	{
		if(myTypeDefs == null)
		{
			return Collections.emptyList();
		}
		return MsilStubBuilder.parseTypeDef(myTypeDefs);
	}

	@Override
	public void drop()
	{
		myTypeDefs = null;
	}
}
