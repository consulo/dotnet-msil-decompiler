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

package consulo.internal.dotnet.msil.decompiler;

import consulo.internal.dotnet.asm.mbel.ModuleParser;
import consulo.internal.dotnet.msil.decompiler.file.DotNetArchiveEntry;
import consulo.internal.dotnet.msil.decompiler.file.DotNetArchiveFile;
import consulo.util.lang.StringUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author VISTALL
 * @since 11.05.2015
 */
public class Main
{
	public static void main(String[] args) throws Exception
	{
		if(args.length != 1)
		{
			System.out.println("Use '<target-file>'");
		}

		File file = new File(args[0]);
		ModuleParser moduleParser = new ModuleParser(file);

		DotNetArchiveFile archiveFile = new DotNetArchiveFile(file, moduleParser, System.currentTimeMillis());

		File outFile = new File(file.getParentFile(), getNameWithoutExtension(file.getName()) + ".zip");


		FileOutputStream fileOutputStream = new FileOutputStream(outFile);
		ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream);
		Iterator<? extends DotNetArchiveEntry> entries = archiveFile.entries().iterator();
		while(entries.hasNext())
		{
			DotNetArchiveEntry next = entries.next();

			ZipEntry zipEntry = new ZipEntry(next.getName());
			zipOutputStream.putNextEntry(zipEntry);

			InputStream inputStream = archiveFile.getInputStream(next);

			byte[] bytes = new byte[1024];
			int length;
			while((length = inputStream.read(bytes)) >= 0)
			{
				zipOutputStream.write(bytes, 0, length);
			}

			zipOutputStream.closeEntry();
			inputStream.close();
		}

		zipOutputStream.close();
		fileOutputStream.close();
	}

	public static String getNameWithoutExtension(String name)
	{
		int i = StringUtil.lastIndexOf(name, '.', 0, name.length());
		return i < 0 ? name : name.substring(0, i);
	}
}
