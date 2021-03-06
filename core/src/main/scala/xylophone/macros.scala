package xylophone

import macrocompat.bundle

import scala.reflect.macros._

@bundle
private class XmlMacros(val c: whitebox.Context) {

  import c.universe._

  def serializerWrapperMacro[T](implicit T: c.WeakTypeTag[T]): c.Expr[SeqSerializerWrapper[T]] = {
    def generateSerializerForCaseClass(serializer: c.universe.Type): c.Expr[SeqSerializerWrapper[T]] = {
      val decls = getClassFields(weakTypeOf[T]).map(field => findAndCollectSerializerForField(serializer, field)(T))
      c.Expr[SeqSerializerWrapper[T]](
        q"""
         new _root_.xylophone.SeqSerializerWrapper[${weakTypeOf[T]}] {
            def serializer: SeqSerializer[${weakTypeOf[T]}] = new _root_.xylophone.SeqSerializer[${weakTypeOf[T]}] {
              def serialize(obj: ${weakTypeOf[T]}): XmlSeq =
                    _root_.xylophone.SeqSerializer.fromMap(_root_.scala.collection.immutable.ListMap(${decls.toSeq: _*}))
              }
         }
       """)
    }

    val tpe = weakTypeOf[T].typeSymbol.asClass
    val serializer = typeOf[SeqSerializer[_]].typeSymbol.asType.toTypeConstructor
    if (tpe.isCaseClass) generateSerializerForCaseClass(serializer)
    else throw new Exception()
  }

  private def findAndCollectSerializerForField[T: c.WeakTypeTag](serializer: c.universe.Type, param: c.universe.MethodSymbol) = {
    val implSerializer = c.inferImplicitValue(appliedType(serializer, param.returnType), silent = false, withMacrosDisabled = false)
    q"""(${param.name.toString}, $implSerializer.serialize(obj.${param.name}))"""
  }

  private def getClassFields(t: c.universe.Type): Iterable[c.universe.MethodSymbol] = {
    t.decls.collect { case m: MethodSymbol if m.isCaseAccessor => m.asMethod }
  }

}
