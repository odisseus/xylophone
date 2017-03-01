package xylophone.test

import rapture.test.TestSuite
import xylophone._

import scala.collection.immutable.ListMap

object JoiningTests extends TestSuite {

  val seq: XmlSeq = xml"<user><name>Fred</name><age>5</age></user>"
  val node: XmlNode = xml"<company><name>Propensive Ltd</name></company>".apply()

  val `Joining two nodes` = test {
    (node ~ node).toString
  }.returns(node.toString+node.toString)

  val `Joining two sequences` = test {
    (seq ~~ seq).toString
  }.returns(seq.toString+seq.toString)

  val `Joining a node to a sequence` = test {
    (seq :~ node).toString
  }.returns(seq.toString+node.toString)
  
  val `Joining a sequence to a node` = test {
    (node ~: seq).toString
  }.returns(node.toString+seq.toString)
  
  val `Joining a sequence to a dereferenced node` = test {
    (node.name() ~: seq).toString
  }.returns(node.name().toString+seq.toString)
  
  val `Joining a dereferenced sequence to a node` = test {
    (node ~ seq.user.age()).toString
  }.returns(node.toString+seq.user.age().toString)
  
  val `Joining two dereferenced sequences` = test {
    (node.name ~~ seq.user.age).toString
  }.returns(node.name.toString+seq.user.age.toString)
}

