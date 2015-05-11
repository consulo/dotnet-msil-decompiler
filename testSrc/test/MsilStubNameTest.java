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

import org.junit.Assert;
import org.junit.Test;
import org.mustbe.dotnet.msil.decompiler.textBuilder.MsilStubBuilder;

/**
 * @author VISTALL
 * @since 20.07.14
 */
public class MsilStubNameTest extends Assert
{
	@Test
	public void testNumberAsName()
	{
		StringBuilder builder = new StringBuilder();
		MsilStubBuilder.appendValidName(builder, "1");

		assertEquals(builder.toString(), "\'1\'");
	}

	@Test
	public void testNumberInNameAnd()
	{
		StringBuilder builder = new StringBuilder();
		MsilStubBuilder.appendValidName(builder, "sun.net.www.protocol.http.BasicAuthentication/1");

		assertEquals(builder.toString(), "\'sun.net.www.protocol.http.BasicAuthentication/1\'");
	}
}
