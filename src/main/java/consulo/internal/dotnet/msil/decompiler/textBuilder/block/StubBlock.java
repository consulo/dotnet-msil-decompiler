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

package consulo.internal.dotnet.msil.decompiler.textBuilder.block;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.util.SmartList;

/**
 * @author VISTALL
 * @since 12.12.13.
 */
public class StubBlock
{
	public static final char[] BRACES = {
			'{',
			'}'
	};

	public static final char[] PAR = {
			'(',
			')'
	};

	private CharSequence myStartText;
	private CharSequence myInnerText;
	private char[] myIndents;

	private List<StubBlock> myBlocks = new SmartList<StubBlock>();

	public StubBlock(@NotNull CharSequence startText, @Nullable CharSequence innerText, @NotNull char[] indents)
	{
		myStartText = startText;
		myInnerText = innerText;
		myIndents = indents;
	}

	@NotNull
	public List<StubBlock> getBlocks()
	{
		return myBlocks;
	}

	@NotNull
	public char[] getIndents()
	{
		return myIndents;
	}

	@NotNull
	public CharSequence getStartText()
	{
		return myStartText;
	}

	@Nullable
	public CharSequence getInnerText()
	{
		return myInnerText;
	}
}
