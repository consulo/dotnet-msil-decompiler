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

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import consulo.internal.dotnet.asm.mbel.AssemblyInfo;
import consulo.internal.dotnet.asm.mbel.CustomAttribute;
import consulo.internal.dotnet.asm.mbel.ModuleParser;
import consulo.internal.dotnet.asm.mbel.TypeDef;
import consulo.internal.dotnet.msil.decompiler.textBuilder.util.XStubUtil;
import consulo.internal.dotnet.msil.decompiler.util.MsilHelper;
import consulo.vfs.impl.archive.ArchiveEntry;
import consulo.vfs.impl.archive.ArchiveFile;
import gnu.trove.THashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * @author VISTALL
 * @since 11.12.13.
 */
public class DotNetArchiveFile implements ArchiveFile
{
	public static final int VERSION = 3;

	public static final String BYTECODE_FILE_EXTENSION = "il";

	private final Map<String, ArchiveEntry> myArchiveEntries;

	private String myName;

	public DotNetArchiveFile(@Nonnull File originalFile, ModuleParser moduleParser, long l)
	{
		myArchiveEntries = map(originalFile.getPath(), moduleParser, l);
		myName = originalFile.getName();
	}

	@Nonnull
	@Override
	public String getName()
	{
		return myName;
	}

	@Nonnull
	private static Map<String, ArchiveEntry> map(@Nonnull String originalFilePath, @Nonnull ModuleParser moduleParser, long lastModifier)
	{
		Ref<ModuleParser> moduleParserRef = Ref.create(moduleParser);
		TypeDef[] typeDefs = moduleParser.getTypeDefs();
		List<DotNetFileArchiveEntry> fileList = new ArrayList<>();

		Map<String, DotNetBaseFileArchiveEntry> duplicateMap = new HashMap<>(); // map used for collect types with same name but different signature

		TypeDef moduleTypeDef = null;

		// iterate type def add as files
		for(TypeDef typeDef : typeDefs)
		{
			String name = typeDef.getName();
			if(Comparing.equal(name, DotNetModuleFileArchiveEntry.ModuleTypeDef))
			{
				moduleTypeDef = typeDef;
				continue;
			}

			if(XStubUtil.isInvisibleMember(name) || typeDef.getParent() != null)
			{
				continue;
			}

			String userName = MsilHelper.cutGenericMarker(name);

			String path;
			String namespace = typeDef.getNamespace();
			if(StringUtil.isEmpty(namespace))
			{
				path = userName + "." + BYTECODE_FILE_EXTENSION;
			}
			else
			{
				path = namespace.replace(".", "/") + "/" + userName + "." + BYTECODE_FILE_EXTENSION;
			}

			DotNetBaseFileArchiveEntry fileWithSameName = duplicateMap.get(path);
			if(fileWithSameName != null)
			{
				fileWithSameName.addTypeDef(typeDef);
			}
			else
			{
				DotNetBaseFileArchiveEntry e = new DotNetBaseFileArchiveEntry(originalFilePath, moduleParserRef, typeDef, path, lastModifier);
				fileList.add(e);
				duplicateMap.put(path, e);
			}
		}

		AssemblyInfo assemblyInfo = moduleParser.getAssemblyInfo();
		if(assemblyInfo != null)
		{
			fileList.add(new DotNetAssemblyFileArchiveEntry(originalFilePath, moduleParserRef, assemblyInfo, lastModifier));
		}

		if(moduleTypeDef != null)
		{
			List<CustomAttribute> customAttributes = moduleTypeDef.getCustomAttributes();
			if(!customAttributes.isEmpty())
			{
				fileList.add(new DotNetModuleFileArchiveEntry(originalFilePath, moduleParserRef, moduleTypeDef, lastModifier));
			}
		}

		// sort - at to head, files without namespaces
		Collections.sort(fileList, (o1, o2) ->
		{
			int compare = StringUtil.compare(o1.getNamespace(), o2.getNamespace(), true);
			if(compare != 0)
			{
				return compare;
			}

			return o1.getName().compareToIgnoreCase(o2.getName());
		});

		Map<String, ArchiveEntry> map = new THashMap<>(fileList.size() + 10);

		List<String> alreadyAddedNamespaces = new ArrayList<>();

		for(DotNetFileArchiveEntry fileEntry : fileList)
		{
			DotNetDirArchiveEntry dirEntry = createNamespaceDirIfNeed(alreadyAddedNamespaces, fileEntry, lastModifier);
			if(dirEntry != null)
			{
				map.put(dirEntry.getName(), dirEntry);
			}

			map.put(fileEntry.getName(), fileEntry);
		}

		return map;
	}

	private static DotNetDirArchiveEntry createNamespaceDirIfNeed(List<String> defineList, DotNetFileArchiveEntry position, long lastModified)
	{
		String namespace = position.getNamespace();
		if(StringUtil.isEmpty(namespace))
		{
			return null;
		}

		String[] split = namespace.split("\\.");

		StringBuilder builder = new StringBuilder();
		for(int i = 0; i < split.length; i++)
		{
			String part = split[i];
			if(i != 0)
			{
				builder.append("/");
			}

			builder.append(part);

			String dirPath = builder.toString();
			if(!defineList.contains(dirPath))
			{
				defineList.add(dirPath);

				return new DotNetDirArchiveEntry(dirPath + "/", lastModified);
			}
		}
		return null;
	}

	@Nullable
	@Override
	public ArchiveEntry getEntry(String name)
	{
		return myArchiveEntries.get(name);
	}

	@Nullable
	@Override
	public InputStream getInputStream(ArchiveEntry archiveEntry) throws IOException
	{
		if(archiveEntry instanceof DotNetDirArchiveEntry)
		{
			return new ByteArrayInputStream(ArrayUtil.EMPTY_BYTE_ARRAY);
		}
		return ((DotNetFileArchiveEntry) archiveEntry).createInputStream();
	}

	@Nonnull
	@Override
	public Iterator<? extends ArchiveEntry> entries()
	{
		return myArchiveEntries.values().iterator();
	}

	@Override
	public int getSize()
	{
		return myArchiveEntries.size();
	}
}
