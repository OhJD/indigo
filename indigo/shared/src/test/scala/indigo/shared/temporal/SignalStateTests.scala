package indigo.shared.temporal

import indigo.shared.time.Seconds

class SignalStateTests extends munit.FunSuite {

  test("contruction methods and value retrieval") {
    // Normal
    assertEquals(
      SignalState((name: String) => Signal(t => (name, s"name was '$name' and time was '${t.toLong}'")))
        .toSignal("Fred")
        .at(Seconds(10)),
      "name was 'Fred' and time was '10'"
    )

    // Quick creation from a value
    assertEquals(
      SignalState.fixed[Unit, Int](100).toSignal(()).at(Seconds.zero),
      100
    )

    // When time isn't of interest
    assertEquals(
      SignalState.fromSignal[Unit, Int](Signal.fixed(10)).toSignal(()).at(Seconds.zero),
      10
    )
  }

  test("map") {
    val signal =
      SignalState((name: String) => Signal.fixed((name, s"name: $name")))

    assertEquals(
      signal.toSignal("Fred").at(Seconds.zero),
      "name: Fred"
    )

    assertEquals(
      signal.map(_ + " Smith").toSignal("Fred").at(Seconds.zero),
      "name: Fred Smith"
    )

    assertEquals(
      signal.map(_ + " Smith").map(_ => "Bob").toSignal("Fred").at(Seconds.zero),
      "Bob"
    )

  }

  test("flatMap") {

    val res =
      SignalState((count: Int) => Signal.fixed((count + 1, "foo"))).flatMap { (str: String) =>
        SignalState((c: Int) => Signal.fixed((c + 1, str + str)))
      }

    val (s, v) = res.run(0).at(Seconds(0))

    assertEquals(s, 2)
    assertEquals(v, "foofoo")

  }

  test("for comp") {
    val a = SignalState((count: Int) => Signal.fixed(count + 1, "foo"))
    val b = SignalState((count: Int) => Signal.fixed(count + 1, "bar"))
    val c = SignalState((count: Int) => Signal.fixed(count + 1, "baz"))

    val res =
      for {
        aa <- a
        bb <- b.map(_ + s"($aa)")
        cc <- c.map(_ + s"($aa)[$bb]")
      } yield aa + ", " + bb + ", " + cc

    val actual =
      res.run(10).at(Seconds(0))

    val expected =
      (13, "foo, bar(foo), baz(foo)[bar(foo)]")

    assertEquals(actual, expected)

  }

}
