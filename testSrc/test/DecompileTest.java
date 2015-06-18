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

import java.io.File;
import java.util.Collections;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.mustbe.dotnet.msil.decompiler.Main;
import org.mustbe.dotnet.msil.decompiler.file.DotNetArchiveFile;
import org.mustbe.dotnet.msil.decompiler.textBuilder.MsilTypeBuilder;
import org.mustbe.dotnet.msil.decompiler.textBuilder.block.StubBlock;
import org.mustbe.dotnet.msil.decompiler.textBuilder.util.StubBlockUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.ArchiveEntry;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.FileComparisonFailure;
import edu.arizona.cs.mbel.mbel.ModuleParser;
import edu.arizona.cs.mbel.mbel.TypeDef;

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
		doTest("nullRef/untitled168.exe", "Program.msil");
	}

	@Test
	public void testValueTyperef() throws Throwable
	{
		doTest("valueTypeRef/untitled168.exe", "Program.msil");
	}

	@Test
	public void testValueTypeRefByte() throws Throwable
	{
		doTest("ValueTypeRefByte/untitled168.exe", "Program.msil");
	}

	@Test
	public void testStringEscape() throws Throwable
	{
		doTest("stringEscape/untitled194.exe", "Program.msil");
	}

	@Test
	public void testGenericParameterCustomAttributes() throws Throwable
	{
		doTest("genericParameterCustomAttributes/FSharpCore.dll");
	}

	public static void doTest(@NotNull String path, @Nullable String fileToTest) throws Throwable
	{
		File file = new File("testData", path);
		if(!file.exists())
		{
			throw new IllegalArgumentException(file.getAbsolutePath() + " is not exists");
		}

		ModuleParser moduleParser = new ModuleParser(file);
		moduleParser.parseNext();

		QualifiedName qualifiedName = QualifiedName.fromComponents(StringUtil.getPackageName(fileToTest).replace("/", "."));

		TypeDef target = null;
		String qNameAsString = qualifiedName.toString();
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
			FileUtil.writeToFile(targetFile, actualData.toString());

			throw new IllegalArgumentException(targetFile.getAbsolutePath() + " is not exists. File generated");
		}

		String expectedData = FileUtil.loadFile(targetFile, "UTF-8", true);
		if(!Comparing.equal(actualData, expectedData))
		{
			throw new FileComparisonFailure("File '" + fileToTest + "' content is not equal", expectedData.toString(), actualData.toString(),
					fileToTest);
		}
	}

	public static void doTest(@NotNull String path) throws Throwable
	{
		File file = new File("testData", path);
		if(!file.exists())
		{
			throw new IllegalArgumentException(file.getAbsolutePath() + " is not exists");
		}

		ModuleParser moduleParser = new ModuleParser(file);
		moduleParser.parseNext();

		DotNetArchiveFile archiveFile = new DotNetArchiveFile(file, moduleParser, System.currentTimeMillis());

		File targetFile = new File(file.getParentFile(), FileUtil.getNameWithoutExtension(file.getName()) + ".zip");
		if(!targetFile.exists())
		{
			Main.main(new String[]{file.getAbsolutePath()});
			throw new IllegalArgumentException(targetFile.getAbsolutePath() + " is not exists. File generated");
		}

		ZipFile zipFile = new ZipFile(targetFile);

		Iterator<? extends ArchiveEntry> entries = archiveFile.entries();
		while(entries.hasNext())
		{
			ArchiveEntry next = entries.next();
			if(next.isDirectory())
			{
				continue;
			}

			ZipEntry entry = zipFile.getEntry(next.getName());
			//System.out.println("Testing: " + next.getName());
			if(entry == null)
			{
				throw new FileComparisonFailure("Entry '" + next.getName() + "' in target file is not found",
						FileUtil.loadTextAndClose(archiveFile.getInputStream(next), true), "", next.getName());
			}
			else
			{
				String actual = FileUtil.loadTextAndClose(zipFile.getInputStream(entry), true);
				String expected = FileUtil.loadTextAndClose(archiveFile.getInputStream(next), true);

				if(!actual.equals(expected))
				{
					throw new FileComparisonFailure("File '" + next.getName() + "' content is not equal", actual, expected, next.getName());
				}
			}
		}
	}
}
