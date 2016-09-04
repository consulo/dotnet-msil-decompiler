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

package org.mustbe.dotnet.msil.decompiler.file;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.mustbe.dotnet.msil.decompiler.textBuilder.MsilStubBuilder;
import org.mustbe.dotnet.msil.decompiler.textBuilder.block.StubBlock;
import com.intellij.openapi.util.Ref;
import consulo.internal.dotnet.asm.mbel.AssemblyInfo;
import consulo.internal.dotnet.asm.mbel.ModuleParser;
import consulo.lombok.annotations.Logger;

/**
 * @author VISTALL
 * @since 11.12.13.
 */
@Logger
public class DotNetAssemblyFileArchiveEntry extends DotNetAbstractFileArchiveEntry
{
	public static final String AssemblyInfo = "AssemblyInfo.msil";

	private AssemblyInfo myAssemblyInfo;

	public DotNetAssemblyFileArchiveEntry(String originalFilePath, Ref<ModuleParser> moduleParserRef, AssemblyInfo assemblyInfo, long lastModified)
	{
		super(originalFilePath, moduleParserRef, AssemblyInfo, lastModified);
		myAssemblyInfo = assemblyInfo;
	}

	@NotNull
	@Override
	public List<? extends StubBlock> build()
	{
		AssemblyInfo assemblyInfo = myAssemblyInfo;
		myAssemblyInfo = null;
		return MsilStubBuilder.parseAssemblyInfo(assemblyInfo);
	}
}
