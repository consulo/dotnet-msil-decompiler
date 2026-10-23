package test;

import consulo.internal.dotnet.asm.mbel.ModuleParser;
import consulo.internal.dotnet.msil.decompiler.fsharp.FSharpSignature;
import consulo.internal.dotnet.msil.decompiler.fsharp.FSharpSignatureReader;
import consulo.internal.dotnet.msil.decompiler.fsharp.FSharpTypePrinter;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the signature data of FSharp.Core: from the system property {@code fsharp.core}, or a local .NET SDK / NuGet cache.
 *
 * @author VISTALL
 * @since 2026-10-25
 */
public class FSharpSignatureTest extends Assert
{
	private static File findFSharpCore()
	{
		List<String> candidates = new ArrayList<>();
		String property = System.getProperty("fsharp.core");
		if(property != null)
		{
			candidates.add(property);
		}
		String home = System.getProperty("user.home");
		File nuget = new File(home, ".nuget/packages/fsharp.core");
		File[] versions = nuget.listFiles();
		if(versions != null)
		{
			for(File version : versions)
			{
				candidates.add(new File(version, "lib/netstandard2.1/FSharp.Core.dll").getPath());
			}
		}
		File sdks = new File("/usr/share/dotnet/sdk");
		File[] sdkVersions = sdks.listFiles();
		if(sdkVersions != null)
		{
			for(File sdk : sdkVersions)
			{
				candidates.add(new File(sdk, "FSharp/FSharp.Core.dll").getPath());
			}
		}
		for(String candidate : candidates)
		{
			File file = new File(candidate);
			if(file.isFile())
			{
				return file;
			}
		}
		return null;
	}

	@Test
	public void testFSharpCore() throws Throwable
	{
		File file = findFSharpCore();
		Assume.assumeNotNull(file);

		FSharpSignature signature = FSharpSignatureReader.read(new ModuleParser(file));
		assertNotNull("no signature data in " + file, signature);

		FSharpSignature.Entity collections = find(signature.root, "Microsoft", "FSharp", "Collections");
		assertTrue(collections.isNamespace());

		FSharpSignature.Entity listModule = find(collections, "ListModule");
		assertTrue(listModule.isModule());
		// 'List' in F#: the module shares its name with the List<'T> type, so it is compiled with a Module suffix
		assertEquals(FSharpSignature.ModuleKind.MODULE_WITH_SUFFIX, listModule.moduleKind);

		FSharpSignature.Val map = null;
		for(FSharpSignature.Val val : listModule.vals)
		{
			if(val.logicalName.equals("map"))
			{
				map = val;
			}
		}
		assertNotNull("List.map", map);
		assertEquals("Map", map.getCompiledName());
		assertEquals("('T -> 'U) -> list<'T> -> list<'U>", FSharpTypePrinter.print(map.type));
		assertEquals(2, map.reprInfo.args.size());
		assertEquals("mapping", map.reprInfo.args.get(0).get(0).name);

		FSharpSignature.Entity list = find(collections, "List`1");
		assertEquals(FSharpSignature.TyconKind.UNION, list.repr.kind);
		assertEquals(2, list.repr.cases.size());

		FSharpSignature.Entity operators = find(signature.root, "Microsoft", "FSharp", "Core", "Operators");
		FSharpSignature.Val plus = null;
		for(FSharpSignature.Val val : operators.vals)
		{
			if(val.logicalName.equals("op_Addition"))
			{
				plus = val;
			}
		}
		assertNotNull("(+)", plus);
		assertTrue(plus.isInline());
		assertEquals("^T1 -> ^T2 -> ^T3", FSharpTypePrinter.print(plus.type));
	}

	private static FSharpSignature.Entity find(FSharpSignature.Entity entity, String... path)
	{
		FSharpSignature.Entity current = entity;
		for(String name : path)
		{
			FSharpSignature.Entity next = null;
			for(FSharpSignature.Entity child : current.entities)
			{
				if(child.logicalName.equals(name))
				{
					next = child;
				}
			}
			assertNotNull(name + " in " + current.logicalName + ": " + current.entities, next);
			current = next;
		}
		return current;
	}
}
