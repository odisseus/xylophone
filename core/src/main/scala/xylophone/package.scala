import contextual._

package object xylophone extends XmlSeqSerializers with XmlNodeSerializers {
  
  implicit val implicitXmlStringParser: StdLibXmlStringParser.type = StdLibXmlStringParser

  /** implicit class providing the `xml` prefix for interpolated strings */
  implicit class XmlStringContext(stringContext: StringContext) {

    /** the `xml` prefix for interpolated strings */
    val xml = Prefix(XmlInterpolator, stringContext)
  }
}
