import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.circe._

import io.circe.syntax._
import io.circe.generic.auto._
import org.http4s.circe.jsonOf

trait AppError
case object NoKeyError extends AppError
case object ParseIdError extends AppError
case object NotEnoughError extends AppError

case class Error(message: String)
case class Balance(balance: Double)
case class Invoice(amount: Double)
case class Withdraw(amount: Double)
case class Transaction(from: Int, to: Int, amount: Double)

object Handler {
  type Result[A] = Either[AppError, A]

  implicit val replenishDecoder: EntityDecoder[IO, Invoice] = jsonOf[IO, Invoice]
  implicit val withdrawDecoder: EntityDecoder[IO, Withdraw] = jsonOf[IO, Withdraw]
  implicit val transactionDecoder: EntityDecoder[IO, Transaction] = jsonOf[IO, Transaction]

  def apply(storage: Storage): HttpService[IO] = {
    HttpService[IO] {
      case req @ POST -> Root / id / "replenish" =>
        req.as[Invoice].flatMap { i =>
          toInt(id).map(storage.add(_, i.amount)) match {
            case Right(_) => Ok()
            case Left(e) => handleError(e)
          }
        }
      case req @ POST -> Root / id / "withdraw" =>
        req.as[Withdraw].flatMap { w =>
          toInt(id).flatMap(storage.subtract(_, w.amount)) match {
            case Right(_) => Ok()
            case Left(e) => handleError(e)
          }
        }
      case req @ POST -> Root / "transactions" =>
        req.as[Transaction].flatMap { t =>
          storage.transfer(t.from, t.to, t.amount) match {
            case Right(_) => Ok()
            case Left(e) => handleError(e)
          }
        }
      case GET -> Root / id / "balance" =>
        toInt(id).flatMap(storage.get) match {
          case Left(e) => handleError(e)
          case Right(v) => Ok(Balance(v).asJson)
        }
    }
  }

  def handleError(e: AppError): IO[Response[IO]] = e match {
    case NoKeyError | ParseIdError => NotFound(Error(serializeError(e)).asJson)
    case NotEnoughError => BadRequest(Error(serializeError(e)).asJson)
  }

  def toInt(s: String): Result[Int] = {
    try {
      Right(s.toInt)
    } catch {
      case _: Exception => Left(ParseIdError)
    }
  }

  def serializeError(error: AppError): String = error match {
    case NoKeyError => "User not found"
    case ParseIdError => "Id is invalid"
    case NotEnoughError => "User has not enough money"
  }
}
