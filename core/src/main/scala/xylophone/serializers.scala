package xylophone

import xylophone.Ast.{Element, Name, Namespace, Text}

import scala.annotation.implicitNotFound
import scala.collection.immutable.ListMap
import scala.language.higherKinds
import scala.reflect.ClassTag

@implicitNotFound("Cannot find WrapTag typeclass for ${T}")
case class WrapTag[T](name: String)

@implicitNotFound("Cannot find SeqTag typeclass for ${T}")
case class SeqTag[T](name: String)

trait XmlSeqSerializers {

  @implicitNotFound("Cannot serialize type ${T} to XmlSeq. Please provide an implicit "+
      "Serializer of type ${T} to XmlSeq")
  trait SeqSerializer[T] { seqSerializer =>
    
    def serialize(t: T): XmlSeq

    def contramap[T2](fn: T2 => T): SeqSerializer[T2] = t => seqSerializer.serialize(fn(t))
  }

  object SeqSerializer {
    
    def fromMap(map: ListMap[String, XmlSeq])(implicit namespace: Namespace): XmlSeq =
      map.foldLeft(XmlSeq.Empty) {
        case (xml, (k, v: XmlSeq)) =>
          XmlSeq(xml.elements :+ Element(Name(namespace, k), Map(), v.elements))
      }
  }

  implicit def defaultSeqTag[T: ClassTag, F[_]]: SeqTag[F[T]] =
    SeqTag(implicitly[ClassTag[T]].runtimeClass.getSimpleName.toLowerCase())
  
  implicit def byteSerializer: SeqSerializer[Byte] = x => XmlSeq(Text(x.toString))
  implicit def shortSerializer: SeqSerializer[Short] = x => XmlSeq(Text(x.toString))
  implicit def intSerializer: SeqSerializer[Int] = x => XmlSeq(Text(x.toString))
  implicit def longSerializer: SeqSerializer[Long] = x => XmlSeq(Text(x.toString))
  implicit def booleanSerializer: SeqSerializer[Boolean] = x => XmlSeq(Text(x.toString))
  implicit def stringSerializer: SeqSerializer[String] = x => XmlSeq(Text(x))
  implicit def floatSerializer: SeqSerializer[Float] = x => XmlSeq(Text(x.toString))
  implicit def doubleSerializer: SeqSerializer[Double] = x => XmlSeq(Text(x.toString))
  implicit def bigDecimalSerializer: SeqSerializer[BigDecimal] = x => XmlSeq(Text(x.toString))
  implicit def bigIntSerializer: SeqSerializer[BigInt] = x => XmlSeq(Text(x.toString))
  implicit def nilSerializer: SeqSerializer[Nil.type] = XmlSeq(_)
  
  implicit def optionSerializer[T](implicit ser: SeqSerializer[T]): SeqSerializer[Option[T]] =
    _.map(ser.serialize).getOrElse(XmlSeq(Text("")))

  implicit def traversableSerializer[Type, S[T] <: Traversable[T]](
      implicit seqSerializer: SeqSerializer[Type],
               tag: SeqTag[S[Type]],
               namespace: Namespace): SeqSerializer[S[Type]] =
    _.foldLeft[XmlSeq](XmlSeq.Empty) { (xml, el) =>
      val elements = seqSerializer.serialize(el).elements
      val node = Element(Name(namespace, tag.name), Map(), elements)
      XmlSeq(xml.elements :+ node)
    }
}

trait XmlNodeSerializers {

  @implicitNotFound("Cannot serialize type ${T} to XmlNode. Please provide an implicit "+
      "Serializer of type ${T} to XmlNode")
  trait NodeSerializer[T] { nodeSerializer =>
    
    def serialize(t: T): XmlNode

    def contramap[T2](fn: T2 => T): NodeSerializer[T2] =
      t => nodeSerializer.serialize(fn(t))
  }

  implicit def seqToNode[T](implicit seqSerializer: XmlSeq.SeqSerializer[T],
                                     wrapTag: WrapTag[T],
                                     namespace: Namespace): NodeSerializer[T] = { (t: T) =>

    val children = seqSerializer.serialize(t).elements
    XmlNode(Seq(Element(Name(namespace, wrapTag.name), Map(), children)), Vector())
  }

}
