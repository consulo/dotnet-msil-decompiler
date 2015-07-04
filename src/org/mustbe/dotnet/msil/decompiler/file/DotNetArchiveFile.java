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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mustbe.dotnet.msil.decompiler.textBuilder.util.XStubUtil;
import org.mustbe.dotnet.msil.decompiler.util.MsilHelper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.ArchiveEntry;
import com.intellij.openapi.vfs.ArchiveFile;
import com.intellij.util.ArrayUtil;
import edu.arizona.cs.mbel.mbel.AssemblyInfo;
import edu.arizona.cs.mbel.mbel.CustomAttribute;
import edu.arizona.cs.mbel.mbel.ModuleParser;
import edu.arizona.cs.mbel.mbel.TypeDef;
import lombok.val;

/**
 * @author VISTALL
 * @since 11.12.13.
 */
public class DotNetArchiveFile implements ArchiveFile
{
	private final List<ArchiveEntry> myArchiveEntries;

	public DotNetArchiveFile(@NotNull File originalFile, ModuleParser moduleParser, long l)
	{
		myArchiveEntries = map(originalFile, moduleParser, l);
	}

	@NotNull
	private static List<ArchiveEntry> map(@NotNull File originalFile, @NotNull ModuleParser moduleParser, long lastModifier)
	{
		Ref<ModuleParser> moduleParserRef = Ref.create(moduleParser);
		TypeDef[] typeDefs = moduleParser.getTypeDefs();
		List<DotNetFileArchiveEntry> fileList = new ArrayList<DotNetFileArchiveEntry>();

		Map<String, DotNetBaseFileArchiveEntry> duplicateMap = new HashMap<String, DotNetBaseFileArchiveEntry>(); // map used for collect types with same name but different signature

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
				path = userName + ".msil";
			}
			else
			{
				path = namespace.replace(".", "/") + "/" + userName + ".msil";
			}

			DotNetBaseFileArchiveEntry fileWithSameName = duplicateMap.get(path);
			if(fileWithSameName != null)
			{
				fileWithSameName.addTypeDef(typeDef);
			}
			else
			{
				DotNetBaseFileArchiveEntry e = new DotNetBaseFileArchiveEntry(originalFile, moduleParserRef, typeDef, path, lastModifier);
				fileList.add(e);
				duplicateMap.put(path, e);
			}
		}

		AssemblyInfo assemblyInfo = moduleParser.getAssemblyInfo();
		if(assemblyInfo != null)
		{
			fileList.add(new DotNetAssemblyFileArchiveEntry(originalFile, moduleParserRef, assemblyInfo, lastModifier));
		}

		if(moduleTypeDef != null)
		{
			List<CustomAttribute> customAttributes = moduleTypeDef.getCustomAttributes();
			if(!customAttributes.isEmpty())
			{
				fileList.add(new DotNetModuleFileArchiveEntry(originalFile, moduleParserRef, moduleTypeDef, lastModifier));
			}
		}

		// sort - at to head, files without namespaces
		Collections.sort(fileList, new Comparator<DotNetFileArchiveEntry>()
		{
			@Override
			public int compare(DotNetFileArchiveEntry o1, DotNetFileArchiveEntry o2)
			{
				int compare = StringUtil.compare(o1.getNamespace(), o2.getNamespace(), true);
				if(compare != 0)
				{
					return compare;
				}

				return o1.getName().compareToIgnoreCase(o2.getName());
			}
		});

		val list = new ArrayList<ArchiveEntry>(fileList.size() + 10);

		val alreadyAddedNamespaces = new ArrayList<String>();

		for(DotNetFileArchiveEntry fileEntry : fileList)
		{
			DotNetDirArchiveEntry dirEntry = createNamespaceDirIfNeed(alreadyAddedNamespaces, fileEntry, lastModifier);
			if(dirEntry != null)
			{
				list.add(dirEntry);
			}
			list.add(fileEntry);
		}

		return list;
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

			val dirPath = builder.toString();
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
	public ArchiveEntry getEntry(String s)
	{
		//noinspection ForLoopReplaceableByForEach
		for(int i = 0; i < myArchiveEntries.size(); i++)
		{
			ArchiveEntry entry = myArchiveEntries.get(i);
			if(StringUtil.equals(entry.getName(), s))
			{
				return entry;
			}
		}
		return null;
	}

	@Nullable
	@Override
	public InputStream getInputStream(ArchiveEntry archiveEntry) throws IOException
	{
		if(archiveEntry instanceof DotNetDirArchiveEntry)
		{
			return new ByteArrayInputStream(ArrayUtil.EMPTY_BYTE_ARRAY);
		}
		return ((DotNetFileArchiveEntry)archiveEntry).createInputStream();
	}

	@NotNull
	@Override
	public Iterator<? extends ArchiveEntry> entries()
	{
		return myArchiveEntries.iterator();
	}

	@Override
	public int getSize()
	{
		return myArchiveEntries.size();
	}
}
