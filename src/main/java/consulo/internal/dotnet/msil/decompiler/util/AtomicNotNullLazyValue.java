package consulo.internal.dotnet.msil.decompiler.util;

import javax.annotation.Nonnull;
import java.util.function.Supplier;

/**
 * @author VISTALL
 * @since 14-May-22
 */
public abstract class AtomicNotNullLazyValue<T> implements Supplier<T>
{
	private volatile T myValue;

	@Nonnull
	protected abstract T compute();

	@Override
	public final T get()
	{
		T value = myValue;
		if(value == null)
		{
			synchronized(this)
			{
				value = myValue;
				if(value == null)
				{
					value = compute();

					myValue = value;
				}
			}
		}
		return value;
	}
}
