// Consulo API Decompiler stub source generated from bytecode
// Implementation of methods is not available

.class public Lib.Returns extends System.Object
{
	.field private int32 myValue

	.property int32& Property
	{
		.custom System.Runtime.CompilerServices.IsReadOnlyAttribute::.ctor() = 
		(
			01 00 00 00
		)

		.get int32& Lib.Returns::get_Property()
	}

	.method public hidebysig valuetype System.ValueTuple`2<int32, string> Tuple()
	{
		.param [0]

		.custom System.Runtime.CompilerServices.TupleElementNamesAttribute::.ctor(string[]) = 
		(
			01 00 02 00 00 00 02 49 64 04 4E 61 6D 65 00 00
		)
	}

	.method public hidebysig int32& RefReadonly([in] int32& 'value')
	{
		.param [0]

		.custom System.Runtime.CompilerServices.IsReadOnlyAttribute::.ctor() = 
		(
			01 00 00 00
		)

		.param [1]

		.custom System.Runtime.CompilerServices.IsReadOnlyAttribute::.ctor() = 
		(
			01 00 00 00
		)
	}

	.method public hidebysig int32& Ref()
	{
	}

	.method public hidebysig specialname int32& get_Property()
	{
		.param [0]

		.custom System.Runtime.CompilerServices.IsReadOnlyAttribute::.ctor() = 
		(
			01 00 00 00
		)
	}

	.method public hidebysig object Dynamic()
	{
		.param [0]

		.custom System.Runtime.CompilerServices.DynamicAttribute::.ctor() = 
		(
			01 00 00 00
		)
	}

	.method public hidebysig specialname rtspecialname void .ctor()
	{
	}
}
