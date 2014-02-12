# akka-scala-HTM

Akka/Scala flavoured Human-Task-Manager

## Setup
### Install
- [Install SBT](http://www.scala-sbt.org/release/docs/Getting-Started/Setup.html)
- Clone repo
- run SBT via the CLI (command `sbt`)
- enter `idea` oder `eclipse` for IDE integration
- install [IDEA](http://www.jetbrains.com/idea/features/scala.html) or [Eclipse](http://scala-ide.org/) plugin

Hint: if you run into dependency problems try to delete the
corresponding directory in your local ivy cache and rebuild. In my
case I had to delete ~/.ivy2/chache/commons-codec before the build
succeeded.

### Run
- `sbt run`
- goto: http://localhost:9000
