# FTPee

Functional FTP client implementation in Scala.

Designed as a free-monadic EDSL that is interpreted into actual network interactions using `Interpreter` 

Example:

```scala
import com.github.unisay.ftpee.Ftpee._
import com.github.unisay.ftpee.ApacheCommonsFtp

// Create program using monad composition:
val program: FtpCommand[String] = for {
  workingDirectory <- printWorkingDirectory
  subdirectories   <- listDirectories(workingDirectory)
  _                <- changeWorkingDirectory(subdirectories.head)
  directory        <- printWorkingDirectory
  _                <- changeToParentDirectory
} yield directory

// Interpret command into other monad (cats.Eval in this example):
import com.github.unisay.ftpee.IOInstances.ioEval
val config = FtpClientConfig(host = "ftphost", port = 21, username = "user", password = "pass") 
val result: Eval[Either[FtpCommandError, String] = ApacheCommonsFtp.runSession(config, program)]
```

At the moment **FTPee** implements:
 
- A subset of FTP commands:

| FTP Command | FTPee Command |
|-------------|---------------|
| NOOP | `noop` |
| PASV | `enterLocalPassiveMode` |
| _default_ | `enterLocalActiveMode` |
| PWD  | `printWorkingDirectory` |
| CDUP | `changeToParentDirectory` |
| CWD  | `changeWorkingDirectory` |
| MKD  | `makeDirectory` |
| DELE | `deleteFile` |
| RETR | `retrieveFileStream` |
| LIST | `listDirectories` |
| NLST | `listNames` |

- An interpreter backed by Apache Commons `FTPClient`
