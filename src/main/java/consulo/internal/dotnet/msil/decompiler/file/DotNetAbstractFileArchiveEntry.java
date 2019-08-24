package consulo.internal.dotnet.msil.decompiler.file;

import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.ArrayUtil;
import consulo.internal.dotnet.asm.mbel.ModuleParser;
import consulo.internal.dotnet.msil.decompiler.textBuilder.block.StubBlock;
import consulo.internal.dotnet.msil.decompiler.textBuilder.util.StubBlockUtil;
import consulo.logging.Logger;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.List;

/**
 * @author VISTALL
 * @since 21.03.14
 */
public abstract class DotNetAbstractFileArchiveEntry implements DotNetFileArchiveEntry
{
	private static final Logger LOG = Logger.getInstance(DotNetAbstractFileArchiveEntry.class);

	private static class LazyValue extends AtomicNotNullLazyValue<byte[]>
	{
		private final DotNetAbstractFileArchiveEntry myEntry;
		private final String myOriginalFilePath;
		private final Ref<ModuleParser> myModuleParserRef;

		public LazyValue(String originalFilePath, Ref<ModuleParser> moduleParserRef, DotNetAbstractFileArchiveEntry entry)
		{
			myOriginalFilePath = originalFilePath;
			myModuleParserRef = moduleParserRef;
			myEntry = entry;
		}

		@Nonnull
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
						myModuleParserRef.set(null);
					}
				}
				catch(Throwable e)
				{
					LOG.error("File '" + new File(myOriginalFilePath).getName() + "' cant decompiled correctly please create issue with this file", e);
					return ArrayUtil.EMPTY_BYTE_ARRAY;
				}

				List<? extends StubBlock> builder = myEntry.build();

				CharSequence charSequence = StubBlockUtil.buildText(builder);

				String text = charSequence.toString();

				return text.getBytes(CharsetToolkit.UTF8_CHARSET);
			}
			catch(Throwable e)
			{
				LOG.error("File '" + new File(myOriginalFilePath).getName() + "' cant decompiled correctly please create issue with this file", e);
				return ArrayUtil.EMPTY_BYTE_ARRAY;
			}
			finally
			{
				myEntry.drop();
			}
		}
	}

	private final String myName;
	private final long myLastModified;

	private final NotNullLazyValue<byte[]> myByteArrayValue;

	public DotNetAbstractFileArchiveEntry(String originalFilePath, @Nonnull Ref<ModuleParser> moduleParserRef, String name, long lastModified)
	{
		myName = name;
		myLastModified = lastModified;
		myByteArrayValue = new LazyValue(originalFilePath, moduleParserRef, this);
	}

	@Nonnull
	public abstract List<? extends StubBlock> build();

	public void drop()
	{
	}

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
	@Nonnull
	public String getNamespace()
	{
		return "";
	}

	@Override
	@Nonnull
	public InputStream createInputStream()
	{
		return new ByteArrayInputStream(myByteArrayValue.getValue());
	}
}
