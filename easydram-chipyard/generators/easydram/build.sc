import mill._
import mill.define.Sources
import mill.modules.Util
import mill.scalalib.TestModule.ScalaTest
import scalalib._
import mill.bsp._

object easydram extends SbtModule { m =>
  override def millSourcePath = os.pwd
  override def scalaVersion = "2.13.8"
  override def scalacOptions = Seq(
    "-language:reflectiveCalls",
    "-deprecation",
    "-feature",
    "-Xcheckinit",
    "-P:chiselplugin:genBundleElements"
  )
  override def ivyDeps = Agg(
    ivy"edu.berkeley.cs::chisel3:3.6.0",
  )
  override def scalacPluginIvyDeps = Agg(
    ivy"edu.berkeley.cs:::chisel3-plugin:3.6.0",
  )
  object test extends Tests with ScalaTest {
    override def ivyDeps = m.ivyDeps() ++ Agg(
      ivy"edu.berkeley.cs::chiseltest:0.5.1"
    )
  }
}
