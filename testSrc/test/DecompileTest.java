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
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.Assert;
import org.junit.Test;
import org.mustbe.dotnet.msil.decompiler.file.DotNetArchiveFile;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.ArchiveEntry;
import com.intellij.util.FileComparisonFailure;
import edu.arizona.cs.mbel.mbel.ModuleParser;

/**
 * @author VISTALL
 * @since 11.05.2015
 */
public class DecompileTest extends Assert
{
	@Test
	public void testMscorlib() throws Throwable
	{
		doTest("test1/mscorlib.dll");
	}

	public static void doTest(String path) throws Throwable
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
			throw new IllegalArgumentException(targetFile.getAbsolutePath() + " is not exists");
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
					throw new FileComparisonFailure("File '" + next.getName() + "' content is not equal", expected, actual, next.getName());
				}
			}
		}
	}
}
