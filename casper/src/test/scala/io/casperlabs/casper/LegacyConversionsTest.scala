package io.casperlabs.casper

import io.casperlabs.comm.gossiping.ArbitraryConsensus
import org.scalatest._
import org.scalatest.prop.GeneratorDrivenPropertyChecks.{forAll, PropertyCheckConfiguration}

class LegacyConversionsTest extends FlatSpec with ArbitraryConsensus with Matchers {

  implicit val propCheckConfig = PropertyCheckConfiguration(minSuccessful = 100)

  implicit val consensusConfig =
    ConsensusConfig(maxSessionCodeBytes = 50, maxPaymentCodeBytes = 10)

  // Some fields are not supported by the legacy one.
  def compatPrune(orig: consensus.Block) =
    orig
      .withBody(
        orig.getBody.withDeploys(orig.getBody.deploys.map { pd =>
          pd.withErrorMessage("")
        })
      )
      .withHeader(
        orig.getHeader
          .withProtocolVersion(orig.getHeader.getProtocolVersion.withMinor(0).withPatch(0))
      )

  "LegacyConversions" should "convert correctly between old and new blocks" in {
    forAll { (orig: consensus.Block) =>
      val comp = compatPrune(orig)
      val conv = LegacyConversions.fromBlock(comp)
      val back = LegacyConversions.toBlock(conv)
      back.toProtoString shouldBe comp.toProtoString
    }
  }

  it should "preserve genesis" in {
    forAll { (orig: consensus.Block) =>
      val genesis =
        compatPrune(orig)
          .withBody(consensus.Block.Body())
          .clearSignature
      val conv = LegacyConversions.fromBlock(genesis)
      val back = LegacyConversions.toBlock(conv)
      back.toProtoString shouldBe genesis.toProtoString
    }
  }

}
