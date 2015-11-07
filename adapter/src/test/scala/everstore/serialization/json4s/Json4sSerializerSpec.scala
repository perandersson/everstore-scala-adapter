package everstore.serialization.json4s

import everstore.DefaultSpec

case class SimpleClass(value: String)

case class ComplexClass(inner: SimpleClass, value: Int)

case class ClassWithDefaultValues(stringVal: String, intVal: Int) {
  def this(stringVal: String) = this(stringVal, 12345)
}

class Json4sSerializerSpec extends DefaultSpec {

  val serializer = new Json4sSerializer()

  "Json4sSerializer" should "be able to serialize and deserialize objects"

  it should "be able to serialize a simple case class" in {
    val event = SimpleClass("test")
    val serializedEvent = serializer.convertToString(event)

    assert(serializedEvent == "everstore.serialization.json4s.SimpleClass {\"value\":\"test\"}")
  }

  it should "be able to deserialize a simple case class" in {
    val serializedEvent = "everstore.serialization.json4s.SimpleClass {\"value\":\"test\"}"
    val event = serializer.convertFromString(serializedEvent)

    assert(event == SimpleClass("test"))
  }

  it should "be able to serialize a complex case class" in {
    val event = ComplexClass(SimpleClass("test"), 12345)
    val serializedEvent = serializer.convertToString(event)

    assert(serializedEvent == "everstore.serialization.json4s.ComplexClass {\"inner\":{\"value\":\"test\"},\"value\":12345}")
  }

  it should "be able to deserialize a complex case class" in {
    val serializedEvent = "everstore.serialization.json4s.ComplexClass {\"inner\":{\"value\":\"test\"},\"value\":12345}"
    val event = serializer.convertFromString(serializedEvent)

    assert(event == ComplexClass(SimpleClass("test"), 12345))
  }

  it should "be able to deserialize an incomplete case class with default values" in {
    val serializedEvent = "everstore.serialization.json4s.ClassWithDefaultValues {\"stringVal\":\"str\"}"
    val event = serializer.convertFromString(serializedEvent)

    assert(event == ClassWithDefaultValues("str", 12345))
  }
}
