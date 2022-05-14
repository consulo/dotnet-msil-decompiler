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

package test;

import consulo.internal.dotnet.asm.mbel.ModuleParser;
import consulo.internal.dotnet.asm.mbel.TypeDef;
import consulo.internal.dotnet.msil.decompiler.Main;
import consulo.internal.dotnet.msil.decompiler.file.DotNetArchiveEntry;
import consulo.internal.dotnet.msil.decompiler.file.DotNetArchiveFile;
import consulo.internal.dotnet.msil.decompiler.textBuilder.MsilTypeBuilder;
import consulo.internal.dotnet.msil.decompiler.textBuilder.block.StubBlock;
import consulo.internal.dotnet.msil.decompiler.textBuilder.util.StubBlockUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import org.junit.Assert;
import org.junit.ComparisonFailure;
import org.junit.Test;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author VISTALL
 * @since 11.05.2015
 */
public class DecompileTest extends Assert
{
	@Test
	public void testTest1Mscorlib() throws Throwable
	{
		doTest("test1/mscorlib.dll");
	}

	@Test
	public void testTest2IkvmCore() throws Throwable
	{
		doTest("test2/IkvmCore.dll");
	}

	@Test
	public void testNullRef() throws Throwable
	{
		doTest("nullRef/untitled168.exe", "Program.il");
	}

	@Test
	public void testInternalType() throws Throwable
	{
		doTest("internalType/untitled.dll", "InternalClass.il");
		doTest("internalType/untitled.dll", "NoModifierClass.il");
	}

	@Test
	public void testValueTyperef() throws Throwable
	{
		doTest("valueTypeRef/untitled168.exe", "Program.il");
	}

	@Test
	public void testValueTypeRefByte() throws Throwable
	{
		doTest("valueTypeRefByte/untitled168.exe", "Program.il");
	}

	@Test
	public void testStringEscape() throws Throwable
	{
		doTest("stringEscape/untitled194.exe", "Program.il");
	}

	@Test
	public void testStringParameterDefaultValue() throws Throwable
	{
		doTest("stringParameterDefaultValue/untitled1.dll", "Program.il");
	}

	@Test
	public void testGenericParameterCustomAttributes() throws Throwable
	{
		doTest("genericParameterCustomAttributes/FSharpCore.dll");
	}

	@Test
	public void testUnityEditorGUILayout() throws Throwable
	{
		doTest("unityEditorGUILayout/UnityEditor.dll", "UnityEditor/EditorGUILayout.il");
	}

	public static void doTest(@Nonnull String path, @Nullable String fileToTest) throws Throwable
	{
		File file = PathSearcher.getTestPath(path);
		if(!file.exists())
		{
			throw new IllegalArgumentException(file.getAbsolutePath() + " is not exists");
		}

		ModuleParser moduleParser = new ModuleParser(file);

		List<String> parts = StringUtil.split(fileToTest.replace(".", "/"), "/");
		// drop .il
		parts = parts.subList(0, parts.size() - 1);

		TypeDef target = null;
		String qNameAsString = String.join(".", parts);
		
		for(TypeDef typeDef : moduleParser.getTypeDefs())
		{
			if(qNameAsString.equals(typeDef.getFullName()))
			{
				target = typeDef;
				break;
			}
		}

		if(target == null)
		{
			throw new IllegalArgumentException("TypeDef " + qNameAsString + " is not found");
		}

		StubBlock block = MsilTypeBuilder.processTypeDef(target);
		CharSequence actualData = StubBlockUtil.buildText(Collections.singletonList(block));

		File targetFile = new File(file.getParentFile(), fileToTest);
		if(!targetFile.exists())
		{
			Files.write(targetFile.toPath(), actualData.toString().getBytes(StandardCharsets.UTF_8));

			throw new IllegalArgumentException(targetFile.getAbsolutePath() + " is not exists. File generated");
		}

		String expectedData = StringUtil.convertLineSeparators(Files.readString(targetFile.toPath(), StandardCharsets.UTF_8));
		if(!Comparing.equal(actualData, expectedData))
		{
			throw new ComparisonFailure("File '" + fileToTest + "' content is not equal", expectedData.toString(), actualData.toString());
		}
	}

	public static void doTest(@Nonnull String path) throws Throwable
	{
		File file = PathSearcher.getTestPath(path);
		if(!file.exists())
		{
			throw new IllegalArgumentException(file.getAbsolutePath() + " is not exists");
		}

		ModuleParser moduleParser = new ModuleParser(file);

		DotNetArchiveFile archiveFile = new DotNetArchiveFile(file, moduleParser, System.currentTimeMillis());

		File targetFile = new File(file.getParentFile(), Main.getNameWithoutExtension(file.getName()) + ".zip");
		if(!targetFile.exists())
		{
			Main.main(new String[]{file.getAbsolutePath()});
			throw new IllegalArgumentException(targetFile.getAbsolutePath() + " is not exists. File generated");
		}

		ZipFile zipFile = new ZipFile(targetFile);

		Iterator<? extends DotNetArchiveEntry> entries = archiveFile.entries();
		while(entries.hasNext())
		{
			DotNetArchiveEntry next = entries.next();
			if(next.isDirectory())
			{
				continue;
			}

			ZipEntry entry = zipFile.getEntry(next.getName());
			//System.out.println("Testing: " + next.getName());
			if(entry == null)
			{
				throw new ComparisonFailure("Entry '" + next.getName() + "' in target file is not found", loadTextAndClose(archiveFile.getInputStream(next)), "");
			}
			else
			{
				String actual = loadTextAndClose(zipFile.getInputStream(entry));
				String expected = loadTextAndClose(archiveFile.getInputStream(next));

				if(!actual.equals(expected))
				{
					throw new ComparisonFailure("File '" + next.getName() + "' content is not equal", actual, expected);
				}
			}
		}
	}

	private static String loadTextAndClose(InputStream stream) throws IOException
	{
		try (stream)
		{
			byte[] bytes = stream.readAllBytes();
			return StringUtil.convertLineSeparators(new String(bytes, StandardCharsets.UTF_8));
		}
	}
}
