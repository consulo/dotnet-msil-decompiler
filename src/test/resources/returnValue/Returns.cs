// csc -target:library -deterministic -out:Returns.dll Returns.cs - against the .NET 10 reference assemblies
namespace Lib
{
    public class Returns
    {
        private int myValue;

        public (int Id, string Name) Tuple() => default;

        public ref readonly int RefReadonly(in int value) => ref value;

        public ref int Ref() => ref myValue;

        public ref readonly int Property => ref myValue;

        public dynamic Dynamic() => null;
    }
}
