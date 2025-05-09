package chipyard

import org.chipsalliance.cde.config.{Config}
import freechips.rocketchip.diplomacy.{AsynchronousCrossing}
import freechips.rocketchip.subsystem.{InCluster}

// --------------
// Rocket Configs
// --------------
class EasyDRAMRocketConfig extends Config(
  // Strip memory ports and attach EasyDRAM
  new easydram.WithEasyMemorySim(
    memBase=BigInt("80000000", 16), memSize=BigInt("0FFFFFFF", 16),
    instBase=BigInt("40000000", 16), instSize=BigInt("00FFFFFF", 16),
    cmdBase=BigInt("41000000", 16), cmdSize=BigInt("00FFFFFF", 16),
    tileFreq=100000000, mcFreq=100000000
  ) ++
  new freechips.rocketchip.subsystem.WithNoMemPort ++
  new chipyard.iobinders.WithEasyDRAMPunchthrough ++
  new chipyard.harness.WithEasyDRAMHarness ++
  // Processor Setup
  new freechips.rocketchip.subsystem.WithAsynchronousRocketTiles(3, 3) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  // System Clock Configuration
  new chipyard.config.WithTileFrequency(100.0) ++
  new chipyard.config.WithSystemBusFrequency(100.0) ++
  new chipyard.config.WithMemoryBusFrequency(100.0) ++
  new chipyard.config.WithPeripheryBusFrequency(100.0) ++
  new chipyard.config.WithFrontBusFrequency(100.0) ++
  new chipyard.config.WithControlBusFrequency(100.0) ++
  new chipyard.config.AbstractConfig)

class EasyDRAMRocketVerifyConfig extends Config(
  // Strip memory ports and attach EasyDRAM
  new easydram.WithEasyMemorySim(
    memBase=BigInt("80000000", 16), memSize=BigInt("0FFFFFFF", 16),
    instBase=BigInt("40000000", 16), instSize=BigInt("00FFFFFF", 16),
    cmdBase=BigInt("41000000", 16), cmdSize=BigInt("00FFFFFF", 16),
    tileFreq=100000000, mcFreq=100000000
  ) ++
  new freechips.rocketchip.subsystem.WithNoMemPort ++
  new chipyard.iobinders.WithEasyDRAMPunchthrough ++
  new chipyard.harness.WithEasyDRAMHarness ++
  // Processor Setup
  new freechips.rocketchip.subsystem.WithAsynchronousRocketTiles(3, 3) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  // System Clock Configuration
  new chipyard.config.WithTileFrequency(1000.0) ++
  new chipyard.config.WithSystemBusFrequency(1000.0) ++
  new chipyard.config.WithMemoryBusFrequency(1000.0) ++
  new chipyard.config.WithPeripheryBusFrequency(1000.0) ++
  new chipyard.config.WithFrontBusFrequency(1000.0) ++
  new chipyard.config.WithControlBusFrequency(1000.0) ++
  new chipyard.config.AbstractConfig)

class RocketConfig extends Config(
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++         // single rocket-core
  new chipyard.config.AbstractConfig)

class DualRocketConfig extends Config(
  new freechips.rocketchip.subsystem.WithNBigCores(2) ++
  new chipyard.config.AbstractConfig)

class TinyRocketConfig extends Config(
  new chipyard.harness.WithDontTouchChipTopPorts(false) ++        // TODO FIX: Don't dontTouch the ports
  new testchipip.soc.WithNoScratchpads ++                         // All memory is the Rocket TCMs
  new freechips.rocketchip.subsystem.WithIncoherentBusTopology ++ // use incoherent bus topology
  new freechips.rocketchip.subsystem.WithNBanks(0) ++             // remove L2$
  new freechips.rocketchip.subsystem.WithNoMemPort ++             // remove backing memory
  new freechips.rocketchip.subsystem.With1TinyCore ++             // single tiny rocket-core
  new chipyard.config.AbstractConfig)

class QuadRocketConfig extends Config(
  new freechips.rocketchip.subsystem.WithNBigCores(4) ++    // quad-core (4 RocketTiles)
  new chipyard.config.AbstractConfig)

class Cloned64RocketConfig extends Config(
  new freechips.rocketchip.subsystem.WithCloneRocketTiles(63, 0) ++ // copy tile0 63 more times
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++            // tile0 is a BigRocket
  new chipyard.config.AbstractConfig)

class RV32RocketConfig extends Config(
  new freechips.rocketchip.subsystem.WithRV32 ++            // set RocketTiles to be 32-bit
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

// DOC include start: l1scratchpadrocket
class ScratchpadOnlyRocketConfig extends Config(
  new chipyard.config.WithL2TLBs(0) ++
  new testchipip.soc.WithNoScratchpads ++                      // remove subsystem scratchpads, confusingly named, does not remove the L1D$ scratchpads
  new freechips.rocketchip.subsystem.WithNBanks(0) ++
  new freechips.rocketchip.subsystem.WithNoMemPort ++          // remove offchip mem port
  new freechips.rocketchip.subsystem.WithScratchpadsOnly ++    // use rocket l1 DCache scratchpad as base phys mem
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)
// DOC include end: l1scratchpadrocket

class MMIOScratchpadOnlyRocketConfig extends Config(
  new freechips.rocketchip.subsystem.WithDefaultMMIOPort ++  // add default external master port
  new freechips.rocketchip.subsystem.WithDefaultSlavePort ++ // add default external slave port
  new ScratchpadOnlyRocketConfig
)

class L1ScratchpadRocketConfig extends Config(
  new chipyard.config.WithRocketICacheScratchpad ++         // use rocket ICache scratchpad
  new chipyard.config.WithRocketDCacheScratchpad ++         // use rocket DCache scratchpad
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class MulticlockRocketConfig extends Config(
  new freechips.rocketchip.subsystem.WithAsynchronousRocketTiles(3, 3) ++ // Add async crossings between RocketTile and uncore
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  // Frequency specifications
  new chipyard.config.WithTileFrequency(1000.0) ++        // Matches the maximum frequency of U540
  new chipyard.clocking.WithClockGroupsCombinedByName(("uncore"   , Seq("sbus", "cbus", "implicit", "clock_tap"), Nil),
                                                      ("periphery", Seq("pbus", "fbus"), Nil)) ++
  new chipyard.config.WithSystemBusFrequency(500.0) ++    // Matches the maximum frequency of U540
  new chipyard.config.WithMemoryBusFrequency(500.0) ++    // Matches the maximum frequency of U540
  new chipyard.config.WithPeripheryBusFrequency(500.0) ++ // Matches the maximum frequency of U540
  //  Crossing specifications
  new chipyard.config.WithFbusToSbusCrossingType(AsynchronousCrossing()) ++ // Add Async crossing between FBUS and SBUS
  new chipyard.config.WithCbusToPbusCrossingType(AsynchronousCrossing()) ++ // Add Async crossing between PBUS and CBUS
  new chipyard.config.WithSbusToMbusCrossingType(AsynchronousCrossing()) ++ // Add Async crossings between backside of L2 and MBUS
  new chipyard.config.AbstractConfig)

class CustomIOChipTopRocketConfig extends Config(
  new chipyard.example.WithBrokenOutUARTIO ++
  new chipyard.example.WithCustomChipTop ++
  new chipyard.example.WithCustomIOCells ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++         // single rocket-core
  new chipyard.config.AbstractConfig)

class PrefetchingRocketConfig extends Config(
  new barf.WithHellaCachePrefetcher(Seq(0), barf.SingleStridedPrefetcherParams()) ++   // strided prefetcher, sits in front of the L1D$, monitors core requests to prefetching into the L1D$
  new barf.WithTLICachePrefetcher(barf.MultiNextLinePrefetcherParams()) ++             // next-line prefetcher, sits between L1I$ and L2, monitors L1I$ misses to prefetch into L2
  new barf.WithTLDCachePrefetcher(barf.SingleAMPMPrefetcherParams()) ++                // AMPM prefetcher, sits between L1D$ and L2, monitors L1D$ misses to prefetch into L2
  new chipyard.config.WithTilePrefetchers ++                                           // add TL prefetchers between tiles and the sbus
  new freechips.rocketchip.subsystem.WithNonblockingL1(2) ++                           // non-blocking L1D$, L1 prefetching only works with non-blocking L1D$
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++                               // single rocket-core
  new chipyard.config.AbstractConfig)

class ClusteredRocketConfig extends Config(
  new freechips.rocketchip.subsystem.WithNBigCores(4, location=InCluster(1)) ++
  new freechips.rocketchip.subsystem.WithNBigCores(4, location=InCluster(0)) ++
  new freechips.rocketchip.subsystem.WithCluster(1) ++
  new freechips.rocketchip.subsystem.WithCluster(0) ++
  new chipyard.config.AbstractConfig)

class FastRTLSimRocketConfig extends Config(
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new chipyard.RocketConfig)
