import cats.effect.IO
import org.http4s.server.blaze.BlazeBuilder
import scala.concurrent.ExecutionContext.Implicits.global

object Main {
  val storage = new Storage

  def main(args: Array[String]): Unit = {
    BlazeBuilder[IO]
      .bindHttp(3000, "0.0.0.0")
      .mountService(Handler(storage), "/")
      .serve
      .compile
      .drain
      .unsafeRunSync
  }
}

