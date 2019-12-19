package io.casperlabs.casper.highway

import io.casperlabs.crypto.hash.Blake2b256
import io.casperlabs.crypto.Keys.{PublicKey, PublicKeyBS}
import io.casperlabs.casper.consensus.Bond
import java.security.SecureRandom

object LeaderSequencer {

  /** Concatentate all the magic bits into a byte array,
    * padding them with zeroes on the right.
    */
  def toByteArray(bits: Seq[Boolean]): Array[Byte] = {
    val size   = bits.size
    val pad    = 8 - size % 8
    val padded = bits.padTo(size + pad, false)
    val arr    = Array.fill(padded.size / 8)(0)
    padded.zipWithIndex.foreach {
      case (bit, i) =>
        val a = i / 8
        val b = 7 - i % 8
        val s = (if (bit) 1 else 0) << b
        arr(a) = arr(a) | s
    }
    arr.map(_.toByte)
  }

  def seed(parentSeed: Array[Byte], magicBits: Seq[Boolean]) =
    Blake2b256.hash(parentSeed ++ toByteArray(magicBits))

  /** Make a function that assigns a leader to each round, deterministically,
    * with a relative frequency based on their weight. */
  def makeSequencer(leaderSeed: Array[Byte], bonds: Seq[Bond]): Ticks => PublicKeyBS = {
    val validators = bonds.map { x =>
      PublicKey(x.validatorPublicKey) -> BigInt(x.getStake.value)
    }.toVector
    val total = validators.map(_._2).sum.doubleValue

    require(validators.nonEmpty, "Bonds cannot be empty.")
    require(validators.forall(_._2 > 0), "Bonds must be positive.")

    // Given a target sum of bonds, seek the validator with a total cumulative weight in that range.
    def seek(target: BigInt, i: Int = 0, acc: BigInt = 0): PublicKeyBS = {
      val b = validators(i)._2
      // Using > instead of >= so a validator has the lower, but not the upper extremum.
      if (acc + b > target || i == validators.size - 1)
        validators(i)._1
      else
        seek(target, i + 1, acc + b)
    }

    (tick: Ticks) => {
      // On Linux SecureRandom uses NativePRNG, and ignores the seed.
      // Re-seeding also doesn't reset the seed, just augments it, so a new instance is required.
      // https://stackoverflow.com/questions/50107982/rhe-7-not-respecting-java-secure-random-seed
      val random = SecureRandom.getInstance("SHA1PRNG", "SUN")
      // Ticks need to be deterministic, so each time we have to reset the seed.
      val tickSeed = leaderSeed ++ longToBytesLittleEndian(tick)
      random.setSeed(tickSeed)
      // Pick a number between [0, 1) and use it to find a validator.
      val r = random.nextDouble()
      // Integer arithmetic is supposed to be safer than double.
      val t = BigDecimal.valueOf(total * r).toBigInt
      // Find the first validator over the target.
      seek(t)
    }
  }

  private def longToBytesLittleEndian(i: Long): Array[Byte] =
    Array(
      i.toByte,
      (i >>> 8).toByte,
      (i >>> 16).toByte,
      (i >>> 24).toByte,
      (i >>> 32).toByte,
      (i >>> 40).toByte,
      (i >>> 48).toByte,
      (i >>> 56).toByte
    )
}
