package everstore.serialization.json4s

import java.io.StringWriter
import java.util

import everstore.api.serialization.Serializer
import org.json4s._
import org.json4s.jackson.JsonMethods
import org.json4s.jackson.Serialization
import org.json4s.reflect.Reflector

import scala.collection.JavaConversions._

/**
 * Serialize and deserialize scala classes using a slight modified version of Json4s to support "Any" types
 */
class Json4sSerializer extends Serializer {

  // Class loader used for serialization
  private val classLoader = getClass.getClassLoader

  // Formatter used when serializing objects
  private val formats = Serialization.formats(NoTypeHints)

  override def convertToString[T](o: T): String = {
    val json = Extraction.decomposeWithBuilder(o, JsonWriter.streaming(new StringWriter))(formats).toString

    o.getClass.getName + " " + json
  }

  override def convertFromString(s: String): AnyRef = {
    val idx = s.indexOf(' ')
    require(idx != -1, "Incoming data do not fit the required format [className] [data]")

    val className = s.substring(0, idx)
    val json = s.substring(idx + 1)

    val classType = classLoader.loadClass(className)
    val jValue = JsonMethods.parse(json, formats.wantsBigDecimal, formats.wantsBigInt)
    val instance = extract(jValue, classType)

    instance.asInstanceOf[AnyRef]
  }

  private def extract(js: JValue, classType: Class[_]) = {
    try {
      Extraction.extract(js, Reflector.scalaTypeOf(classType))(formats)
    } catch {
      case e: MappingException => throw e
      case e: Exception =>
        throw new MappingException("unknown error", e)
    }
  }

  override def convertToTypes[T](o: T): util.Set[String] = {
    val s = o.getClass.getInterfaces.filterNot(i => i.equals(classOf[scala.Product]) || i.equals(classOf[Serializable]))
    new util.HashSet[String](s.map(_.getSimpleName).toSet)
  }
}
