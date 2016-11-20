package com.github.unisay.ftpee

import cats.Show
import cats.syntax.semigroup._
import cats.instances.string._

trait ThrowableInstances {

  implicit val showThrowable: Show[Throwable] = Show.show(throwableToString(parent = None))

  private def throwableToString(parent: Option[Throwable])(t: Throwable): String =
    t.getClass.getName |+| "(message = \"" |+| t.getMessage |+| "\")" |+|
      Option(t.getCause).filter(_ != t).map(" caused by " |+| throwableToString(Some(t))(_)).getOrElse("")

}

object ThrowableInstances extends ThrowableInstances
