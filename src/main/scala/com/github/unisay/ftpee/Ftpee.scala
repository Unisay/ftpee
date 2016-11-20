package com.github.unisay.ftpee

import java.io.{IOException, InputStream}

import cats.free.Free
import cats.free.Free.liftF

object Ftpee {

  case class FtpClientConfig(host: String, port: Int, username: String, password: String)

  sealed trait FtpError
  case class ConnectionError(e: IOException) extends FtpError
  case class DisconnectionError(e: IOException) extends FtpError
  case class CommandError(error: FtpCommandError) extends FtpError

  sealed trait FtpCommandError
  case class ConnectionClosedError(io: IOException) extends FtpCommandError
  case class IoError(io: IOException) extends FtpCommandError
  case class UnknownError(e: Exception) extends FtpCommandError
  object NullResult extends FtpCommandError

  type FtpCommand[A] = Free[FtpCommandA, A]
  type FtpUnsafeCommand[A] = FtpCommand[FtpCommandError Either A]

  sealed trait FtpCommandA[A]
  object NOOP extends FtpCommandA[Either[FtpCommandError, Unit]]
  object PWD extends FtpCommandA[Either[FtpCommandError, String]]
  object CDUP extends FtpCommandA[Either[FtpCommandError, Unit]]
  case class CWD(pathName: String) extends FtpCommandA[Either[FtpCommandError, Unit]]
  case class RETR(remote: String) extends FtpCommandA[Either[FtpCommandError, InputStream]]
  case class LIST(parent: String) extends FtpCommandA[Either[FtpCommandError, List[String]]]
  case class NLST(name: String) extends FtpCommandA[Either[FtpCommandError, List[String]]]

  def noop: FtpCommand[Either[FtpCommandError, Unit]] = liftF(NOOP)
  def pwd: FtpCommand[Either[FtpCommandError, String]] = liftF(PWD)
  def cdup: FtpCommand[Either[FtpCommandError, Unit]] = liftF(CDUP)
  def cwd(name: String): FtpCommand[Either[FtpCommandError, Unit]] = liftF(CWD(name))
  def retr(name: String): FtpCommand[Either[FtpCommandError, InputStream]] = liftF(RETR(name))
  def list(name: String): FtpCommand[Either[FtpCommandError, List[String]]] = liftF(LIST(name))
  def nlst(name: String): FtpCommand[Either[FtpCommandError, List[String]]] = liftF(NLST(name))
}
