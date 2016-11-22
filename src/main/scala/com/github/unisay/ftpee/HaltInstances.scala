package com.github.unisay.ftpee

import cats.Functor
import cats.free.Free

import scala.language.higherKinds

object HaltInstances {

  implicit def haltFunctor[F[_]]: Functor[Halt[F, ?]] =
    new Functor[Halt[F, ?]] {
      override def map[A, B](fa: Halt[F, A])(f: A => B): Halt[F, B] = fa
    }

  implicit class FreeHaltOps[F[_], A](free: Free[Halt[F, ?], A]) {
    def unhalt: Free[F, Unit] = free.fold(x => Free.pure(()), Free.liftF(_))
  }

}
