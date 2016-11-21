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

  it must "run printWorkingDirectory" in {
    execute(printWorkingDirectory).right.value mustBe "/home"
  }

  it must "run noop" in {
    execute(noop).right.value mustBe 200
  }

  it must "enter local passive mode" in {
    ApacheCommonsFtp.runSession(clientConfig, enterLocalPassiveMode).value.right.value mustBe unit
  }

  it must "run changeWorkingDirectory(existing dir)" in {
    execute(changeWorkingDirectory("/tmp")).right.value mustBe unit
  }

  it must "run changeWorkingDirectory(non-existing dir)" in {
    execute(changeWorkingDirectory("/non-existing")).left.value mustBe NonExistingPath("/non-existing")
  }

  it must "run makeDirectory(non-existing dir)" in {
    execute(makeDirectory("/newdir")).right.value mustBe unit
  }

  it must "run makeDirectory(existing dir)" in {
    execute(makeDirectory("/tmp")).left.value mustBe GenericError(550, "550 The path [/tmp] already exists.")
  }  

  it must "run deleteFile(existing file)" in {
    execute(deleteFile("/tmp/file2.txt")).right.value mustBe unit
  }

  it must "run deleteFile(non-existing file)" in {
    execute(deleteFile("/non-existing")).left.value mustBe NonExistingPath("/non-existing")
  }

  it must "run changeToParentDirectory(child dir)" in {
    execute(changeWorkingDirectory("/tmp") followedBy changeToParentDirectory).right.value mustBe unit
  }

  it must "run changeToParentDirectory(root dir)" in {
    execute(changeWorkingDirectory("/") followedBy changeToParentDirectory).left.value mustBe NoParentDirectory
  }

  it must "run retrieveFileStream(existing file)" in {
    val is = execute(retrieveFileStream("/tmp/file1.txt")).right.value
    scala.io.Source.fromInputStream(is).getLines().mkString("\n") mustBe "abcdef\n1234567890"
  }

  it must "run retrieveFileStream(non-existing file)" in {
    execute(retrieveFileStream("/foo")).left.value mustBe GenericError(550, "550 [/foo] does not exist.")
  }

  it must "run listDirectories(existing path)" in {
    execute(listDirectories("/")).right.value.map(f => (f.name, f.size, f.group, f.user)) must contain
      allOf (("tmp", 0, "none", "none"), ("home", 0, "none", "none"))
  }

  it must "run listDirectories(non-existing path)" in {
    execute(listDirectories("/foo")).right.value mustBe empty
  }

  it must "run listDirectories(file path)" in {
    execute(listDirectories("/tmp/file1.txt")).right.value mustBe empty
  }

  it must "run listNames(existing path)" in {
    execute(listNames("/")).right.value must contain allOf ("tmp", "home")
  }

  it must "run listNames(non-existing path)" in {
    execute(listNames("/foo")).right.value mustBe empty
  }

  def execute[A](command: FtpCommand[FtpCommandError Either A]): FtpCommandError Either A =
    ApacheCommonsFtp.runSession(clientConfig, command).value.right.value

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
    fileSystem.add(new FileEntry("/tmp/file2.txt", "delete me"))
    fakeFtpServer.setFileSystem(fileSystem)
    fakeFtpServer.setServerControlPort(0)
    fakeFtpServer.start()
  }

  override protected def afterAll(): Unit = {
    fakeFtpServer.stop()
  }

}
