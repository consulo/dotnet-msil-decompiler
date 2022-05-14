package consulo.internal.dotnet.msil.decompiler.file;

import consulo.internal.dotnet.asm.mbel.ModuleParser;
import consulo.internal.dotnet.msil.decompiler.textBuilder.block.StubBlock;
import consulo.internal.dotnet.msil.decompiler.textBuilder.util.StubBlockUtil;
import consulo.internal.dotnet.msil.decompiler.util.AtomicNotNullLazyValue;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.ref.SimpleReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;

/**
 * @author VISTALL
 * @since 21.03.14
 */
public abstract class DotNetAbstractFileArchiveEntry implements DotNetFileArchiveEntry
{
	private static final Logger LOG = LoggerFactory.getLogger(DotNetAbstractFileArchiveEntry.class);

	private static class LazyValue extends AtomicNotNullLazyValue<byte[]>
	{
		private final DotNetAbstractFileArchiveEntry myEntry;
		private final String myOriginalFilePath;
		private final SimpleReference<ModuleParser> myModuleParserRef;

		public LazyValue(String originalFilePath, SimpleReference<ModuleParser> moduleParserRef, DotNetAbstractFileArchiveEntry entry)
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

				return text.getBytes(StandardCharsets.UTF_8);
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

	private final Supplier<byte[]> myByteArrayValue;

	public DotNetAbstractFileArchiveEntry(String originalFilePath, @Nonnull SimpleReference<ModuleParser> moduleParserRef, String name, long lastModified)
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

	@Nonnull
	@Override
	public String getName()
	{
		return myName;
	}

	@Override
	public long getSize()
	{
		return myByteArrayValue.get().length;
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
		return new ByteArrayInputStream(myByteArrayValue.get());
	}
}
