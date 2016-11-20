package com.github.unisay.ftpee

import java.io.InputStream

import cats.free.Free
import cats.free.Free.liftF

object Ftpee {

  case class FtpClientConfig(host: String, port: Int, username: String, password: String)

  trait RemoteFile {
    def name: String
    def size: Long
    def group: String
    def user: String
  }

  sealed trait FtpError
  case class ConnectionError(message: String) extends FtpError
  case class DisconnectionError(message: String) extends FtpError
  case class CommandError(cause: FtpCommandError) extends FtpError

  sealed trait FtpCommandError
  case class ConnectionClosedError(message: String) extends FtpCommandError
  case class IoError(message: String) extends FtpCommandError
  case class UnknownError(message: String) extends FtpCommandError
  case class NonExistingPath(path: String) extends FtpCommandError
  object NoParentDirectory extends FtpCommandError
  object CantObtainCurrentDirectory extends FtpCommandError

  type FtpCommand[A] = Free[FtpCommandA, A]

  sealed trait FtpCommandA[A]
  object NOOP extends FtpCommandA[Either[FtpCommandError, Int]]
  object PWD extends FtpCommandA[Either[FtpCommandError, String]]
  object CDUP extends FtpCommandA[Either[FtpCommandError, Unit]]
  case class CWD(pathName: String) extends FtpCommandA[Either[FtpCommandError, Unit]]
  case class RETR(remote: String) extends FtpCommandA[Either[FtpCommandError, InputStream]]
  case class LIST(parent: String) extends FtpCommandA[Either[FtpCommandError, List[RemoteFile]]]
  case class NLST(name: String) extends FtpCommandA[Either[FtpCommandError, List[String]]]

  def noop: FtpCommand[Either[FtpCommandError, Int]] = liftF(NOOP)
  def pwd: FtpCommand[Either[FtpCommandError, String]] = liftF(PWD)
  def cdup: FtpCommand[Either[FtpCommandError, Unit]] = liftF(CDUP)
  def cwd(name: String): FtpCommand[Either[FtpCommandError, Unit]] = liftF(CWD(name))
  def retr(name: String): FtpCommand[Either[FtpCommandError, InputStream]] = liftF(RETR(name))
  def list(name: String): FtpCommand[Either[FtpCommandError, List[RemoteFile]]] = liftF(LIST(name))
  def nlst(name: String): FtpCommand[Either[FtpCommandError, List[String]]] = liftF(NLST(name))
}
