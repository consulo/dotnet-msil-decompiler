/*
 * Copyright 2013-2015 must-be.org
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

import com.intellij.openapi.util.Ref;
import consulo.internal.dotnet.asm.mbel.ModuleParser;
import consulo.internal.dotnet.asm.mbel.TypeDef;
import consulo.internal.dotnet.msil.decompiler.textBuilder.MsilTypeBuilder;
import consulo.internal.dotnet.msil.decompiler.textBuilder.block.StubBlock;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author VISTALL
 * @since 18.05.2015
 */
public class DotNetModuleFileArchiveEntry extends DotNetAbstractFileArchiveEntry
{
	public static final String ModuleInfo = "ModuleInfo." + DotNetArchiveFile.BYTECODE_FILE_EXTENSION;

	public static final String ModuleTypeDef = "<Module>";

	private TypeDef myModuleTypeDef;

	public DotNetModuleFileArchiveEntry(String originalFilePath, Ref<ModuleParser> moduleParserRef, TypeDef assemblyInfo, long lastModified)
	{
		super(originalFilePath, moduleParserRef, ModuleInfo, lastModified);
		myModuleTypeDef = assemblyInfo;
	}

	@Nonnull
	@Override
	public List<? extends StubBlock> build()
	{
		if(myModuleTypeDef == null)
		{
			return Collections.emptyList();
		}
		return Arrays.asList(MsilTypeBuilder.processTypeDef(myModuleTypeDef));
	}

	@Override
	public void drop()
	{
		myModuleTypeDef = null;
	}
}
