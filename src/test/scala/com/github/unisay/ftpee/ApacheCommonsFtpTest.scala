package com.github.unisay.ftpee

import cats.Eval._
import cats.implicits._
import com.github.unisay.ftpee.Ftpee._
import com.github.unisay.ftpee.IOInstances.ioEval
import org.mockftpserver.fake.filesystem.{DirectoryEntry, FileEntry, UnixFakeFileSystem}
import org.mockftpserver.fake.{FakeFtpServer, UserAccount}
import org.scalatest.{BeforeAndAfterAll, EitherValues, FlatSpec, MustMatchers}

class ApacheCommonsFtpTest extends FlatSpec with MustMatchers with EitherValues with BeforeAndAfterAll {

  behavior of "ApacheCommonsFtp"

  it should "run PWD" in {
    execute(pwd).right.value mustBe "/home"
  }

  it should "run NOOP" in {
    execute(noop).right.get mustBe 200
  }

  it should "run CWD(existing dir)" in {
    execute(cwd("/tmp")).right.value mustBe unit
  }

  it should "run CWD(non-existing dir)" in {
    execute(cwd("/non-existing")).left.value mustBe CommandError(NonExistingPath("/non-existing"))
  }

  it should "run CDUP" in {
    execute(cwd("/tmp") followedBy cdup).right.value mustBe unit
  }

  it should "run CDUP(root dir)" in {
    execute(cwd("/") followedBy cdup).left.value mustBe CommandError(NoParentDirectory)
  }

  it should "run RETR" in {
    val is = execute(retr("/tmp/file1.txt")).right.value
    scala.io.Source.fromInputStream(is).getLines().mkString("\n") mustBe "abcdef\n1234567890"
  }

  it should "run RETR(non-existing file)" in {
    execute(retr("/foo")).left.value mustBe CommandError(UnknownError("550 [/foo] does not exist."))
  }

  def execute[A](command: FtpCommand[FtpCommandError Either A]): Either[FtpError, A] =
    ApacheCommonsFtp.runSession(clientConfig, command).value

  def clientConfig = FtpClientConfig(
    host = "localhost",
    port = fakeFtpServer.getServerControlPort,
    username = "test-username",
    password = "test-password"
  )

  val unit = () // For readability
  lazy val fakeFtpServer = new FakeFtpServer()

  override protected def beforeAll(): Unit = {
    fakeFtpServer.addUserAccount(new UserAccount(clientConfig.username, clientConfig.password, "/home"))

    val fileSystem = new UnixFakeFileSystem()
    fileSystem.add(new DirectoryEntry("/home"))
    fileSystem.add(new DirectoryEntry("/tmp"))
    fileSystem.add(new FileEntry("/tmp/file1.txt", "abcdef\n1234567890"))
    fakeFtpServer.setFileSystem(fileSystem)
    fakeFtpServer.setServerControlPort(0)
    fakeFtpServer.start()
  }

  override protected def afterAll(): Unit = {
    fakeFtpServer.stop()
  }

}
