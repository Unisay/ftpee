package com.github.unisay.ftpee

import cats._
import cats.free.Free
import com.github.unisay.ftpee.Ftpee._
import com.github.unisay.ftpee.Logging.LoggingF.Log

import scala.language.higherKinds

object Logging {

  sealed trait LoggingF[A]
  object LoggingF {
    case class Log(string: String) extends LoggingF[Unit]
    def log(string: String): Free[LoggingF, Unit] = Free.liftF(Log(string))
  }

  def exec[I[_]: Monad](implicit io: IO[I]) =
    new (LoggingF ~> I) {
      override def apply[A](fa: LoggingF[A]): I[A] = fa match {
        case Log(line) => io.delay(println())
      }
    }

  val ftpLogging: FtpCommandF ~< Halt[LoggingF, ?] = new (FtpCommandF ~< Halt[LoggingF, ?]) {
      override def apply[A](fa: FtpCommandF[A]): Free[Halt[LoggingF, ?], A] = {
        def log(string: String): Free[Halt[LoggingF, ?], A] = Free.liftF[Halt[LoggingF, ?], A](LoggingF.Log(string))
        fa match {
          case Noop => log("Noop")
          case ChangeToParentDirectory => log("ChangeToParentDirectory")
          case PrintWorkingDirectory => log("PrintWorkingDirectory")
          case ChangeWorkingDirectory(pathName) => log(s"ChangeWorkingDirectory($pathName)")
          case RetrieveFileStream(remote) => log(s"RetrieveFileStream($remote)")
          case EnterLocalPassiveMode => log("EnterLocalPassiveMode")
          case EnterLocalActiveMode => log("EnterLocalActiveMode")
          case DeleteFile(pathName) => log(s"DeleteFile($pathName)")
          case MakeDirectory(pathName) => log(s"MakeDirectory($pathName)")
          case ListDirectories(parent) => log(s"ListDirectories($parent)")
          case ListNames(pathName) => log(s"ListNames($pathName)")
        }
      }
    }

}
