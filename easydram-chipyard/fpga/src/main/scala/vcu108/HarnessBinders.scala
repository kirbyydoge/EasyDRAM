package chipyard.fpga.vcu108

import chisel3._
import chisel3.experimental.{Analog, attach}

import org.chipsalliance.diplomacy.nodes.HeterogeneousBag

import chipyard.harness._
import chipyard.iobinders._
import sifive.fpgashells.shell.IOPin

/*** EasyDRAM ***/
class WithEasyDRAMFPGAHarness extends HarnessBinder({
  case (th: VCU108FPGATestHarnessImp, port: EasyDRAMPort, chipId: Int) => {
    val c0_sys_clk_p = IO(Input(Clock())).suggestName("easydram_c0_sys_clk_p")
    val c0_sys_clk_n = IO(Input(Clock())).suggestName("easydram_c0_sys_clk_n")
    val c0_ddr4_adr = IO(Output(chiselTypeOf(port.io.c0_ddr4_adr))).suggestName("easydram_c0_ddr4_adr")
    val c0_ddr4_ba = IO(Output(chiselTypeOf(port.io.c0_ddr4_ba))).suggestName("easydram_c0_ddr4_ba")
    val c0_ddr4_act_n = IO(Output(chiselTypeOf(port.io.c0_ddr4_act_n))).suggestName("easydram_c0_ddr4_act_n")
    val c0_ddr4_bg = IO(Output(chiselTypeOf(port.io.c0_ddr4_bg))).suggestName("easydram_c0_ddr4_bg")
    val c0_ddr4_reset_n = IO(Output(chiselTypeOf(port.io.c0_ddr4_reset_n))).suggestName("easydram_c0_ddr4_reset_n")
    val c0_ddr4_ck_c = IO(Output(chiselTypeOf(port.io.c0_ddr4_ck_c))).suggestName("easydram_c0_ddr4_ck_c")
    val c0_ddr4_ck_t = IO(Output(chiselTypeOf(port.io.c0_ddr4_ck_t))).suggestName("easydram_c0_ddr4_ck_t")
    val c0_ddr4_cke = IO(Output(chiselTypeOf(port.io.c0_ddr4_cke))).suggestName("easydram_c0_ddr4_cke")
    val c0_ddr4_cs_n = IO(Output(chiselTypeOf(port.io.c0_ddr4_cs_n))).suggestName("easydram_c0_ddr4_cs_n")
    val c0_ddr4_odt = IO(Output(chiselTypeOf(port.io.c0_ddr4_odt))).suggestName("easydram_c0_ddr4_odt")
    val c0_ddr4_dqs_c = IO(Analog(port.io.p.dqsWidth.W)).suggestName("easydram_c0_ddr4_dqs_c")
    val c0_ddr4_dqs_t = IO(Analog(port.io.p.dqsWidth.W)).suggestName("easydram_c0_ddr4_dqs_t")
    val c0_ddr4_dq = IO(Analog(port.io.p.dqWidth.W)).suggestName("easydram_c0_ddr4_dq")
    val c0_ddr4_dm_dbi_n = IO(Analog(port.io.p.dmWidth.W)).suggestName("easydram_c0_ddr4_dm_dbi_n")

    port.io.c0_sys_clk_p := c0_sys_clk_p
    port.io.c0_sys_clk_n := c0_sys_clk_n
    port.io.sys_rst := th.vcu108Outer.pllReset
    port.io.btn_rst := false.B
    c0_ddr4_adr := port.io.c0_ddr4_adr
    c0_ddr4_ba := port.io.c0_ddr4_ba
    c0_ddr4_act_n := port.io.c0_ddr4_act_n
    c0_ddr4_bg := port.io.c0_ddr4_bg
    c0_ddr4_reset_n := port.io.c0_ddr4_reset_n
    c0_ddr4_ck_c := port.io.c0_ddr4_ck_c
    c0_ddr4_ck_t := port.io.c0_ddr4_ck_t
    c0_ddr4_cke := port.io.c0_ddr4_cke
    c0_ddr4_cs_n := port.io.c0_ddr4_cs_n
    c0_ddr4_odt := port.io.c0_ddr4_odt
    attach(c0_ddr4_dqs_c, port.io.c0_ddr4_dqs_c)
    attach(c0_ddr4_dqs_t, port.io.c0_ddr4_dqs_t)
    attach(c0_ddr4_dq, port.io.c0_ddr4_dq)
    attach(c0_ddr4_dm_dbi_n, port.io.c0_ddr4_dm_dbi_n)

    val xdc = th.vcu108Outer.xdc
    xdc.addPackagePin(IOPin.of(c0_sys_clk_p).head, "G31")
    xdc.addPackagePin(IOPin.of(c0_sys_clk_n).head, "F31")
    xdc.addIOStandard(IOPin.of(c0_sys_clk_p).head, "DIFF_SSTL12")
    xdc.addIOStandard(IOPin.of(c0_sys_clk_n).head, "DIFF_SSTL12")

    // Needed for newer Chipyard harnesses.
    val adrPins = Seq("AM27", "AT25", "AN25", "AN26", "AR25", "AU28", "AU27", "AR28", "AP25", "AM26", "AP26", "AN28", "AR27", "AP28", "AL27", "AP27", "AM28")
    val dqPins = Seq(
      "BE30", "BE33", "BD30", "BD33", "BD31", "BC33", "BD32", "BC31", "BA31", "AY33", "BA30", "AW31", "AW32", "BB33", "AY32", "BA32",
      "AT31", "AV31", "AV30", "AU33", "AU31", "AU32", "AW30", "AU34", "AT29", "AT34", "AT30", "AR33", "AR30", "AN30", "AP30", "AN31",
      "BF34", "BF36", "BC35", "BE37", "BE34", "BD36", "BF37", "BC36", "BD37", "BE38", "BD38", "BD40", "BB38", "BB39", "BC39", "BC38",
      "AW40", "BA40", "AY39", "AY38", "AY40", "BA39", "BB36", "BB37", "AV38", "AU38", "AU39", "AW35", "AU40", "AV40", "AW36", "AV39")
    val dqsCPins = Seq("BF31", "BA34", "AV29", "AP32", "BF35", "BF39", "BA36", "AW38")
    val dqsTPins = Seq("BF30", "AY34", "AU29", "AP31", "BE35", "BE39", "BA35", "AW37")
    val dmPins = Seq("BE32", "BB31", "AV33", "AR32", "BC34", "BE40", "AY37", "AV35")

    (IOPin.of(c0_ddr4_adr) zip adrPins).foreach { case (pin, loc) => xdc.addPackagePin(pin, loc) }
    (IOPin.of(c0_ddr4_ba) zip Seq("AU26", "AV26")).foreach { case (pin, loc) => xdc.addPackagePin(pin, loc) }
    xdc.addPackagePin(IOPin.of(c0_ddr4_act_n).head, "AW28")
    xdc.addPackagePin(IOPin.of(c0_ddr4_bg).head, "AV28")
    xdc.addPackagePin(IOPin.of(c0_ddr4_reset_n).head, "BF40")
    xdc.addPackagePin(IOPin.of(c0_ddr4_ck_c).head, "AT27")
    xdc.addPackagePin(IOPin.of(c0_ddr4_ck_t).head, "AT26")
    xdc.addPackagePin(IOPin.of(c0_ddr4_cke).head, "AY29")
    xdc.addPackagePin(IOPin.of(c0_ddr4_cs_n).head, "AW26")
    xdc.addPackagePin(IOPin.of(c0_ddr4_odt).head, "BB29")
    (IOPin.of(c0_ddr4_dq) zip dqPins).foreach { case (pin, loc) => xdc.addPackagePin(pin, loc) }
    (IOPin.of(c0_ddr4_dqs_c) zip dqsCPins).foreach { case (pin, loc) => xdc.addPackagePin(pin, loc) }
    (IOPin.of(c0_ddr4_dqs_t) zip dqsTPins).foreach { case (pin, loc) => xdc.addPackagePin(pin, loc) }
    (IOPin.of(c0_ddr4_dm_dbi_n) zip dmPins).foreach { case (pin, loc) => xdc.addPackagePin(pin, loc) }
  }
})

/*** UART ***/
class WithUART extends HarnessBinder({
  case (th: VCU108FPGATestHarnessImp, port: UARTPort, chipId: Int) => {
    th.vcu108Outer.io_uart_bb.get.bundle <> port.io
  }
})

/*** SPI ***/
class WithSPISDCard extends HarnessBinder({
  case (th: VCU108FPGATestHarnessImp, port: SPIPort, chipId: Int) => {
    th.vcu108Outer.io_spi_bb.get.bundle <> port.io
  }
})

/*** Experimental DDR ***/
class WithDDRMem extends HarnessBinder({
  case (th: VCU108FPGATestHarnessImp, port: TLMemPort, chipId: Int) => {
    val bundles = th.vcu108Outer.ddrClient.get.out.map(_._1)
    val ddrClientBundle = Wire(new HeterogeneousBag(bundles.map(_.cloneType)))
    bundles.zip(ddrClientBundle).foreach { case (bundle, io) => bundle <> io }
    ddrClientBundle <> port.io
  }
})
