package easydram.debug

import chisel3._
import chisel3.util._
import chisel3.experimental.{Analog, BaseModule}
import freechips.rocketchip.util.{ElaborationArtefacts}

object MarkDebug {
    val NUM_PROBES = 40
    private var counter = 0

    def apply(clock: Clock, signals: Seq[UInt]): ILACore = {
        assert(signals.length <= MarkDebug.NUM_PROBES, s"MarkDebug accepts at most ${MarkDebug.NUM_PROBES} signals")
        val probeWidths = signals map { s => if(s.widthKnown) s.getWidth else 64 }
        MarkDebug(clock, signals, probeWidths)
    }

    def apply(clock: Clock, signals: Seq[UInt], widths: Seq[Int]): ILACore = {
        assert(signals.length <= MarkDebug.NUM_PROBES, s"MarkDebug accepts at most ${MarkDebug.NUM_PROBES} signals")
        val ila = Module(new ILACore(s"ila${counter}", widths ++ Seq.fill(MarkDebug.NUM_PROBES - widths.length + 1)(1)))
        counter += 1
        ila.io.clk := clock
        val probes = Seq(
            ila.io.probe0,
            ila.io.probe1,
            ila.io.probe2,
            ila.io.probe3,
            ila.io.probe4,
            ila.io.probe5,
            ila.io.probe6,
            ila.io.probe7,
            ila.io.probe8,
            ila.io.probe9,
            ila.io.probe10,
            ila.io.probe11,
            ila.io.probe12,
            ila.io.probe13,
            ila.io.probe14,
            ila.io.probe15,
            ila.io.probe16,
            ila.io.probe17,
            ila.io.probe18,
            ila.io.probe19,
            ila.io.probe20,
            ila.io.probe21,
            ila.io.probe22,
            ila.io.probe23,
            ila.io.probe24,
            ila.io.probe25,
            ila.io.probe26,
            ila.io.probe27,
            ila.io.probe28,
            ila.io.probe29,
            ila.io.probe30,
            ila.io.probe31,
            ila.io.probe32,
            ila.io.probe33,
            ila.io.probe34,
            ila.io.probe35,
            ila.io.probe36,
            ila.io.probe37,
            ila.io.probe38,
            ila.io.probe39
        )
        signals zip probes foreach { case (s, p) =>
            p := s 
        }
        ila
    }
}

class ILACoreIO(probeWidths: Seq[Int]) extends Bundle {
    val clk     = Input(Clock())
    val probe0  = Input(UInt(probeWidths(0).W))
    val probe1  = Input(UInt(probeWidths(1).W))
    val probe2  = Input(UInt(probeWidths(2).W))
    val probe3  = Input(UInt(probeWidths(3).W))
    val probe4  = Input(UInt(probeWidths(4).W))
    val probe5  = Input(UInt(probeWidths(5).W))
    val probe6  = Input(UInt(probeWidths(6).W))
    val probe7  = Input(UInt(probeWidths(7).W))
    val probe8  = Input(UInt(probeWidths(8).W))
    val probe9  = Input(UInt(probeWidths(9).W))
    val probe10 = Input(UInt(probeWidths(10).W))
    val probe11 = Input(UInt(probeWidths(11).W))
    val probe12 = Input(UInt(probeWidths(12).W))
    val probe13 = Input(UInt(probeWidths(13).W))
    val probe14 = Input(UInt(probeWidths(14).W))
    val probe15 = Input(UInt(probeWidths(15).W))
    val probe16 = Input(UInt(probeWidths(16).W))
    val probe17 = Input(UInt(probeWidths(17).W))
    val probe18 = Input(UInt(probeWidths(18).W))
    val probe19 = Input(UInt(probeWidths(19).W))
    val probe20 = Input(UInt(probeWidths(20).W))
    val probe21 = Input(UInt(probeWidths(21).W))
    val probe22 = Input(UInt(probeWidths(22).W))
    val probe23 = Input(UInt(probeWidths(23).W))
    val probe24 = Input(UInt(probeWidths(24).W))
    val probe25 = Input(UInt(probeWidths(25).W))
    val probe26 = Input(UInt(probeWidths(26).W))
    val probe27 = Input(UInt(probeWidths(27).W))
    val probe28 = Input(UInt(probeWidths(28).W))
    val probe29 = Input(UInt(probeWidths(29).W))
    val probe30 = Input(UInt(probeWidths(30).W))
    val probe31 = Input(UInt(probeWidths(31).W))
    val probe32 = Input(UInt(probeWidths(32).W))
    val probe33 = Input(UInt(probeWidths(33).W))
    val probe34 = Input(UInt(probeWidths(34).W))
    val probe35 = Input(UInt(probeWidths(35).W))
    val probe36 = Input(UInt(probeWidths(36).W))
    val probe37 = Input(UInt(probeWidths(37).W))
    val probe38 = Input(UInt(probeWidths(38).W))
    val probe39 = Input(UInt(probeWidths(39).W))
}

class ILACore(name: String, probeWidths: Seq[Int]) extends BlackBox with HasBlackBoxInline {
    val io = IO(new ILACoreIO(probeWidths))

    override def desiredName = s"BlackBox${name}"

    setInline(s"BlackBox${name}.v",
    s"""`timescale 1ns/1ps
    |
    |module BlackBox${name} (
    |    input clk,
    ${(probeWidths.zip(0 until MarkDebug.NUM_PROBES) map {
        case (w, idx) => s"|    input [${w-1}:0] probe${idx}"}).mkString(",\n")}
    |);
    |
    |ila_debug_${name} debug_${name} (
    |    .clk(clk),
    ${((0 until probeWidths.length) map {
        i => s"|    .probe${i}(probe${i})"}).mkString(",\n")}
    |); 
    |
    |endmodule""".stripMargin)

    ILAGenerator(name, probeWidths)
}

object ILAGenerator {
    def apply(name: String, probeWidths: Seq[Int]) = {
        var builder = s"""
        create_ip -vendor xilinx.com -library ip -name ila -version 6.2 -module_name ila_debug_${name} -dir $$ipdir -force
        set_property -dict [list \\
            CONFIG.ALL_PROBE_SAME_MU          {TRUE} \\
            CONFIG.ALL_PROBE_SAME_MU_CNT      {1} \\
            CONFIG.C_ADV_TRIGGER              {FALSE} \\
            CONFIG.C_CLKFBOUT_MULT_F          {10} \\
            CONFIG.C_CLKOUT0_DIVIDE_F         {10} \\
            CONFIG.C_CLK_FREQ                 {200} \\
            CONFIG.C_CLK_PERIOD               {5} \\
            CONFIG.C_DATA_DEPTH               {32768} \\
            CONFIG.C_DDR_CLK_GEN              {FALSE} \\
            CONFIG.C_DIVCLK_DIVIDE            {3} \\
            CONFIG.C_ENABLE_ILA_AXI_MON       {false} \\
            CONFIG.C_EN_DDR_ILA               {FALSE} \\
            CONFIG.C_EN_STRG_QUAL             {0} \\
            CONFIG.C_EN_TIME_TAG              {0} \\
            CONFIG.C_ILA_CLK_FREQ             {2000000} \\
            CONFIG.C_INPUT_PIPE_STAGES        {0} \\
            CONFIG.C_MONITOR_TYPE             {Native} \\
            CONFIG.C_NUM_MONITOR_SLOTS        {1} \\"""
        builder += s"\n            CONFIG.C_NUM_OF_PROBES            {${probeWidths.length}} \\"
        for (i <- 0 until probeWidths.length) {
            builder += s"\n            CONFIG.C_PROBE${i}_MU_CNT            {1} \\"
            builder += s"\n            CONFIG.C_PROBE${i}_TYPE              {0} \\"
            builder += s"\n            CONFIG.C_PROBE${i}_WIDTH             {${probeWidths(i)}} \\"
        }
        builder += s"""
            CONFIG.C_SLOT_0_AXIS_TDATA_WIDTH  {32} \\
            CONFIG.C_SLOT_0_AXIS_TDEST_WIDTH  {1} \\
            CONFIG.C_SLOT_0_AXIS_TID_WIDTH    {1} \\
            CONFIG.C_SLOT_0_AXIS_TUSER_WIDTH  {1} \\
            CONFIG.C_SLOT_0_AXI_ADDR_WIDTH    {32} \\
            CONFIG.C_SLOT_0_AXI_ARUSER_WIDTH  {1} \\
            CONFIG.C_SLOT_0_AXI_AWUSER_WIDTH  {1} \\
            CONFIG.C_SLOT_0_AXI_BUSER_WIDTH   {1} \\
            CONFIG.C_SLOT_0_AXI_DATA_WIDTH    {32} \\
            CONFIG.C_SLOT_0_AXI_ID_WIDTH      {1} \\
            CONFIG.C_SLOT_0_AXI_PROTOCOL      {AXI4} \\
            CONFIG.C_SLOT_0_AXI_RUSER_WIDTH   {1} \\
            CONFIG.C_SLOT_0_AXI_WUSER_WIDTH   {1} \\
            CONFIG.C_TIME_TAG_WIDTH           {32} \\
            CONFIG.C_TRIGIN_EN                {false} \\
            CONFIG.C_TRIGOUT_EN               {false} \\
            CONFIG.C_XLNX_HW_PROBE_INFO       {DEFAULT} \\
            CONFIG.EN_BRAM_DRC                {TRUE} \\
            CONFIG.SIGNAL_CLOCK.FREQ_HZ       {100000000} \\
            CONFIG.SIGNAL_CLOCK.INSERT_VIP    {0} \\
            CONFIG.SLOT_0_AXI.INSERT_VIP      {0} \\
            CONFIG.SLOT_0_AXIS.INSERT_VIP     {0} \\
        ] [get_ips ila_debug_${name}]
        """
        ElaborationArtefacts.add( s"iladebug${name}.vivado.tcl", builder)
    }
}