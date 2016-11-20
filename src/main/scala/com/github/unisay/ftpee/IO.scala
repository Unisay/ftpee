package com.github.unisay.ftpee

import cats.Eval

import scala.language.higherKinds

trait IO[T[_]] {
  def delay[A](a: => A): T[A]
}

object IOInstances {
  implicit val ioEval: IO[Eval] = new IO[Eval] { def delay[A](a: => A): Eval[A] = Eval.later(a) }
}
