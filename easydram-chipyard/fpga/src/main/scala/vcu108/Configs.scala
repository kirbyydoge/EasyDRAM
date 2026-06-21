package chipyard.fpga.vcu108

import sys.process._

import org.chipsalliance.cde.config.{Config, Parameters}
import freechips.rocketchip.subsystem.{SystemBusKey, PeripheryBusKey, ControlBusKey, ExtMem}
import freechips.rocketchip.devices.tilelink.{DevNullParams, BootROMLocated}
import freechips.rocketchip.diplomacy.DTSTimebase
import freechips.rocketchip.subsystem.ExtMem
import sifive.blocks.devices.spi.{PeripherySPIKey, SPIParams}
import sifive.blocks.devices.uart.{PeripheryUARTKey, UARTParams}
import testchipip.serdes.SerialTLKey

class WithDefaultPeripherals extends Config((site, here, up) => {
  case PeripheryUARTKey => List(UARTParams(address = BigInt(0x64000000L), stopBits=1, nTxEntries = 64, nRxEntries = 64))
  case PeripherySPIKey => List(SPIParams(rAddress = BigInt(0x64001000L)))
  case VCU108ShellPMOD => "SDIO"
})

class WithSystemModifications extends Config((site, here, up) => {
  case DTSTimebase => BigInt((1e6).toLong)
  case BootROMLocated(x) => up(BootROMLocated(x), site).map { p =>
    // invoke makefile for EasyDRAM frontend
    val freqMHz = (site(SystemBusKey).dtsFrequency.get / (1000 * 1000)).toLong
    val make = s"make -C fpga/src/main/resources/vcu108/boot PBUS_CLK=${freqMHz} bin"
    require (make.! == 0, "Failed to build bootrom")
    p.copy(hang = 0x10000, contentFileName = s"./fpga/src/main/resources/vcu108/boot/build/frontend.bin")
  }
  case ExtMem => up(ExtMem, site).map(x => x.copy(master = x.master.copy(size = site(VCU108DDRSize)))) // set extmem to DDR size
  case SerialTLKey => Nil // remove serialized tl port
})

// DOC include start: AbstractVCU108 and Rocket
class WithVCU108Tweaks extends Config(
  // harness binders
  new WithUART ++
  new WithSPISDCard ++
  new WithDDRMem ++
  // other configuration
  new WithDefaultPeripherals ++
  new chipyard.config.WithTLBackingMemory ++ // use TL backing memory
  new WithSystemModifications ++ // setup busses, use EasyDRAM frontend bootrom, setup ext. mem. size
  new chipyard.config.WithNoDebug ++ // remove debug module
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new freechips.rocketchip.subsystem.WithNMemoryChannels(1))

// Default Configs
class PrefetchingRocketVCU108Config extends Config(
  new WithFPGAFrequency(100) ++
  new WithVCU108Tweaks ++
  new chipyard.PrefetchingRocketConfig)

class RocketVCU108Config extends Config(
  new WithFPGAFrequency(100) ++
  new WithVCU108Tweaks ++
  new chipyard.RocketConfig)

class PrefetchingBoomVCU108Config extends Config(
  new WithFPGAFrequency(50) ++
  new WithVCU108Tweaks ++
  new chipyard.MegaBoomV3Config)

class BoomVCU108Config extends Config(
  new WithFPGAFrequency(50) ++
  new WithVCU108Tweaks ++
  new chipyard.SmallBoomV3Config)

class WithVCU108EasyDRAMHarnessBinders extends Config((site, here, up) => {
  case chipyard.harness.HarnessBinders => ({ case _ => }: chipyard.harness.HarnessBinderFunction)
})

class EasyRocketVCU108Config extends Config(
  new WithFPGAFrequency(100) ++
  new WithUART ++
  new WithEasyDRAMFPGAHarness ++
  new chipyard.harness.WithSerialTLTiedOff ++
  new chipyard.harness.WithTiedOffJTAG ++
  new chipyard.harness.WithCustomBootPinPlusArg ++
  new chipyard.harness.WithClockFromHarness ++
  new chipyard.harness.WithResetFromHarness ++
  new WithVCU108EasyDRAMHarnessBinders ++
  new easydram.WithEasyMemory(
    memBase=BigInt("80000000", 16), memSize=BigInt("0FFFFFFF", 16),
    instBase=BigInt("40000000", 16), instSize=BigInt("00FFFFFF", 16),
    cmdBase=BigInt("41000000", 16), cmdSize=BigInt("00FFFFFF", 16),
    tileFreq=100000000, mcFreq=100000000
  ) ++
  new freechips.rocketchip.subsystem.WithNoMemPort ++
  new chipyard.iobinders.WithEasyDRAMPunchthrough ++
  new freechips.rocketchip.subsystem.WithAsynchronousRocketTiles(3, 3) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.WithTileFrequency(100.0) ++
  new chipyard.config.AbstractConfig)

class EasyBoomVCU108Config extends Config(
  new WithFPGAFrequency(100) ++
  new WithUART ++
  new WithEasyDRAMFPGAHarness ++
  new chipyard.harness.WithSerialTLTiedOff ++
  new chipyard.harness.WithTiedOffJTAG ++
  new chipyard.harness.WithCustomBootPinPlusArg ++
  new chipyard.harness.WithClockFromHarness ++
  new chipyard.harness.WithResetFromHarness ++
  new WithVCU108EasyDRAMHarnessBinders ++
  new easydram.WithEasyMemory(
    memBase=BigInt("80000000", 16), memSize=BigInt("0FFFFFFF", 16),
    instBase=BigInt("40000000", 16), instSize=BigInt("00FFFFFF", 16),
    cmdBase=BigInt("41000000", 16), cmdSize=BigInt("00FFFFFF", 16),
    tileFreq=100000000, mcFreq=100000000
  ) ++
  new freechips.rocketchip.subsystem.WithNoMemPort ++
  new chipyard.iobinders.WithEasyDRAMPunchthrough ++
  new freechips.rocketchip.subsystem.WithAsynchronousRocketTiles(3, 3) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.WithTileFrequency(100.0) ++
  new chipyard.config.AbstractConfig)

class WithFPGAFrequency(fMHz: Double) extends Config(
  new chipyard.harness.WithAllClocksFromHarnessClockInstantiator ++
  new chipyard.clocking.WithPassthroughClockGenerator ++
  new chipyard.harness.WithHarnessBinderClockFreqMHz(fMHz) ++
  new chipyard.config.WithSystemBusFrequency(fMHz) ++
  new chipyard.config.WithPeripheryBusFrequency(fMHz) ++
  new chipyard.config.WithControlBusFrequency(fMHz) ++
  new chipyard.config.WithFrontBusFrequency(fMHz) ++
  new chipyard.config.WithMemoryBusFrequency(fMHz))

class WithFPGAFreq25MHz extends WithFPGAFrequency(25)

class WithFPGAFreq50MHz extends WithFPGAFrequency(50)

class WithFPGAFreq75MHz extends WithFPGAFrequency(75)

class WithFPGAFreq100MHz extends WithFPGAFrequency(100)

class Rocket4VCU108Config extends Config(
  new WithVCU108Tweaks ++
  new freechips.rocketchip.subsystem.WithNBigCores(4) ++         // 4 rocket-core
  new chipyard.config.AbstractConfig)

class Rocket32VCU108Config extends Config(
  new WithVCU108Tweaks ++
  new freechips.rocketchip.subsystem.WithNBigCores(32) ++         // 32 rocket-core
  new chipyard.config.AbstractConfig)
