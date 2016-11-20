package com.github.unisay.ftpee

import java.io.{IOException, InputStream}

import cats.data.{EitherT, Kleisli}
import cats.implicits._
import cats.{Monad, Show, ~>}
import com.github.unisay.ftpee.Ftpee.{ConnectionError, _}
import org.apache.commons.net.ftp.{FTPClient, FTPConnectionClosedException, FTPFile}

import scala.language.{higherKinds, implicitConversions}

object ApacheCommonsFtp extends ThrowableInstances {

  def runSession[I[_] : IO : Monad, A](clientConfig: FtpClientConfig,
                                       command: FtpCommand[FtpCommandError Either A]): I[FtpError Either A] =
    (
      for {
        client <- EitherT(connectClient(clientConfig)).leftMap(e => e : FtpError)
        result <- EitherT(runCommand(client, command)).leftMap(CommandError(_): FtpError)
        _ <- EitherT(disconnectClient(client)).leftMap(e => e : FtpError)
      } yield result
    ).value

  private def compiler[I[_]: IO : Monad]: FtpCommandA ~> Kleisli[I, FTPClient, ?] = {
    type Res[A] = Kleisli[I, FTPClient, A]
    type Cmd[A] = Kleisli[I, FTPClient, Either[FtpCommandError, A]]
    new (FtpCommandA ~> Kleisli[I, FTPClient, ?]) {
      def apply[A](ftpCommand: FtpCommandA[A]): Kleisli[I, FTPClient, A] = {
        val io = implicitly[IO[I]]

        def useClient[T](f: FTPClient => T): EitherT[Res, FtpCommandError, T] =
          EitherT[Res, FtpCommandError, T](
            Kleisli { (client: FTPClient) =>
              io.delay {
                Either.catchNonFatal(f(client))
                  .leftMap {
                    case e: FTPConnectionClosedException => ConnectionClosedError(Show[Throwable].show(e))
                    case e: IOException => IoError(Show[Throwable].show(e))
                    case e: Exception => UnknownError(Show[Throwable].show(e))
                  }
              }
            }
          )

        def asRemoteFile(file: FTPFile): RemoteFile = new RemoteFile {
          def name: String = file.getName
          def size: Long = file.getSize
          def group: String = file.getGroup
          def user: String = file.getUser
        }

        implicit def kToA[T](cmd: Cmd[T]): Kleisli[I, FTPClient, A] = cmd.map(_.asInstanceOf[A])

        ftpCommand match {
          case NOOP =>
            useClient(_.noop()).value

          case CDUP =>
            useClient(_.changeToParentDirectory())
              .transform {
                case Right(false) => Left(NoParentDirectory)
                case Right(true) => Right(())
                case other => other
              }
              .value

          case PWD =>
            useClient(client => Option(client.printWorkingDirectory()))
              .subflatMap(_.toRight[FtpCommandError](CantObtainCurrentDirectory))
              .value

          case CWD(pathName) =>
            useClient(_.changeWorkingDirectory(pathName))
              .transform {
                case Right(false) => Left(NonExistingPath(pathName))
                case Right(true) => Right(())
                case other => other
              }
              .value

          case RETR(remote) =>
            useClient(client => Option(client.retrieveFileStream(remote)))
              .flatMapF[InputStream] {
                case None => useClient(_.getReplyString.trim).transform {
                  case Right(message) => Left(UnknownError(message))
                  case other => other
                }.value
                case Some(inputStream) => Kleisli.pure(Right(inputStream))
              }
              .value

          case LIST(parent) =>
            useClient(_.listDirectories(parent).map(asRemoteFile).toList).value

          case NLST(pathName) =>
            useClient(_.listNames(pathName).toList).value
        }
      }
    }
  }

  private def connectClient[I[_]: IO, A](clientConfig: FtpClientConfig): I[ConnectionError Either FTPClient] =
    implicitly[IO[I]].delay {
      Either.catchNonFatal {
        val client = new FTPClient
        client.connect(clientConfig.host, clientConfig.port)
        client.login(clientConfig.username, clientConfig.password)
        client
      } leftMap { case e: IOException => ConnectionError(Show[Throwable].show(e)) }
    }

  private def runCommand[I[_] : IO : Monad, A](
    client: FTPClient,
    cmd: FtpCommand[FtpCommandError Either A]
  ): I[Either[FtpCommandError, A]] = cmd.foldMap[Kleisli[I, FTPClient, ?]](compiler).run(client)

  private def disconnectClient[I[_]: IO](client: FTPClient): I[DisconnectionError Either Unit] =
    implicitly[IO[I]].delay {
      Either.catchNonFatal(client.disconnect())
        .leftMap { case e: IOException => DisconnectionError(Show[Throwable].show(e)) }
    }

}
