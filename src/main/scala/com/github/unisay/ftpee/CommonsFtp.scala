package com.github.unisay.ftpee

import java.io.{IOException, InputStream}

import cats._
import cats.data.{EitherT, Kleisli}
import cats.implicits._
import com.github.unisay.ftpee.Ftpee._
import org.apache.commons.net.ftp.{FTPClient, FTPConnectionClosedException, FTPFile}

import scala.language.implicitConversions

object CommonsFtp extends ThrowableInstances {

  type FtpActionF[A] = Kleisli[Eval, FTPClient, A]
  type FtpActionE[A] = FtpActionF[Either[FtpCommandError, A]]

  def runSession[A](config: FtpClientConfig, command: FtpCommand[A]): Eval[Either[FtpError, A]] =
    actionToEval(config, command.foldMap(ftpToAction)).value

  private val ftpToAction = new (FtpCommandF ~> FtpActionF) {
    def apply[A](ftpCommand: FtpCommandF[A]): FtpActionF[A] = {

      def useClient[T](f: FTPClient => T): EitherT[FtpActionF, FtpCommandError, T] =
        EitherT[FtpActionF, FtpCommandError, T](
          Kleisli { (client: FTPClient) =>
            Eval.later {
              Either.catchNonFatal(f(client))
                .leftMap {
                  case e: FTPConnectionClosedException => ConnectionClosedError(Show[Throwable].show(e))
                  case e: IOException => IoError(Show[Throwable].show(e))
                  case e: Exception => UnknownError(Show[Throwable].show(e))
                }
            }
          }
        )

      def getGenericError[T]: FtpActionF[Either[FtpCommandError, T]] =
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

      implicit def toA[T](ftpActionE: FtpActionE[T]): FtpActionF[A] = ftpActionE.map(_.asInstanceOf[A])

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
          Kleisli(client => Eval.later(client.enterLocalPassiveMode()))

        case EnterLocalActiveMode =>
          Kleisli(client => Eval.later(client.enterLocalActiveMode()))

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

  private def actionToEval[A](config: FtpClientConfig, action: FtpActionF[A]): EitherT[Eval, FtpError, A] =
    for {
      client <- connect(config)
      result <- runAction(client, action)
      _ <- disconnect(client)
    } yield result

  private def runAction[A](client: FTPClient, action: FtpActionF[A]): EitherT[Eval, FtpError, A] =
    EitherT(action.run(client).map(Either.right[FtpError, A]))

  private def connect[A](config: FtpClientConfig): EitherT[Eval, FtpError, FTPClient] =
    EitherT(connectClient(config)).leftMap(e => e: FtpError)

  private def connectClient(clientConfig: FtpClientConfig): Eval[ConnectionError Either FTPClient] =
    Eval.later {
      Either.catchNonFatal {
        val client = new FTPClient
        client.connect(clientConfig.host, clientConfig.port)
        client.login(clientConfig.username, clientConfig.password)
        client
      } leftMap { case e: IOException => ConnectionError(Show[Throwable].show(e)) }
    }

  private def disconnect[A](client: FTPClient): EitherT[Eval, FtpError, Unit] =
    EitherT(disconnectClient(client)).leftMap(e => e: FtpError)

  private def disconnectClient(client: FTPClient): Eval[DisconnectionError Either Unit] =
    Eval.later {
      Either.catchNonFatal(client.disconnect())
        .leftMap { case e: IOException => DisconnectionError(Show[Throwable].show(e)) }
    }

}
