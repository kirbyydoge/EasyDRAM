package easydram

import chisel3._
import chisel3.util._
import chisel3.experimental.{Analog, attach}

import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.tile._
import freechips.rocketchip.util._
import freechips.rocketchip.prci._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.rocket._

import org.chipsalliance.cde.config.{Config, Field, Parameters}

import easydram.drambender._
import easydram.bramblackbox._
import easydram.debug._
import easydram.kasirgauartblackbox._

import scala.collection.mutable.ArrayBuffer

object EasyVisibilityNode extends TLEphemeralNode()(ValName("easy-visibility"))

case class EasyMemoryConfig(
  memBase: BigInt, memSize: BigInt,
  instBase: BigInt, instSize: BigInt,
  cmdBase: BigInt, cmdSize: BigInt,
  debug: Boolean, simulation: Boolean,
  scaled: Boolean = true,
  tileFreq: BigInt, mcFreq: BigInt)
case object EasyMemoryKey extends Field[Option[EasyMemoryConfig]](None)

case class EasyAddressSet(base: UInt, mask: UInt)

class EasyDRAMTopIO(val p: DRAMBenderParams) extends Bundle {
  val c0_sys_clk_p                = Input(Clock())
  val c0_sys_clk_n                = Input(Clock())
  val sys_rst                     = Input(Bool())
  val btn_rst                     = Input(Bool())
  val mc_tx                       = Output(Bool())

  val c0_ddr4_act_n               = Output(Bool())
  val c0_ddr4_adr                 = Output(UInt(p.addrWidth.W))
  val c0_ddr4_ba                  = Output(UInt(p.bankWidth.W))
  val c0_ddr4_bg                  = Output(UInt(p.bankGroupWidth.W))
  val c0_ddr4_cke                 = Output(UInt(p.ckeWidth.W))
  val c0_ddr4_odt                 = Output(UInt(p.odtWidth.W))
  val c0_ddr4_cs_n                = Output(UInt(p.csWidth.W))
  val c0_ddr4_ck_t                = Output(UInt(p.ckWidth.W))
  val c0_ddr4_ck_c                = Output(UInt(p.ckWidth.W))
  val c0_ddr4_reset_n             = Output(Bool())

  // val c0_ddr4_dqs_c               = Analog(p.dqsWidth.W)
  // val c0_ddr4_dqs_t               = Analog(p.dqsWidth.W)
  // val c0_ddr4_dq                  = Analog(p.dqWidth.W)
  // val c0_ddr4_dm_dbi_n            = Analog(p.dmWidth.W)
  val c0_ddr4_parity              = Output(Bool())
}

trait HasEasyDRAMTopIO {
  def io: EasyDRAMTopIO
}

case class EasyMemBusNodeParams(beatBytes: Int, minTransferBytes: Int, maxTransferBytes: Int)

class EasyMemory()(implicit p: Parameters) extends LazyModule {
  val device = new SimpleDevice("easy-mem-device", Seq("easydram,easy-mem-device0"))
  val config = p(EasyMemoryKey).get
  val memNodeParams = EasyMemBusNodeParams(8, 1, 64)
  val instNodeParams = EasyMemBusNodeParams(8, 1, 64)
  val cmdNodeParams = EasyMemBusNodeParams(8, 1, 64)
  val memNode = TLManagerNode(Seq(TLSlavePortParameters.v1(
    managers = Seq(TLSlaveParameters.v1(
      address = Seq(AddressSet(config.memBase, config.memSize)),
      resources = device.reg,
      regionType = RegionType.UNCACHED,
      executable = true,
      supportsGet = TransferSizes(memNodeParams.minTransferBytes, memNodeParams.maxTransferBytes),
      supportsPutFull = TransferSizes(memNodeParams.minTransferBytes, memNodeParams.maxTransferBytes),
      supportsPutPartial = TransferSizes(memNodeParams.minTransferBytes, memNodeParams.maxTransferBytes),
      fifoId = Some(0)
    )),
    beatBytes = memNodeParams.beatBytes
  )))
  val instNode = TLManagerNode(Seq(TLSlavePortParameters.v1(
    managers = Seq(TLSlaveParameters.v1(
      address = Seq(AddressSet(config.instBase, config.instSize)),
      resources = device.reg,
      regionType = RegionType.UNCACHED,
      executable = true,
      supportsGet = TransferSizes(instNodeParams.minTransferBytes, instNodeParams.maxTransferBytes),
      supportsPutFull = TransferSizes(instNodeParams.minTransferBytes, instNodeParams.maxTransferBytes),
      supportsPutPartial = TransferSizes(instNodeParams.minTransferBytes, instNodeParams.maxTransferBytes),
      fifoId = Some(0)
    )),
    beatBytes = instNodeParams.beatBytes
  )))
  val cmdNode = TLManagerNode(Seq(TLSlavePortParameters.v1(
    managers = Seq(TLSlaveParameters.v1(
      address = Seq(AddressSet(config.cmdBase, config.cmdSize)),
      resources = device.reg,
      regionType = RegionType.UNCACHED,
      executable = true,
      supportsGet = TransferSizes(cmdNodeParams.minTransferBytes, cmdNodeParams.maxTransferBytes),
      supportsPutFull = TransferSizes(cmdNodeParams.minTransferBytes, cmdNodeParams.maxTransferBytes),
      supportsPutPartial = TransferSizes(cmdNodeParams.minTransferBytes, cmdNodeParams.maxTransferBytes),
      fifoId = Some(0)
    )),
    beatBytes = cmdNodeParams.beatBytes
  )))
  val gateNode = TLClientNode(Seq(TLMasterPortParameters.v1(
    Seq(TLClientParameters(
      name = "easy-gate-node",
      sourceId = IdRange(0, 2))))))
  val dummyOut = TLManagerNode(Seq(TLSlavePortParameters.v1(
    managers = Seq(TLSlaveParameters.v1(
      address = Seq(AddressSet(config.memBase + config.memSize + 1, config.memSize)),
      resources = device.reg,
      regionType = RegionType.UNCACHED,
      executable = false,
      supportsGet = TransferSizes(memNodeParams.minTransferBytes, memNodeParams.maxTransferBytes),
      supportsPutFull = TransferSizes(memNodeParams.minTransferBytes, memNodeParams.maxTransferBytes),
      supportsPutPartial = TransferSizes(memNodeParams.minTransferBytes, memNodeParams.maxTransferBytes),
      fifoId = Some(0)
    )),
    beatBytes = memNodeParams.beatBytes
  )))
  val dummyIn = TLClientNode(Seq(TLMasterPortParameters.v1(
    Seq(TLMasterParameters.v1(
      name = "easyDummyIn",
      sourceId = IdRange(0, 4),
      requestFifo = true,
      visibility = Seq(AddressSet(0x10000, 0xffff)))))))

  lazy val module = new EasyMemoryModuleImp(this)
}

class EasyTLReq(channel: TLBundleA, dataWidth: Int) extends Bundle {
  val msg = channel.cloneType
  val msgData = Vec(dataWidth / 64, UInt(64.W))
  val msgTick = UInt(64.W)
}

class RWPort(maskWidth: Int, addrWidth: Int) extends Bundle {
  val write = Input(Bool())
  val mask = Input(Vec(maskWidth, Bool()))
  val addr = Input(UInt(addrWidth.toInt.W))
  val dataIn = Input(Vec(maskWidth, UInt(8.W)))
  val dataOut = Output(Vec(maskWidth, UInt(8.W)))
}

trait HasDualRWPorts {
  def width: Int
  def depth: Int
  val maskWidth = width / 8
  val maskBits = (scala.math.log10(maskWidth)/scala.math.log10(2.0)).toInt
  val addrWidth = scala.math.log10(depth*width)/scala.math.log10(2.0)
  val io = IO(new Bundle {
    val port0 = new RWPort(maskWidth, addrWidth.toInt)
    val port1 = new RWPort(maskWidth, addrWidth.toInt)
  })
}

case class MemDualRWPortBB(width: Int, depth: Int, initFile: String = "")
  extends Module with HasDualRWPorts {
  
  val mem = Module(new BramDualPort(width, depth, initFile))

  // Port0
  val port0ByteOffset = io.port0.addr(maskBits-1, 0)
  val port0AllignedAddr = io.port0.addr >> maskBits
  val port0AllignedMask = Wire(UInt(8.W))
  port0AllignedMask := io.port0.mask.asUInt << port0ByteOffset

  mem.io.clk_i := clock
  mem.io.p0_data_i := io.port0.dataIn.asUInt
  mem.io.p0_addr_i := port0AllignedAddr
  mem.io.p0_mask_i := port0AllignedMask
  mem.io.p0_wr_en_i := io.port0.write
  mem.io.p0_cmd_en_i := true.B
  io.port0.dataOut := (0 to width/8 - 1).map(i => mem.io.p0_data_o(i * 8 + 7, i * 8))

  // Port1
  val port1ByteOffset = io.port1.addr(maskBits-1, 0)
  val port1AllignedAddr = io.port1.addr >> maskBits
  val port1AllignedMask = Wire(UInt(8.W))
  port1AllignedMask := io.port1.mask.asUInt << port1ByteOffset

  mem.io.p1_data_i := io.port1.dataIn.asUInt
  mem.io.p1_addr_i := port1AllignedAddr
  mem.io.p1_mask_i := port1AllignedMask
  mem.io.p1_wr_en_i := io.port1.write
  mem.io.p1_cmd_en_i := true.B
  io.port1.dataOut := (0 to width/8 - 1).map(i => mem.io.p1_data_o(i * 8 + 7, i * 8))
}

case class MemDualRWPort(width: Int, depth: Int)
  extends Module with HasDualRWPorts {

  val mem = SyncReadMem(depth, Vec(maskWidth, UInt(8.W)))

  // Port0
  val port0ByteOffset = io.port0.addr(maskBits-1, 0)
  val port0AllignedAddr = io.port0.addr >> maskBits
  val port0AllignedMask = Wire(UInt(8.W))

  port0AllignedMask := io.port0.mask.asUInt << port0ByteOffset
  when (io.port0.write) {
    mem.write(port0AllignedAddr, io.port0.dataIn, port0AllignedMask.asBools)
  }
  io.port0.dataOut := mem.read(port0AllignedAddr, true.B)

  // Port1
  val port1ByteOffset = io.port1.addr(maskBits-1, 0)
  val port1AllignedAddr = io.port1.addr >> maskBits
  val port1AllignedMask = Wire(UInt(8.W))

  port1AllignedMask := io.port1.mask.asUInt << port1ByteOffset
  when (io.port1.write) {
    mem.write(port1AllignedAddr, io.port1.dataIn, port1AllignedMask.asBools)
  }
  io.port1.dataOut := mem.read(port1AllignedAddr, true.B)
}

case class EasyMappedRegister(addrSet: EasyAddressSet, wrFun: UInt => Any, rdFun: () => UInt, description: String = "Unspecified")

class EasyMemoryModuleImp(outer: EasyMemory) extends LazyModuleImp(outer) with HasEasyDRAMTopIO {
  val io = IO(new EasyDRAMTopIO(new DRAMBenderParams))
  val isDebug = outer.config.debug
  val isSimulation = outer.config.simulation
  val isTimeScaled = outer.config.scaled
  val tileFreq = outer.config.tileFreq
  val clkFreq = outer.config.mcFreq
  val clkPeriodNs = 1000000000 / clkFreq
  val baseFreqRatio = clkFreq / tileFreq

  println("*** EASYDRAM CLOCK CONFIG ***")
  println(s"MC Frequency: ${clkFreq}")
  println(s"Tile Frequency: ${tileFreq}")
  println(s"Base Frequency Ratio: ${baseFreqRatio}")

  val config = p(EasyMemoryKey).get
  val tileConfig = p(TileKey)
  val sm_pass :: sm_pass_stall :: sm_pass_resp :: sm_idle :: sm_resp_wr :: sm_resp_wr_enq :: sm_stall :: Nil = Enum(7)
  val sp_idle :: sp_start :: sp_reset :: sp_send :: sp_done :: Nil = Enum(5)

  val (memMaster, memEdge) = outer.memNode.in(0)
  val (instMaster, instEdge) = outer.instNode.in(0)
  val (cmdMaster, cmdEdge) = outer.cmdNode.in(0)
  val (gateMaster, gateEdge) = outer.gateNode.out(0)
  val masterState = RegInit(sm_idle)
  val memAddr = memMaster.a.bits.address & config.memSize.asUInt(64.W)
  val imemAddr = instMaster.a.bits.address & config.instSize.asUInt(64.W)
  val cmdAddr = cmdMaster.a.bits.address & config.cmdSize.asUInt(64.W)

  // Dram Bender Program FSM
  val progState = RegInit(sp_idle)
  val isBlueprint = RegInit(false.B)

  // Memory Controller Internal Mapping
  // NOTICE that these are different from Chipyard system-wide mappings
  val instMemAddr = EasyAddressSet("h80000000".U(32.W), "h0fffffff".U(32.W))
  val dataMemAddr = EasyAddressSet("h80000000".U(32.W), "h0fffffff".U(32.W))
  val bPrtMemAddr = EasyAddressSet("hf0000000".U(32.W), "h00ffffff".U(32.W))
  val progMemAddr = EasyAddressSet("h30000000".U(32.W), "h0000ffff".U(32.W))

  val instMem = Module(new MemDualRWPortBB(64, 65536))
  instMem.io.port0 <> 0.U.asTypeOf(instMem.io.port0)
  instMem.io.port1 <> 0.U.asTypeOf(instMem.io.port1)
  instMem.io.port0.write := false.B
  instMem.io.port1.write := false.B

  // BluePrint declarations
  val bPrtMem = Module(new MemDualRWPortBB(64, 16384))
  bPrtMem.io.port0 <> 0.U.asTypeOf(bPrtMem.io.port0)
  bPrtMem.io.port1 <> 0.U.asTypeOf(bPrtMem.io.port1)
  bPrtMem.io.port0.write := false.B 
  bPrtMem.io.port1.write := false.B

  val bp_idle :: bp_send :: Nil = Enum(2)
  val bpState = RegInit(bp_idle)

  val NUM_BLUEPRINT_SLOTS = 2

  val bpRegs = RegInit(VecInit(Seq.fill(NUM_BLUEPRINT_SLOTS)({
    val bundle = Wire(new Bundle {
      val valid = Bool()
      val periodCycles = UInt(32.W)
      val startOff = UInt(32.W)
      val progLen = UInt(32.W)
      val periodCounter = UInt(32.W) // We keep seperate counters to evade modulo operations
      val skew = UInt(16.W) // Intended to internally by FSM in the future to reduce collisions
      val ready = Bool() // Program is ready
    })
    bundle := 0.U.asTypeOf(bundle)
    bundle
  })))

  val bpFlush = RegInit(false.B)
  val bpLen = RegInit(0.U(32.W))
  val bpOff = RegInit(0.U(32.W))

  val cfgPassThrough = false
  val dramParams = new DRAMBenderParams(isSynthesis = !isSimulation)
  val drambender = if (isSimulation) Module(new DRAMBenderSim()(dramParams)) else Module(new DRAMBender()(dramParams))

  val fpuOpt = tileConfig.core.fpu.map(params => Module(new FPU(params)(outer.p)))
  val core = Module(new EasyRocket()(p.alterPartial {
    case useVM: Bool => false
    case retireWidth: Int => 1
  }))
  core.io <> 0.U.asTypeOf(core.io)
  fpuOpt foreach { fpu =>
    fpu.io <> 0.U.asTypeOf(fpu.io)
    core.io.fpu :<>= fpu.io.waiveAs[FPUCoreIO](_.cp_req, _.cp_resp)
  }

  // Request Queues
  val tileTicks = RegInit(0.U(64.W))
  val mcTicks = RegInit(0.U(64.W))
  val schedTicks = RegInit(0.U(64.W))

  val pc = WireDefault(instMemAddr.base)
  val pcEnabled = RegInit(false.B)
  val pcEnCtr = RegInit(32.U(8.W))
  val pcSeq = RegInit(false.B)

  val reqBits = Reg(new EasyTLReq(memMaster.a.bits, 512))

  val PARAM_INCOMING_DEPTH = 16
  val incomingReqQ = Module(new Queue(new EasyTLReq(memMaster.a.bits, 512), PARAM_INCOMING_DEPTH))
  incomingReqQ.io <> 0.U.asTypeOf(incomingReqQ.io)
  incomingReqQ.io.deq.ready := false.B
  incomingReqQ.io.enq.valid := false.B
  dontTouch(incomingReqQ.io.deq)
  dontTouch(incomingReqQ.io.enq)

  val PARAM_OUTDOING_DEPTH = 16
  val outgoingReqQ = Module(new Queue(new EasyTLReq(memMaster.a.bits, 512), PARAM_OUTDOING_DEPTH))
  outgoingReqQ.io <> 0.U.asTypeOf(outgoingReqQ.io)
  outgoingReqQ.io.deq.ready := false.B
  outgoingReqQ.io.enq.valid := false.B
  dontTouch(outgoingReqQ.io.deq)
  dontTouch(outgoingReqQ.io.enq)

  val PARAM_RDBACK_DEPTH = 64
  val rdBackQ = Module(new Queue(UInt(dramParams.rdDataWidth.W), PARAM_RDBACK_DEPTH))
  rdBackQ.io <> 0.U.asTypeOf(rdBackQ.io)
  rdBackQ.io.deq.ready := false.B
  
  // Clock Gating Check
  val clockGated = RegInit(false.B)
  val schedOngoing = RegInit(false.B)
  val lookaheadReg = RegInit(20.U)
  
  val unseenReq = (incomingReqQ.io.deq.valid && mcTicks >= incomingReqQ.io.deq.bits.msgTick)
  
  val tileClockCheck = tileTicks < mcTicks || ((tileTicks === mcTicks) && !unseenReq && !schedOngoing)
  // val tileClockLookahead = (tileTicks + lookaheadReg) <= mcTicks
  // val tileClockPast = RegNext(tileClockCheck, init=true.B)
  
  // If the clock is disabled and we are scheduling don't enable tile for small gaps.
  // BUS has ~5 cycle delay for requests to be processed so toggling gating quickly is an issue atm.
  // val tileClockEnable = Mux(clockGated && schedOngoing, tileClockLookahead, tileClockCheck)
  val tileClockEnable = tileClockCheck
  
  val regIsTimeScaled = RegInit(false.B) // RegInit(isTimeScaled) 
  when (regIsTimeScaled) {
    tileTicks := tileTicks + (!clockGated).asUInt
    when (mcTicks <= tileTicks && !unseenReq && !schedOngoing) {
      mcTicks := tileTicks + (!clockGated).asUInt
    }
  }
  .otherwise {
    tileTicks := 0.U
    mcTicks := 0.U
  }

  // Program interface
  val benderProgram = Wire(new Bundle{
    val inst = Decoupled(UInt(dramParams.instWidth.W))
    val instRst = Bool()
    val instLast = Bool()
    val rdback = Flipped(Decoupled(UInt(dramParams.rdDataWidth.W)))
    val rdbackLast = Bool()
    val calibComplete = Bool()
    val benderDone = Bool()
  })
  
  val benderMem = Module(new MemDualRWPortBB(64, 1024))
  benderMem.io <> 0.U.asTypeOf(benderMem.io)
  benderMem.io.port0.write := false.B
  benderMem.io.port1.write := false.B

  val instResp = RegInit(instMaster.d.bits)
  val instRespValid = RegInit(false.B)
  
  val benderCtr = RegInit(0.U(16.W))
  val stallCtr = RegInit(0.U(16.W))
  val calibDone = RegInit(false.B)
  val benderDone = RegInit(true.B)
  val autoRefreshEn = RegInit(true.B)
  val isRefreshing = RegInit(false.B)
  
  val rdbackCtr = RegInit(0.U(16.W))

  def inside(range: EasyAddressSet)(addr: UInt): Bool = {
    range.base === (addr & ~range.mask)
  }

  def chop(data: UInt, granularity: Int) = {
    val chunks = data.getWidth / granularity
    if (chunks > 0) {
      (0 until chunks).map(i => data(i * granularity + granularity - 1, i * granularity))
    }
    else {
      Seq(data)
    }
  }

  // Memory Controller Internal Mapping Setup
  // TODO: Must ve wrapped with a class feels too dumb.
  val internalMemMap = Seq((dataMemAddr, instMem.io.port1), (progMemAddr, benderMem.io.port0), (bPrtMemAddr, bPrtMem.io.port0))

  var internalCmdMap: Seq[EasyMappedRegister] = Nil

  val startReg = RegInit(0.U(32.W))
  val startRegAddr = EasyAddressSet("h40000000".U(32.W), 4.U)
  def startRegWrFun(data: UInt) = { startReg := data }
  def startRegRdFun() = { startReg }
  internalCmdMap :+= EasyMappedRegister(startRegAddr, startRegWrFun, startRegRdFun)

  val stopReg = RegInit(0.U(32.W))
  val stopRegAddr = EasyAddressSet("h40000004".U(32.W), 4.U)
  def stopRegWrFun(data: UInt) = { stopReg := data }
  def stopRegRdFun() = { stopReg }
  internalCmdMap :+= EasyMappedRegister(stopRegAddr, stopRegWrFun, stopRegRdFun)

  val mcReadEmptyAddr = EasyAddressSet("h50000000".U(32.W), 4.U)
  def mcReadEmptyWrFun(data: UInt) = { /* NOP */ }
  def mcReadEmptyRdFun() = { !incomingReqQ.io.deq.valid || mcTicks < incomingReqQ.io.deq.bits.msgTick }
  internalCmdMap :+= EasyMappedRegister(mcReadEmptyAddr, mcReadEmptyWrFun, mcReadEmptyRdFun)
  
  val mcIncomingMetaAddr = EasyAddressSet("h50000004".U(32.W), 4.U)
  def mcIncomingMetaWrFun(data: UInt) = { /* NOP */ }
  def mcIncomingMetaRdFun() = {
    incomingReqQ.io.deq.bits.msg.opcode
  }
  internalCmdMap :+= EasyMappedRegister(mcIncomingMetaAddr, mcIncomingMetaWrFun, mcIncomingMetaRdFun)

  val mcIncomingReqAddrAddr = EasyAddressSet("h50000008".U(32.W), 4.U)
  def mcIncomingReqAddrWrFun(data: UInt) = { /* NOP */ }
  def mcIncomingReqAddrRdFun() = {
    incomingReqQ.io.deq.bits.msg.address
  }
  internalCmdMap :+= EasyMappedRegister(mcIncomingReqAddrAddr, mcIncomingReqAddrWrFun, mcIncomingReqAddrRdFun)

  val mcIncomingReqConsumeAddr = EasyAddressSet("h5000000c".U(32.W), 4.U)
  def mcIncomingReqConsumeWrFun(data: UInt) = {
    incomingReqQ.io.deq.ready := true.B
  }
  def mcIncomingReqConsumeRdFun() = {
    0.U(32.W)
  }
  internalCmdMap :+= EasyMappedRegister(mcIncomingReqConsumeAddr, mcIncomingReqConsumeWrFun, mcIncomingReqConsumeRdFun)
  
  val mcIncomingSourceAddr = EasyAddressSet("h50000034".U(32.W), 4.U)
  def mcIncomingSourceWrFun(data: UInt) = { /* NOP */ }
  def mcIncomingSourceRdFun() = {
    incomingReqQ.io.deq.bits.msg.source
  }
  internalCmdMap :+= EasyMappedRegister(mcIncomingSourceAddr, mcIncomingSourceWrFun, mcIncomingSourceRdFun)
  
  val mcIncomingSizeAddr = EasyAddressSet("h50000038".U(32.W), 4.U)
  def mcIncomingSizeWrFun(data: UInt) = { /* NOP */ }
  def mcIncomingSizeRdFun() = {
    incomingReqQ.io.deq.bits.msg.size
  }
  internalCmdMap :+= EasyMappedRegister(mcIncomingSizeAddr, mcIncomingSizeWrFun, mcIncomingSizeRdFun)
  
  val mcIncomingTickAddr = EasyAddressSet("h50000040".U(32.W), 8.U)
  def mcIncomingTickWrFun(data: UInt) = { /* NOP */ }
  def mcIncomingTickRdFun() = {
    incomingReqQ.io.deq.bits.msgTick
  }
  internalCmdMap :+= EasyMappedRegister(mcIncomingTickAddr, mcIncomingTickWrFun, mcIncomingTickRdFun)

  for (i <- 0 to 7) {
    val mcIncomingReqPartialDataAddr = EasyAddressSet("h50000210".U(32.W) + i.U * 8.U, 8.U)
    def mcIncomingReqPartialDataWrFun(data: UInt) = { /* NOP */ }
    def mcIncomingReqPartialDataRdFun() = {
      incomingReqQ.io.deq.bits.msgData(i)
    }
    internalCmdMap :+= EasyMappedRegister(mcIncomingReqPartialDataAddr, mcIncomingReqPartialDataWrFun, mcIncomingReqPartialDataRdFun)
  }

  val mcWriteFullAddr = EasyAddressSet("h50001100".U(32.W), 4.U)
  def mcWriteFullWrFun(data: UInt) = { /* NOP */ }
  def mcWriteFullRdFun() = { !outgoingReqQ.io.enq.ready }
  internalCmdMap :+= EasyMappedRegister(mcWriteFullAddr, mcWriteFullWrFun, mcWriteFullRdFun)
  
  val outgoingMsg = Reg(new EasyTLReq(memMaster.a.bits, 512))
  val mcOutgoingOpcodeAddr = EasyAddressSet("h50001104".U(32.W), 4.U)
  def mcOutgoingOpcodeWrFun(data: UInt) = { 
    outgoingMsg.msg.opcode := data
  }
  def mcOutgoingOpcodeRdFun() = {
    0.U(32.W)
  }
  internalCmdMap :+= EasyMappedRegister(mcOutgoingOpcodeAddr, mcOutgoingOpcodeWrFun, mcOutgoingOpcodeRdFun)

  val mcOutgoingAddrAddr = EasyAddressSet("h50001108".U(32.W), 4.U)
  def mcOutgoingAddrWrFun(data: UInt) = { 
    outgoingMsg.msg.address := data
  }
  def mcOutgoingAddrRdFun() = { 0.U(32.W) }
  internalCmdMap :+= EasyMappedRegister(mcOutgoingAddrAddr, mcOutgoingAddrWrFun, mcOutgoingAddrRdFun)

  val mcOutgoingReqSendAddr = EasyAddressSet("h5000110c".U(32.W), 4.U)
  def mcOutgoingReqSendWrFun(data: UInt) = {
    outgoingReqQ.io.enq.bits := outgoingMsg
    outgoingReqQ.io.enq.valid := true.B
  }
  def mcOutgoingReqSendRdFun() = { 0.U(32.W) }
  internalCmdMap :+= EasyMappedRegister(mcOutgoingReqSendAddr, mcOutgoingReqSendWrFun, mcOutgoingReqSendRdFun)

  val mcSetSchedAddr = EasyAddressSet("h50001114".U(32.W), 4.U)
  def mcSetSchedWrFun(data: UInt) = { 
    schedOngoing := data(0).asBool
  }
  def mcSetSchedRdFun() = { schedOngoing }
  internalCmdMap :+= EasyMappedRegister(mcSetSchedAddr, mcSetSchedWrFun, mcSetSchedRdFun)
  
  val mcOutgoingSourceAddr = EasyAddressSet("h50001134".U(32.W), 4.U)
  def mcOutgoingSourceWrFun(data: UInt) = { 
    outgoingMsg.msg.source := data
  }
  def mcOutgoingSourceRdFun() = { 0.U(32.W) }
  internalCmdMap :+= EasyMappedRegister(mcOutgoingSourceAddr, mcOutgoingSourceWrFun, mcOutgoingSourceRdFun)
  
  val mcOutgoingSizeAddr = EasyAddressSet("h50001138".U(32.W), 4.U)
  def mcOutgoingSizeWrFun(data: UInt) = { 
    outgoingMsg.msg.size := data
  }
  def mcOutgoingSizeRdFun() = { 0.U(32.W) }
  internalCmdMap :+= EasyMappedRegister(mcOutgoingSizeAddr, mcOutgoingSizeWrFun, mcOutgoingSizeRdFun)
  
  val mcOutgoingTickAddr = EasyAddressSet("h50001140".U(32.W), 8.U)
  def mcOutgoingTickWrFun(data: UInt) = {
    outgoingMsg.msgTick := data
  }
  def mcOutgoingTickRdFun() = { 0.U(32.W) }
  internalCmdMap :+= EasyMappedRegister(mcOutgoingTickAddr, mcOutgoingTickWrFun, mcOutgoingTickRdFun)

  for (i <- 0 to 7) {
    val mcOutgoingReqPartialDataAddr = EasyAddressSet("h50001210".U(32.W) + i.U * 8.U, 8.U)
    def mcOutgoingReqPartialDataWrFun(data: UInt) = {
      outgoingMsg.msgData(i) := data
    }
    def mcOutgoingReqPartialDataRdFun() = { 0.U(32.W) }
    internalCmdMap :+= EasyMappedRegister(mcOutgoingReqPartialDataAddr, mcOutgoingReqPartialDataWrFun, mcOutgoingReqPartialDataRdFun)
  }

  val rdBackValidAddr = EasyAddressSet("h30010000".U(32.W), 4.U)
  def rdBackValidWrFun(data: UInt) = { /* NOP */ }
  def rdBackValidRdFun() = { rdBackQ.io.deq.valid }
  internalCmdMap :+= EasyMappedRegister(rdBackValidAddr, rdBackValidWrFun, rdBackValidRdFun)

  val rdBackConsumeAddr = EasyAddressSet("h30010004".U(32.W), 4.U)
  def rdBackConsumeWrFun(data: UInt) = { rdBackQ.io.deq.ready := true.B }
  def rdBackConsumeRdFun() = { 0.U(32.W) }
  internalCmdMap :+= EasyMappedRegister(rdBackConsumeAddr, rdBackConsumeWrFun, rdBackConsumeRdFun)

  for (i <- 0 to 3) {
    val rdBackPartialDataAddr = EasyAddressSet("h30010008".U(32.W) + i.U * 8.U, 8.U)
    def rdBackPartialDataWrFun(data: UInt) = { /* NOP */ }
    def rdBackPartialDataRdFun() = {
      rdBackQ.io.deq.bits(63 + i * 64, i * 64)
    }
    internalCmdMap :+= EasyMappedRegister(rdBackPartialDataAddr, rdBackPartialDataWrFun, rdBackPartialDataRdFun)
  }

  benderProgram.instRst := false.B
  val progFlush = RegInit(false.B)
  val progFlushAddr = EasyAddressSet("h31000000".U(32.W), 4.U)
  def progFlushWrFun(data: UInt) = { progFlush := true.B }
  def progFlushRdFun() = { 0.U(32.W) }
  internalCmdMap :+= EasyMappedRegister(progFlushAddr, progFlushWrFun, progFlushRdFun)

  val progLen = RegInit(0.U(32.W))
  val progLenAddr = EasyAddressSet("h31000004".U(32.W), 4.U)
  def progLenWrFun(data: UInt) = { progLen := data }
  def progLenRdFun() = { progLen }
  internalCmdMap :+= EasyMappedRegister(progLenAddr, progLenWrFun, progLenRdFun)

  val benderDoneAddr = EasyAddressSet("h31000008".U(32.W), 4.U)
  def benderDoneWrFun(data: UInt) = { /* NOP */ }
  def benderDoneRdFun() = { benderDone }
  internalCmdMap :+= EasyMappedRegister(benderDoneAddr, benderDoneWrFun, benderDoneRdFun)

  val progCycles = RegInit(0.U(32.W))
  val progCyclesAddr = EasyAddressSet("h31000010".U(32.W), 4.U)
  def progCyclesWrFun(data: UInt) = { /* NOP */ }
  def progCyclesRdFun() = { progCycles }
  internalCmdMap :+= EasyMappedRegister(progCyclesAddr, progCyclesWrFun, progCyclesRdFun)

  val progCmdDone = RegInit(0.U(32.W))
  val progCmdDoneAddr = EasyAddressSet("h41000000".U(32.W), 4.U)
  def progCmdDoneWrFun(data: UInt) = { progCmdDone := data }
  def progCmdDoneRdFun() = { progCmdDone }
  internalCmdMap :+= EasyMappedRegister(progCmdDoneAddr, progCmdDoneWrFun, progCmdDoneRdFun)

  internalCmdMap :+= EasyMappedRegister(
    EasyAddressSet("h40000008".U(32.W), 8.U),
    (data: UInt) => { regIsTimeScaled := data },
    () => { regIsTimeScaled },
    "Update or read timescaling status.")

  // Blueprint MC Memory Map
  val bpRegSel = RegInit(0.U(16.W))
  internalCmdMap :+= EasyMappedRegister(
    EasyAddressSet("hf1000000".U(32.W), 8.U),
    (data: UInt) => { bpRegSel := data },
    () => { bpRegSel },
    "Update or read target blueprint register")

  internalCmdMap :+= EasyMappedRegister(
    EasyAddressSet("hf1000008".U(32.W), 8.U),
    (data: UInt) => { bpRegs(bpRegSel).valid := data },
    () => { bpRegs(bpRegSel).valid },
    "Update or read blueprint register validity")

  internalCmdMap :+= EasyMappedRegister(
    EasyAddressSet("hf1000010".U(32.W), 8.U),
    (data: UInt) => { bpRegs(bpRegSel).periodCycles := data },
    () => { bpRegs(bpRegSel).periodCycles },
    "Update or read blueprint register period")

  internalCmdMap :+= EasyMappedRegister(
    EasyAddressSet("hf1000018".U(32.W), 8.U),
    (data: UInt) => { bpRegs(bpRegSel).startOff := data },
    () => { bpRegs(bpRegSel).startOff },
    "Update or read blueprint register start offset in memory")

  internalCmdMap :+= EasyMappedRegister(
    EasyAddressSet("hf1000020".U(32.W), 8.U),
    (data: UInt) => { bpRegs(bpRegSel).progLen := data },
    () => { bpRegs(bpRegSel).progLen },
    "Update or read blueprint register program length")

  // GLOBALLY accessible registers 
  var globalCmdMap: Seq[EasyMappedRegister] = Nil
  val globalBase = config.cmdBase.U(32.W)

  val cmdResp = RegInit(cmdMaster.d.bits)
  val cmdRespValid = RegInit(false.B)

  val gcmd_MCStartAddr = EasyAddressSet(globalBase, 4.U)
  def gcmd_MCStartWrFun(data: UInt) = {
    if (!cfgPassThrough) {
      pcEnCtr := 32.U
      pcSeq := true.B
      masterState := sm_idle
    }
  }
  def gcmd_MCStartRdFun() = { 0.U(32.W) }
  globalCmdMap :+= EasyMappedRegister(gcmd_MCStartAddr, gcmd_MCStartWrFun, gcmd_MCStartRdFun)

  val gcmd_MCStopAddr = EasyAddressSet(globalBase + 4.U, 4.U)
  def gcmd_MCStopWrFun(data: UInt) = {
    if (!cfgPassThrough) {
      pcEnabled := false.B
      // pcPast := instMemAddr.base
      // masterState := sm_pass
    }
  }
  def gcmd_MCStopRdFun() = { 0.U(32.W) }
  globalCmdMap :+= EasyMappedRegister(gcmd_MCStopAddr, gcmd_MCStopWrFun, gcmd_MCStopRdFun)

  // Memory Mapped registers that are accessible from both workload and memory controller processors.
  // Allows users to define their own protocols and implement special request types (i.e., rowclone)
  val gcmd_MCTickAddr = EasyAddressSet(globalBase + 8.U, 8.U)
  def gcmd_MCTickWrFun(data: UInt) = {
    when (data > mcTicks) {
      mcTicks := data
    }
  }
  def gcmd_MCTickRdFun() = { mcTicks }
  globalCmdMap :+= EasyMappedRegister(gcmd_MCTickAddr, gcmd_MCTickWrFun, gcmd_MCTickRdFun)
  internalCmdMap :+= EasyMappedRegister(gcmd_MCTickAddr, gcmd_MCTickWrFun, gcmd_MCTickRdFun)

  val gcmd_TileTickAddr = EasyAddressSet(globalBase + 16.U, 8.U)
  def gcmd_TileTickWrFun(data: UInt) = { tileTicks := data }
  def gcmd_TileTickRdFun() = { tileTicks }
  globalCmdMap :+= EasyMappedRegister(gcmd_TileTickAddr, gcmd_TileTickWrFun, gcmd_TileTickRdFun)
  internalCmdMap :+= EasyMappedRegister(gcmd_TileTickAddr, gcmd_TileTickWrFun, gcmd_TileTickRdFun)

  val gcmd_MCTileClockRatioAddr = EasyAddressSet(globalBase + 24.U, 8.U)
  def gcmd_MCTileClockRatioWrFun(data: UInt) = { /* NOP */ }
  def gcmd_MCTileClockRatioRdFun() = { baseFreqRatio.U(64.W) }
  globalCmdMap :+= EasyMappedRegister(gcmd_MCTileClockRatioAddr, gcmd_MCTileClockRatioWrFun, gcmd_MCTileClockRatioRdFun)
  internalCmdMap :+= EasyMappedRegister(gcmd_MCTileClockRatioAddr, gcmd_MCTileClockRatioWrFun, gcmd_MCTileClockRatioRdFun)

  val gcmd_MCClockFreqAddr = EasyAddressSet(globalBase + 32.U, 8.U)
  def gcmd_MCClockFreqWrFun(data: UInt) = { /* NOP */ }
  def gcmd_MCClockFreqRdFun() = { clkFreq.U(64.W) }
  globalCmdMap :+= EasyMappedRegister(gcmd_MCClockFreqAddr, gcmd_MCClockFreqWrFun, gcmd_MCClockFreqRdFun)
  internalCmdMap :+= EasyMappedRegister(gcmd_MCClockFreqAddr, gcmd_MCClockFreqWrFun, gcmd_MCClockFreqRdFun)

  val gcmd_TileClockFreqAddr = EasyAddressSet(globalBase + 40.U, 8.U)
  def gcmd_TileClockFreqWrFun(data: UInt) = { /* NOP */ }
  def gcmd_TileClockFreqRdFun() = { tileFreq.U(64.W) }
  globalCmdMap :+= EasyMappedRegister(gcmd_TileClockFreqAddr, gcmd_TileClockFreqWrFun, gcmd_TileClockFreqRdFun)
  internalCmdMap :+= EasyMappedRegister(gcmd_TileClockFreqAddr, gcmd_TileClockFreqWrFun, gcmd_TileClockFreqRdFun)

  val globalClk = RegInit(0.U(64.W))
  globalClk := globalClk + 1.U
  val gcmd_globalClockAddr = EasyAddressSet(globalBase + 48.U, 8.U)
  def gcmd_globalClockWrFun(data: UInt) = { /* NOP */ }
  def gcmd_globalClockRdFun() = { globalClk }
  globalCmdMap :+= EasyMappedRegister(gcmd_globalClockAddr, gcmd_globalClockWrFun, gcmd_globalClockRdFun)
  internalCmdMap :+= EasyMappedRegister(gcmd_globalClockAddr, gcmd_globalClockWrFun, gcmd_globalClockRdFun)

  var reqDelayAmount = RegInit(0.U(64.W))
  val gcmd_setReqDelayAddr = EasyAddressSet(globalBase + 56.U, 8.U)
  def gcmd_setReqDelayWrFun(data: UInt) = { reqDelayAmount := data }
  def gcmd_setReqDelayRdFun() = { reqDelayAmount }
  globalCmdMap :+= EasyMappedRegister(gcmd_setReqDelayAddr, gcmd_setReqDelayWrFun, gcmd_setReqDelayRdFun)
  internalCmdMap :+= EasyMappedRegister(gcmd_setReqDelayAddr, gcmd_setReqDelayWrFun, gcmd_setReqDelayRdFun)

  val cmdFreeBase = globalBase + "h1000".U(32.W)
  val cmdFreeWidth = 8
  val cmdFreeCount = 256

  for (i <- 0 until cmdFreeCount) {
    val freeReg = RegInit(0.U((cmdFreeWidth * 8).W))
    val freeAddr = EasyAddressSet(cmdFreeBase + i.U * cmdFreeWidth.U, cmdFreeWidth.U)
    def freeWrFun(data: UInt) = { freeReg := data }
    def freeRdFun() = { freeReg }
    globalCmdMap :+= EasyMappedRegister(freeAddr, freeWrFun, freeRdFun)
    internalCmdMap :+= EasyMappedRegister(freeAddr, freeWrFun, freeRdFun)
  }

  // UART DEBUG BEGIN
  val UART_QUEUE_DEPTH = 32
  val uartWrQ = Module(new Queue(UInt(8.W), UART_QUEUE_DEPTH))
  uartWrQ.io <> 0.U.asTypeOf(uartWrQ.io)
  uartWrQ.io.enq.valid := false.B

  val DEF_FREQ = 100000000
  val DEF_BAUD_RATE = 115200
  val uartBaudDiv = RegInit((DEF_FREQ / DEF_BAUD_RATE).U(32.W))
  val uartBaudAddr = EasyAddressSet("h20000000".U(32.W), 4.U)
  def uartBaudWrFun(data: UInt) = { uartBaudDiv := data }
  def uartBaudRdFun() = { uartBaudDiv }
  internalCmdMap :+= EasyMappedRegister(uartBaudAddr, uartBaudWrFun, uartBaudRdFun)

  val uartIsFullAddr = EasyAddressSet("h20000004".U(32.W), 4.U)
  def uartIsFullWrFun(data: UInt) = { /* NOP */ }
  def uartIsFullRdFun() = { !uartWrQ.io.enq.ready }
  internalCmdMap :+= EasyMappedRegister(uartIsFullAddr, uartIsFullWrFun, uartIsFullRdFun)

  val uartWriteAddr = EasyAddressSet("h20000008".U(32.W), 4.U)
  def uart_write_wr_fun(data: UInt) = {
    uartWrQ.io.enq.valid := true.B
    uartWrQ.io.enq.bits := data(7, 0)
  }
  def uart_write_rd_fun() = { 0.U(32.W)}
  internalCmdMap :+= EasyMappedRegister(uartWriteAddr, uart_write_wr_fun, uart_write_rd_fun)

  val kasirga_uart = Module(new UartTransmitter)

  kasirga_uart.io.clk_i := clock
  kasirga_uart.io.rstn_i := true.B
  kasirga_uart.io.tx_en_i := true.B
  kasirga_uart.io.veri_gecerli_i := uartWrQ.io.deq.valid
  kasirga_uart.io.gelen_veri_i := uartWrQ.io.deq.bits
  uartWrQ.io.deq.ready := kasirga_uart.io.consume_o
  kasirga_uart.io.baud_div_i := uartBaudDiv
  io.mc_tx := kasirga_uart.io.tx_o
  // UART DEBUG END

  // FSM DEBUG BEGIN
  // Incoming Req Master
  val masterStallCtr = RegInit(0.U(32.W))
  val masterRespCtr = RegInit(0.U(32.W))
  val masterRespDelay = 0
  val masterRespPattern = 0.U

  // Outgoing Req State
  val sr_idle :: sr_delay :: sr_send :: Nil = Enum(3)
  val responseState = RegInit(sr_idle)

  val isMasterWrite = Wire(Bool())

  internalCmdMap :+= EasyMappedRegister(
    EasyAddressSet("h20000010".U(32.W), 8.U),
    (data: UInt) => { /* NOP */ },
    () => { masterState },
    "Read master FSM state register")

  internalCmdMap :+= EasyMappedRegister(
    EasyAddressSet("h20000018".U(32.W), 8.U),
    (data: UInt) => { /* NOP */ },
    () => { responseState },
    "Read response FSM state register")
  // FSM DEBUG END

  // TODO: Check for duplicates in internal map

  // DDR signals
  drambender.io.c0_sys_clk_p  := io.c0_sys_clk_p
  drambender.io.c0_sys_clk_n  := io.c0_sys_clk_n
  drambender.io.sys_rst       := io.sys_rst
  io.c0_ddr4_act_n            := drambender.io.c0_ddr4_act_n
  io.c0_ddr4_adr              := drambender.io.c0_ddr4_adr
  io.c0_ddr4_ba               := drambender.io.c0_ddr4_ba
  io.c0_ddr4_bg               := drambender.io.c0_ddr4_bg
  io.c0_ddr4_cke              := drambender.io.c0_ddr4_cke
  io.c0_ddr4_odt              := drambender.io.c0_ddr4_odt
  io.c0_ddr4_cs_n             := drambender.io.c0_ddr4_cs_n
  io.c0_ddr4_ck_c             := drambender.io.c0_ddr4_ck_c
  io.c0_ddr4_ck_t             := drambender.io.c0_ddr4_ck_t
  io.c0_ddr4_reset_n          := drambender.io.c0_ddr4_reset_n
  io.c0_ddr4_parity           := drambender.io.c0_ddr4_parity

  // DDR inouts
  // attach(io.c0_ddr4_dqs_c, drambender.io.c0_ddr4_dqs_c)
  // attach(io.c0_ddr4_dqs_t, drambender.io.c0_ddr4_dqs_t)
  // attach(io.c0_ddr4_dq, drambender.io.c0_ddr4_dq)
  // attach(io.c0_ddr4_dm_dbi_n, drambender.io.c0_ddr4_dm_dbi_n)

  drambender.io.batch_clk_i := clock
  drambender.io.batch_rstn_i := !benderProgram.instRst

  drambender.io.inst_data_i := benderProgram.inst.bits
  drambender.io.inst_valid_i := benderProgram.inst.valid
  drambender.io.inst_last_i := benderProgram.instLast
  benderProgram.inst.ready := drambender.io.inst_ready_o

  benderProgram.rdback.bits := drambender.io.db_data_o 
  benderProgram.rdbackLast := drambender.io.db_last_o 
  // DRAM-Bender sends a duplicate beat with last signal, we discard that one.
  benderProgram.rdback.valid := drambender.io.db_valid_o && !drambender.io.db_last_o
  drambender.io.db_ready_i := benderProgram.rdback.ready || drambender.io.db_last_o
  benderProgram.benderDone := drambender.io.bender_done_o
  benderDone := benderDone || drambender.io.bender_done_o

  benderProgram.calibComplete := drambender.io.db_init_calib_complete_o
  calibDone := calibDone || drambender.io.db_init_calib_complete_o

  rdBackQ.io.enq <> benderProgram.rdback

  drambender.io.bypass_addr_i := 0.U(64.W)
  drambender.io.bypass_wr_data_i := 0.U(512.W)
  drambender.io.bypass_wr_en_i := false.B

  // MC Fetch stage
  val imemReqValid = RegInit(false.B)
  val imemReqPC = RegInit(core.io.imem.req.bits.pc)
  val pcPast = RegNext(pc)
  val pcPastValid = RegNext(inside(instMemAddr)(pc))
  val instPartSelect = Mux(pcPast(2), instMem.io.port0.dataOut.asUInt(63, 32), instMem.io.port0.dataOut.asUInt(31, 0))
 
  core.io.hartid := 0.U
  core.io.reset_vector := 0.U
  core.io.imem.clock_enabled := true.B
  core.io.imem.resp.valid := false.B
  core.io.imem.resp.bits.pc := pcPast
  core.io.imem.resp.bits.data := instPartSelect
  core.io.imem.resp.bits.mask := 0xFFFF.U
  core.io.imem.resp.bits.replay := false.B

  // Currently not using exceptions (and will probably never use them)
  core.io.imem.resp.bits.xcpt.pf.inst := false.B
  core.io.imem.resp.bits.xcpt.gf.inst := false.B
  core.io.imem.resp.bits.xcpt.ae.inst := false.B

  // TODO: Currently not using BTB and BHTs
  core.io.imem.resp.bits.btb.cfiType := 0.U
  core.io.imem.resp.bits.btb.taken := false.B
  core.io.imem.resp.bits.btb.mask := 0.U
  core.io.imem.resp.bits.btb.bridx := 0.U
  core.io.imem.resp.bits.btb.target := 0.U
  core.io.imem.resp.bits.btb.entry := 0.U
  core.io.imem.resp.bits.btb.bht.history := 0.U
  core.io.imem.resp.bits.btb.bht.value := 0.U
  core.io.imem.resp.valid := pcEnabled && pcPastValid

  pc := pcPast
  when (core.io.imem.req.valid) {
    pc := core.io.imem.req.bits.pc
    imemReqValid := true.B
  }
  .elsewhen (imemReqValid) {
    imemReqValid := false.B  
    core.io.imem.resp.bits.replay := true.B
    core.io.imem.resp.valid := false.B
  }
  .elsewhen (pcEnabled && core.io.imem.resp.ready) {
    pc := (pcPast + 4.U) & ~3.U(pc.getWidth.W)
  }

  // DataMem Access
  val dmem_s1_addr = RegNext(core.io.dmem.req.bits.addr)
  val dmem_s1_tag = RegNext(core.io.dmem.req.bits.tag)
  val dmem_s1_size = RegNext(core.io.dmem.req.bits.size)
  val dmem_s1_is_signed = RegNext(core.io.dmem.req.bits.signed)
  val dmem_s1_size_pow = 1.U << dmem_s1_size
  val dmem_s1_sign_idx = dmem_s1_size_pow * 8.U(8.W) - 1.U(8.W)
  val dmem_s1_mask = (1.U << dmem_s1_size_pow) - 1.U(8.W)
  val dmem_s1_valid = RegNext(core.io.dmem.req.fire)
  val dmem_s1_hasData = RegNext(core.io.dmem.req.bits.cmd === M_XRD)
  val dmem_s2_rdata = RegInit(VecInit(Seq.fill(8)(0.U(8.W))))
  val dmem_s2_tag = RegNext(dmem_s1_tag)
  val dmem_s2_size = RegNext(dmem_s1_size)
  val dmem_s2_valid = RegNext(dmem_s1_valid && !core.io.dmem.s1_kill)
  val dmem_s2_hasData = RegNext(dmem_s1_hasData)
  val dmem_s2_misalign = RegNext((dmem_s1_addr % dmem_s1_size_pow) =/= 0.U)
  val dmem_s2_ma_ld = dmem_s2_misalign && dmem_s2_hasData
  val dmem_s2_ma_st = dmem_s2_misalign && !dmem_s2_hasData

  core.io.dmem.ordered := true.B
  core.io.dmem.clock_enabled := true.B
  core.io.dmem.resp.bits.data := dmem_s2_rdata.asUInt
  core.io.dmem.resp.bits.data_word_bypass := dmem_s2_rdata.asUInt
  core.io.dmem.resp.bits.has_data := dmem_s2_hasData
  core.io.dmem.resp.bits.tag := dmem_s2_tag
  core.io.dmem.resp.bits.size := dmem_s2_size
  core.io.dmem.resp.valid := dmem_s2_valid
  core.io.dmem.s2_xcpt.ma.ld := dmem_s2_ma_ld
  core.io.dmem.s2_xcpt.ma.st := dmem_s2_ma_st
  
  core.io.dmem.req.ready := !dmem_s1_valid || dmem_s1_hasData

  instMem.io.port0.addr := pc
  for ((addrSet, memPort) <- internalMemMap) {
    memPort.addr := addrSet.mask & core.io.dmem.req.bits.addr
  }

  val isMcMemAccess = Wire(Bool())
  val isMcRegAccess = Wire(Bool())
  val isMcUARTAccess = Wire(Bool())

  dontTouch(isMcMemAccess)
  dontTouch(isMcRegAccess)
  dontTouch(isMcUARTAccess)

  isMcMemAccess := false.B
  isMcRegAccess := false.B
  isMcUARTAccess := false.B

  when (dmem_s1_valid && !core.io.dmem.s1_kill) {
    val byteOffset = dmem_s1_addr(2, 0)
    val bitMask = Reverse(Cat(dmem_s1_mask(7, 0).asBools.map(i => Seq.fill(8)(i)).reduce(_++_)))
    for ((addrSet, memPort) <- internalMemMap) {
      val convertedAddr = addrSet.mask & dmem_s1_addr
      val memAllignedData = memPort.dataOut.asUInt >> (byteOffset * 8.U)
      val memMaskedData = memAllignedData & bitMask
      val dataSign = memMaskedData(dmem_s1_sign_idx)
      val memSignedData = (((0.U(64.W) - dataSign) << dmem_s1_sign_idx) | memMaskedData)(63, 0)
      val memData = Mux(dmem_s1_is_signed, memSignedData, memMaskedData)
      when (inside(addrSet)(dmem_s1_addr)) {
        isMcMemAccess := true.B
        when (!dmem_s1_hasData) {
          memPort.dataIn := chop(core.io.dmem.s1_data.data, 8)
          memPort.mask := dmem_s1_mask(7, 0).asBools
          memPort.addr := convertedAddr
          memPort.write := true.B
        }
        .otherwise {
          dmem_s2_rdata := chop(memData, 8)
        }
      }
    }

    // Control registers
    val regAllignedData = core.io.dmem.s1_data.data.asUInt >> (byteOffset * 8.U)
    val regMaskedData = regAllignedData & bitMask
    for (easyReg <- internalCmdMap) {
      val cmdSet = easyReg.addrSet
      val cmdWrFun = easyReg.wrFun
      val cmdRdFun = easyReg.rdFun
      when(dmem_s1_addr === cmdSet.base) {
        isMcRegAccess := true.B
        when (!dmem_s1_hasData) {
          val size = dmem_s1_size
          cmdWrFun(regMaskedData)
        }
        .otherwise {
          val choppedSeq = chop(cmdRdFun(), 8).toSeq
          dmem_s2_rdata := choppedSeq ++ Seq.fill(8 - choppedSeq.length)(0.U(8.W)) 
        }
      }
      when(dmem_s1_addr === "h20000008".U) {
        isMcUARTAccess := true.B
      }
    }
  }

  // Incoming Req Master FSM
  memMaster.d.bits := 0.U.asTypeOf(memMaster.d.bits)
  memMaster.d.valid := false.B
  memMaster.a.ready := false.B
  masterStallCtr := Mux(memMaster.a.valid, 0.U, masterStallCtr + 1.U)

  switch (masterState) {
    is (sm_pass) {
      memMaster.a.ready := true.B
      when (memEdge.done(memMaster.a)) {
        reqBits.msg := memMaster.a.bits
        if (masterRespDelay > 1) {
          masterRespCtr := 0.U
          masterState := sm_pass_stall
        }
        else {
          masterState := sm_pass_resp
        }
      }
    }
    is (sm_pass_stall) {
      memMaster.a.ready := memEdge.hasData(reqBits.msg)
      masterRespCtr := masterRespCtr + 1.U
      masterState := Mux(masterRespCtr === masterRespDelay.U - 2.U, sm_pass_resp, sm_pass_stall)
    }
    is (sm_pass_resp) {
      memMaster.d.valid := true.B
      memMaster.d.bits := Mux(memEdge.hasData(reqBits.msg), memEdge.AccessAck(reqBits.msg), memEdge.AccessAck(reqBits.msg, masterRespPattern))
      masterState := Mux(memEdge.done(memMaster.d), sm_pass, sm_pass_resp)
    }
    is (sm_idle) {
      memMaster.a.ready := true.B
      when (memMaster.a.fire) {
        reqBits.msg := memMaster.a.bits
        reqBits.msgTick := tileTicks
        when (memEdge.hasData(memMaster.a.bits)) {
          masterState := sm_resp_wr
          reqBits.msgData(0) := memMaster.a.bits.data
        }
        .otherwise {
          incomingReqQ.io.enq.bits.msg := memMaster.a.bits
          incomingReqQ.io.enq.bits.msgData := chop(0.U(512.W), 64)
          incomingReqQ.io.enq.bits.msgTick := tileTicks
          incomingReqQ.io.enq.valid := true.B
          masterState := Mux(incomingReqQ.io.enq.ready, sm_idle, sm_stall)
        }
      }
    }
    is (sm_resp_wr) {
      memMaster.a.ready := true.B
      when (memMaster.a.fire) {
        // edge.count also returns first, last, done info??
        reqBits.msgData(memEdge.count(memMaster.a)._4) := memMaster.a.bits.data
      }
      masterState := Mux(memEdge.done(memMaster.a), sm_resp_wr_enq, sm_resp_wr)
    }
    is (sm_resp_wr_enq) {
      incomingReqQ.io.enq.bits := reqBits
      incomingReqQ.io.enq.valid := true.B
      masterState := Mux(incomingReqQ.io.enq.ready, sm_idle, sm_resp_wr_enq)
    }
    is (sm_stall) {
      incomingReqQ.io.enq.bits := reqBits
      incomingReqQ.io.enq.valid := true.B
      masterState := Mux(incomingReqQ.io.enq.ready, sm_idle, sm_stall)
    }
  }

  val TL_OP_GET = 4.U
  val TL_OP_ACKD = 1.U
  val TL_OP_PUTF = 0.U
  val TL_OP_PUTP = 1.U
  val TL_OP_ACK = 0.U

  val reqOpCode = mcIncomingMetaRdFun()
  val reqSrc = mcIncomingSourceRdFun()
  val reqSize = mcIncomingSizeRdFun()
  val reqIsWr = reqOpCode === TL_OP_PUTF || reqOpCode === TL_OP_PUTP
  val reqData = Cat((0 until 8).map(i => incomingReqQ.io.deq.bits.msgData(7 - i)))
  val reqAddr = mcIncomingReqAddrRdFun()
  val reqConsume = incomingReqQ.io.deq.valid & !pcEnabled

  when (reqConsume) {
    drambender.io.bypass_addr_i := reqAddr
    drambender.io.bypass_wr_data_i := reqData
    drambender.io.bypass_wr_en_i := reqIsWr
    mcIncomingReqConsumeWrFun(true.B)
  }

  val reqOpCodePast = RegNext(reqOpCode)
  val reqSrcPast = RegNext(reqSrc)
  val reqSizePast = RegNext(reqSize)
  val reqIsWrPast = RegNext(reqIsWr)
  val reqAddrPast = RegNext(reqAddr)
  val reqConsumePast = RegNext(reqConsume)

  val reqDelayCtr = RegInit(0.U(64.W))
  when (reqConsumePast) {
    outgoingMsg.msgData := chop(drambender.io.bypass_rd_data_o, 64)
    mcOutgoingOpcodeWrFun(reqOpCodePast)
    mcOutgoingSourceWrFun(reqSrcPast)
    mcOutgoingSizeWrFun(reqSizePast)
    mcOutgoingTickWrFun(0.U)
    mcOutgoingAddrWrFun(reqAddrPast)
    reqDelayCtr := reqDelayAmount
  }

  val reqOutValid = RegNext(reqConsumePast)

  when(reqOutValid) {
    mcOutgoingReqSendWrFun(true.B)
  }
  
  isMasterWrite := memMaster.a.fire && memEdge.hasData(memMaster.a.bits)

  when(isMasterWrite) {
    printf("EASYDRAM: Write at addr %x\n", memMaster.a.bits.address);
  }

  // Outgoing Req Logic
  switch (responseState) {
    is (sr_idle) {
      outgoingReqQ.io.deq.ready := false.B
      when (outgoingReqQ.io.deq.valid && (tileTicks >= outgoingReqQ.io.deq.bits.msgTick || !regIsTimeScaled)) {
        responseState := Mux(reqDelayCtr > 0.U, sr_delay, sr_send)
      }
    }
    is (sr_delay) {
      reqDelayCtr := reqDelayCtr - 1.U
      responseState := Mux(reqDelayCtr > 1.U, sr_delay, sr_send)
    }
    is (sr_send) {
      val curMsg = outgoingReqQ.io.deq.bits.msg
      val curData = outgoingReqQ.io.deq.bits.msgData
      memMaster.d.bits := Mux(memEdge.hasData(curMsg),
                            memEdge.AccessAck(curMsg),
                            memEdge.AccessAck(curMsg, curData(memEdge.count(memMaster.d)._4)))
      memMaster.d.valid := true.B
      responseState := Mux(memEdge.done(memMaster.d), sr_idle, sr_send)
      outgoingReqQ.io.deq.ready := memEdge.done(memMaster.d)
    }
  }

  // CMD Logic
  cmdMaster.d.bits := cmdResp
  cmdMaster.d.valid := cmdRespValid
  cmdMaster.a.ready := !cmdRespValid

  when (cmdMaster.a.fire) {
    val byteOffset = cmdMaster.a.bits.address(2, 0)
    val regAllignedData = cmdMaster.a.bits.data.asUInt >> (byteOffset * 8.U)
    cmdResp := Mux(cmdEdge.hasData(cmdMaster.a.bits), cmdEdge.AccessAck(cmdMaster.a.bits), cmdEdge.AccessAck(cmdMaster.a.bits, 0.U(64.W)))
    for (easyReg <- globalCmdMap) {
      val cmdSet = easyReg.addrSet
      val cmdWrFun = easyReg.wrFun
      val cmdRdFun = easyReg.rdFun
      when(cmdMaster.a.bits.address === cmdSet.base) {
        when (cmdEdge.hasData(cmdMaster.a.bits)) {
          cmdWrFun(regAllignedData)
        }
        .otherwise {
          val rdData = cmdRdFun()
          val choppedSeq = chop(rdData, 8).toSeq
          cmdResp := cmdEdge.AccessAck(cmdMaster.a.bits, rdData)
        }
      }
    }
    cmdRespValid := true.B
  }

  when (pcSeq) {
    pcEnCtr := pcEnCtr - 1.U
    pc := instMemAddr.base
    pcPast := instMemAddr.base
    pcEnabled := Mux(pcEnCtr === 1.U, true.B, false.B)
    pcSeq := Mux(pcEnCtr === 1.U, false.B, true.B)
  }

  when (cmdEdge.done(cmdMaster.d)) {
    cmdRespValid := false.B
  }

  // Instr Logic
  instMaster.d.bits := instResp
  instMaster.d.valid := instRespValid
  instMaster.a.ready := !instRespValid
  val instWrite = RegInit(false.B)
  val instMasterSizePow = 1.U << instMaster.a.bits.size
  val instWriteMask = (1.U << instMasterSizePow) - 1.U(8.W)

  when (instMaster.a.fire) {
    instRespValid := true.B
    instWrite := instEdge.hasData(instMaster.a.bits)
    when (instEdge.hasData(instMaster.a.bits)) {
      instMem.io.port0.addr := instMaster.a.bits.address
      instMem.io.port0.dataIn := (0 to 7).map(i => instMaster.a.bits.data(8 * i + 7, 8 * i))
      instMem.io.port0.mask := instWriteMask(7, 0).asBools
      instMem.io.port0.write := true.B
      instResp := instEdge.AccessAck(instMaster.a.bits)
    } .otherwise {
      instResp := instEdge.AccessAck(instMaster.a.bits, 0.U) // Currently unreadable
    }
  }

  when (instEdge.done(instMaster.d)) {
    instRespValid := false.B
  }

  // Update register state
  for (i <- 0 until NUM_BLUEPRINT_SLOTS) {
    val bpReg = bpRegs(i)
    // Tick register
    when (!bpReg.ready && bpReg.skew > 0.U) {
      bpReg.skew := bpReg.skew - 1.U
    }
    .elsewhen (bpReg.valid) {
      bpReg.periodCounter := bpReg.periodCounter - 1.U
    }

    // Skew the program if we are waiting for another blueprint to execute
    val bpExecuting = progState =/= sp_idle && isBlueprint
    when (bpReg.ready && bpReg.ready && bpExecuting) {
      bpReg.skew := bpReg.skew + 1.U
    }
    
    // Check ready
    when (bpReg.valid && bpReg.periodCounter === 0.U) {
      bpReg.periodCounter := bpReg.periodCycles - 1.U
      bpReg.ready := true.B
    }
  }

  // Select a blueprint to send if there isn't one pending
  for (i <- 0 until NUM_BLUEPRINT_SLOTS) {
    val bpReg = bpRegs(i)
    when (!bpFlush && bpReg.ready) {
      // Update blueprint state
      bpReg.ready := false.B

      // Update program flush register
      bpFlush := true.B
      bpOff := bpReg.startOff
      bpLen := bpReg.progLen
    }
  }
  
  // DRAMBender Program FSM
  val progPort = benderMem.io.port1
  val bPrtPort = bPrtMem.io.port1
  val progOff = RegInit(0.U(32.W)) 
  val sendLen = RegInit(0.U(32.W))

  // DEBUG
  val progFlushPast = RegNext(progFlush)
  val progStallPerfCtr = RegInit(0.U(32.W))
  progStallPerfCtr := Mux(progFlushPast && progFlush, progStallPerfCtr + 1.U, 0.U)
  dontTouch(progFlushPast)
  dontTouch(progStallPerfCtr)

  benderProgram.inst.bits := Mux(isBlueprint, bPrtPort.dataOut.asUInt, progPort.dataOut.asUInt)
  benderProgram.inst.valid := false.B
  benderProgram.instLast := false.B

  val PROG_RESET_INSN = "h10000000000000000".U(68.W)
  val PROG_STALL = 10
  switch (progState) {
    is (sp_idle) {
      // Notice that we are prioritizing bpFlushes here
      when (bpFlush || progFlush) {
        progState := sp_start
        isBlueprint := bpFlush 
        bpFlush := false.B
        progFlush := Mux(bpFlush, progFlush, false.B)
        progOff := Mux(bpFlush, bpOff, 0.U)
        sendLen := Mux(bpFlush, bpLen, progLen)
      }
    }
    is (sp_start) {
      benderProgram.inst.bits := PROG_RESET_INSN
      benderProgram.inst.valid := true.B
      calibDone := false.B
      when (benderProgram.inst.fire) {
        progState := sp_reset
        stallCtr := 10.U
      }
    }
    is (sp_reset) {
      stallCtr := Mux(stallCtr === 0.U, stallCtr, stallCtr - 1.U)
      when (calibDone && stallCtr === 0.U) {
        progState := sp_send
        progPort.addr := progOff
        bPrtPort.addr := progOff
        benderCtr := 0.U
      }
    }
    is (sp_send) {
      val currentProgPC = benderCtr * 8.U + progOff
      val nextProgPC = (benderCtr + 1.U) * 8.U + progOff
      progPort.addr := Mux(benderProgram.inst.fire, nextProgPC, currentProgPC)
      bPrtPort.addr := Mux(benderProgram.inst.fire, nextProgPC, currentProgPC)
      benderProgram.instLast := benderCtr === (sendLen - 1.U)
      benderProgram.inst.valid := true.B
      benderDone := false.B
      progCycles := 0.U
      when (benderProgram.inst.fire) {
        progState := Mux(benderProgram.instLast, sp_done, sp_send)
        benderCtr := benderCtr + 1.U
      }
    }
    is (sp_done) {
      progCycles := progCycles + 1.U
      when (benderDone) {
        progState := sp_idle
      }
    }
  }

  // Clock Gating
  val clockGaterAddr = Seq("h100004".U(32.W))
  val clockGaterBytes = 4
  val gateCtr = RegInit(clockGaterAddr.length.U)
  val gateStallCtr = RegInit(0.U(8.W))
  val stallCycles = 50

  val sg_idle :: sg_req :: sg_ack :: sg_stall :: Nil = Enum(4)
  val gaterState = RegInit(sg_idle)
  
  val gateData = Wire(UInt(32.W))
  gateData := !clockGated
  gateMaster.a.bits := gateEdge.Put(
    fromSource = 0.U,
    toAddress = clockGaterAddr(gateCtr),
    lgSize = log2Ceil(clockGaterBytes).U,
    data = Cat(gateData, gateData))._2
  gateMaster.a.valid := gaterState === sg_req
  gateMaster.d.ready := true.B

  switch (gaterState) {
    is (sg_idle) {
      gateStallCtr := 0.U
      if (!cfgPassThrough) {
        when (regIsTimeScaled && clockGated === tileClockEnable) {
          gaterState := sg_req
          clockGated := !tileClockEnable
          gateCtr := 0.U
        }
      }
    }
    is (sg_req) {
      gaterState := Mux(gateEdge.done(gateMaster.a) && gateCtr === clockGaterAddr.length.U - 1.U, sg_stall, sg_req)
      gateCtr := Mux(gateEdge.done(gateMaster.a), gateCtr + 1.U, gateCtr)
    }
    is (sg_ack) { // TODO: EXTREMELY lazy to implement this properly RN.
      when (gateEdge.done(gateMaster.d)) {
        gaterState := Mux(gateCtr === clockGaterAddr.length.U - 1.U, sg_idle, sg_req)
      }
    }
    is (sg_stall) { // Required as we shouldn't toggle the clock on and off too quickly.
      gaterState := Mux(gateStallCtr < stallCycles.U - 1.U, sg_stall, sg_idle)
      gateStallCtr := gateStallCtr + 1.U
    }
  }

  if (!isSimulation && isDebug) {
    val master_addr = Wire(UInt(32.W))
    master_addr := memMaster.a.bits.address
    val master_data = Wire(UInt(64.W))
    master_data := memMaster.a.bits.data
    val master_source = Wire(UInt(32.W))
    master_source := memMaster.a.bits.source
    val master_opcode = Wire(UInt(32.W))
    master_opcode := memMaster.a.bits.opcode
    val master_valid = Wire(Bool())
    master_valid := memMaster.a.valid
    val master_ready = Wire(Bool())
    master_ready := memMaster.a.ready

    dontTouch(master_addr)
    dontTouch(master_data)
    dontTouch(master_source)
    dontTouch(master_opcode)
    dontTouch(master_valid)
    dontTouch(master_ready)
    dontTouch(memMaster.a.bits.address)
    dontTouch(memMaster.a.bits.data)
    dontTouch(memMaster.a.bits.source)
    dontTouch(memMaster.a.bits.opcode)
    dontTouch(memMaster.a.valid)
    dontTouch(memMaster.a.ready)

    MarkDebug(clock,
      Seq(master_addr, master_data, master_source, master_opcode, master_valid, master_ready))

    MarkDebug(clock,
      Seq(gaterState, gateStallCtr, gateCtr, clockGated, tileClockEnable))

    MarkDebug(clock,
      Seq(benderProgram.inst.valid, benderProgram.inst.ready, benderProgram.instLast, benderDone, benderCtr, progState))

    MarkDebug(clock,
      Seq(memMaster.d.bits.data, memMaster.d.bits.opcode, memMaster.d.bits.source, memMaster.d.valid, memMaster.d.ready))
  }
}

trait CanHavePeripheryEasyMemory { this: BaseSubsystem =>
  private val sbus = locateTLBusWrapper(SBUS)
  private val mbus = locateTLBusWrapper(MBUS)
  private val cbus = locateTLBusWrapper(CBUS)
  val easydram_io = p(EasyMemoryKey).map { params =>
      def verifTLUBundleParams: TLBundleParameters = TLBundleParameters(addressBits = 64, dataBits = 64, sourceBits = 1,
        sinkBits = 1, sizeBits = 6,  echoFields = Seq(), requestFields = Seq(), responseFields = Seq(), hasBCE = false)
      val easymem = mbus { LazyModule(new EasyMemory()(p)) }
      val visibilityNode = p(TileVisibilityNodeKey)
      mbus.coupleTo("easy-memory") { easymem.memNode := TLBuffer() := _ }
      sbus.coupleTo("easy-instr") { easymem.instNode := TLBuffer() := _ }
      sbus.coupleTo("easy-command") { easymem.cmdNode := TLBuffer() := _ }
      cbus.coupleFrom("easy-gating") { _ := TLBuffer() := easymem.gateNode}
      easymem.dummyOut := visibilityNode
      visibilityNode := easymem.dummyIn
      val easymem_io = mbus { InModuleBody {
        val easydram_io = IO(new EasyDRAMTopIO(new DRAMBenderParams))
        easydram_io <> easymem.module.io
        easydram_io
      } }
      val easydram_io = InModuleBody {
        val easydram_io = IO(new EasyDRAMTopIO(new DRAMBenderParams))
        easydram_io <> easymem_io
        easydram_io
      }
      easydram_io
  }
}

// DOC include start: WithEasyMemory
class WithEasyMemory(
  memBase: BigInt, memSize: BigInt,
  instBase: BigInt, instSize: BigInt,
  cmdBase: BigInt, cmdSize: BigInt,
  tileFreq: BigInt, mcFreq: BigInt)
  extends Config((site, here, up) => {
  case EasyMemoryKey => Some(EasyMemoryConfig(
    memBase = memBase,
    memSize = memSize,
    instBase = instBase,
    instSize = instSize,
    cmdBase = cmdBase,
    cmdSize = cmdSize,
    debug = false,
    simulation = false,
    tileFreq = tileFreq,
    mcFreq = mcFreq))
  case TileKey => RocketTileParams(
    core = RocketCoreParams(useVM = false),
    btb = None,
    dcache = Some(DCacheParams(
      rowBits = site(SystemBusKey).beatBits,
      nSets = 64,
      nWays = 1,
      nTLBSets = 1,
      nTLBWays = 4,
      nMSHRs = 0,
      blockBytes = site(CacheBlockBytes))),
    icache = Some(ICacheParams(
      rowBits = site(SystemBusKey).beatBits,
      nSets = 64,
      nWays = 1,
      nTLBSets = 1,
      nTLBWays = 4,
      blockBytes = site(CacheBlockBytes))))
  case TileVisibilityNodeKey => EasyVisibilityNode
})

class WithEasyMemoryDebug(
  memBase: BigInt, memSize: BigInt,
  instBase: BigInt, instSize: BigInt,
  cmdBase: BigInt, cmdSize: BigInt,
  tileFreq: BigInt, mcFreq: BigInt)
  extends Config((site, here, up) => {
  case EasyMemoryKey => Some(EasyMemoryConfig(
    memBase = memBase,
    memSize = memSize,
    instBase = instBase,
    instSize = instSize,
    cmdBase = cmdBase,
    cmdSize = cmdSize,
    debug = true,
    simulation = false,
    tileFreq = tileFreq,
    mcFreq = mcFreq))
  case TileKey => RocketTileParams(
    core = RocketCoreParams(useVM = false),
    btb = None,
    dcache = Some(DCacheParams(
      rowBits = site(SystemBusKey).beatBits,
      nSets = 64,
      nWays = 1,
      nTLBSets = 1,
      nTLBWays = 4,
      nMSHRs = 0,
      blockBytes = site(CacheBlockBytes))),
    icache = Some(ICacheParams(
      rowBits = site(SystemBusKey).beatBits,
      nSets = 64,
      nWays = 1,
      nTLBSets = 1,
      nTLBWays = 4,
      blockBytes = site(CacheBlockBytes))))
  case TileVisibilityNodeKey => EasyVisibilityNode
})

class WithEasyMemoryNoScaleDebug(
  memBase: BigInt, memSize: BigInt,
  instBase: BigInt, instSize: BigInt,
  cmdBase: BigInt, cmdSize: BigInt,
  tileFreq: BigInt, mcFreq: BigInt)
  extends Config((site, here, up) => {
  case EasyMemoryKey => Some(EasyMemoryConfig(
    memBase = memBase,
    memSize = memSize,
    instBase = instBase,
    instSize = instSize,
    cmdBase = cmdBase,
    cmdSize = cmdSize,
    debug = true,
    simulation = false,
    scaled = false,
    tileFreq = tileFreq,
    mcFreq = mcFreq))
  case TileKey => RocketTileParams(
    core = RocketCoreParams(useVM = false),
    btb = None,
    dcache = Some(DCacheParams(
      rowBits = site(SystemBusKey).beatBits,
      nSets = 64,
      nWays = 1,
      nTLBSets = 1,
      nTLBWays = 4,
      nMSHRs = 0,
      blockBytes = site(CacheBlockBytes))),
    icache = Some(ICacheParams(
      rowBits = site(SystemBusKey).beatBits,
      nSets = 64,
      nWays = 1,
      nTLBSets = 1,
      nTLBWays = 4,
      blockBytes = site(CacheBlockBytes))))
  case TileVisibilityNodeKey => EasyVisibilityNode
})

class WithEasyMemorySim(
  memBase: BigInt, memSize: BigInt,
  instBase: BigInt, instSize: BigInt,
  cmdBase: BigInt, cmdSize: BigInt,
  tileFreq: BigInt, mcFreq: BigInt)
  extends Config((site, here, up) => {
  case EasyMemoryKey => Some(EasyMemoryConfig(
    memBase = memBase,
    memSize = memSize,
    instBase = instBase,
    instSize = instSize,
    cmdBase = cmdBase,
    cmdSize = cmdSize,
    debug = false,
    simulation = true,
    tileFreq = tileFreq,
    mcFreq = mcFreq))
  case TileKey => RocketTileParams(
    core = RocketCoreParams(useVM = false),
    btb = None,
    dcache = Some(DCacheParams(
      rowBits = site(SystemBusKey).beatBits,
      nSets = 64,
      nWays = 1,
      nTLBSets = 1,
      nTLBWays = 4,
      nMSHRs = 0,
      blockBytes = site(CacheBlockBytes))),
    icache = Some(ICacheParams(
      rowBits = site(SystemBusKey).beatBits,
      nSets = 64,
      nWays = 1,
      nTLBSets = 1,
      nTLBWays = 4,
      blockBytes = site(CacheBlockBytes))))
  case TileVisibilityNodeKey => EasyVisibilityNode
})

class WithEasyMemoryNoScaleSim(
  memBase: BigInt, memSize: BigInt,
  instBase: BigInt, instSize: BigInt,
  cmdBase: BigInt, cmdSize: BigInt,
  tileFreq: BigInt, mcFreq: BigInt)
  extends Config((site, here, up) => {
  case EasyMemoryKey => Some(EasyMemoryConfig(
    memBase = memBase,
    memSize = memSize,
    instBase = instBase,
    instSize = instSize,
    cmdBase = cmdBase,
    cmdSize = cmdSize,
    debug = false,
    simulation = true,
    scaled = false,
    tileFreq = tileFreq,
    mcFreq = mcFreq))
  case TileKey => RocketTileParams(
    core = RocketCoreParams(useVM = false),
    btb = None,
    dcache = Some(DCacheParams(
      rowBits = site(SystemBusKey).beatBits,
      nSets = 64,
      nWays = 1,
      nTLBSets = 1,
      nTLBWays = 4,
      nMSHRs = 0,
      blockBytes = site(CacheBlockBytes))),
    icache = Some(ICacheParams(
      rowBits = site(SystemBusKey).beatBits,
      nSets = 64,
      nWays = 1,
      nTLBSets = 1,
      nTLBWays = 4,
      blockBytes = site(CacheBlockBytes))))
  case TileVisibilityNodeKey => EasyVisibilityNode
})
// DOC include end: WithEasyMemory

class EasyRocket()(implicit p: Parameters) extends CoreModule()(p)
    with HasRocketCoreParameters
    with HasRocketCoreIO {
  def nTotalRoCCCSRs = 0

  val clock_en_reg = RegInit(true.B)
  val long_latency_stall = Reg(Bool())
  val id_reg_pause = Reg(Bool())
  val imem_might_request_reg = Reg(Bool())
  val clock_en = WireDefault(true.B)
  val gated_clock =
    if (!rocketParams.clockGate) clock
    else ClockGate(clock, clock_en, "rocket_clock_gate")

  class RocketImpl { // entering gated-clock domain

  // performance counters
  def pipelineIDToWB[T <: Data](x: T): T =
    RegEnable(RegEnable(RegEnable(x, !ctrl_killd), ex_pc_valid), mem_pc_valid)
  val perfEvents = new EventSets(Seq(
    new EventSet((mask, hits) => Mux(wb_xcpt, mask(0), wb_valid && pipelineIDToWB((mask & hits).orR)), Seq(
      ("exception", () => false.B),
      ("load", () => id_ctrl.mem && id_ctrl.mem_cmd === M_XRD && !id_ctrl.fp),
      ("store", () => id_ctrl.mem && id_ctrl.mem_cmd === M_XWR && !id_ctrl.fp),
      ("amo", () => usingAtomics.B && id_ctrl.mem && (isAMO(id_ctrl.mem_cmd) || id_ctrl.mem_cmd.isOneOf(M_XLR, M_XSC))),
      ("system", () => id_ctrl.csr =/= CSR.N),
      ("arith", () => id_ctrl.wxd && !(id_ctrl.jal || id_ctrl.jalr || id_ctrl.mem || id_ctrl.fp || id_ctrl.mul || id_ctrl.div || id_ctrl.csr =/= CSR.N)),
      ("branch", () => id_ctrl.branch),
      ("jal", () => id_ctrl.jal),
      ("jalr", () => id_ctrl.jalr))
      ++ (if (!usingMulDiv) Seq() else Seq(
        ("mul", () => if (pipelinedMul) id_ctrl.mul else id_ctrl.div && (id_ctrl.alu_fn & aluFn.FN_DIV) =/= aluFn.FN_DIV),
        ("div", () => if (pipelinedMul) id_ctrl.div else id_ctrl.div && (id_ctrl.alu_fn & aluFn.FN_DIV) === aluFn.FN_DIV)))
      ++ (if (!usingFPU) Seq() else Seq(
        ("fp load", () => id_ctrl.fp && io.fpu.dec.ldst && io.fpu.dec.wen),
        ("fp store", () => id_ctrl.fp && io.fpu.dec.ldst && !io.fpu.dec.wen),
        ("fp add", () => id_ctrl.fp && io.fpu.dec.fma && io.fpu.dec.swap23),
        ("fp mul", () => id_ctrl.fp && io.fpu.dec.fma && !io.fpu.dec.swap23 && !io.fpu.dec.ren3),
        ("fp mul-add", () => id_ctrl.fp && io.fpu.dec.fma && io.fpu.dec.ren3),
        ("fp div/sqrt", () => id_ctrl.fp && (io.fpu.dec.div || io.fpu.dec.sqrt)),
        ("fp other", () => id_ctrl.fp && !(io.fpu.dec.ldst || io.fpu.dec.fma || io.fpu.dec.div || io.fpu.dec.sqrt))))),
    new EventSet((mask, hits) => (mask & hits).orR, Seq(
      ("load-use interlock", () => id_ex_hazard && ex_ctrl.mem || id_mem_hazard && mem_ctrl.mem || id_wb_hazard && wb_ctrl.mem),
      ("long-latency interlock", () => id_sboard_hazard),
      ("csr interlock", () => id_ex_hazard && ex_ctrl.csr =/= CSR.N || id_mem_hazard && mem_ctrl.csr =/= CSR.N || id_wb_hazard && wb_ctrl.csr =/= CSR.N),
      ("I$ blocked", () => icache_blocked),
      ("D$ blocked", () => id_ctrl.mem && dcache_blocked),
      ("branch misprediction", () => take_pc_mem && mem_direction_misprediction),
      ("control-flow target misprediction", () => take_pc_mem && mem_misprediction && mem_cfi && !mem_direction_misprediction && !icache_blocked),
      ("flush", () => wb_reg_flush_pipe),
      ("replay", () => replay_wb))
      ++ (if (!usingMulDiv) Seq() else Seq(
        ("mul/div interlock", () => id_ex_hazard && (ex_ctrl.mul || ex_ctrl.div) || id_mem_hazard && (mem_ctrl.mul || mem_ctrl.div) || id_wb_hazard && wb_ctrl.div)))
      ++ (if (!usingFPU) Seq() else Seq(
        ("fp interlock", () => id_ex_hazard && ex_ctrl.fp || id_mem_hazard && mem_ctrl.fp || id_wb_hazard && wb_ctrl.fp || id_ctrl.fp && id_stall_fpu)))),
    new EventSet((mask, hits) => (mask & hits).orR, Seq(
      ("I$ miss", () => io.imem.perf.acquire),
      ("D$ miss", () => io.dmem.perf.acquire),
      ("D$ release", () => io.dmem.perf.release),
      ("ITLB miss", () => io.imem.perf.tlbMiss),
      ("DTLB miss", () => io.dmem.perf.tlbMiss),
      ("L2 TLB miss", () => io.ptw.perf.l2miss)))))

  val pipelinedMul = usingMulDiv && mulDivParams.mulUnroll == xLen
  val decode_table = {
    (if (usingMulDiv) new MDecode(pipelinedMul, aluFn) +: (xLen > 32).option(new M64Decode(pipelinedMul, aluFn)).toSeq else Nil) ++:
    (if (usingAtomics) new ADecode(aluFn) +: (xLen > 32).option(new A64Decode(aluFn)).toSeq else Nil) ++:
    (if (fLen >= 32)    new FDecode(aluFn) +: (xLen > 32).option(new F64Decode(aluFn)).toSeq else Nil) ++:
    (if (fLen >= 64)    new DDecode(aluFn) +: (xLen > 32).option(new D64Decode(aluFn)).toSeq else Nil) ++:
    (if (minFLen == 16) new HDecode(aluFn) +: (xLen > 32).option(new H64Decode(aluFn)).toSeq ++: (fLen >= 64).option(new HDDecode(aluFn)).toSeq else Nil) ++:
    (usingRoCC.option(new RoCCDecode(aluFn))) ++:
    (if (xLen == 32) new I32Decode(aluFn) else new I64Decode(aluFn)) +:
    (usingVM.option(new SVMDecode(aluFn))) ++:
    (usingSupervisor.option(new SDecode(aluFn))) ++:
    (usingHypervisor.option(new HypervisorDecode(aluFn))) ++:
    ((usingHypervisor && (xLen == 64)).option(new Hypervisor64Decode(aluFn))) ++:
    (usingDebug.option(new DebugDecode(aluFn))) ++:
    (usingNMI.option(new NMIDecode(aluFn))) ++:
    (usingConditionalZero.option(new ConditionalZeroDecode(aluFn))) ++:
    Seq(new FenceIDecode(true, aluFn)) ++:
    coreParams.haveCFlush.option(new CFlushDecode(false, aluFn)) ++:
    rocketParams.haveCease.option(new CeaseDecode(aluFn)) ++:
    usingVector.option(new VCFGDecode(aluFn)) ++:
    Seq(new IDecode(aluFn))
  } flatMap(_.table)

  val ex_ctrl = Reg(new IntCtrlSigs(aluFn))
  val mem_ctrl = Reg(new IntCtrlSigs(aluFn))
  val wb_ctrl = Reg(new IntCtrlSigs(aluFn))

  val ex_reg_xcpt_interrupt  = Reg(Bool())
  val ex_reg_valid           = Reg(Bool())
  val ex_reg_rvc             = Reg(Bool())
  val ex_reg_btb_resp        = Reg(new BTBResp)
  val ex_reg_xcpt            = Reg(Bool())
  val ex_reg_flush_pipe      = Reg(Bool())
  val ex_reg_load_use        = Reg(Bool())
  val ex_reg_cause           = Reg(UInt())
  val ex_reg_replay = Reg(Bool())
  val ex_reg_pc = Reg(UInt())
  val ex_reg_mem_size = Reg(UInt())
  val ex_reg_hls = Reg(Bool())
  val ex_reg_inst = Reg(Bits())
  val ex_reg_raw_inst = Reg(UInt())
  val ex_reg_wphit            = Reg(Vec(nBreakpoints, Bool()))
  val ex_reg_set_vconfig      = Reg(Bool())

  val mem_reg_xcpt_interrupt  = Reg(Bool())
  val mem_reg_valid           = Reg(Bool())
  val mem_reg_rvc             = Reg(Bool())
  val mem_reg_btb_resp        = Reg(new BTBResp)
  val mem_reg_xcpt            = Reg(Bool())
  val mem_reg_replay          = Reg(Bool())
  val mem_reg_flush_pipe      = Reg(Bool())
  val mem_reg_cause           = Reg(UInt())
  val mem_reg_slow_bypass     = Reg(Bool())
  val mem_reg_load            = Reg(Bool())
  val mem_reg_store           = Reg(Bool())
  val mem_reg_set_vconfig     = Reg(Bool())
  val mem_reg_sfence = Reg(Bool())
  val mem_reg_pc = Reg(UInt())
  val mem_reg_inst = Reg(Bits())
  val mem_reg_mem_size = Reg(UInt())
  val mem_reg_hls_or_dv = Reg(Bool())
  val mem_reg_raw_inst = Reg(UInt())
  val mem_reg_wdata = Reg(Bits())
  val mem_reg_rs2 = Reg(Bits())
  val mem_br_taken = Reg(Bool())
  val take_pc_mem = Wire(Bool())
  val mem_reg_wphit          = Reg(Vec(nBreakpoints, Bool()))

  val wb_reg_valid           = Reg(Bool())
  val wb_reg_xcpt            = Reg(Bool())
  val wb_reg_replay          = Reg(Bool())
  val wb_reg_flush_pipe      = Reg(Bool())
  val wb_reg_cause           = Reg(UInt())
  val wb_reg_set_vconfig     = Reg(Bool())
  val wb_reg_sfence = Reg(Bool())
  val wb_reg_pc = Reg(UInt())
  val wb_reg_mem_size = Reg(UInt())
  val wb_reg_hls_or_dv = Reg(Bool())
  val wb_reg_hfence_v = Reg(Bool())
  val wb_reg_hfence_g = Reg(Bool())
  val wb_reg_inst = Reg(Bits())
  val wb_reg_raw_inst = Reg(UInt())
  val wb_reg_wdata = Reg(Bits())
  val wb_reg_rs2 = Reg(Bits())
  val take_pc_wb = Wire(Bool())
  val wb_reg_wphit           = Reg(Vec(nBreakpoints, Bool()))

  val take_pc_mem_wb = take_pc_wb || take_pc_mem
  val take_pc = take_pc_mem_wb

  // decode stage
  val ibuf = Module(new IBuf)
  val id_expanded_inst = ibuf.io.inst.map(_.bits.inst)
  val id_raw_inst = ibuf.io.inst.map(_.bits.raw)
  val id_inst = id_expanded_inst.map(_.bits)
  ibuf.io.imem <> io.imem.resp
  ibuf.io.kill := take_pc

  require(decodeWidth == 1 /* TODO */ && retireWidth == decodeWidth)
  require(!(coreParams.useRVE && coreParams.fpu.nonEmpty), "Can't select both RVE and floating-point")
  require(!(coreParams.useRVE && coreParams.useHypervisor), "Can't select both RVE and Hypervisor")
  val id_ctrl = Wire(new IntCtrlSigs(aluFn)).decode(id_inst(0), decode_table)

  val lgNXRegs = if (coreParams.useRVE) 4 else 5
  val regAddrMask = (1 << lgNXRegs) - 1

  def decodeReg(x: UInt) = (x.extract(x.getWidth-1, lgNXRegs).asBool, x(lgNXRegs-1, 0))
  val (id_raddr3_illegal, id_raddr3) = decodeReg(id_expanded_inst(0).rs3)
  val (id_raddr2_illegal, id_raddr2) = decodeReg(id_expanded_inst(0).rs2)
  val (id_raddr1_illegal, id_raddr1) = decodeReg(id_expanded_inst(0).rs1)
  val (id_waddr_illegal,  id_waddr)  = decodeReg(id_expanded_inst(0).rd)

  val id_load_use = Wire(Bool())
  val id_reg_fence = RegInit(false.B)
  val id_ren = IndexedSeq(id_ctrl.rxs1, id_ctrl.rxs2)
  val id_raddr = IndexedSeq(id_raddr1, id_raddr2)
  val rf = new RegFile(regAddrMask, xLen)
  val id_rs = id_raddr.map(rf.read _)
  val ctrl_killd = Wire(Bool())
  val id_npc = (ibuf.io.pc.asSInt + ImmGen(IMM_UJ, id_inst(0))).asUInt

  val csr = Module(new CSRFile(perfEvents, coreParams.customCSRs.decls, Seq(), false))
  val id_csr_en = id_ctrl.csr.isOneOf(CSR.S, CSR.C, CSR.W)
  val id_system_insn = id_ctrl.csr === CSR.I
  val id_csr_ren = id_ctrl.csr.isOneOf(CSR.S, CSR.C) && id_expanded_inst(0).rs1 === 0.U
  val id_csr = Mux(id_system_insn && id_ctrl.mem, CSR.N, Mux(id_csr_ren, CSR.R, id_ctrl.csr))
  val id_csr_flush = id_system_insn || (id_csr_en && !id_csr_ren && csr.io.decode(0).write_flush)
  val id_set_vconfig = Seq(Instructions.VSETVLI, Instructions.VSETIVLI, Instructions.VSETVL).map(_ === id_inst(0)).orR && usingVector.B

  id_ctrl.vec := false.B
  if (usingVector) {
    val v_decode = rocketParams.vector.get.decoder(p)
    v_decode.io.inst := id_inst(0)
    v_decode.io.vconfig := csr.io.vector.get.vconfig
    when (v_decode.io.legal) {
      id_ctrl.legal := !csr.io.vector.get.vconfig.vtype.vill
      id_ctrl.fp := v_decode.io.fp
      id_ctrl.rocc := false.B
      id_ctrl.branch := false.B
      id_ctrl.jal := false.B
      id_ctrl.jalr := false.B
      id_ctrl.rxs2 := v_decode.io.read_rs2
      id_ctrl.rxs1 := v_decode.io.read_rs1
      id_ctrl.mem := false.B
      id_ctrl.rfs1 := v_decode.io.read_frs1
      id_ctrl.rfs2 := false.B
      id_ctrl.rfs3 := false.B
      id_ctrl.wfd := v_decode.io.write_frd
      id_ctrl.mul := false.B
      id_ctrl.div := false.B
      id_ctrl.wxd := v_decode.io.write_rd
      id_ctrl.csr := CSR.N
      id_ctrl.fence_i := false.B
      id_ctrl.fence := false.B
      id_ctrl.amo := false.B
      id_ctrl.dp := false.B
      id_ctrl.vec := true.B
    }
  }


  val id_illegal_insn = !id_ctrl.legal ||
    (id_ctrl.mul || id_ctrl.div) && !csr.io.status.isa('m'-'a') ||
    id_ctrl.amo && !csr.io.status.isa('a'-'a') ||
    id_ctrl.fp && (csr.io.decode(0).fp_illegal || (io.fpu.illegal_rm && !id_ctrl.vec)) ||
    (id_ctrl.vec) && (csr.io.decode(0).vector_illegal || csr.io.vector.map(_.vconfig.vtype.vill).getOrElse(false.B)) ||
    id_ctrl.dp && !csr.io.status.isa('d'-'a') ||
    ibuf.io.inst(0).bits.rvc && !csr.io.status.isa('c'-'a') ||
    id_raddr2_illegal && id_ctrl.rxs2 ||
    id_raddr1_illegal && id_ctrl.rxs1 ||
    id_waddr_illegal && id_ctrl.wxd ||
    id_ctrl.rocc && csr.io.decode(0).rocc_illegal ||
    id_csr_en && (csr.io.decode(0).read_illegal || !id_csr_ren && csr.io.decode(0).write_illegal) ||
    !ibuf.io.inst(0).bits.rvc && (id_system_insn && csr.io.decode(0).system_illegal)
  val id_virtual_insn = id_ctrl.legal &&
    ((id_csr_en && !(!id_csr_ren && csr.io.decode(0).write_illegal) && csr.io.decode(0).virtual_access_illegal) ||
     (!ibuf.io.inst(0).bits.rvc && id_system_insn && csr.io.decode(0).virtual_system_illegal))
  // stall decode for fences (now, for AMO.rl; later, for AMO.aq and FENCE)
  val id_amo_aq = id_inst(0)(26)
  val id_amo_rl = id_inst(0)(25)
  val id_fence_pred = id_inst(0)(27,24)
  val id_fence_succ = id_inst(0)(23,20)
  val id_fence_next = id_ctrl.fence || id_ctrl.amo && id_amo_aq
  val id_mem_busy = !io.dmem.ordered || io.dmem.req.valid
  when (!id_mem_busy) { id_reg_fence := false.B }
  val id_rocc_busy = usingRoCC.B &&
    (io.rocc.busy || ex_reg_valid && ex_ctrl.rocc ||
     mem_reg_valid && mem_ctrl.rocc || wb_reg_valid && wb_ctrl.rocc)
  val id_csr_rocc_write = false.B
  val id_vec_busy = io.vector.map(v => v.backend_busy || v.trap_check_busy).getOrElse(false.B)
  val id_do_fence = WireDefault(id_rocc_busy && (id_ctrl.fence || id_csr_rocc_write) ||
    id_vec_busy && id_ctrl.fence ||
    id_mem_busy && (id_ctrl.amo && id_amo_rl || id_ctrl.fence_i || id_reg_fence && (id_ctrl.mem || id_ctrl.rocc)))

  val bpu = Module(new BreakpointUnit(nBreakpoints))
  bpu.io.status := csr.io.status
  bpu.io.bp := csr.io.bp
  bpu.io.pc := ibuf.io.pc
  bpu.io.ea := mem_reg_wdata
  bpu.io.mcontext := csr.io.mcontext
  bpu.io.scontext := csr.io.scontext

  val id_xcpt0 = ibuf.io.inst(0).bits.xcpt0
  val id_xcpt1 = ibuf.io.inst(0).bits.xcpt1
  val (id_xcpt, id_cause) = checkExceptions(List(
    (csr.io.interrupt, csr.io.interrupt_cause),
    (bpu.io.debug_if,  CSR.debugTriggerCause.U),
    (bpu.io.xcpt_if,   Causes.breakpoint.U),
    (id_xcpt0.pf.inst, Causes.fetch_page_fault.U),
    (id_xcpt0.gf.inst, Causes.fetch_guest_page_fault.U),
    (id_xcpt0.ae.inst, Causes.fetch_access.U),
    (id_xcpt1.pf.inst, Causes.fetch_page_fault.U),
    (id_xcpt1.gf.inst, Causes.fetch_guest_page_fault.U),
    (id_xcpt1.ae.inst, Causes.fetch_access.U),
    (id_virtual_insn,  Causes.virtual_instruction.U),
    (id_illegal_insn,  Causes.illegal_instruction.U)))

  val idCoverCauses = List(
    (CSR.debugTriggerCause, "DEBUG_TRIGGER"),
    (Causes.breakpoint, "BREAKPOINT"),
    (Causes.fetch_access, "FETCH_ACCESS"),
    (Causes.illegal_instruction, "ILLEGAL_INSTRUCTION")
  ) ++ (if (usingVM) List(
    (Causes.fetch_page_fault, "FETCH_PAGE_FAULT")
  ) else Nil)
  coverExceptions(id_xcpt, id_cause, "DECODE", idCoverCauses)

  val dcache_bypass_data =
    if (fastLoadByte) io.dmem.resp.bits.data(xLen-1, 0)
    else if (fastLoadWord) io.dmem.resp.bits.data_word_bypass(xLen-1, 0)
    else wb_reg_wdata

  // detect bypass opportunities
  val ex_waddr = ex_reg_inst(11,7) & regAddrMask.U
  val mem_waddr = mem_reg_inst(11,7) & regAddrMask.U
  val wb_waddr = wb_reg_inst(11,7) & regAddrMask.U
  val bypass_sources = IndexedSeq(
    (true.B, 0.U, 0.U), // treat reading x0 as a bypass
    (ex_reg_valid && ex_ctrl.wxd, ex_waddr, mem_reg_wdata),
    (mem_reg_valid && mem_ctrl.wxd && !mem_ctrl.mem, mem_waddr, wb_reg_wdata),
    (mem_reg_valid && mem_ctrl.wxd, mem_waddr, dcache_bypass_data))
  val id_bypass_src = id_raddr.map(raddr => bypass_sources.map(s => s._1 && s._2 === raddr))

  // execute stage
  val bypass_mux = bypass_sources.map(_._3)
  val ex_reg_rs_bypass = Reg(Vec(id_raddr.size, Bool()))
  val ex_reg_rs_lsb = Reg(Vec(id_raddr.size, UInt(log2Ceil(bypass_sources.size).W)))
  val ex_reg_rs_msb = Reg(Vec(id_raddr.size, UInt()))
  val ex_rs = for (i <- 0 until id_raddr.size)
    yield Mux(ex_reg_rs_bypass(i), bypass_mux(ex_reg_rs_lsb(i)), Cat(ex_reg_rs_msb(i), ex_reg_rs_lsb(i)))
  val ex_imm = ImmGen(ex_ctrl.sel_imm, ex_reg_inst)
  val ex_op1 = MuxLookup(ex_ctrl.sel_alu1, 0.S)(Seq(
    A1_RS1 -> ex_rs(0).asSInt,
    A1_PC -> ex_reg_pc.asSInt))
  val ex_op2 = MuxLookup(ex_ctrl.sel_alu2, 0.S)(Seq(
    A2_RS2 -> ex_rs(1).asSInt,
    A2_IMM -> ex_imm,
    A2_SIZE -> Mux(ex_reg_rvc, 2.S, 4.S)))

  val (ex_new_vl, ex_new_vconfig) = if (usingVector) {
    val ex_new_vtype = VType.fromUInt(MuxCase(ex_rs(1), Seq(
      ex_reg_inst(31,30).andR -> ex_reg_inst(29,20),
      !ex_reg_inst(31)        -> ex_reg_inst(30,20))))
    val ex_avl = Mux(ex_ctrl.rxs1,
      Mux(ex_reg_inst(19,15) === 0.U,
        Mux(ex_reg_inst(11,6) === 0.U, csr.io.vector.get.vconfig.vl, ex_new_vtype.vlMax),
        ex_rs(0)
      ),
      ex_reg_inst(19,15))
    val ex_new_vl = ex_new_vtype.vl(ex_avl, csr.io.vector.get.vconfig.vl, false.B, false.B, false.B)
    val ex_new_vconfig = Wire(new VConfig)
    ex_new_vconfig.vtype := ex_new_vtype
    ex_new_vconfig.vl := ex_new_vl
    (Some(ex_new_vl), Some(ex_new_vconfig))
  } else { (None, None) }

  val alu = Module(aluFn match {
    case _: ALUFN => new ALU
  })
  alu.io.dw := ex_ctrl.alu_dw
  alu.io.fn := ex_ctrl.alu_fn
  alu.io.in2 := ex_op2.asUInt
  alu.io.in1 := ex_op1.asUInt

  // multiplier and divider
  val div = Module(new MulDiv(if (pipelinedMul) mulDivParams.copy(mulUnroll = 0) else mulDivParams, width = xLen, aluFn = aluFn))
  div.io.req.valid := ex_reg_valid && ex_ctrl.div
  div.io.req.bits.dw := ex_ctrl.alu_dw
  div.io.req.bits.fn := ex_ctrl.alu_fn
  div.io.req.bits.in1 := ex_rs(0)
  div.io.req.bits.in2 := ex_rs(1)
  div.io.req.bits.tag := ex_waddr
  val mul = pipelinedMul.option {
    val m = Module(new PipelinedMultiplier(xLen, 2, aluFn = aluFn))
    m.io.req.valid := ex_reg_valid && ex_ctrl.mul
    m.io.req.bits := div.io.req.bits
    m
  }

  ex_reg_valid := !ctrl_killd
  ex_reg_replay := !take_pc && ibuf.io.inst(0).valid && ibuf.io.inst(0).bits.replay
  ex_reg_xcpt := !ctrl_killd && id_xcpt
  ex_reg_xcpt_interrupt := !take_pc && ibuf.io.inst(0).valid && csr.io.interrupt

  when (!ctrl_killd) {
    ex_ctrl := id_ctrl
    ex_reg_rvc := ibuf.io.inst(0).bits.rvc
    ex_ctrl.csr := id_csr
    when (id_ctrl.fence && id_fence_succ === 0.U) { id_reg_pause := true.B }
    when (id_fence_next) { id_reg_fence := true.B }
    when (id_xcpt) { // pass PC down ALU writeback pipeline for badaddr
      ex_ctrl.alu_fn := aluFn.FN_ADD
      ex_ctrl.alu_dw := DW_XPR
      ex_ctrl.sel_alu1 := A1_RS1 // badaddr := instruction
      ex_ctrl.sel_alu2 := A2_ZERO
      when (id_xcpt1.asUInt.orR) { // badaddr := PC+2
        ex_ctrl.sel_alu1 := A1_PC
        ex_ctrl.sel_alu2 := A2_SIZE
        ex_reg_rvc := true.B
      }
      when (bpu.io.xcpt_if || id_xcpt0.asUInt.orR) { // badaddr := PC
        ex_ctrl.sel_alu1 := A1_PC
        ex_ctrl.sel_alu2 := A2_ZERO
      }
    }
    ex_reg_flush_pipe := id_ctrl.fence_i || id_csr_flush
    ex_reg_load_use := id_load_use
    ex_reg_hls := usingHypervisor.B && id_system_insn && id_ctrl.mem_cmd.isOneOf(M_XRD, M_XWR, M_HLVX)
    ex_reg_mem_size := Mux(usingHypervisor.B && id_system_insn, id_inst(0)(27, 26), id_inst(0)(13, 12))
    when (id_ctrl.mem_cmd.isOneOf(M_SFENCE, M_HFENCEV, M_HFENCEG, M_FLUSH_ALL)) {
      ex_reg_mem_size := Cat(id_raddr2 =/= 0.U, id_raddr1 =/= 0.U)
    }
    when (id_ctrl.mem_cmd === M_SFENCE && csr.io.status.v) {
      ex_ctrl.mem_cmd := M_HFENCEV
    }
    when (id_ctrl.fence_i) {
      ex_reg_mem_size := 0.U
    }

    for (i <- 0 until id_raddr.size) {
      val do_bypass = id_bypass_src(i).reduce(_||_)
      val bypass_src = PriorityEncoder(id_bypass_src(i))
      ex_reg_rs_bypass(i) := do_bypass
      ex_reg_rs_lsb(i) := bypass_src
      when (id_ren(i) && !do_bypass) {
        ex_reg_rs_lsb(i) := id_rs(i)(log2Ceil(bypass_sources.size)-1, 0)
        ex_reg_rs_msb(i) := id_rs(i) >> log2Ceil(bypass_sources.size)
      }
    }
    when (id_illegal_insn || id_virtual_insn) {
      val inst = Mux(ibuf.io.inst(0).bits.rvc, id_raw_inst(0)(15, 0), id_raw_inst(0))
      ex_reg_rs_bypass(0) := false.B
      ex_reg_rs_lsb(0) := inst(log2Ceil(bypass_sources.size)-1, 0)
      ex_reg_rs_msb(0) := inst >> log2Ceil(bypass_sources.size)
    }
  }
  when (!ctrl_killd || csr.io.interrupt || ibuf.io.inst(0).bits.replay) {
    ex_reg_cause := id_cause
    ex_reg_inst := id_inst(0)
    ex_reg_raw_inst := id_raw_inst(0)
    ex_reg_pc := ibuf.io.pc
    ex_reg_btb_resp := ibuf.io.btb_resp
    ex_reg_wphit := bpu.io.bpwatch.map { bpw => bpw.ivalid(0) }
    ex_reg_set_vconfig := id_set_vconfig && !id_xcpt
  }

  // replay inst in ex stage?
  val ex_pc_valid = ex_reg_valid || ex_reg_replay || ex_reg_xcpt_interrupt
  val wb_dcache_miss = wb_ctrl.mem && !io.dmem.resp.valid
  val replay_ex_structural = ex_ctrl.mem && !io.dmem.req.ready ||
                             ex_ctrl.div && !div.io.req.ready ||
                             ex_ctrl.vec && !io.vector.map(_.ex.ready).getOrElse(true.B)
  val replay_ex_load_use = wb_dcache_miss && ex_reg_load_use
  val replay_ex = ex_reg_replay || (ex_reg_valid && (replay_ex_structural || replay_ex_load_use))
  val ctrl_killx = take_pc_mem_wb || replay_ex || !ex_reg_valid
  // detect 2-cycle load-use delay for LB/LH/SC
  val ex_slow_bypass = ex_ctrl.mem_cmd === M_XSC || ex_reg_mem_size < 2.U
  val ex_sfence = usingVM.B && ex_ctrl.mem && (ex_ctrl.mem_cmd === M_SFENCE || ex_ctrl.mem_cmd === M_HFENCEV || ex_ctrl.mem_cmd === M_HFENCEG)

  val (ex_xcpt, ex_cause) = checkExceptions(List(
    (ex_reg_xcpt_interrupt || ex_reg_xcpt, ex_reg_cause)))

  val exCoverCauses = idCoverCauses
  coverExceptions(ex_xcpt, ex_cause, "EXECUTE", exCoverCauses)

  // memory stage
  val mem_pc_valid = mem_reg_valid || mem_reg_replay || mem_reg_xcpt_interrupt
  val mem_br_target = mem_reg_pc.asSInt +
    Mux(mem_ctrl.branch && mem_br_taken, ImmGen(IMM_SB, mem_reg_inst),
    Mux(mem_ctrl.jal, ImmGen(IMM_UJ, mem_reg_inst),
    Mux(mem_reg_rvc, 2.S, 4.S)))
  val mem_npc = (Mux(mem_ctrl.jalr || mem_reg_sfence, encodeVirtualAddress(mem_reg_wdata, mem_reg_wdata).asSInt, mem_br_target) & (-2).S).asUInt
  val mem_wrong_npc =
    Mux(ex_pc_valid, mem_npc =/= ex_reg_pc,
    Mux(ibuf.io.inst(0).valid || ibuf.io.imem.valid, mem_npc =/= ibuf.io.pc, true.B))
  val mem_npc_misaligned = !csr.io.status.isa('c'-'a') && mem_npc(1) && !mem_reg_sfence
  val mem_int_wdata = Mux(!mem_reg_xcpt && (mem_ctrl.jalr ^ mem_npc_misaligned), mem_br_target, mem_reg_wdata.asSInt).asUInt
  val mem_cfi = mem_ctrl.branch || mem_ctrl.jalr || mem_ctrl.jal
  val mem_cfi_taken = (mem_ctrl.branch && mem_br_taken) || mem_ctrl.jalr || mem_ctrl.jal
  val mem_direction_misprediction = mem_ctrl.branch && mem_br_taken =/= (usingBTB.B && mem_reg_btb_resp.taken)
  val mem_misprediction = if (usingBTB) mem_wrong_npc else mem_cfi_taken
  take_pc_mem := mem_reg_valid && !mem_reg_xcpt && (mem_misprediction || mem_reg_sfence)

  mem_reg_valid := !ctrl_killx
  mem_reg_replay := !take_pc_mem_wb && replay_ex
  mem_reg_xcpt := !ctrl_killx && ex_xcpt
  mem_reg_xcpt_interrupt := !take_pc_mem_wb && ex_reg_xcpt_interrupt

  // on pipeline flushes, cause mem_npc to hold the sequential npc, which
  // will drive the W-stage npc mux
  when (mem_reg_valid && mem_reg_flush_pipe) {
    mem_reg_sfence := false.B
  }.elsewhen (ex_pc_valid) {
    mem_ctrl := ex_ctrl
    mem_reg_rvc := ex_reg_rvc
    mem_reg_load := ex_ctrl.mem && isRead(ex_ctrl.mem_cmd)
    mem_reg_store := ex_ctrl.mem && isWrite(ex_ctrl.mem_cmd)
    mem_reg_sfence := ex_sfence
    mem_reg_btb_resp := ex_reg_btb_resp
    mem_reg_flush_pipe := ex_reg_flush_pipe
    mem_reg_slow_bypass := ex_slow_bypass
    mem_reg_wphit := ex_reg_wphit
    mem_reg_set_vconfig := ex_reg_set_vconfig

    mem_reg_cause := ex_cause
    mem_reg_inst := ex_reg_inst
    mem_reg_raw_inst := ex_reg_raw_inst
    mem_reg_mem_size := ex_reg_mem_size
    mem_reg_hls_or_dv := io.dmem.req.bits.dv
    mem_reg_pc := ex_reg_pc
    // IDecode ensured they are 1H
    mem_reg_wdata := Mux(ex_reg_set_vconfig, ex_new_vl.getOrElse(alu.io.out), alu.io.out)
    mem_br_taken := alu.io.cmp_out


    when (ex_ctrl.rxs2 && (ex_ctrl.mem || ex_ctrl.rocc || ex_sfence)) {
      val size = Mux(ex_ctrl.rocc, log2Ceil(xLen/8).U, ex_reg_mem_size)
      mem_reg_rs2 := new StoreGen(size, 0.U, ex_rs(1), coreDataBytes).data
    }
    if (usingVector) { when (ex_reg_set_vconfig) {
      mem_reg_rs2 := ex_new_vconfig.get.asUInt
    } }
    when (ex_ctrl.jalr && csr.io.status.debug) {
      // flush I$ on D-mode JALR to effect uncached fetch without D$ flush
      mem_ctrl.fence_i := true.B
      mem_reg_flush_pipe := true.B
    }
  }

  val mem_breakpoint = (mem_reg_load && bpu.io.xcpt_ld) || (mem_reg_store && bpu.io.xcpt_st)
  val mem_debug_breakpoint = (mem_reg_load && bpu.io.debug_ld) || (mem_reg_store && bpu.io.debug_st)
  val (mem_ldst_xcpt, mem_ldst_cause) = checkExceptions(List(
    (mem_debug_breakpoint, CSR.debugTriggerCause.U),
    (mem_breakpoint,       Causes.breakpoint.U)))

  val (mem_xcpt, mem_cause) = checkExceptions(List(
    (mem_reg_xcpt_interrupt || mem_reg_xcpt, mem_reg_cause),
    (mem_reg_valid && mem_npc_misaligned,    Causes.misaligned_fetch.U),
    (mem_reg_valid && mem_ldst_xcpt,         mem_ldst_cause)))

  val memCoverCauses = (exCoverCauses ++ List(
    (CSR.debugTriggerCause, "DEBUG_TRIGGER"),
    (Causes.breakpoint, "BREAKPOINT"),
    (Causes.misaligned_fetch, "MISALIGNED_FETCH")
  )).distinct
  coverExceptions(mem_xcpt, mem_cause, "MEMORY", memCoverCauses)

  val dcache_kill_mem = mem_reg_valid && mem_ctrl.wxd && io.dmem.replay_next // structural hazard on writeback port
  val fpu_kill_mem = mem_reg_valid && mem_ctrl.fp && io.fpu.nack_mem
  val vec_kill_mem = mem_reg_valid && mem_ctrl.mem && io.vector.map(_.mem.block_mem).getOrElse(false.B)
  val vec_kill_all = mem_reg_valid && io.vector.map(_.mem.block_all).getOrElse(false.B)
  val replay_mem  = dcache_kill_mem || mem_reg_replay || fpu_kill_mem || vec_kill_mem || vec_kill_all
  val killm_common = dcache_kill_mem || take_pc_wb || mem_reg_xcpt || !mem_reg_valid
  div.io.kill := killm_common && RegNext(div.io.req.fire)
  val ctrl_killm = killm_common || mem_xcpt || fpu_kill_mem || vec_kill_mem

  // writeback stage
  wb_reg_valid := !ctrl_killm
  wb_reg_replay := replay_mem && !take_pc_wb
  wb_reg_xcpt := mem_xcpt && !take_pc_wb && !io.vector.map(_.mem.block_all).getOrElse(false.B)
  wb_reg_flush_pipe := !ctrl_killm && mem_reg_flush_pipe
  when (mem_pc_valid) {
    wb_ctrl := mem_ctrl
    wb_reg_sfence := mem_reg_sfence
    wb_reg_wdata := Mux(!mem_reg_xcpt && mem_ctrl.fp && mem_ctrl.wxd, io.fpu.toint_data, mem_int_wdata)
    when (mem_ctrl.rocc || mem_reg_sfence || mem_reg_set_vconfig) {
      wb_reg_rs2 := mem_reg_rs2
    }
    wb_reg_cause := mem_cause
    wb_reg_inst := mem_reg_inst
    wb_reg_raw_inst := mem_reg_raw_inst
    wb_reg_mem_size := mem_reg_mem_size
    wb_reg_hls_or_dv := mem_reg_hls_or_dv
    wb_reg_hfence_v := mem_ctrl.mem_cmd === M_HFENCEV
    wb_reg_hfence_g := mem_ctrl.mem_cmd === M_HFENCEG
    wb_reg_pc := mem_reg_pc
    wb_reg_wphit := mem_reg_wphit | bpu.io.bpwatch.map { bpw => (bpw.rvalid(0) && mem_reg_load) || (bpw.wvalid(0) && mem_reg_store) }
    wb_reg_set_vconfig := mem_reg_set_vconfig
  }

  val (wb_xcpt, wb_cause) = checkExceptions(List(
    (wb_reg_xcpt,  wb_reg_cause),
    (wb_reg_valid && wb_ctrl.mem && io.dmem.s2_xcpt.pf.st, Causes.store_page_fault.U),
    (wb_reg_valid && wb_ctrl.mem && io.dmem.s2_xcpt.pf.ld, Causes.load_page_fault.U),
    (wb_reg_valid && wb_ctrl.mem && io.dmem.s2_xcpt.gf.st, Causes.store_guest_page_fault.U),
    (wb_reg_valid && wb_ctrl.mem && io.dmem.s2_xcpt.gf.ld, Causes.load_guest_page_fault.U),
    (wb_reg_valid && wb_ctrl.mem && io.dmem.s2_xcpt.ae.st, Causes.store_access.U),
    (wb_reg_valid && wb_ctrl.mem && io.dmem.s2_xcpt.ae.ld, Causes.load_access.U),
    (wb_reg_valid && wb_ctrl.mem && io.dmem.s2_xcpt.ma.st, Causes.misaligned_store.U),
    (wb_reg_valid && wb_ctrl.mem && io.dmem.s2_xcpt.ma.ld, Causes.misaligned_load.U)
  ))

  val wbCoverCauses = List(
    (Causes.misaligned_store, "MISALIGNED_STORE"),
    (Causes.misaligned_load, "MISALIGNED_LOAD"),
    (Causes.store_access, "STORE_ACCESS"),
    (Causes.load_access, "LOAD_ACCESS")
  ) ++ (if(usingVM) List(
    (Causes.store_page_fault, "STORE_PAGE_FAULT"),
    (Causes.load_page_fault, "LOAD_PAGE_FAULT")
  ) else Nil) ++ (if (usingHypervisor) List(
    (Causes.store_guest_page_fault, "STORE_GUEST_PAGE_FAULT"),
    (Causes.load_guest_page_fault, "LOAD_GUEST_PAGE_FAULT"),
  ) else Nil)
  coverExceptions(wb_xcpt, wb_cause, "WRITEBACK", wbCoverCauses)

  val wb_pc_valid = wb_reg_valid || wb_reg_replay || wb_reg_xcpt
  val wb_wxd = wb_reg_valid && wb_ctrl.wxd
  val wb_set_sboard = wb_ctrl.div || wb_dcache_miss || wb_ctrl.rocc || wb_ctrl.vec
  val replay_wb_common = io.dmem.s2_nack || wb_reg_replay
  val replay_wb_rocc = wb_reg_valid && wb_ctrl.rocc && !io.rocc.cmd.ready
  val replay_wb_csr: Bool = wb_reg_valid && csr.io.rw_stall
  val replay_wb_vec = wb_reg_valid && io.vector.map(_.wb.replay).getOrElse(false.B)
  val replay_wb = replay_wb_common || replay_wb_rocc || replay_wb_csr || replay_wb_vec
  take_pc_wb := replay_wb || wb_xcpt || csr.io.eret || wb_reg_flush_pipe

  // writeback arbitration
  val dmem_resp_xpu = !io.dmem.resp.bits.tag(0).asBool
  val dmem_resp_fpu =  io.dmem.resp.bits.tag(0).asBool
  val dmem_resp_waddr = io.dmem.resp.bits.tag(5, 1)
  val dmem_resp_valid = io.dmem.resp.valid && io.dmem.resp.bits.has_data
  val dmem_resp_replay = dmem_resp_valid && io.dmem.resp.bits.replay

  val ll_arb = Module(new Arbiter(new Bundle {
    val data = UInt(xLen.W)
    val tag = UInt(5.W)
  }, 3)) // div, rocc, vec
  ll_arb.io.in.foreach(_.valid := false.B)
  ll_arb.io.in.foreach(_.bits := DontCare)
  val ll_wdata = WireInit(ll_arb.io.out.bits.data)
  val ll_waddr = WireInit(ll_arb.io.out.bits.tag)
  val ll_wen = WireInit(ll_arb.io.out.fire)
  ll_arb.io.out.ready := !wb_wxd

  div.io.resp.ready := ll_arb.io.in(0).ready
  ll_arb.io.in(0).valid := div.io.resp.valid
  ll_arb.io.in(0).bits.data := div.io.resp.bits.data
  ll_arb.io.in(0).bits.tag := div.io.resp.bits.tag

  if (usingRoCC) {
    io.rocc.resp.ready := ll_arb.io.in(1).ready
    ll_arb.io.in(1).valid := io.rocc.resp.valid
    ll_arb.io.in(1).bits.data := io.rocc.resp.bits.data
    ll_arb.io.in(1).bits.tag := io.rocc.resp.bits.rd
  } else {
    // tie off RoCC
    io.rocc.resp.ready := false.B
    io.rocc.mem.req.ready := false.B
  }

  io.vector.map { v =>
    v.resp.ready := Mux(v.resp.bits.fp, !(dmem_resp_valid && dmem_resp_fpu), ll_arb.io.in(2).ready)
    ll_arb.io.in(2).valid := v.resp.valid && !v.resp.bits.fp
    ll_arb.io.in(2).bits.data := v.resp.bits.data
    ll_arb.io.in(2).bits.tag := v.resp.bits.rd
  }
  // Dont care mem since not all RoCC need accessing memory
  io.rocc.mem := DontCare

  when (dmem_resp_replay && dmem_resp_xpu) {
    ll_arb.io.out.ready := false.B
    ll_waddr := dmem_resp_waddr
    ll_wen := true.B
  }

  val wb_valid = wb_reg_valid && !replay_wb && !wb_xcpt
  val wb_wen = wb_valid && wb_ctrl.wxd
  val rf_wen = wb_wen || ll_wen
  val rf_waddr = Mux(ll_wen, ll_waddr, wb_waddr)
  val rf_wdata = Mux(dmem_resp_valid && dmem_resp_xpu, io.dmem.resp.bits.data(xLen-1, 0),
                 Mux(ll_wen, ll_wdata,
                 Mux(wb_ctrl.csr =/= CSR.N, csr.io.rw.rdata,
                 Mux(wb_ctrl.mul, mul.map(_.io.resp.bits.data).getOrElse(wb_reg_wdata),
                 wb_reg_wdata))))
  when (rf_wen) { rf.write(rf_waddr, rf_wdata) }

  // hook up control/status regfile
  csr.io.ungated_clock := clock
  csr.io.decode(0).inst := id_inst(0)
  csr.io.exception := wb_xcpt
  csr.io.cause := wb_cause
  csr.io.retire := wb_valid
  csr.io.inst(0) := (if (usingCompressed) Cat(Mux(wb_reg_raw_inst(1, 0).andR, wb_reg_inst >> 16, 0.U), wb_reg_raw_inst(15, 0)) else wb_reg_inst)
  csr.io.interrupts := io.interrupts
  csr.io.hartid := io.hartid
  io.fpu.fcsr_rm := csr.io.fcsr_rm
  val vector_fcsr_flags = io.vector.map(_.set_fflags.bits).getOrElse(0.U(5.W))
  val vector_fcsr_flags_valid = io.vector.map(_.set_fflags.valid).getOrElse(false.B)
  csr.io.fcsr_flags.valid := io.fpu.fcsr_flags.valid | vector_fcsr_flags_valid
  csr.io.fcsr_flags.bits := (io.fpu.fcsr_flags.bits & Fill(5, io.fpu.fcsr_flags.valid)) | (vector_fcsr_flags & Fill(5, vector_fcsr_flags_valid))
  io.fpu.time := csr.io.time(31,0)
  io.fpu.hartid := io.hartid
  csr.io.rocc_interrupt := io.rocc.interrupt
  csr.io.pc := wb_reg_pc

  val tval_dmem_addr = !wb_reg_xcpt
  val tval_any_addr = tval_dmem_addr ||
    wb_reg_cause.isOneOf(Causes.breakpoint.U, Causes.fetch_access.U, Causes.fetch_page_fault.U, Causes.fetch_guest_page_fault.U)
  val tval_inst = wb_reg_cause === Causes.illegal_instruction.U
  val tval_valid = wb_xcpt && (tval_any_addr || tval_inst)
  csr.io.gva := wb_xcpt && (tval_any_addr && csr.io.status.v || tval_dmem_addr && wb_reg_hls_or_dv)
  csr.io.tval := Mux(tval_valid, encodeVirtualAddress(wb_reg_wdata, wb_reg_wdata), 0.U)
  val (htval, mhtinst_read_pseudo) = {
    val htval_valid_imem = wb_reg_xcpt && wb_reg_cause === Causes.fetch_guest_page_fault.U
    val htval_imem = Mux(htval_valid_imem, io.imem.gpa.bits, 0.U)
    assert(!htval_valid_imem || io.imem.gpa.valid)

    val htval_valid_dmem = wb_xcpt && tval_dmem_addr && io.dmem.s2_xcpt.gf.asUInt.orR && !io.dmem.s2_xcpt.pf.asUInt.orR
    val htval_dmem = Mux(htval_valid_dmem, io.dmem.s2_gpa, 0.U)

    val htval = (htval_dmem | htval_imem) >> hypervisorExtraAddrBits
    // read pseudoinstruction if a guest-page fault is caused by an implicit memory access for VS-stage address translation
    val mhtinst_read_pseudo = (io.imem.gpa_is_pte && htval_valid_imem) || (io.dmem.s2_gpa_is_pte && htval_valid_dmem)
    (htval, mhtinst_read_pseudo)
  }

  csr.io.vector.foreach { v =>
    v.set_vconfig.valid := wb_reg_set_vconfig && wb_reg_valid
    v.set_vconfig.bits := wb_reg_rs2.asTypeOf(new VConfig)
    v.set_vs_dirty := wb_valid && wb_ctrl.vec
    v.set_vstart.valid := wb_valid && wb_reg_set_vconfig
    v.set_vstart.bits := 0.U
  }

  io.vector.foreach { v =>
    when (v.wb.retire || v.wb.xcpt || wb_ctrl.vec) {
      csr.io.pc := v.wb.pc
      csr.io.retire := v.wb.retire
      csr.io.inst(0) := v.wb.inst
      when (v.wb.xcpt && !wb_reg_xcpt) {
        wb_xcpt := true.B
        wb_cause := v.wb.cause
        csr.io.tval := v.wb.tval
      }
    }
    v.wb.store_pending := io.dmem.store_pending
    v.wb.vxrm := csr.io.vector.get.vxrm
    v.wb.frm := csr.io.fcsr_rm
    csr.io.vector.get.set_vxsat := v.set_vxsat
    when (v.set_vconfig.valid) {
      csr.io.vector.get.set_vconfig.valid := true.B
      csr.io.vector.get.set_vconfig.bits := v.set_vconfig.bits
    }
    when (v.set_vstart.valid) {
      csr.io.vector.get.set_vstart.valid := true.B
      csr.io.vector.get.set_vstart.bits := v.set_vstart.bits
    }
  }

  csr.io.htval := htval
  csr.io.mhtinst_read_pseudo := mhtinst_read_pseudo
  io.ptw.ptbr := csr.io.ptbr
  io.ptw.hgatp := csr.io.hgatp
  io.ptw.vsatp := csr.io.vsatp
  (io.ptw.customCSRs.csrs zip csr.io.customCSRs).map { case (lhs, rhs) => lhs <> rhs }
  io.ptw.status := csr.io.status
  io.ptw.hstatus := csr.io.hstatus
  io.ptw.gstatus := csr.io.gstatus
  io.ptw.pmp := csr.io.pmp
  csr.io.rw.addr := wb_reg_inst(31,20)
  csr.io.rw.cmd := CSR.maskCmd(wb_reg_valid, wb_ctrl.csr)
  csr.io.rw.wdata := wb_reg_wdata


  io.rocc.csrs <> csr.io.roccCSRs
  io.trace.time := csr.io.time
  io.trace.insns := csr.io.trace
  if (rocketParams.debugROB.isDefined) {
    val sz = rocketParams.debugROB.get.size
    if (sz < 1) { // use unsynthesizable ROB
      val csr_trace_with_wdata = WireInit(csr.io.trace(0))
      csr_trace_with_wdata.wdata.get := rf_wdata
      val should_wb = WireInit((wb_ctrl.wfd || (wb_ctrl.wxd && wb_waddr =/= 0.U)) && !csr.io.trace(0).exception)
      val has_wb = WireInit(wb_ctrl.wxd && wb_wen && !wb_set_sboard)
      val wb_addr = WireInit(wb_waddr + Mux(wb_ctrl.wfd, 32.U, 0.U))

      io.vector.foreach { v => when (v.wb.retire) {
        should_wb := v.wb.rob_should_wb
        has_wb := false.B
        wb_addr := Cat(v.wb.rob_should_wb_fp, csr_trace_with_wdata.insn(11,7))
      }}

      DebugROB.pushTrace(clock, reset,
        io.hartid, csr_trace_with_wdata,
        should_wb, has_wb, wb_addr)

      io.trace.insns(0) := DebugROB.popTrace(clock, reset, io.hartid)

      DebugROB.pushWb(clock, reset, io.hartid, ll_wen, rf_waddr, rf_wdata)
    } else { // synthesizable ROB (no FPRs)
      require(!usingVector, "Synthesizable ROB does not support vector implementations")
      val csr_trace_with_wdata = WireInit(csr.io.trace(0))
      csr_trace_with_wdata.wdata.get := rf_wdata

      val debug_rob = Module(new HardDebugROB(sz, 32))
      debug_rob.io.i_insn := csr_trace_with_wdata
      debug_rob.io.should_wb := (wb_ctrl.wfd || (wb_ctrl.wxd && wb_waddr =/= 0.U)) &&
                                !csr.io.trace(0).exception
      debug_rob.io.has_wb := wb_ctrl.wxd && wb_wen && !wb_set_sboard
      debug_rob.io.tag    := wb_waddr + Mux(wb_ctrl.wfd, 32.U, 0.U)

      debug_rob.io.wb_val  := ll_wen
      debug_rob.io.wb_tag  := rf_waddr
      debug_rob.io.wb_data := rf_wdata

      io.trace.insns(0) := debug_rob.io.o_insn
    }
  } else {
    io.trace.insns := csr.io.trace
  }
  for (((iobpw, wphit), bp) <- io.bpwatch zip wb_reg_wphit zip csr.io.bp) {
    iobpw.valid(0) := wphit
    iobpw.action := bp.control.action
    // tie off bpwatch valids
    iobpw.rvalid.foreach(_ := false.B)
    iobpw.wvalid.foreach(_ := false.B)
    iobpw.ivalid.foreach(_ := false.B)
  }

  val hazard_targets = Seq((id_ctrl.rxs1 && id_raddr1 =/= 0.U, id_raddr1),
                           (id_ctrl.rxs2 && id_raddr2 =/= 0.U, id_raddr2),
                           (id_ctrl.wxd  && id_waddr  =/= 0.U, id_waddr))
  val fp_hazard_targets = Seq((io.fpu.dec.ren1, id_raddr1),
                              (io.fpu.dec.ren2, id_raddr2),
                              (io.fpu.dec.ren3, id_raddr3),
                              (io.fpu.dec.wen, id_waddr))

  val sboard = new Scoreboard(32, true)
  sboard.clear(ll_wen, ll_waddr)
  def id_sboard_clear_bypass(r: UInt) = {
    // ll_waddr arrives late when D$ has ECC, so reshuffle the hazard check
    ll_wen && ll_waddr === r
  }
  val id_sboard_hazard = checkHazards(hazard_targets, rd => sboard.read(rd) && !id_sboard_clear_bypass(rd))
  sboard.set(wb_set_sboard && wb_wen, wb_waddr)

  // stall for RAW/WAW hazards on CSRs, loads, AMOs, and mul/div in execute stage.
  val ex_cannot_bypass = ex_ctrl.csr =/= CSR.N || ex_ctrl.jalr || ex_ctrl.mem || ex_ctrl.mul || ex_ctrl.div || ex_ctrl.fp || ex_ctrl.rocc
  val data_hazard_ex = ex_ctrl.wxd && checkHazards(hazard_targets, _ === ex_waddr)
  val fp_data_hazard_ex = id_ctrl.fp && ex_ctrl.wfd && checkHazards(fp_hazard_targets, _ === ex_waddr)
  val id_ex_hazard = ex_reg_valid && (data_hazard_ex && ex_cannot_bypass || fp_data_hazard_ex)

  // stall for RAW/WAW hazards on CSRs, LB/LH, and mul/div in memory stage.
  val mem_mem_cmd_bh =
    if (fastLoadWord) (!fastLoadByte).B && mem_reg_slow_bypass
    else true.B
  val mem_cannot_bypass = mem_ctrl.csr =/= CSR.N || mem_ctrl.mem && mem_mem_cmd_bh || mem_ctrl.mul || mem_ctrl.div || mem_ctrl.fp || mem_ctrl.rocc
  val data_hazard_mem = mem_ctrl.wxd && checkHazards(hazard_targets, _ === mem_waddr)
  val fp_data_hazard_mem = id_ctrl.fp && mem_ctrl.wfd && checkHazards(fp_hazard_targets, _ === mem_waddr)
  val id_mem_hazard = mem_reg_valid && (data_hazard_mem && mem_cannot_bypass || fp_data_hazard_mem)
  id_load_use := mem_reg_valid && data_hazard_mem && mem_ctrl.mem
  val id_vconfig_hazard = id_ctrl.vec && (
    (ex_reg_valid && ex_reg_set_vconfig) ||
    (mem_reg_valid && mem_reg_set_vconfig) ||
    (wb_reg_valid && wb_reg_set_vconfig))

  // stall for RAW/WAW hazards on load/AMO misses and mul/div in writeback.
  val data_hazard_wb = wb_ctrl.wxd && checkHazards(hazard_targets, _ === wb_waddr)
  val fp_data_hazard_wb = id_ctrl.fp && wb_ctrl.wfd && checkHazards(fp_hazard_targets, _ === wb_waddr)
  val id_wb_hazard = wb_reg_valid && (data_hazard_wb && wb_set_sboard || fp_data_hazard_wb)

  val id_stall_fpu = if (usingFPU) {
    val fp_sboard = new Scoreboard(32)
    fp_sboard.set(((wb_dcache_miss || wb_ctrl.vec) && wb_ctrl.wfd || io.fpu.sboard_set) && wb_valid, wb_waddr)
    val v_ll = io.vector.map(v => v.resp.fire && v.resp.bits.fp).getOrElse(false.B)
    fp_sboard.clear((dmem_resp_replay && dmem_resp_fpu) || v_ll, io.fpu.ll_resp_tag)
    fp_sboard.clear(io.fpu.sboard_clr, io.fpu.sboard_clra)

    checkHazards(fp_hazard_targets, fp_sboard.read _)
  } else false.B

  val dcache_blocked = {
    // speculate that a blocked D$ will unblock the cycle after a Grant
    val blocked = Reg(Bool())
    blocked := !io.dmem.req.ready && io.dmem.clock_enabled && !io.dmem.perf.grant && (blocked || io.dmem.req.valid || io.dmem.s2_nack)
    blocked && !io.dmem.perf.grant
  }
  val rocc_blocked = Reg(Bool())
  rocc_blocked := !wb_xcpt && !io.rocc.cmd.ready && (io.rocc.cmd.valid || rocc_blocked)

  val ctrl_stalld =
    id_ex_hazard || id_mem_hazard || id_wb_hazard || id_sboard_hazard ||
    id_vconfig_hazard ||
    csr.io.singleStep && (ex_reg_valid || mem_reg_valid || wb_reg_valid) ||
    id_csr_en && csr.io.decode(0).fp_csr && !io.fpu.fcsr_rdy ||
    id_csr_en && csr.io.decode(0).vector_csr && id_vec_busy ||
    id_ctrl.fp && id_stall_fpu ||
    id_ctrl.mem && dcache_blocked || // reduce activity during D$ misses
    id_ctrl.rocc && rocc_blocked || // reduce activity while RoCC is busy
    id_ctrl.div && (!(div.io.req.ready || (div.io.resp.valid && !wb_wxd)) || div.io.req.valid) || // reduce odds of replay
    !clock_en ||
    id_do_fence ||
    csr.io.csr_stall ||
    id_reg_pause ||
    io.traceStall
  ctrl_killd := !ibuf.io.inst(0).valid || ibuf.io.inst(0).bits.replay || take_pc_mem_wb || ctrl_stalld || csr.io.interrupt

  io.imem.req.valid := take_pc
  io.imem.req.bits.speculative := !take_pc_wb
  io.imem.req.bits.pc :=
    Mux(wb_xcpt || csr.io.eret, csr.io.evec, // exception or [m|s]ret
    Mux(replay_wb,              wb_reg_pc,   // replay
                                mem_npc))    // flush or branch misprediction
  io.imem.flush_icache := wb_reg_valid && wb_ctrl.fence_i && !io.dmem.s2_nack
  io.imem.might_request := {
    imem_might_request_reg := ex_pc_valid || mem_pc_valid || io.ptw.customCSRs.disableICacheClockGate || io.vector.map(_.trap_check_busy).getOrElse(false.B)
    imem_might_request_reg
  }
  io.imem.progress := RegNext(wb_reg_valid && !replay_wb_common)
  io.imem.sfence.valid := wb_reg_valid && wb_reg_sfence
  io.imem.sfence.bits.rs1 := wb_reg_mem_size(0)
  io.imem.sfence.bits.rs2 := wb_reg_mem_size(1)
  io.imem.sfence.bits.addr := wb_reg_wdata
  io.imem.sfence.bits.asid := wb_reg_rs2
  io.imem.sfence.bits.hv := wb_reg_hfence_v
  io.imem.sfence.bits.hg := wb_reg_hfence_g
  io.ptw.sfence := io.imem.sfence

  ibuf.io.inst(0).ready := !ctrl_stalld

  io.imem.btb_update.valid := mem_reg_valid && !take_pc_wb && mem_wrong_npc && (!mem_cfi || mem_cfi_taken)
  io.imem.btb_update.bits.isValid := mem_cfi
  io.imem.btb_update.bits.cfiType :=
    Mux((mem_ctrl.jal || mem_ctrl.jalr) && mem_waddr(0), CFIType.call,
    Mux(mem_ctrl.jalr && (mem_reg_inst(19,15) & regAddrMask.U) === BitPat("b00?01"), CFIType.ret,
    Mux(mem_ctrl.jal || mem_ctrl.jalr, CFIType.jump,
    CFIType.branch)))
  io.imem.btb_update.bits.target := io.imem.req.bits.pc
  io.imem.btb_update.bits.br_pc := (if (usingCompressed) mem_reg_pc + Mux(mem_reg_rvc, 0.U, 2.U) else mem_reg_pc)
  io.imem.btb_update.bits.pc := ~(~io.imem.btb_update.bits.br_pc | (coreInstBytes*fetchWidth-1).U)
  io.imem.btb_update.bits.prediction := mem_reg_btb_resp
  io.imem.btb_update.bits.taken := DontCare

  io.imem.bht_update.valid := mem_reg_valid && !take_pc_wb
  io.imem.bht_update.bits.pc := io.imem.btb_update.bits.pc
  io.imem.bht_update.bits.taken := mem_br_taken
  io.imem.bht_update.bits.mispredict := mem_wrong_npc
  io.imem.bht_update.bits.branch := mem_ctrl.branch
  io.imem.bht_update.bits.prediction := mem_reg_btb_resp.bht

  // Connect RAS in Frontend
  io.imem.ras_update := DontCare

  io.fpu.valid := !ctrl_killd && id_ctrl.fp
  io.fpu.killx := ctrl_killx
  io.fpu.killm := killm_common
  io.fpu.inst := id_inst(0)
  io.fpu.fromint_data := ex_rs(0)
  io.fpu.ll_resp_val := dmem_resp_valid && dmem_resp_fpu
  io.fpu.ll_resp_data := (if (minFLen == 32) io.dmem.resp.bits.data_word_bypass else io.dmem.resp.bits.data)
  io.fpu.ll_resp_type := io.dmem.resp.bits.size
  io.fpu.ll_resp_tag := dmem_resp_waddr
  io.fpu.keep_clock_enabled := io.ptw.customCSRs.disableCoreClockGate

  io.fpu.v_sew := csr.io.vector.map(_.vconfig.vtype.vsew).getOrElse(0.U)

  io.vector.map { v =>
    when (!(dmem_resp_valid && dmem_resp_fpu)) {
      io.fpu.ll_resp_val := v.resp.valid && v.resp.bits.fp
      io.fpu.ll_resp_data := v.resp.bits.data
      io.fpu.ll_resp_type := v.resp.bits.size
      io.fpu.ll_resp_tag := v.resp.bits.rd
    }
  }

  io.vector.foreach { v =>
    v.ex.valid := ex_reg_valid && (ex_ctrl.vec || rocketParams.vector.get.issueVConfig.B && ex_reg_set_vconfig) && !ctrl_killx
    v.ex.inst := ex_reg_inst
    v.ex.vconfig := csr.io.vector.get.vconfig
    v.ex.vstart := Mux(mem_reg_valid && mem_ctrl.vec || wb_reg_valid && wb_ctrl.vec, 0.U, csr.io.vector.get.vstart)
    v.ex.rs1 := ex_rs(0)
    v.ex.rs2 := ex_rs(1)
    v.ex.pc := ex_reg_pc
    v.mem.frs1 := io.fpu.store_data
    v.killm := killm_common
    v.status := csr.io.status
  }


  io.dmem.req.valid     := ex_reg_valid && ex_ctrl.mem
  val ex_dcache_tag = Cat(ex_waddr, ex_ctrl.fp)
  require(coreParams.dcacheReqTagBits >= ex_dcache_tag.getWidth)
  io.dmem.req.bits.tag  := ex_dcache_tag
  io.dmem.req.bits.cmd  := ex_ctrl.mem_cmd
  io.dmem.req.bits.size := ex_reg_mem_size
  io.dmem.req.bits.signed := !Mux(ex_reg_hls, ex_reg_inst(20), ex_reg_inst(14))
  io.dmem.req.bits.phys := false.B
  io.dmem.req.bits.addr := encodeVirtualAddress(ex_rs(0), alu.io.adder_out)
  io.dmem.req.bits.idx.foreach(_ := io.dmem.req.bits.addr)
  io.dmem.req.bits.dprv := Mux(ex_reg_hls, csr.io.hstatus.spvp, csr.io.status.dprv)
  io.dmem.req.bits.dv := ex_reg_hls || csr.io.status.dv
  io.dmem.req.bits.no_resp := !isRead(ex_ctrl.mem_cmd) || (!ex_ctrl.fp && ex_waddr === 0.U)
  io.dmem.req.bits.no_alloc := DontCare
  io.dmem.req.bits.no_xcpt := DontCare
  io.dmem.req.bits.data := DontCare
  io.dmem.req.bits.mask := DontCare

  io.dmem.s1_data.data := (if (fLen == 0) mem_reg_rs2 else Mux(mem_ctrl.fp, Fill(coreDataBits / fLen, io.fpu.store_data), mem_reg_rs2))
  io.dmem.s1_data.mask := DontCare

  io.dmem.s1_kill := killm_common || mem_ldst_xcpt || fpu_kill_mem || vec_kill_mem
  io.dmem.s2_kill := false.B
  // don't let D$ go to sleep if we're probably going to use it soon
  io.dmem.keep_clock_enabled := ibuf.io.inst(0).valid && id_ctrl.mem && !csr.io.csr_stall

  io.rocc.cmd.valid := wb_reg_valid && wb_ctrl.rocc && !replay_wb_common
  io.rocc.exception := wb_xcpt && csr.io.status.xs.orR
  io.rocc.cmd.bits.status := csr.io.status
  io.rocc.cmd.bits.inst := wb_reg_inst.asTypeOf(new RoCCInstruction())
  io.rocc.cmd.bits.rs1 := wb_reg_wdata
  io.rocc.cmd.bits.rs2 := wb_reg_rs2

  // gate the clock
  val unpause = csr.io.time(rocketParams.lgPauseCycles-1, 0) === 0.U || csr.io.inhibit_cycle || io.dmem.perf.release || take_pc
  when (unpause) { id_reg_pause := false.B }
  io.cease := csr.io.status.cease && !clock_en_reg
  io.wfi := csr.io.status.wfi
  if (rocketParams.clockGate) {
    long_latency_stall := csr.io.csr_stall || io.dmem.perf.blocked || id_reg_pause && !unpause
    clock_en := clock_en_reg || ex_pc_valid || (!long_latency_stall && io.imem.resp.valid)
    clock_en_reg :=
      ex_pc_valid || mem_pc_valid || wb_pc_valid || // instruction in flight
      io.ptw.customCSRs.disableCoreClockGate || // chicken bit
      !div.io.req.ready || // mul/div in flight
      usingFPU.B && !io.fpu.fcsr_rdy || // long-latency FPU in flight
      io.dmem.replay_next || // long-latency load replaying
      (!long_latency_stall && (ibuf.io.inst(0).valid || io.imem.resp.valid)) // instruction pending

    assert(!(ex_pc_valid || mem_pc_valid || wb_pc_valid) || clock_en)
  }

  // evaluate performance counters
  val icache_blocked = !(io.imem.resp.valid || RegNext(io.imem.resp.valid))
  csr.io.counters foreach { c => c.inc := RegNext(perfEvents.evaluate(c.eventSel)) }

  val coreMonitorBundle = Wire(new CoreMonitorBundle(xLen, fLen))

  coreMonitorBundle.clock := clock
  coreMonitorBundle.reset := reset
  coreMonitorBundle.hartid := io.hartid
  coreMonitorBundle.timer := csr.io.time(31,0)
  coreMonitorBundle.valid := csr.io.trace(0).valid && !csr.io.trace(0).exception
  coreMonitorBundle.pc := csr.io.trace(0).iaddr(vaddrBitsExtended-1, 0).sextTo(xLen)
  coreMonitorBundle.wrenx := wb_wen && !wb_set_sboard
  coreMonitorBundle.wrenf := false.B
  coreMonitorBundle.wrdst := wb_waddr
  coreMonitorBundle.wrdata := rf_wdata
  coreMonitorBundle.rd0src := wb_reg_inst(19,15)
  coreMonitorBundle.rd0val := RegNext(RegNext(ex_rs(0)))
  coreMonitorBundle.rd1src := wb_reg_inst(24,20)
  coreMonitorBundle.rd1val := RegNext(RegNext(ex_rs(1)))
  coreMonitorBundle.inst := csr.io.trace(0).insn
  coreMonitorBundle.excpt := csr.io.trace(0).exception
  coreMonitorBundle.priv_mode := csr.io.trace(0).priv

  if (enableCommitLog) {
    val t = csr.io.trace(0)
    val rd = wb_waddr
    val wfd = wb_ctrl.wfd
    val wxd = wb_ctrl.wxd
    val has_data = wb_wen && !wb_set_sboard

    when (t.valid && !t.exception) {
      when (wfd) {
        printf ("%d 0x%x (0x%x) f%d p%d 0xXXXXXXXXXXXXXXXX\n", t.priv, t.iaddr, t.insn, rd, rd+32.U)
      }
      .elsewhen (wxd && rd =/= 0.U && has_data) {
        printf ("%d 0x%x (0x%x) x%d 0x%x\n", t.priv, t.iaddr, t.insn, rd, rf_wdata)
      }
      .elsewhen (wxd && rd =/= 0.U && !has_data) {
        printf ("%d 0x%x (0x%x) x%d p%d 0xXXXXXXXXXXXXXXXX\n", t.priv, t.iaddr, t.insn, rd, rd)
      }
      .otherwise {
        printf ("%d 0x%x (0x%x)\n", t.priv, t.iaddr, t.insn)
      }
    }

    when (ll_wen && rf_waddr =/= 0.U) {
      printf ("x%d p%d 0x%x\n", rf_waddr, rf_waddr, rf_wdata)
    }
  }
  else {
    when (csr.io.trace(0).valid) {
      printf("C%d: %d [%d] pc=[%x] W[r%d=%x][%d] R[r%d=%x] R[r%d=%x] inst=[%x] DASM(%x)\n",
         io.hartid, coreMonitorBundle.timer, coreMonitorBundle.valid,
         coreMonitorBundle.pc,
         Mux(wb_ctrl.wxd || wb_ctrl.wfd, coreMonitorBundle.wrdst, 0.U),
         Mux(coreMonitorBundle.wrenx, coreMonitorBundle.wrdata, 0.U),
         coreMonitorBundle.wrenx,
         Mux(wb_ctrl.rxs1 || wb_ctrl.rfs1, coreMonitorBundle.rd0src, 0.U),
         Mux(wb_ctrl.rxs1 || wb_ctrl.rfs1, coreMonitorBundle.rd0val, 0.U),
         Mux(wb_ctrl.rxs2 || wb_ctrl.rfs2, coreMonitorBundle.rd1src, 0.U),
         Mux(wb_ctrl.rxs2 || wb_ctrl.rfs2, coreMonitorBundle.rd1val, 0.U),
         coreMonitorBundle.inst, coreMonitorBundle.inst)
    }
  }

  // CoreMonitorBundle for late latency writes
  val xrfWriteBundle = Wire(new CoreMonitorBundle(xLen, fLen))

  xrfWriteBundle.clock := clock
  xrfWriteBundle.reset := reset
  xrfWriteBundle.hartid := io.hartid
  xrfWriteBundle.timer := csr.io.time(31,0)
  xrfWriteBundle.valid := false.B
  xrfWriteBundle.pc := 0.U
  xrfWriteBundle.wrdst := rf_waddr
  xrfWriteBundle.wrenx := rf_wen && !(csr.io.trace(0).valid && wb_wen && (wb_waddr === rf_waddr))
  xrfWriteBundle.wrenf := false.B
  xrfWriteBundle.wrdata := rf_wdata
  xrfWriteBundle.rd0src := 0.U
  xrfWriteBundle.rd0val := 0.U
  xrfWriteBundle.rd1src := 0.U
  xrfWriteBundle.rd1val := 0.U
  xrfWriteBundle.inst := 0.U
  xrfWriteBundle.excpt := false.B
  xrfWriteBundle.priv_mode := csr.io.trace(0).priv

  if (rocketParams.haveSimTimeout) PlusArg.timeout(
    name = "max_core_cycles",
    docstring = "Kill the emulation after INT rdtime cycles. Off if 0."
  )(csr.io.time)

  } // leaving gated-clock domain
  val rocketImpl = withClock (gated_clock) { new RocketImpl }

  def checkExceptions(x: Seq[(Bool, UInt)]) =
    (WireInit(x.map(_._1).reduce(_||_)), WireInit(PriorityMux(x)))

  def coverExceptions(exceptionValid: Bool, cause: UInt, labelPrefix: String, coverCausesLabels: Seq[(Int, String)]): Unit = {
    for ((coverCause, label) <- coverCausesLabels) {
      property.cover(exceptionValid && (cause === coverCause.U), s"${labelPrefix}_${label}")
    }
  }

  def checkHazards(targets: Seq[(Bool, UInt)], cond: UInt => Bool) =
    targets.map(h => h._1 && cond(h._2)).reduce(_||_)

  def encodeVirtualAddress(a0: UInt, ea: UInt) = if (vaddrBitsExtended == vaddrBits) ea else {
    // efficient means to compress 64-bit VA into vaddrBits+1 bits
    // (VA is bad if VA(vaddrBits) != VA(vaddrBits-1))
    val b = vaddrBitsExtended-1
    val a = (a0 >> b).asSInt
    val msb = Mux(a === 0.S || a === -1.S, ea(b), !ea(b-1))
    Cat(msb, ea(b-1, 0))
  }

  class Scoreboard(n: Int, zero: Boolean = false)
  {
    def set(en: Bool, addr: UInt): Unit = update(en, _next | mask(en, addr))
    def clear(en: Bool, addr: UInt): Unit = update(en, _next & ~mask(en, addr))
    def read(addr: UInt): Bool = r(addr)
    def readBypassed(addr: UInt): Bool = _next(addr)

    private val _r = RegInit(0.U(n.W))
    private val r = if (zero) (_r >> 1 << 1) else _r
    private var _next = r
    private var ens = false.B
    private def mask(en: Bool, addr: UInt) = Mux(en, 1.U << addr, 0.U)
    private def update(en: Bool, update: UInt) = {
      _next = update
      ens = ens || en
      when (ens) { _r := _next }
    }
  }
}

class RegFile(n: Int, w: Int, zero: Boolean = false) {
  val rf = Mem(n, UInt(w.W))
  private def access(addr: UInt) = rf(~addr(log2Up(n)-1,0))
  private val reads = ArrayBuffer[(UInt,UInt)]()
  private var canRead = true
  def read(addr: UInt) = {
    require(canRead)
    reads += addr -> Wire(UInt())
    reads.last._2 := Mux(zero.B && addr === 0.U, 0.U, access(addr))
    reads.last._2
  }
  def write(addr: UInt, data: UInt) = {
    canRead = false
    when (addr =/= 0.U) {
      access(addr) := data
      for ((raddr, rdata) <- reads)
        when (addr === raddr) { rdata := data }
    }
  }
}

object ImmGen {
  def apply(sel: UInt, inst: UInt) = {
    val sign = Mux(sel === IMM_Z, 0.S, inst(31).asSInt)
    val b30_20 = Mux(sel === IMM_U, inst(30,20).asSInt, sign)
    val b19_12 = Mux(sel =/= IMM_U && sel =/= IMM_UJ, sign, inst(19,12).asSInt)
    val b11 = Mux(sel === IMM_U || sel === IMM_Z, 0.S,
              Mux(sel === IMM_UJ, inst(20).asSInt,
              Mux(sel === IMM_SB, inst(7).asSInt, sign)))
    val b10_5 = Mux(sel === IMM_U || sel === IMM_Z, 0.U, inst(30,25))
    val b4_1 = Mux(sel === IMM_U, 0.U,
               Mux(sel === IMM_S || sel === IMM_SB, inst(11,8),
               Mux(sel === IMM_Z, inst(19,16), inst(24,21))))
    val b0 = Mux(sel === IMM_S, inst(7),
             Mux(sel === IMM_I, inst(20),
             Mux(sel === IMM_Z, inst(15), 0.U)))

    Cat(sign, b30_20, b19_12, b11, b10_5, b4_1, b0).asSInt
  }
}
