package org.mustbe.dotnet.msil.decompiler.file;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.List;

import org.consulo.lombok.annotations.Logger;
import org.jetbrains.annotations.NotNull;
import org.mustbe.dotnet.msil.decompiler.textBuilder.block.StubBlock;
import org.mustbe.dotnet.msil.decompiler.textBuilder.util.StubBlockUtil;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.ArrayUtil;
import edu.arizona.cs.mbel.mbel.ModuleParser;

/**
 * @author VISTALL
 * @since 21.03.14
 */
@Logger
public abstract class DotNetAbstractFileArchiveEntry implements DotNetFileArchiveEntry
{
	private static class LazyValue extends NotNullLazyValue<byte[]>
	{
		private final DotNetAbstractFileArchiveEntry myEntry;
		private final File myOriginalFile;
		private final Ref<ModuleParser> myModuleParserRef;

		public LazyValue(File originalFile, Ref<ModuleParser> moduleParserRef, DotNetAbstractFileArchiveEntry entry)
		{
			myOriginalFile = originalFile;
			myModuleParserRef = moduleParserRef;
			myEntry = entry;
		}

		@NotNull
		@Override
		protected byte[] compute()
		{
			try
			{
				ModuleParser moduleParser = myModuleParserRef.get();

				try
				{
					if(moduleParser != null)
					{
						moduleParser.parseNext();
						myModuleParserRef.set(null);
					}
				}
				catch(Throwable e)
				{
					LOGGER.error("File '" + myOriginalFile.getPath() + "' cant decompiled correctly please create issue with this file", e);
					return ArrayUtil.EMPTY_BYTE_ARRAY;
				}

				List<? extends StubBlock> builder = myEntry.build();

				CharSequence charSequence = StubBlockUtil.buildText(builder);

				String text = charSequence.toString();

				return text.getBytes(CharsetToolkit.UTF8_CHARSET);
			}
			catch(Throwable e)
			{
				LOGGER.error("File '" + myOriginalFile.getPath() + "' cant decompiled correctly please create issue with this file", e);
				return ArrayUtil.EMPTY_BYTE_ARRAY;
			}
		}
	}

	private final String myName;
	private final long myLastModified;

	private final NotNullLazyValue<byte[]> myByteArrayValue;

	public DotNetAbstractFileArchiveEntry(File originalFile, @NotNull Ref<ModuleParser> moduleParserRef, String name, long lastModified)
	{
		myName = name;
		myLastModified = lastModified;
		myByteArrayValue = new LazyValue(originalFile, moduleParserRef, this);
	}

	@NotNull
	public abstract List<? extends StubBlock> build();

	@Override
	public String getName()
	{
		return myName;
	}

	@Override
	public long getSize()
	{
		return myByteArrayValue.getValue().length;
	}

	@Override
	public long getTime()
	{
		return myLastModified;
	}

	@Override
	public boolean isDirectory()
	{
		return false;
	}

	@Override
	@NotNull
	public String getNamespace()
	{
		return "";
	}

	@Override
	@NotNull
	public InputStream createInputStream()
	{
		return new ByteArrayInputStream(myByteArrayValue.getValue());
	}
}
