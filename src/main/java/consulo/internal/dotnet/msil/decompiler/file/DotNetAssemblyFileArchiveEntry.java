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

import consulo.internal.dotnet.asm.mbel.AssemblyInfo;
import consulo.internal.dotnet.asm.mbel.ModuleParser;
import consulo.internal.dotnet.msil.decompiler.textBuilder.MsilStubBuilder;
import consulo.internal.dotnet.msil.decompiler.textBuilder.block.StubBlock;
import consulo.util.lang.ref.SimpleReference;

import jakarta.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

/**
 * @author VISTALL
 * @since 11.12.13.
 */
public class DotNetAssemblyFileArchiveEntry extends DotNetAbstractFileArchiveEntry
{
	public static final String AssemblyInfo = "AssemblyInfo." + DotNetArchiveFile.BYTECODE_FILE_EXTENSION;

	private AssemblyInfo myAssemblyInfo;

	public DotNetAssemblyFileArchiveEntry(String originalFilePath, SimpleReference<ModuleParser> moduleParserRef, AssemblyInfo assemblyInfo, long lastModified)
	{
		super(originalFilePath, moduleParserRef, AssemblyInfo, lastModified);
		myAssemblyInfo = assemblyInfo;
	}

	@Nonnull
	@Override
	public List<? extends StubBlock> build()
	{
		if(myAssemblyInfo == null)
		{
			return Collections.emptyList();
		}
		return MsilStubBuilder.parseAssemblyInfo(myAssemblyInfo);
	}

	@Override
	public void drop()
	{
		myAssemblyInfo = null;
	}
}
