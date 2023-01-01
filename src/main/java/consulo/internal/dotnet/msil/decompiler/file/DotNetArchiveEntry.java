package consulo.internal.dotnet.msil.decompiler.file;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 13-May-22
 */
public interface DotNetArchiveEntry
{
	@Nonnull
	String getName();

	long getSize();

	boolean isDirectory();

	long getTime();
}
