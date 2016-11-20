package com.github.unisay.ftpee

import java.io.IOException

import cats.data.{EitherT, Kleisli}
import cats.implicits._
import cats.{Monad, ~>}
import com.github.unisay.ftpee.Ftpee.{ConnectionError, _}
import org.apache.commons.net.ftp.{FTPClient, FTPConnectionClosedException}

import scala.language.higherKinds

object ApacheCommonsFtp {

  def runSession[I[_] : IO : Monad, A](clientConfig: FtpClientConfig, command: FtpUnsafeCommand[A]): I[FtpError Either A] =
    (
      for {
        client <- EitherT(connectClient(clientConfig)).leftMap(e => e : FtpError)
        result <- EitherT(runCommand(client, command)).leftMap(CommandError(_): FtpError)
        _ <- EitherT(disconnectClient(client)).leftMap(e => e : FtpError)
      } yield result
    ).value

  private def compiler[I[_]: IO]: FtpCommandA ~> Kleisli[I, FTPClient, ?] = new (FtpCommandA ~> Kleisli[I, FTPClient, ?]) {

    def apply[A](ftpCommand: FtpCommandA[A]): Kleisli[I, FTPClient, A] = {
      val io = implicitly[IO[I]]
      val unitR: Kleisli[I, FTPClient, Either[FtpCommandError, Unit]] =
        Kleisli.lift(io.delay(Either.right[FtpCommandError, Unit](())))
      def doWithClient(f: FTPClient => Any): Kleisli[I, FTPClient, A] =
        Kleisli { (client: FTPClient) =>
          io.delay {
            Either.catchNonFatal(Option(f(client)))
              .leftMap {
                case e: FTPConnectionClosedException => ConnectionClosedError(e)
                case e: IOException => IoError(e)
                case e: Exception => UnknownError(e)
              }
              .flatMap(maybeA => maybeA.toRight[FtpCommandError](NullResult))
              .asInstanceOf[A]
          }
        }

      ftpCommand match {
        case NOOP => unitR
        case CDUP => doWithClient(_.changeToParentDirectory())
        case PWD => doWithClient(_.printWorkingDirectory())
        case CWD(pathName) => doWithClient(_.changeWorkingDirectory(pathName))
        case RETR(remote) => doWithClient(_.retrieveFileStream(remote))
        case LIST(parent) => doWithClient(_.listDirectories(parent))
        case NLST(pathName) => doWithClient(_.listNames(pathName))
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
      } leftMap { case e: IOException => ConnectionError(e) }
    }

  private def runCommand[I[_] : IO : Monad, A](client: FTPClient, cmd: FtpUnsafeCommand[A]): I[Either[FtpCommandError, A]] =
    cmd.foldMap[Kleisli[I, FTPClient, ?]](compiler).run(client)

  private def disconnectClient[I[_]: IO](client: FTPClient): I[DisconnectionError Either Unit] =
    implicitly[IO[I]].delay {
      Either.catchNonFatal(client.disconnect())
        .leftMap { case e: IOException => DisconnectionError(e) }
    }

}
