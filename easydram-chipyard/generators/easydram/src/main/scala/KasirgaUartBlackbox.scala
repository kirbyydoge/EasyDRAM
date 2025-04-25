package easydram.kasirgauartblackbox

import chisel3._
import chisel3.util._
import chisel3.experimental._

class UARTTransmitterIO extends Bundle {
    val clk_i                   = Input(Clock())
    val rstn_i                  = Input(Bool())

    val tx_en_i                 = Input(Bool())
    val veri_gecerli_i          = Input(Bool())
    val consume_o               = Output(Bool())

    val gelen_veri_i            = Input(UInt(8.W))
    val baud_div_i              = Input(UInt(16.W))

    val tx_o                    = Output(Bool())
    val hazir_o                 = Output(Bool())
}

class UartTransmitter extends BlackBox with HasBlackBoxResource {
    val io = IO(new UARTTransmitterIO)

    addResource("/vsrc/UartTransmitter.v")
}