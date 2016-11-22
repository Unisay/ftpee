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

  sealed trait FtpCommandError
  case class ConnectionClosedError(message: String) extends FtpCommandError
  case class IoError(message: String) extends FtpCommandError
  case class UnknownError(message: String) extends FtpCommandError
  case class GenericError(code: Int, message: String) extends FtpCommandError
  case class NonExistingPath(path: String) extends FtpCommandError
  object NoParentDirectory extends FtpCommandError
  object CantObtainCurrentDirectory extends FtpCommandError

  type FtpCommand[A] = Free[FtpCommandF, A]

  sealed trait FtpCommandF[A]
  object EnterLocalPassiveMode extends FtpCommandF[Unit]
  object EnterLocalActiveMode extends FtpCommandF[Unit]
  object Noop extends FtpCommandF[Either[FtpCommandError, Int]]
  object PrintWorkingDirectory extends FtpCommandF[Either[FtpCommandError, String]]
  object ChangeToParentDirectory extends FtpCommandF[Either[FtpCommandError, Unit]]
  case class ChangeWorkingDirectory(pathName: String) extends FtpCommandF[Either[FtpCommandError, Unit]]
  case class MakeDirectory(pathName: String) extends FtpCommandF[Either[FtpCommandError, Unit]]
  case class DeleteFile(pathName: String) extends FtpCommandF[Either[FtpCommandError, Unit]]
  case class RetrieveFileStream(remote: String) extends FtpCommandF[Either[FtpCommandError, InputStream]]
  case class ListDirectories(parent: String) extends FtpCommandF[Either[FtpCommandError, List[RemoteFile]]]
  case class ListNames(name: String) extends FtpCommandF[Either[FtpCommandError, List[String]]]


  def enterLocalPassiveMode: FtpCommand[Unit] =
    liftF(EnterLocalPassiveMode)

  def enterLocalActiveMode: FtpCommand[Unit] =
    liftF(EnterLocalActiveMode)

  def noop: FtpCommand[Either[FtpCommandError, Int]] =
    liftF(Noop)

  def printWorkingDirectory: FtpCommand[Either[FtpCommandError, String]] =
    liftF(PrintWorkingDirectory)

  def changeToParentDirectory: FtpCommand[Either[FtpCommandError, Unit]] =
    liftF(ChangeToParentDirectory)

  def makeDirectory(pathName: String): FtpCommand[Either[FtpCommandError, Unit]] =
    liftF(MakeDirectory(pathName))

  def deleteFile(pathName: String): FtpCommand[Either[FtpCommandError, Unit]] =
    liftF(DeleteFile(pathName))

  def changeWorkingDirectory(name: String): FtpCommand[Either[FtpCommandError, Unit]] =
    liftF(ChangeWorkingDirectory(name))

  def retrieveFileStream(name: String): FtpCommand[Either[FtpCommandError, InputStream]] =
    liftF(RetrieveFileStream(name))

  def listDirectories(name: String): FtpCommand[Either[FtpCommandError, List[RemoteFile]]] =
    liftF(ListDirectories(name))

  def listNames(name: String): FtpCommand[Either[FtpCommandError, List[String]]] =
    liftF(ListNames(name))
}
