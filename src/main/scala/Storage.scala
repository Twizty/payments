class Storage {
  type Result[A] = Either[AppError, A]

  val mutex = new Object
  var store = scala.collection.mutable.Map[Int, Double]()

  def add(id: Int, amount: Double): Unit = mutex.synchronized { _add(id, amount) }

  def subtract(id: Int, amount: Double): Result[Unit] = mutex.synchronized { _subtract(id, amount) }

  def transfer(from: Int, to: Int, amount: Double): Result[Unit] = mutex.synchronized {
    _subtract(from, amount).map(_ => _add(to, amount))
  }

  def get(id: Int): Result[Double] = mutex.synchronized {
    store.get(id).toRight(NoKeyError)
  }

  private def _add(id: Int, amount: Double): Unit = store.get(id) match {
    case Some(v) => store.update(id, v + amount)
    case None => store += (id -> amount)
  }

  private def _subtract(id: Int, amount: Double): Result[Unit] = store.get(id) match {
    case Some(v) if v >= amount => Right(store.update(id, v - amount))
    case Some(v) if v < amount => Left(NotEnoughError)
    case None => Left(NoKeyError)
  }
}

