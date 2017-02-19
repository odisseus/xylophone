package xylophone

import rapture.core.{MethodConstraint, Mode}
import xylophone.Ast.DefaultNamespace

import language.dynamics
import scala.util.{Failure, Success}

case class XmlSeq(elements: Seq[Ast.Node], path: Vector[Ast.Path]) extends Dynamic {
  import Ast._

  def selectDynamic(tag: String)(implicit namespace: Namespace): XmlSeq =
    XmlSeq(elements, path :+ Select(Name(namespace, tag)))

  def apply(index: Int = 0): XmlNode =
    XmlNode(elements, path :+ Index(index))

  def * : XmlSeq = this

  def applyDynamic(name: String)(index: Int = 0): XmlNode =
    XmlNode(elements, path :+ Index(index))

  override def toString(): String =
    $normalize.map(_.string(Set())).mkString("")

  private[xylophone] def $normalize: Seq[Node] = Xml.normalize(elements, path)

}

object XmlSeq extends XmlSeqSerializers{
  
  private[xylophone] val Empty = XmlSeq(Nil, Vector())
  private[xylophone] def apply(element: Ast.Node): XmlSeq = XmlSeq(Seq(element), Vector())
  private[xylophone] def apply(elements: Seq[Ast.Node]): XmlSeq = XmlSeq(elements, Vector())
}



case class XmlNode(elements: Seq[Ast.Node], path: Vector[Ast.Path]) extends Dynamic {
  
  def apply(attribute: Symbol)(implicit namespace: Ast.Namespace): XmlAttribute =
    XmlAttribute(this, Ast.Name(namespace, attribute.name))

  def selectDynamic(tag: String)(implicit namespace: Ast.Namespace): XmlSeq =
    XmlSeq(elements, path :+ Ast.Select(Ast.Name(namespace, tag)))

  def * : XmlSeq = {
    elements.headOption match {
      case Some(Ast.Element(name, _, _)) =>  XmlSeq(elements, path :+ Ast.Select(name))
      case _ =>  XmlSeq(elements, path)
    }
  }

  override def toString(): String = $normalize.map(_.string(Set())).mkString("")

  private[xylophone] def $normalize: Option[Ast.Node] =
    Xml.normalize(elements, path).headOption
}

object XmlNode extends XmlNodeSerializers {

  private[xylophone] val Empty = XmlNode(Nil, Vector())
  private[xylophone] def apply(element: Ast.Node): XmlNode = XmlNode(Seq(element), Vector())
  private[xylophone]  def apply(elements: Seq[Ast.Node]): XmlNode = XmlNode(elements, Vector())

  def apply[T](data: T)(implicit serializer: NodeSerializer[T]): XmlNode =
    serializer.serialize(data)
}


case class XmlAttribute(xmlNode: XmlNode, attributeName: Ast.Name) {
  
  private[xylophone] def $normalize: Option[String] =
    xmlNode.$normalize.to[Seq].flatMap(_.attribute(attributeName)).headOption
}

object Xml {

  private[this] val TopOpenTag = "<self>"
  private[this] val TopClosingTag = "</self>"

  def parse(str: String)(implicit mode: Mode[_ <: MethodConstraint], parser: Parser[String],
      namespace: Ast.Namespace): mode.Wrap[XmlSeq, ParseException] = mode.wrap {

    val wrappedString = wrapXmlByTag(str)

    parser.parse(wrappedString) match {
      case Success(parsedXml) => XmlSeq(parsedXml.self.$normalize, Vector())
      case Failure(_) => mode.exception(ParseException(str))
    }
  }

  def apply[T](data: T)(implicit serializer: XmlSeq.SeqSerializer[T]): XmlSeq =
    serializer.serialize(data)

  private[this] def wrapXmlByTag(str: String): String =
    if (str.trim.startsWith("<?xml"))  str.replace("?>", s"?>$TopOpenTag") + TopClosingTag
    else TopOpenTag + str + TopClosingTag

  private[xylophone] def normalize(elements: Seq[Ast.Node],
                                   path: Vector[Ast.Path]): Seq[Ast.Node] =
    path.foldLeft(elements) {
      case (element, Ast.Select(name)) =>
        element.collect { case element@Ast.Element(`name`, _, _) =>
          element.children
        }.flatten
      
      case (element, Ast.Index( index)) =>
        if (element.length > index) List(element(index)) else Nil
    }

}

object Ast {

  sealed trait Path
  case class Select(name: Name) extends Path
  case class Index(index: Int) extends Path

  object Namespace { val XmlnsNamespace: Namespace = Namespace("xmlns", None) }

  implicit object DefaultNamespace extends Namespace("", None) {
    
    override def prefix(tag: String): String = tag
  }

  case class Namespace(alias: String, namespace: Option[String]) {

    def prefix(tag: String): String = if (alias.trim.isEmpty) tag else s"$alias:$tag"

    def xmlnsAttribute: Option[(Name, String)] = namespace.map { nsString =>
      (Name(Namespace.XmlnsNamespace, alias), nsString)
    }
  }

  case class Name(namespace: Namespace, name: String) {
    override def toString: String = namespace.prefix(name)
  }

  sealed trait Node {
    def attribute(name: Name): Option[String] = None
    def string(namespaces: Set[Namespace]): String
  }

  case class Element(name: Name,
                     attributes: Map[Name, String],
                     children: Seq[Node]) extends Node {

    override def attribute(name: Name): Option[String] = attributes.get(name)

    def string(namespaces: Set[Namespace]): String = {
      
      val allAttributes = if(namespaces contains name.namespace) {
        attributes ++ name.namespace.xmlnsAttribute
      } else attributes

      val attributesString = if(allAttributes.nonEmpty) allAttributes.map {
        case (key, value) => s"""$key="$value""""
      }.mkString(" ", " ", "") else ""

      val content = children.map(_.string(namespaces).mkString("")).mkString("")

      s"<$name$attributesString>$content</$name>"
    }
  }

  case class Text(text: String) extends Node {
    def string(namespaces: Set[Namespace]): String = text
    override def toString: String = string(Set())
  }

  case class ProessingInstruction(target: String, content: String) extends Node {
    def string(namespaces: Set[Namespace]): String = s"<! $target $content !>"
  }

  case class Comment(content: String) extends Node {
    def string(namespaces: Set[Namespace]): String = s"<!--$content-->"
  }

  case class Entity(name: String) extends Node {
    def string(namespaces: Set[Namespace]): String = s"&$name;"
  }
}
