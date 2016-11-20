package com.github.unisay.ftpee

import cats.Eval._
import com.github.unisay.ftpee.Ftpee._
import com.github.unisay.ftpee.IOInstances.ioEval
import org.mockftpserver.fake.filesystem.{DirectoryEntry, FileEntry, UnixFakeFileSystem}
import org.mockftpserver.fake.{FakeFtpServer, UserAccount}
import org.scalatest.{BeforeAndAfterAll, EitherValues, FlatSpec, MustMatchers}

class ApacheCommonsFtpTest extends FlatSpec with MustMatchers with EitherValues with BeforeAndAfterAll {

  behavior of "ApacheCommonsFtp"

  it should "run open connection, run command, close connection" in {
    ApacheCommonsFtp.runSession(clientConfig, command = pwd).value.right.get mustBe "/home"
  }

  def clientConfig = FtpClientConfig(
    host = "localhost",
    port = fakeFtpServer.getServerControlPort,
    username = "test-username",
    password = "test-password"
  )

  lazy val fakeFtpServer = new FakeFtpServer()

  override protected def beforeAll(): Unit = {
    fakeFtpServer.addUserAccount(new UserAccount(clientConfig.username, clientConfig.password, "/home"))

    val fileSystem = new UnixFakeFileSystem()
    fileSystem.add(new DirectoryEntry("/home"))
    fileSystem.add(new FileEntry("/home/file1.txt", "abcdef 1234567890"))
    fakeFtpServer.setFileSystem(fileSystem)
    fakeFtpServer.setServerControlPort(0)
    fakeFtpServer.start()

  }

  override protected def afterAll(): Unit = {
    fakeFtpServer.stop()
  }

}
