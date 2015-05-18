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

package org.mustbe.dotnet.msil.decompiler.file;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.mustbe.dotnet.msil.decompiler.textBuilder.MsilTypeBuilder;
import org.mustbe.dotnet.msil.decompiler.textBuilder.block.StubBlock;
import edu.arizona.cs.mbel.mbel.ModuleParser;
import edu.arizona.cs.mbel.mbel.TypeDef;

/**
 * @author VISTALL
 * @since 18.05.2015
 */
public class DotNetModuleFileArchiveEntry extends DotNetAbstractFileArchiveEntry
{
	public static final String ModuleInfo = "ModuleInfo.msil";
	public static final String ModuleTypeDef = "<Module>";

	private TypeDef myModuleTypeDef;

	public DotNetModuleFileArchiveEntry(File originalFile, ModuleParser moduleParser, TypeDef assemblyInfo, long lastModified)
	{
		super(originalFile, moduleParser, ModuleInfo, lastModified);
		myModuleTypeDef = assemblyInfo;
	}

	@NotNull
	@Override
	public List<? extends StubBlock> build()
	{
		TypeDef moduleTypeDef = myModuleTypeDef;
		myModuleTypeDef = null;
		return Arrays.asList(MsilTypeBuilder.processTypeDef(moduleTypeDef));
	}
}
