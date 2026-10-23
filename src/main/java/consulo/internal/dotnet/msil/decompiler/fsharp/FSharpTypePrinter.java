/*
 * Copyright 2013-2026 consulo.io
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

package consulo.internal.dotnet.msil.decompiler.fsharp;

import consulo.internal.dotnet.msil.decompiler.fsharp.FSharpSignature.*;

import java.util.List;
import java.util.StringJoiner;

/**
 * F# syntax of signature data types: {@code 'T list -> int}, {@code ^T * ^T -> ^T}, {@code float<m/s>}.
 *
 * @author VISTALL
 * @since 2026-10-25
 */
public final class FSharpTypePrinter
{
	private FSharpTypePrinter()
	{
	}

	public static String print(Type type)
	{
		StringBuilder builder = new StringBuilder();
		print(builder, type, 0);
		return builder.toString();
	}

	// precedence: 0 - function, 1 - tuple, 2 - application argument
	private static void print(StringBuilder builder, Type type, int precedence)
	{
		if(type instanceof ForallType forall)
		{
			print(builder, forall.body, precedence);
		}
		else if(type instanceof FunType fun)
		{
			if(precedence > 0)
			{
				builder.append('(');
			}
			print(builder, fun.domain, 1);
			builder.append(" -> ");
			print(builder, fun.range, 0);
			if(precedence > 0)
			{
				builder.append(')');
			}
		}
		else if(type instanceof TupleType tuple)
		{
			if(tuple.isStruct)
			{
				builder.append("struct ");
			}
			boolean parens = precedence > 1 || tuple.isStruct;
			if(parens)
			{
				builder.append('(');
			}
			for(int i = 0; i < tuple.elements.size(); i++)
			{
				if(i > 0)
				{
					builder.append(" * ");
				}
				print(builder, tuple.elements.get(i), 2);
			}
			if(parens)
			{
				builder.append(')');
			}
		}
		else if(type instanceof AppType app)
		{
			printApp(builder, app.tcref.getName(), app.arguments);
		}
		else if(type instanceof VarType var)
		{
			builder.append(var.typar);
		}
		else if(type instanceof MeasureType measure)
		{
			printMeasure(builder, measure.measure);
		}
		else if(type instanceof UnionCaseType unionCase)
		{
			printApp(builder, unionCase.unionType.getName(), unionCase.arguments);
		}
		else if(type instanceof AnonRecordType record)
		{
			builder.append(record.isStruct ? "struct {| " : "{| ");
			StringJoiner joiner = new StringJoiner("; ");
			for(int i = 0; i < record.fieldNames.size(); i++)
			{
				joiner.add(record.fieldNames.get(i) + ": " + (i < record.fieldTypes.size() ? print(record.fieldTypes.get(i)) : "?"));
			}
			builder.append(joiner).append(" |}");
		}
		else
		{
			builder.append('?');
		}
	}

	/**
	 * {@code list`1} -> {@code list}
	 */
	public static String demangleGenericName(String name)
	{
		int tick = name.lastIndexOf('`');
		if(tick > 0 && tick < name.length() - 1)
		{
			for(int i = tick + 1; i < name.length(); i++)
			{
				if(!Character.isDigit(name.charAt(i)))
				{
					return name;
				}
			}
			return name.substring(0, tick);
		}
		return name;
	}

	private static void printApp(StringBuilder builder, String name, List<Type> arguments)
	{
		builder.append(demangleGenericName(name));
		if(arguments.isEmpty())
		{
			return;
		}
		builder.append('<');
		for(int i = 0; i < arguments.size(); i++)
		{
			if(i > 0)
			{
				builder.append(", ");
			}
			print(builder, arguments.get(i), 0);
		}
		builder.append('>');
	}

	private static void printMeasure(StringBuilder builder, Measure measure)
	{
		if(measure instanceof ConstMeasure constant)
		{
			builder.append(constant.tcref.getName());
		}
		else if(measure instanceof VarMeasure var)
		{
			builder.append(var.typar);
		}
		else if(measure instanceof InvMeasure inv)
		{
			builder.append("/");
			printMeasure(builder, inv.measure);
		}
		else if(measure instanceof ProdMeasure prod)
		{
			printMeasure(builder, prod.left);
			builder.append(' ');
			printMeasure(builder, prod.right);
		}
		else if(measure instanceof PowerMeasure power)
		{
			printMeasure(builder, power.measure);
			builder.append('^');
			if(power.denominator == 1)
			{
				builder.append(power.numerator);
			}
			else
			{
				builder.append('(').append(power.numerator).append('/').append(power.denominator).append(')');
			}
		}
		else
		{
			builder.append('1');
		}
	}
}
