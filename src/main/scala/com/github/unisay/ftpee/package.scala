package com.github.unisay

import cats.free.Free
import cats.~>

import scala.language.higherKinds

package object ftpee {
  type Interpreter[F[_], G[_]] = F ~> Free[G, ?]
  type ~<[F[_], G[_]] = Interpreter[F, G]
  type Halt[F[_], A] = F[Unit]
}
