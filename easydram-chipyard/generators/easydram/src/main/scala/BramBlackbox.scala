package easydram.bramblackbox

import chisel3._
import chisel3.util._
import chisel3.experimental._
import freechips.rocketchip.util.{ElaborationArtefacts}

class BramDualPortIO(dataWidth: Int, addrWidth: Int) extends Bundle {
    val maskWidth     = (dataWidth/8).toInt

    val clk_i         = Input(Clock())

    val p0_data_i     = Input(UInt(dataWidth.W))           
    val p0_addr_i     = Input(UInt(addrWidth.W))     
    val p0_mask_i     = Input(UInt(maskWidth.W))         
    val p0_wr_en_i    = Input(Bool())                                    
    val p0_cmd_en_i   = Input(Bool())                                       
    val p0_data_o     = Output(UInt(dataWidth.W))      

    val p1_data_i     = Input(UInt(dataWidth.W))               
    val p1_addr_i     = Input(UInt(addrWidth.W))        
    val p1_mask_i     = Input(UInt(maskWidth.W))                
    val p1_wr_en_i    = Input(Bool())                                    
    val p1_cmd_en_i   = Input(Bool())                                       
    val p1_data_o     = Output(UInt(dataWidth.W))
}

class BramDualPort(dataWidth: Int, depth: Int, initFile: String = "")
    extends BlackBox(Map(
        "DATA_WIDTH" -> IntParam(dataWidth),
        "BRAM_DEPTH" -> IntParam(depth),
        "INIT_FILE" -> initFile,
        "HARDCORE_DEBUG" -> "TRUE"
    )) with HasBlackBoxResource {
    val addrWidth = scala.math.log10(depth)/scala.math.log10(2.0)

    val io = IO(new BramDualPortIO(dataWidth, addrWidth.toInt))

    addResource("/vsrc/BramDualPort.v")
}