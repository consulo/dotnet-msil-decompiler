/**
 * @author VISTALL
 * @since 09-May-22
 */
module consulo.internal.dotnet.msil.decompiler {
  requires consulo.annotation;
  requires org.slf4j;
  requires consulo.internal.dotnet.asm;
  requires consulo.util.collection;

  exports consulo.internal.dotnet.msil.decompiler;
  exports consulo.internal.dotnet.msil.decompiler.file;
  exports consulo.internal.dotnet.msil.decompiler.textBuilder;
  exports consulo.internal.dotnet.msil.decompiler.textBuilder.block;
  exports consulo.internal.dotnet.msil.decompiler.textBuilder.util;
  exports consulo.internal.dotnet.msil.decompiler.util;
}