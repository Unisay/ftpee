package com.github.unisay.ftpee

import java.io.{IOException, InputStream}

import cats.data.{EitherT, Kleisli}
import cats.implicits._
import cats.{Monad, Show, ~>}
import com.github.unisay.ftpee.Ftpee.{ConnectionError, _}
import org.apache.commons.net.ftp.{FTPClient, FTPConnectionClosedException, FTPFile}

import scala.language.{higherKinds, implicitConversions}

object ApacheCommonsFtp extends ThrowableInstances {

  def runSession[I[_] : IO : Monad, A](config: FtpClientConfig, cmd: FtpCommand[A]): I[FtpError Either A] =
    (
      for {
        client <- EitherT(connectClient(config)).leftMap(e => e : FtpError)
        result <- EitherT(cmd.foldMap[Kleisli[I, FTPClient, ?]](compiler).run(client).map(Either.right[FtpError, A]))
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

        def getGenericError[T]: Res[Either[FtpCommandError, T]] =
          useClient(client => client.getReplyCode -> client.getReplyString.trim)
            .transform {
              case Right((code, message)) => Left(GenericError(code, message): FtpCommandError)
              case _ => sys.error("Failed to retrieve error code")
            }.value

        def asRemoteFile(file: FTPFile): RemoteFile = new RemoteFile {
          def name: String = file.getName
          def size: Long = file.getSize
          def group: String = file.getGroup
          def user: String = file.getUser
        }

        implicit def kToA[T](cmd: Cmd[T]): Kleisli[I, FTPClient, A] = cmd.map(_.asInstanceOf[A])

        ftpCommand match {
          case Noop =>
            useClient(_.noop()).value

          case ChangeToParentDirectory =>
            useClient(_.changeToParentDirectory())
              .transform {
                case Right(false) => Left(NoParentDirectory)
                case Right(true) => Right(())
                case other => other
              }
              .value

          case PrintWorkingDirectory =>
            useClient(client => Option(client.printWorkingDirectory()))
              .subflatMap(_.toRight[FtpCommandError](CantObtainCurrentDirectory))
              .value

          case ChangeWorkingDirectory(pathName) =>
            useClient(_.changeWorkingDirectory(pathName))
              .transform {
                case Right(false) => Left(NonExistingPath(pathName))
                case Right(true) => Right(())
                case other => other
              }
              .value

          case RetrieveFileStream(remote) =>
            useClient(client => Option(client.retrieveFileStream(remote)))
              .flatMapF[InputStream] {
                case None => getGenericError[InputStream]
                case Some(inputStream) => Kleisli.pure(Right(inputStream))
              }
              .value

          case EnterLocalPassiveMode =>
            Kleisli(client => io.delay(client.enterLocalPassiveMode()))

          case EnterLocalActiveMode =>
            Kleisli(client => io.delay(client.enterLocalActiveMode()))

          case DeleteFile(pathName) =>
            useClient(_.deleteFile(pathName))
              .transform {
                case Right(false) => Left(NonExistingPath(pathName))
                case Right(true) => Right(())
                case other => other
              }
              .value

          case MakeDirectory(pathName) =>
            useClient(_.makeDirectory(pathName))
              .flatMapF {
                case false => getGenericError[Unit]
                case true => Kleisli.pure(Either.right[FtpCommandError, Unit](()))
              }
              .value

          case ListDirectories(parent) =>
            useClient(_.listDirectories(parent).map(asRemoteFile).toList).value

          case ListNames(pathName) =>
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

  private def disconnectClient[I[_]: IO](client: FTPClient): I[DisconnectionError Either Unit] =
    implicitly[IO[I]].delay {
      Either.catchNonFatal(client.disconnect())
        .leftMap { case e: IOException => DisconnectionError(Show[Throwable].show(e)) }
    }

}
