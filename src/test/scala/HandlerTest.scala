import cats.effect._
import fs2.{Stream, text}
import org.http4s._
import org.http4s.dsl.io._

import io.circe.syntax._
import io.circe.generic.auto._
import org.scalatest.FunSuite

class HandlerTest extends FunSuite {
  test("balance responses with 404 when there is no user") {
    val service = Handler.apply(new Storage)
    val body = Invoice(10).asJson.toString

    val request = Request[IO](Method.GET, uri("/1/balance"))
    val response = service.orNotFound.run(request).unsafeRunSync
    assert(response.status == Status.NotFound)
    assert(
      response.body
        .through(text.utf8Decode)
        .compile
        .toList
        .unsafeRunSync
        .head == Error(Handler.serializeError(NoKeyError)).asJson.noSpaces
    )
  }

  test("adds money with replenish") {
    val service = Handler.apply(new Storage)
    val body = Invoice(10).asJson.toString

    val request1 = Request[IO](Method.POST, uri("/1/replenish"), body = Stream(body).through(text.utf8Encode))
    val response1 = service.orNotFound.run(request1).unsafeRunSync
    assert(response1.status == Status.Ok)

    val request2 = Request[IO](Method.GET, uri("/1/balance"))
    val response2 = service.orNotFound.run(request2).unsafeRunSync
    assert(response2.status == Status.Ok)

    assert(
      response2.body
        .through(text.utf8Decode)
        .compile
        .toList
        .unsafeRunSync
        .head == Balance(10).asJson.noSpaces
    )
  }

  test("subtracts money with withdraw") {
    val service = Handler.apply(new Storage)
    val body1 = Invoice(10).asJson.toString

    val request1 = Request[IO](Method.POST, uri("/1/replenish"), body = Stream(body1).through(text.utf8Encode))
    val response1 = service.orNotFound.run(request1).unsafeRunSync
    assert(response1.status == Status.Ok)

    val body2 = Withdraw(7).asJson.toString
    val request2 = Request[IO](Method.POST, uri("/1/withdraw"), body = Stream(body2).through(text.utf8Encode))
    val response2 = service.orNotFound.run(request2).unsafeRunSync
    assert(response2.status == Status.Ok)

    val request3 = Request[IO](Method.GET, uri("/1/balance"))
    val response3 = service.orNotFound.run(request3).unsafeRunSync
    assert(response3.status == Status.Ok)

    assert(
      response3.body
        .through(text.utf8Decode)
        .compile
        .toList
        .unsafeRunSync
        .head == Balance(3).asJson.noSpaces
    )
  }

  test("withdraw responses with 404 when no user") {
    val service = Handler.apply(new Storage)

    val body = Withdraw(7).asJson.toString
    val request = Request[IO](Method.POST, uri("/1/withdraw"), body = Stream(body).through(text.utf8Encode))
    val response = service.orNotFound.run(request).unsafeRunSync
    assert(response.status == Status.NotFound)
    assert(
      response.body
        .through(text.utf8Decode)
        .compile
        .toList
        .unsafeRunSync
        .head == Error(Handler.serializeError(NoKeyError)).asJson.noSpaces
    )
  }

  test("withdraw responses with 400 when not enough money") {
    val service = Handler.apply(new Storage)
    val body1 = Invoice(5).asJson.toString

    val request1 = Request[IO](Method.POST, uri("/1/replenish"), body = Stream(body1).through(text.utf8Encode))
    val response1 = service.orNotFound.run(request1).unsafeRunSync
    assert(response1.status == Status.Ok)

    val body = Withdraw(7).asJson.toString
    val request = Request[IO](Method.POST, uri("/1/withdraw"), body = Stream(body).through(text.utf8Encode))
    val response = service.orNotFound.run(request).unsafeRunSync
    assert(response.status == Status.BadRequest)
    assert(
      response.body
        .through(text.utf8Decode)
        .compile
        .toList
        .unsafeRunSync
        .head == Error(Handler.serializeError(NotEnoughError)).asJson.noSpaces
    )
  }

  test("transfers money from one user to another") {
    val service = Handler.apply(new Storage)
    val body1 = Invoice(5).asJson.toString

    val request1 = Request[IO](Method.POST, uri("/1/replenish"), body = Stream(body1).through(text.utf8Encode))
    val response1 = service.orNotFound.run(request1).unsafeRunSync
    assert(response1.status == Status.Ok)

    val body = Transaction(1, 2, 3).asJson.toString
    val request2 = Request[IO](Method.POST, uri("/transactions"), body = Stream(body).through(text.utf8Encode))
    val response2 = service.orNotFound.run(request2).unsafeRunSync
    assert(response2.status == Status.Ok)

    val request3 = Request[IO](Method.GET, uri("/1/balance"))
    val response3 = service.orNotFound.run(request3).unsafeRunSync
    assert(response3.status == Status.Ok)

    assert(
      response3.body
        .through(text.utf8Decode)
        .compile
        .toList
        .unsafeRunSync
        .head == Balance(2).asJson.noSpaces
    )

    val request4 = Request[IO](Method.GET, uri("/2/balance"))
    val response4 = service.orNotFound.run(request4).unsafeRunSync
    assert(response4.status == Status.Ok)

    assert(
      response4.body
        .through(text.utf8Decode)
        .compile
        .toList
        .unsafeRunSync
        .head == Balance(3).asJson.noSpaces
    )
  }

  test("return 400 when not enough money to transfer") {
    val service = Handler.apply(new Storage)
    val body1 = Invoice(5).asJson.toString

    val request1 = Request[IO](Method.POST, uri("/1/replenish"), body = Stream(body1).through(text.utf8Encode))
    val response1 = service.orNotFound.run(request1).unsafeRunSync
    assert(response1.status == Status.Ok)

    val body = Transaction(1, 2, 10).asJson.toString
    val request2 = Request[IO](Method.POST, uri("/transactions"), body = Stream(body).through(text.utf8Encode))
    val response2 = service.orNotFound.run(request2).unsafeRunSync
    assert(response2.status == Status.BadRequest)
    assert(
      response2.body
        .through(text.utf8Decode)
        .compile
        .toList
        .unsafeRunSync
        .head == Error(Handler.serializeError(NotEnoughError)).asJson.noSpaces
    )
  }

  test("return 404 when no user to transfer from") {
    val service = Handler.apply(new Storage)
    val body1 = Invoice(5).asJson.toString

    val request1 = Request[IO](Method.POST, uri("/1/replenish"), body = Stream(body1).through(text.utf8Encode))
    val response1 = service.orNotFound.run(request1).unsafeRunSync
    assert(response1.status == Status.Ok)

    val body = Transaction(100500, 2, 10).asJson.toString
    val request2 = Request[IO](Method.POST, uri("/transactions"), body = Stream(body).through(text.utf8Encode))
    val response2 = service.orNotFound.run(request2).unsafeRunSync
    assert(response2.status == Status.NotFound)
    assert(
      response2.body
        .through(text.utf8Decode)
        .compile
        .toList
        .unsafeRunSync
        .head == Error(Handler.serializeError(NoKeyError)).asJson.noSpaces
    )
  }

  test("return 404 when id is invalid") {
    val service = Handler.apply(new Storage)
    val body1 = Invoice(5).asJson.toString

    val request1 = Request[IO](Method.POST, uri("/NOT_VALID/replenish"), body = Stream(body1).through(text.utf8Encode))
    val response1 = service.orNotFound.run(request1).unsafeRunSync
    assert(response1.status == Status.NotFound)
    assert(
      response1.body
        .through(text.utf8Decode)
        .compile
        .toList
        .unsafeRunSync
        .head == Error(Handler.serializeError(ParseIdError)).asJson.noSpaces
    )

    val body2 = Withdraw(5).asJson.toString
    val request2 = Request[IO](Method.POST, uri("/NOT_VALID/withdraw"), body = Stream(body2).through(text.utf8Encode))
    val response2 = service.orNotFound.run(request2).unsafeRunSync
    assert(response2.status == Status.NotFound)
    assert(
      response2.body
        .through(text.utf8Decode)
        .compile
        .toList
        .unsafeRunSync
        .head == Error(Handler.serializeError(ParseIdError)).asJson.noSpaces
    )

    val request3 = Request[IO](Method.GET, uri("/NOT_VALID/balance"))
    val response3 = service.orNotFound.run(request3).unsafeRunSync
    assert(response3.status == Status.NotFound)
    assert(
      response3.body
        .through(text.utf8Decode)
        .compile
        .toList
        .unsafeRunSync
        .head == Error(Handler.serializeError(ParseIdError)).asJson.noSpaces
    )
  }
}
