package io.casperlabs.client.configuration
import java.io.File

import cats.implicits._
import org.rogach.scallop.ScallopOption

final case class ConnectOptions(
    host: String,
    portExternal: Int,
    portInternal: Int,
    nodeId: Option[String]
)

sealed trait Configuration

final case class MakeDeploy(
    from: String,
    nonce: Long,
    sessionCode: File,
    paymentCode: File,
    gasPrice: Long,
    deployPath: Option[File]
) extends Configuration

final case class Deploy(
    deploy: Array[Byte]
) extends Configuration

/** Client command to sign a deploy.
  */
final case class Sign(
    deploy: Array[Byte],
    signedDeployPath: Option[File],
    publicKey: File,
    privateKey: File
) extends Configuration

final case object Propose extends Configuration

final case class ShowBlock(blockHash: String)   extends Configuration
final case class ShowDeploys(blockHash: String) extends Configuration
final case class ShowDeploy(deployHash: String) extends Configuration
final case class ShowBlocks(depth: Int)         extends Configuration
final case class Bond(
    amount: Long,
    nonce: Long,
    sessionCode: Option[File],
    privateKey: File
) extends Configuration
final case class Transfer(
    amount: Long,
    recipientPublicKeyBase64: String,
    nonce: Long,
    sessionCode: Option[File],
    privateKey: File
) extends Configuration
final case class Unbond(
    amount: Option[Long],
    nonce: Long,
    sessionCode: Option[File],
    privateKey: File
) extends Configuration
final case class VisualizeDag(
    depth: Int,
    showJustificationLines: Boolean,
    out: Option[String],
    streaming: Option[Streaming]
) extends Configuration
final case class Balance(address: String, blockhash: String) extends Configuration

sealed trait Streaming extends Product with Serializable
object Streaming {
  final case object Single   extends Streaming
  final case object Multiple extends Streaming
}

final case class Query(
    blockHash: String,
    keyType: String,
    key: String,
    path: String
) extends Configuration

object Configuration {
  def parse(args: Array[String]): Option[(ConnectOptions, Configuration)] = {
    val options = Options(args)
    val connect = ConnectOptions(
      options.host(),
      options.port(),
      options.portInternal(),
      options.nodeId.toOption
    )
    val conf = options.subcommand.map {
      case options.makeDeploy =>
        MakeDeploy(
          options.makeDeploy.from(),
          options.makeDeploy.nonce(),
          options.makeDeploy.session(),
          options.makeDeploy.payment(),
          options.makeDeploy.gasPrice(),
          options.makeDeploy.deployPath.toOption
        )
      case options.deploy =>
        Deploy(options.deploy.deployFile())
      case options.sign =>
        Sign(
          options.sign.deployFile(),
          options.sign.signedDeployPath.toOption,
          options.sign.publicKey(),
          options.sign.privateKey()
        )
      case options.propose =>
        Propose
      case options.showBlock =>
        ShowBlock(options.showBlock.hash())
      case options.showDeploys =>
        ShowDeploys(options.showDeploys.hash())
      case options.showDeploy =>
        ShowDeploy(options.showDeploy.hash())
      case options.showBlocks =>
        ShowBlocks(options.showBlocks.depth())
      case options.unbond =>
        Unbond(
          options.unbond.amount.toOption,
          options.unbond.nonce(),
          options.unbond.session.toOption,
          options.unbond.privateKey()
        )
      case options.bond =>
        Bond(
          options.bond.amount(),
          options.bond.nonce(),
          options.bond.session.toOption,
          options.bond.privateKey()
        )
      case options.transfer =>
        Transfer(
          options.transfer.amount(),
          options.transfer.targetAccount(),
          options.transfer.nonce(),
          options.transfer.session.toOption,
          options.transfer.privateKey()
        )
      case options.visualizeBlocks =>
        VisualizeDag(
          options.visualizeBlocks.depth(),
          options.visualizeBlocks.showJustificationLines(),
          options.visualizeBlocks.out.toOption,
          options.visualizeBlocks.stream.toOption
        )
      case options.query =>
        Query(
          options.query.blockHash(),
          options.query.keyType(),
          options.query.key(),
          options.query.path()
        )
      case options.balance =>
        Balance(
          options.balance.address(),
          options.balance.blockHash()
        )
    }
    conf map (connect -> _)
  }
}
