package easydram.obufdsblackbox

import chisel3._
import chisel3.util._
import chisel3.experimental._

class OBUFDS extends BlackBox(Map("IOSTANDARD" -> "DEFAULT")) {
    val io = IO(new Bundle {
        val I = Input(Bool())
        val O = Output(Bool())
        val OB = Output(Bool())
    })
}