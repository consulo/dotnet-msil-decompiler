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

package consulo.internal.dotnet.msil.decompiler.textBuilder.util;

import consulo.internal.dotnet.asm.io.ByteBuffer;
import consulo.internal.dotnet.asm.signature.Signature;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * @author VISTALL
 * @since 13.12.13.
 */
public class XStubUtil
{
	public static final Charset STRING_CHARSET = StandardCharsets.UTF_16LE;

	public static final String CONSTRUCTOR_NAME = ".ctor";
	public static final String STATIC_CONSTRUCTOR_NAME = ".cctor";

	private static final char[] ILLEGAL_CHARS = new char[] {'{', '}', '<', '>', '=', '\\', '/'};

	public static boolean isSet(long value, int mod)
	{
		return (value & mod) == mod;
	}

	public static boolean isSet(long value, int mod, int v)
	{
		return (value & mod) == v;
	}

	@Nonnull
	public static CharSequence getString(@Nonnull ByteBuffer byteBuffer, @Nonnull Charset charset)
	{
		int b = byteBuffer.get() & 0xFF;
		if(b == 0xFF)
		{
			return "";
		}
		else
		{
			byteBuffer.back();
			int size = Signature.readCodedInteger(byteBuffer);
			if(size == 0)
			{
				return "";
			}
			else
			{
				return escapeChars(new String(byteBuffer.get(size), charset));
			}
		}
	}

	@Nonnull
	public static CharSequence escapeChars(@Nonnull CharSequence charSequence)
	{
		final int length = charSequence.length();
		StringBuilder builder = new StringBuilder(length);
		for(int i = 0; i < length; i++)
		{
			char aChar = charSequence.charAt(i);
			builder.append(escapeChar(aChar));
		}
		return builder.capacity() == length ? charSequence : builder;
	}

	@Nonnull
	public static Object escapeChar(char a)
	{
		switch(a)
		{
			case '\n':
				return "\\n";
			case '\r':
				return "\\r";
			case '\t':
				return "\\t";
			case '\f':
				return "\\f";
			case '\'':
				return "\\\'";
			case '"':
				return "\\\"";
			case '\\':
				return "\\\\";
		}
		return a;
	}

	public static boolean isInvisibleMember(String name)
	{
		for(char illegalChar : ILLEGAL_CHARS)
		{
			if(StringUtil.containsChar(name, illegalChar))
			{
				return true;
			}
		}
		return false;
	}
}
