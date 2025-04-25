package easydram.drambender

import chisel3._
import chisel3.util._
import chisel3.experimental.{Analog, BaseModule}
import freechips.rocketchip.util.{ElaborationArtefacts}
import easydram.bramblackbox._
import easydram.obufdsblackbox._

case class DRAMBenderParams(
    addrWidth: Int = 17,
    bankWidth: Int = 2,
    bankGroupWidth: Int = 1,
    ckeWidth: Int = 1,
    odtWidth: Int = 1,
    csWidth: Int = 1,
    ckWidth: Int = 1,
    dqsWidth: Int = 8,
    dqWidth: Int = 64,
    dmWidth: Int = 8,
    instWidth: Int = 256,
    rdDataWidth: Int = 256,
    instBits: Int = 68,
    instDepth: Int = 351,
    isSynthesis: Boolean = false
)

class DRAMBenderProgramIO(p: DRAMBenderParams) extends Bundle {
    val batch_clk_i                 = Input(Clock())
    val batch_rstn_i                = Input(Bool())

    val inst_data_i                 = Input(UInt(p.instWidth.W))
    val inst_valid_i                = Input(Bool())  
    val inst_last_i                 = Input(Bool()) 
    val inst_ready_o                = Output(Bool())  

    val db_init_calib_complete_o    = Output(Bool())
    val db_data_o                   = Output(UInt(p.rdDataWidth.W))
    val db_valid_o                  = Output(Bool())
    val db_last_o                   = Output(Bool())
    val db_ready_i                  = Input(Bool())
}

class DRAMBenderDDR4IO(p: DRAMBenderParams) extends Bundle {
    val c0_sys_clk_p                = Input(Clock())
    val c0_sys_clk_n                = Input(Clock())
    val sys_rst                     = Input(Bool())

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

    val c0_ddr4_dqs_c               = Analog(p.dqsWidth.W)
    val c0_ddr4_dqs_t               = Analog(p.dqsWidth.W)
    val c0_ddr4_dq                  = Analog(p.dqWidth.W)
    val c0_ddr4_dm_dbi_n            = Analog(p.dmWidth.W)
    val c0_ddr4_parity              = Output(Bool())
}

class DRAMBenderBlackBoxIO(p: DRAMBenderParams) extends Bundle {
    val batch_clk_i                 = Input(Clock())
    val batch_rstn_i                = Input(Bool())

    val inst_data_i                 = Input(UInt(p.instWidth.W))
    val inst_valid_i                = Input(Bool())  
    val inst_last_i                 = Input(Bool()) 
    val inst_ready_o                = Output(Bool())  

    val db_init_calib_complete_o    = Output(Bool())
    val db_data_o                   = Output(UInt(p.rdDataWidth.W))
    val db_valid_o                  = Output(Bool())
    val db_last_o                   = Output(Bool())
    val db_ready_i                  = Input(Bool())

    val bender_done_o               = Output(Bool())

    val c0_sys_clk_p                = Input(Clock())
    val c0_sys_clk_n                = Input(Clock())
    val sys_rst                     = Input(Bool())

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

    val c0_ddr4_dqs_c               = Analog(p.dqsWidth.W)
    val c0_ddr4_dqs_t               = Analog(p.dqsWidth.W)
    val c0_ddr4_dq                  = Analog(p.dqWidth.W)
    val c0_ddr4_dm_dbi_n            = Analog(p.dmWidth.W)
    val c0_ddr4_parity              = Output(Bool())

    val bypass_addr_i               = Input(UInt(64.W))
    val bypass_wr_data_i            = Input(UInt(512.W))
    val bypass_wr_en_i              = Input(Bool())
    val bypass_rd_data_o            = Output(UInt(512.W))
    val bypass_rd_valid_o           = Output(Bool())
}

trait HasDRAMBenderBlackBoxIO {
    implicit val p: DRAMBenderParams
    val io = IO(new DRAMBenderBlackBoxIO(p))
}

class DRAMBender()(implicit val p: DRAMBenderParams) extends BlackBox with HasBlackBoxResource 
    with HasDRAMBenderBlackBoxIO
    with WithVCU108PhyDDR4IP
    with WithPhyDDR4IPPatches
    with WithVCU108ScratchpadIP
    with WithVCU108PrReadMemIP
    with WithVCU108ZqCalibMemIP
    with WithVCU108InstrBlkMemIP
    with WithVCU108AxisClkConvIP
    with WithVCU108RdBackFIFOIP 
    with WithVCU108PrRefMemIP 
    with WithVCU108ILAInstrRbIP
    with WithVCU108ILAPipelineIP {

    addResource("/vsrc/project.vh")
    addResource("/vsrc/parameters.vh")
    addResource("/vsrc/softmc_pipeline.v")
    addResource("/vsrc/register_file.v")
    addResource("/vsrc/reg_mem.v")
    addResource("/vsrc/readback_engine.v")
    addResource("/vsrc/pre_decode.v")
    addResource("/vsrc/pop_count4.v")
    addResource("/vsrc/maintenance_controller.v")
    addResource("/vsrc/softmc_frontend.v")
    addResource("/vsrc/fetch_stage.v")
    addResource("/vsrc/execute_stage.v")
    addResource("/vsrc/exe_pipeline.v")
    addResource("/vsrc/encoding.vh")
    addResource("/vsrc/dll_toggler.v")
    addResource("/vsrc/diff_shift_reg.v")
    addResource("/vsrc/decode_stage.v")
    addResource("/vsrc/ddr4_mc_odt.v")
    addResource("/vsrc/ddr4_adapter.v")
    addResource("/vsrc/ddr_pipeline.v")
    addResource("/vsrc/DRAMBender.v")
}

trait WithVCU108PhyDDR4IP {
    ElaborationArtefacts.add(
        "vcu108phyddr4.vivado.tcl",
    """
    create_ip -vendor xilinx.com -library ip -name ddr4 -version 2.2 -module_name phy_ddr4_comp -dir $ipdir -force
    set_property -dict [list \
        CONFIG.ADDN_UI_CLKOUT1.INSERT_VIP                  {0} \
        CONFIG.ADDN_UI_CLKOUT1_FREQ_HZ                     {None} \
        CONFIG.ADDN_UI_CLKOUT2.INSERT_VIP                  {0} \
        CONFIG.ADDN_UI_CLKOUT2_FREQ_HZ                     {None} \
        CONFIG.ADDN_UI_CLKOUT3.INSERT_VIP                  {0} \
        CONFIG.ADDN_UI_CLKOUT3_FREQ_HZ                     {None} \
        CONFIG.ADDN_UI_CLKOUT4.INSERT_VIP                  {0} \
        CONFIG.ADDN_UI_CLKOUT4_FREQ_HZ                     {None} \
        CONFIG.AL_SEL                                      {0} \
        CONFIG.C0.ADDR_WIDTH                               {17} \
        CONFIG.C0.BANK_GROUP_WIDTH                         {1} \
        CONFIG.C0.CKE_WIDTH                                {1} \
        CONFIG.C0.CK_WIDTH                                 {1} \
        CONFIG.C0.CS_WIDTH                                 {1} \
        CONFIG.C0.ControllerType                           {DDR4_SDRAM} \
        CONFIG.C0.DDR4_ACT_SKEW                            {0} \
        CONFIG.C0.DDR4_ADDR_SKEW_0                         {0} \
        CONFIG.C0.DDR4_ADDR_SKEW_1                         {0} \
        CONFIG.C0.DDR4_ADDR_SKEW_2                         {0} \
        CONFIG.C0.DDR4_ADDR_SKEW_3                         {0} \
        CONFIG.C0.DDR4_ADDR_SKEW_4                         {0} \
        CONFIG.C0.DDR4_ADDR_SKEW_5                         {0} \
        CONFIG.C0.DDR4_ADDR_SKEW_6                         {0} \
        CONFIG.C0.DDR4_ADDR_SKEW_7                         {0} \
        CONFIG.C0.DDR4_ADDR_SKEW_8                         {0} \
        CONFIG.C0.DDR4_ADDR_SKEW_9                         {0} \
        CONFIG.C0.DDR4_ADDR_SKEW_10                        {0} \
        CONFIG.C0.DDR4_ADDR_SKEW_11                        {0} \
        CONFIG.C0.DDR4_ADDR_SKEW_12                        {0} \
        CONFIG.C0.DDR4_ADDR_SKEW_13                        {0} \
        CONFIG.C0.DDR4_ADDR_SKEW_14                        {0} \
        CONFIG.C0.DDR4_ADDR_SKEW_15                        {0} \
        CONFIG.C0.DDR4_ADDR_SKEW_16                        {0} \
        CONFIG.C0.DDR4_ADDR_SKEW_17                        {0} \
        CONFIG.C0.DDR4_AUTO_AP_COL_A3                      {false} \
        CONFIG.C0.DDR4_AutoPrecharge                       {false} \
        CONFIG.C0.DDR4_AxiAddressWidth                     {31} \
        CONFIG.C0.DDR4_AxiArbitrationScheme                {RD_PRI_REG} \
        CONFIG.C0.DDR4_AxiDataWidth                        {512} \
        CONFIG.C0.DDR4_AxiIDWidth                          {4} \
        CONFIG.C0.DDR4_AxiNarrowBurst                      {false} \
        CONFIG.C0.DDR4_AxiSelection                        {false} \
        CONFIG.C0.DDR4_BA_SKEW_0                           {0} \
        CONFIG.C0.DDR4_BA_SKEW_1                           {0} \
        CONFIG.C0.DDR4_BG_SKEW_0                           {0} \
        CONFIG.C0.DDR4_BG_SKEW_1                           {0} \
        CONFIG.C0.DDR4_BurstLength                         {8} \
        CONFIG.C0.DDR4_BurstType                           {Sequential} \
        CONFIG.C0.DDR4_CKE_SKEW_0                          {0} \
        CONFIG.C0.DDR4_CKE_SKEW_1                          {0} \
        CONFIG.C0.DDR4_CKE_SKEW_2                          {0} \
        CONFIG.C0.DDR4_CKE_SKEW_3                          {0} \
        CONFIG.C0.DDR4_CK_SKEW_0                           {0} \
        CONFIG.C0.DDR4_CK_SKEW_1                           {0} \
        CONFIG.C0.DDR4_CK_SKEW_2                           {0} \
        CONFIG.C0.DDR4_CK_SKEW_3                           {0} \
        CONFIG.C0.DDR4_CLKFBOUT_MULT                       {10} \
        CONFIG.C0.DDR4_CLKOUT0_DIVIDE                      {6} \
        CONFIG.C0.DDR4_CS_SKEW_0                           {0} \
        CONFIG.C0.DDR4_CS_SKEW_1                           {0} \
        CONFIG.C0.DDR4_CS_SKEW_2                           {0} \
        CONFIG.C0.DDR4_CS_SKEW_3                           {0} \
        CONFIG.C0.DDR4_Capacity                            {512} \
        CONFIG.C0.DDR4_CasLatency                          {9} \
        CONFIG.C0.DDR4_CasWriteLatency                     {9} \
        CONFIG.C0.DDR4_ChipSelect                          {true} \
        CONFIG.C0.DDR4_Clamshell                           {false} \
        CONFIG.C0.DDR4_CustomParts                         {no_file_loaded} \
        CONFIG.C0.DDR4_DIVCLK_DIVIDE                       {3} \
        CONFIG.C0.DDR4_DataMask                            {DM_NO_DBI} \
        CONFIG.C0.DDR4_DataWidth                           {64} \
        CONFIG.C0.DDR4_EN_PARITY                           {false} \
        CONFIG.C0.DDR4_Ecc                                 {false} \
        CONFIG.C0.DDR4_Enable_LVAUX                        {false} \
        CONFIG.C0.DDR4_InputClockPeriod                    {3333} \
        CONFIG.C0.DDR4_LR_SKEW_0                           {0} \
        CONFIG.C0.DDR4_LR_SKEW_1                           {0} \
        CONFIG.C0.DDR4_MCS_ECC                             {false} \
        CONFIG.C0.DDR4_Mem_Add_Map                         {ROW_COLUMN_BANK} \
        CONFIG.C0.DDR4_MemoryName                          {MainMemory} \
        CONFIG.C0.DDR4_MemoryPart                          {EDY4016AABG-DR-F} \
        CONFIG.C0.DDR4_MemoryType                          {Components} \
        CONFIG.C0.DDR4_MemoryVoltage                       {1.2V} \
        CONFIG.C0.DDR4_ODT_SKEW_0                          {0} \
        CONFIG.C0.DDR4_ODT_SKEW_1                          {0} \
        CONFIG.C0.DDR4_ODT_SKEW_2                          {0} \
        CONFIG.C0.DDR4_ODT_SKEW_3                          {0} \
        CONFIG.C0.DDR4_OnDieTermination                    {RZQ/6} \
        CONFIG.C0.DDR4_Ordering                            {Normal} \
        CONFIG.C0.DDR4_OutputDriverImpedenceControl        {RZQ/7} \
        CONFIG.C0.DDR4_PAR_SKEW                            {0} \
        CONFIG.C0.DDR4_PhyClockRatio                       {4:1} \
        CONFIG.C0.DDR4_RESTORE_CRC                         {false} \
        CONFIG.C0.DDR4_SAVE_RESTORE                        {false} \
        CONFIG.C0.DDR4_SELF_REFRESH                        {false} \
        CONFIG.C0.DDR4_Slot                                {Single} \
        CONFIG.C0.DDR4_Specify_MandD                       {false} \
        CONFIG.C0.DDR4_TREFI                               {0} \
        CONFIG.C0.DDR4_TRFC                                {0} \
        CONFIG.C0.DDR4_TRFC_DLR                            {0} \
        CONFIG.C0.DDR4_TXPR                                {0} \
        CONFIG.C0.DDR4_TimePeriod                          {1500} \
        CONFIG.C0.DDR4_UserRefresh_ZQCS                    {false} \
        CONFIG.C0.DDR4_isCKEShared                         {false} \
        CONFIG.C0.DDR4_isCustom                            {false} \
        CONFIG.C0.DDR4_nCK_TREFI                           {0} \
        CONFIG.C0.DDR4_nCK_TRFC                            {0} \
        CONFIG.C0.DDR4_nCK_TRFC_DLR                        {0} \
        CONFIG.C0.DDR4_nCK_TXPR                            {5} \
        CONFIG.C0.LR_WIDTH                                 {1} \
        CONFIG.C0.MIGRATION                                {false} \
        CONFIG.C0.ODT_WIDTH                                {1} \
        CONFIG.C0.StackHeight                              {1} \
        CONFIG.C0_CLOCK_BOARD_INTERFACE                    {Custom} \
        CONFIG.C0_DDR4_ARESETN.INSERT_VIP                  {0} \
        CONFIG.C0_DDR4_BOARD_INTERFACE                     {Custom} \
        CONFIG.C0_DDR4_CLOCK.INSERT_VIP                    {0} \
        CONFIG.C0_DDR4_RESET.INSERT_VIP                    {0} \
        CONFIG.C0_DDR4_S_AXI.INSERT_VIP                    {0} \
        CONFIG.C0_DDR4_S_AXI_CTRL.INSERT_VIP               {0} \
        CONFIG.C0_SYS_CLK_I.INSERT_VIP                     {0} \
        CONFIG.CLKOUT6                                     {false} \
        CONFIG.DCI_Cascade                                 {false} \
        CONFIG.DIFF_TERM_SYSCLK                            {false} \
        CONFIG.Debug_Signal                                {Disable} \
        CONFIG.Default_Bank_Selections                     {false} \
        CONFIG.EN_PP_4R_MIR                                {false} \
        CONFIG.Enable_SysPorts                             {true} \
        CONFIG.Example_TG                                  {SIMPLE_TG} \
        CONFIG.IOPowerReduction                            {OFF} \
        CONFIG.IO_Power_Reduction                          {false} \
        CONFIG.IS_FROM_PHY                                 {1} \
        CONFIG.MCS_DBG_EN                                  {false} \
        CONFIG.No_Controller                               {1} \
        CONFIG.PARTIAL_RECONFIG_FLOW_MIG                   {false} \
        CONFIG.PING_PONG_PHY                               {1} \
        CONFIG.Phy_Only                                    {Phy_Only_Single} \
        CONFIG.RECONFIG_XSDB_SAVE_RESTORE                  {false} \
        CONFIG.RESET_BOARD_INTERFACE                       {Custom} \
        CONFIG.Reference_Clock                             {Differential} \
        CONFIG.SET_DW_TO_40                                {false} \
        CONFIG.SYSTEM_RESET.INSERT_VIP                     {0} \
        CONFIG.Simulation_Mode                             {BFM} \
        CONFIG.System_Clock                                {Differential} \
        CONFIG.TIMING_3DS                                  {false} \
        CONFIG.TIMING_OP1                                  {false} \
        CONFIG.TIMING_OP2                                  {false} \
    ] [get_ips phy_ddr4_comp]
    """)
}

trait WithPhyDDR4IPPatches {
    ElaborationArtefacts.add(
        "rd_en.patch",
    """--- __PATHTOFILE__
@@ -114,55 +114,86 @@
 
 reg                      [RL+12 + 4*EXTRA_CMD_DELAY :0] rdEn;
 (* KEEP = "true" *) reg  [RL+12 + 4*EXTRA_CMD_DELAY :0] rdEn_nxt;
-reg                      [18:0]                        rsMask[0:7];
+reg                      [19:0]                        rsMask[0:15];
 
-function [18:0] rs2mask;
+// RS probably stands for rank select.
+// TODO edit below in worst case.
+function [19:0] rs2mask;
    input [3:0] diff;
    input       slot2;
-case ({diff, slot2})
-   5'b0_0000: rs2mask = 19'b000_0000_0000_0000_1111;
-   5'b0_0001: rs2mask = 19'b000_0000_0000_0011_1100;
-   5'b0_0010: rs2mask = 19'b000_0000_0000_0001_1110;
-   5'b0_0011: rs2mask = 19'b000_0000_0000_0111_1000;
-   5'b0_0100: rs2mask = 19'b000_0000_0000_0011_1100;
-   5'b0_0101: rs2mask = 19'b000_0000_0000_1111_0000;
-   5'b0_0110: rs2mask = 19'b000_0000_0000_0111_1000;
-   5'b0_0111: rs2mask = 19'b000_0000_0001_1110_0000;
-   5'b0_1000: rs2mask = 19'b000_0000_0000_1111_0000;
-   5'b0_1001: rs2mask = 19'b000_0000_0011_1100_0000;
-   5'b0_1010: rs2mask = 19'b000_0000_0001_1110_0000;
-   5'b0_1011: rs2mask = 19'b000_0000_0111_1000_0000;
-   5'b0_1100: rs2mask = 19'b000_0000_0011_1100_0000;
-   5'b0_1101: rs2mask = 19'b000_0000_1111_0000_0000;
-   5'b0_1110: rs2mask = 19'b000_0000_0111_1000_0000;
-   5'b0_1111: rs2mask = 19'b000_0001_1110_0000_0000;
-   5'b1_0000: rs2mask = 19'b000_0000_1111_0000_0000;
-   5'b1_0001: rs2mask = 19'b000_0011_1100_0000_0000;
-   5'b1_0010: rs2mask = 19'b000_0001_1110_0000_0000;
-   5'b1_0011: rs2mask = 19'b000_0111_1000_0000_0000;
-   5'b1_0100: rs2mask = 19'b000_0011_1100_0000_0000;
-   5'b1_0101: rs2mask = 19'b000_1111_0000_0000_0000;
-   5'b1_0110: rs2mask = 19'b000_0111_1000_0000_0000;
-   5'b1_0111: rs2mask = 19'b001_1110_0000_0000_0000;
-   5'b1_1000: rs2mask = 19'b000_1111_0000_0000_0000;
-   5'b1_1001: rs2mask = 19'b011_1100_0000_0000_0000;
-   5'b1_1010: rs2mask = 19'b001_1110_0000_0000_0000;
-   5'b1_1011: rs2mask = 19'b111_1000_0000_0000_0000;
+   input       slot1;
+case ({diff, slot2, slot1})
+6'b0_00000: rs2mask = 19'b000_0000_0000_0000_1111;
+    6'b0_00001: rs2mask = 19'b000_0000_0000_0001_1110;
+   6'b0_00010: rs2mask = 19'b000_0000_0000_0011_1100;
+    6'b0_00011: rs2mask = 19'b000_0000_0000_0111_1000;
+   6'b0_00100: rs2mask = 19'b000_0000_0000_0001_1110;
+    6'b0_00101: rs2mask = 19'b000_0000_0000_0011_1100;
+   6'b0_00110: rs2mask = 19'b000_0000_0000_0111_1000;
+    6'b0_00111: rs2mask = 19'b000_0000_0000_1111_0000;
+   6'b0_01000: rs2mask = 19'b000_0000_0000_0011_1100;
+    6'b0_01001: rs2mask = 19'b000_0000_0000_0111_1000;
+   6'b0_01010: rs2mask = 19'b000_0000_0000_1111_0000;
+    6'b0_01011: rs2mask = 19'b000_0000_0001_1110_0000;
+   6'b0_01100: rs2mask = 19'b000_0000_0000_0111_1000;
+    6'b0_01101: rs2mask = 19'b000_0000_0000_1111_0000;
+   6'b0_01110: rs2mask = 19'b000_0000_0001_1110_0000;
+    6'b0_01111: rs2mask = 19'b000_0000_0011_1100_0000;
+   6'b0_10000: rs2mask = 19'b000_0000_0000_1111_0000;
+    6'b0_10001: rs2mask = 19'b000_0000_0001_1110_0000;
+   6'b0_10010: rs2mask = 19'b000_0000_0011_1100_0000;
+    6'b0_10011: rs2mask = 19'b000_0000_0111_1000_0000;
+   6'b0_10100: rs2mask = 19'b000_0000_0001_1110_0000;
+    6'b0_10101: rs2mask = 19'b000_0000_0011_1100_0000;
+   6'b0_10110: rs2mask = 19'b000_0000_0111_1000_0000;
+    6'b0_10111: rs2mask = 19'b000_0000_1111_0000_0000;
+   6'b0_11000: rs2mask = 19'b000_0000_0011_1100_0000;
+    6'b0_11001: rs2mask = 19'b000_0000_0111_1000_0000;
+   6'b0_11010: rs2mask = 19'b000_0000_1111_0000_0000;
+    6'b0_11011: rs2mask = 19'b000_0001_1110_0000_0000;
+   6'b0_11100: rs2mask = 19'b000_0000_0111_1000_0000;
+    6'b0_11101: rs2mask = 19'b000_0000_1111_0000_0000;
+   6'b0_11110: rs2mask = 19'b000_0001_1110_0000_0000;
+    6'b0_11111: rs2mask = 19'b000_0011_1100_0000_0000;
+   6'b1_00000: rs2mask = 19'b000_0000_1111_0000_0000;
+    6'b1_00001: rs2mask = 19'b000_0001_1110_0000_0000;
+   6'b1_00010: rs2mask = 19'b000_0011_1100_0000_0000;
+    6'b1_00011: rs2mask = 19'b000_0111_1000_0000_0000;
+   6'b1_00100: rs2mask = 19'b000_0001_1110_0000_0000;
+    6'b1_00101: rs2mask = 19'b000_0011_1100_0000_0000;
+   6'b1_00110: rs2mask = 19'b000_0111_1000_0000_0000;
+    6'b1_00111: rs2mask = 19'b000_1111_0000_0000_0000;
+   6'b1_01000: rs2mask = 19'b000_0011_1100_0000_0000;
+    6'b1_01001: rs2mask = 19'b000_0111_1000_0000_0000;
+   6'b1_01010: rs2mask = 19'b000_1111_0000_0000_0000;
+    6'b1_01011: rs2mask = 19'b001_1110_0000_0000_0000;
+   6'b1_01100: rs2mask = 19'b000_0111_1000_0000_0000;
+    6'b1_01101: rs2mask = 19'b000_1111_0000_0000_0000;
+   6'b1_01110: rs2mask = 19'b001_1110_0000_0000_0000;
+    6'b1_01111: rs2mask = 19'b011_1100_0000_0000_0000;
+   6'b1_10000: rs2mask = 19'b000_1111_0000_0000_0000;
+    6'b1_10001: rs2mask = 19'b001_1110_0000_0000_0000;
+   6'b1_10010: rs2mask = 19'b011_1100_0000_0000_0000;
+    6'b1_10011: rs2mask = 19'b111_1000_0000_0000_0000;
+   6'b1_10100: rs2mask = 19'b001_1110_0000_0000_0000;
+    6'b1_10101: rs2mask = 19'b011_1100_0000_0000_0000;
+   6'b1_10110: rs2mask = 19'b111_1000_0000_0000_0000;
+    6'b1_10111: rs2mask = 20'b1111_0000_0000_0000_0000;
      default: rs2mask = 19'b000_0000_1000_0000_1111;
 endcase
 endfunction
 
 always @(posedge clk) begin: blk1
-   reg [3:0] rsNdx;
-   for (rsNdx = 0; rsNdx <= 7; rsNdx = rsNdx + 1)
-      rsMask[rsNdx] <= #TCQ rs2mask(mCL[rsNdx[2:1]] - (RL - 3), rsNdx[0]);
+   reg [4:0] rsNdx;
+   for (rsNdx = 0; rsNdx <= 15; rsNdx = rsNdx + 1)
+      rsMask[rsNdx] <= #TCQ rs2mask(mCL[rsNdx[3:2]] - (RL - 3), rsNdx[1], rsNdx[0]);
 end
 
 always @(*) if (rst) begin
    rdEn_nxt = 'b0;
 end else begin
    rdEn_nxt =   (rdEn >> 4)
-                | (rdCAS ? {rsMask[{winRank, casSlot[1]}], { (RL-6 + 4*EXTRA_CMD_DELAY) {1'b0}}}
+                | (rdCAS ? {rsMask[{winRank, casSlot[1], casSlot[0]}], { (RL-6 + 4*EXTRA_CMD_DELAY) {1'b0}}}
                 : 'b0);
 end
""")
    ElaborationArtefacts.add(
        "write.patch",
    """--- __PATHTOFILE__
@@ -157,9 +157,13 @@
 // used in pushing new write CAS commands into the wrQ shift register.  These values are
 // calculated for both casslot0 and casslot2.
 localparam FABRIC_CASSLOT0 = (   ALL_WR_LATENCY       / 4 ) - 2;
+localparam FABRIC_CASSLOT1 = ( ( ALL_WR_LATENCY + 1 ) / 4 ) - 2;
 localparam FABRIC_CASSLOT2 = ( ( ALL_WR_LATENCY + 2 ) / 4 ) - 2;
+localparam FABRIC_CASSLOT3 = ( ( ALL_WR_LATENCY + 3 ) / 4 ) - 2;
 localparam OFFSET_CASSLOT0 =   ( ALL_WR_LATENCY     ) % 4;
+localparam OFFSET_CASSLOT1 =   ( ALL_WR_LATENCY + 1 ) % 4;
 localparam OFFSET_CASSLOT2 =   ( ALL_WR_LATENCY + 2 ) % 4;
+localparam OFFSET_CASSLOT3 =   ( ALL_WR_LATENCY + 3 ) % 4;
 
 integer i;
 
@@ -192,10 +196,12 @@
 // Output enable shift register fabric load cycle for cal and mc.  mccasSlot2 is timing critical.
 reg  [3:0] oe_0_mux_cal;
 wire [3:0] oe_0_mux_cal_nxt = calDone    ? FABRIC_CASSLOT0 : FABRIC_CASSLOT2;
-wire [3:0] oe_fabric        = mccasSlot2 ? FABRIC_CASSLOT2 : oe_0_mux_cal;
+wire [3:0] oe_fabric        = mccasSlot2 ? (casSlot[0] ? FABRIC_CASSLOT3 : FABRIC_CASSLOT2) :
+                         (casSlot[0] ? FABRIC_CASSLOT1 : oe_0_mux_cal);
 
 // tCK offset for mc.  mccasSlot2 is timing critical.
-wire [1:0] tck_offset = mccasSlot2 ? OFFSET_CASSLOT2 : OFFSET_CASSLOT0;
+wire [1:0] tck_offset = mccasSlot2 ? (casSlot[0] ? OFFSET_CASSLOT3 : OFFSET_CASSLOT2) :
+                         (casSlot[0] ? OFFSET_CASSLOT1 : OFFSET_CASSLOT0);
 
 // Output enable shift register load value for cal and mc.  mcwrCAS and tck_offset are timing critical.
 wire [DBAW+4:0] wrQ_cal_load_value = {winBuf,   calRank, OFFSET_CASSLOT2[1:0], 1'b1};
""")

    ElaborationArtefacts.add(
        "vcu108phyddr4.ipinit.tcl",
    """
# Version safe wait procedure for a single run. wait_on_run changed to wait_on_runs after Vivado 2021.2
proc safe_wait { run } {
    if { [catch wait_on_runs $run] } {
        wait_on_run $run
    }
}

# Version safe wait procedure for multiple runs. wait_on_run changed to wait_on_runs after Vivado 2021.2
proc safe_wait_multiple { run_list } {
    if { [catch wait_on_runs $run_list] } {
        foreach run $run_list { wait_on_run $run }
    }
}

set PATH_DIR [exec pwd]
set TARGET_DIR "${PATH_DIR}/obj/ip/phy_ddr4_comp/rtl/cal"

set patch_list [glob -nocomplain *.patch]
set target_list {rd_en write}

foreach patch $patch_list {
    foreach target $target_list {
        if {[string first $target $patch] != -1} {
            set fd [open "${patch}" r]
            set newfd [open "${patch}.tmp" w]
            while {[gets $fd line] >= 0} {
                set newline [string map "__PATHTOFILE__ ${TARGET_DIR}/ddr4_v2_2_cal_${target}.sv" $line]
                puts $newfd $newline
            }
            close $fd
            close $newfd
            file rename -force "${patch}.tmp" "${patch}"
        }
    }
}

puts "${TARGET_DIR}/ddr4_v2_2_cal_rd_en.sv"

foreach target $target_list {
    if [ catch {exec dos2unix "${TARGET_DIR}/ddr4_v2_2_cal_${target}.sv"} error ] {
        puts "Error while dos2unix ${target}. Assumed safe to ignore."
    }
}

foreach patch $patch_list {
    set res [exec patch -d/ -p0 < $patch]
} 
""")
}

trait WithVCU108ScratchpadIP {
    ElaborationArtefacts.add(
        "vcu108scratchpad.vivado.tcl",
    """
    create_ip -vendor xilinx.com -library ip -name blk_mem_gen -version 8.4 -module_name scratchpad -dir $ipdir -force
    set_property -dict [list \
        CONFIG.AXILITE_SLAVE_S_AXI.INSERT_VIP              {0} \
        CONFIG.AXI_ID_Width                                {4} \
        CONFIG.AXI_SLAVE_S_AXI.INSERT_VIP                  {0} \
        CONFIG.AXI_Slave_Type                              {Memory_Slave} \
        CONFIG.AXI_Type                                    {AXI4_Full} \
        CONFIG.Additional_Inputs_for_Power_Estimation      {false} \
        CONFIG.Algorithm                                   {Minimum_Area} \
        CONFIG.Assume_Synchronous_Clk                      {false} \
        CONFIG.Byte_Size                                   {9} \
        CONFIG.CLK.ACLK.INSERT_VIP                         {0} \
        CONFIG.CTRL_ECC_ALGO                               {NONE} \
        CONFIG.Coe_File                                    {no_coe_file_loaded} \
        CONFIG.Collision_Warnings                          {ALL} \
        CONFIG.Disable_Collision_Warnings                  {false} \
        CONFIG.Disable_Out_of_Range_Warnings               {false} \
        CONFIG.ECC                                         {false} \
        CONFIG.EN_DEEPSLEEP_PIN                            {false} \
        CONFIG.EN_ECC_PIPE                                 {false} \
        CONFIG.EN_SAFETY_CKT                               {false} \
        CONFIG.EN_SHUTDOWN_PIN                             {false} \
        CONFIG.EN_SLEEP_PIN                                {false} \
        CONFIG.Enable_32bit_Address                        {false} \
        CONFIG.Enable_A                                    {Use_ENA_Pin} \
        CONFIG.Enable_B                                    {Always_Enabled} \
        CONFIG.Error_Injection_Type                        {Single_Bit_Error_Injection} \
        CONFIG.Fill_Remaining_Memory_Locations             {false} \
        CONFIG.Interface_Type                              {Native} \
        CONFIG.Load_Init_File                              {false} \
        CONFIG.MEM_FILE                                    {no_mem_loaded} \
        CONFIG.Memory_Type                                 {Single_Port_RAM} \
        CONFIG.Operating_Mode_A                            {WRITE_FIRST} \
        CONFIG.Operating_Mode_B                            {WRITE_FIRST} \
        CONFIG.Output_Reset_Value_A                        {0} \
        CONFIG.Output_Reset_Value_B                        {0} \
        CONFIG.PRIM_type_to_Implement                      {BRAM} \
        CONFIG.Pipeline_Stages                             {0} \
        CONFIG.Port_A_Clock                                {100} \
        CONFIG.Port_A_Enable_Rate                          {100} \
        CONFIG.Port_A_Write_Rate                           {50} \
        CONFIG.Port_B_Clock                                {0} \
        CONFIG.Port_B_Enable_Rate                          {0} \
        CONFIG.Port_B_Write_Rate                           {0} \
        CONFIG.Primitive                                   {8kx2} \
        CONFIG.RD_ADDR_CHNG_A                              {false} \
        CONFIG.RD_ADDR_CHNG_B                              {false} \
        CONFIG.READ_LATENCY_A                              {1} \
        CONFIG.READ_LATENCY_B                              {1} \
        CONFIG.RST.ARESETN.INSERT_VIP                      {0} \
        CONFIG.Read_Width_A                                {32} \
        CONFIG.Read_Width_B                                {32} \
        CONFIG.Register_PortA_Output_of_Memory_Core        {false} \
        CONFIG.Register_PortA_Output_of_Memory_Primitives  {false} \
        CONFIG.Register_PortB_Output_of_Memory_Core        {false} \
        CONFIG.Register_PortB_Output_of_Memory_Primitives  {false} \
        CONFIG.Remaining_Memory_Locations                  {0} \
        CONFIG.Reset_Memory_Latch_A                        {false} \
        CONFIG.Reset_Memory_Latch_B                        {false} \
        CONFIG.Reset_Priority_A                            {CE} \
        CONFIG.Reset_Priority_B                            {CE} \
        CONFIG.Reset_Type                                  {SYNC} \
        CONFIG.Use_AXI_ID                                  {false} \
        CONFIG.Use_Byte_Write_Enable                       {false} \
        CONFIG.Use_Error_Injection_Pins                    {false} \
        CONFIG.Use_REGCEA_Pin                              {false} \
        CONFIG.Use_REGCEB_Pin                              {false} \
        CONFIG.Use_RSTA_Pin                                {false} \
        CONFIG.Use_RSTB_Pin                                {false} \
        CONFIG.Write_Depth_A                               {1024} \
        CONFIG.Write_Width_A                               {32} \
        CONFIG.Write_Width_B                               {32} \
        CONFIG.ecctype                                     {No_ECC} \
        CONFIG.register_porta_input_of_softecc             {false} \
        CONFIG.register_portb_output_of_softecc            {false} \
        CONFIG.softecc                                     {false} \
        CONFIG.use_bram_block                              {Stand_Alone} \
    ] [get_ips scratchpad]
    """)
}

trait WithVCU108PrReadMemIP {
    ElaborationArtefacts.add(
        "vcu108prrdmem.vivado.tcl",
    """
    create_ip -vendor xilinx.com -library ip -name blk_mem_gen -version 8.4 -module_name pr_read_mem -dir $ipdir -force
    set_property -dict [list \
        CONFIG.AXILITE_SLAVE_S_AXI.INSERT_VIP              {0} \
        CONFIG.AXI_ID_Width                                {4} \
        CONFIG.AXI_SLAVE_S_AXI.INSERT_VIP                  {0} \
        CONFIG.AXI_Slave_Type                              {Memory_Slave} \
        CONFIG.AXI_Type                                    {AXI4_Full} \
        CONFIG.Additional_Inputs_for_Power_Estimation      {false} \
        CONFIG.Algorithm                                   {Minimum_Area} \
        CONFIG.Assume_Synchronous_Clk                      {false} \
        CONFIG.Byte_Size                                   {9} \
        CONFIG.CLK.ACLK.INSERT_VIP                         {0} \
        CONFIG.CTRL_ECC_ALGO                               {NONE} \
        CONFIG.Coe_File                                    {/home/kirbyydoge/GitHub/chipyard/generators/easydram/src/main/resources/vsrc/pr_read.coe} \
        CONFIG.Collision_Warnings                          {ALL} \
        CONFIG.Disable_Collision_Warnings                  {false} \
        CONFIG.Disable_Out_of_Range_Warnings               {false} \
        CONFIG.ECC                                         {false} \
        CONFIG.EN_DEEPSLEEP_PIN                            {false} \
        CONFIG.EN_ECC_PIPE                                 {false} \
        CONFIG.EN_SAFETY_CKT                               {false} \
        CONFIG.EN_SHUTDOWN_PIN                             {false} \
        CONFIG.EN_SLEEP_PIN                                {false} \
        CONFIG.Enable_32bit_Address                        {false} \
        CONFIG.Enable_A                                    {Use_ENA_Pin} \
        CONFIG.Enable_B                                    {Always_Enabled} \
        CONFIG.Error_Injection_Type                        {Single_Bit_Error_Injection} \
        CONFIG.Fill_Remaining_Memory_Locations             {true} \
        CONFIG.Interface_Type                              {Native} \
        CONFIG.Load_Init_File                              {true} \
        CONFIG.MEM_FILE                                    {no_mem_loaded} \
        CONFIG.Memory_Type                                 {Single_Port_RAM} \
        CONFIG.Operating_Mode_A                            {WRITE_FIRST} \
        CONFIG.Operating_Mode_B                            {WRITE_FIRST} \
        CONFIG.Output_Reset_Value_A                        {0} \
        CONFIG.Output_Reset_Value_B                        {0} \
        CONFIG.PRIM_type_to_Implement                      {BRAM} \
        CONFIG.Pipeline_Stages                             {0} \
        CONFIG.Port_A_Clock                                {100} \
        CONFIG.Port_A_Enable_Rate                          {100} \
        CONFIG.Port_A_Write_Rate                           {50} \
        CONFIG.Port_B_Clock                                {0} \
        CONFIG.Port_B_Enable_Rate                          {0} \
        CONFIG.Port_B_Write_Rate                           {0} \
        CONFIG.Primitive                                   {8kx2} \
        CONFIG.RD_ADDR_CHNG_A                              {false} \
        CONFIG.RD_ADDR_CHNG_B                              {false} \
        CONFIG.READ_LATENCY_A                              {1} \
        CONFIG.READ_LATENCY_B                              {1} \
        CONFIG.RST.ARESETN.INSERT_VIP                      {0} \
        CONFIG.Read_Width_A                                {64} \
        CONFIG.Read_Width_B                                {64} \
        CONFIG.Register_PortA_Output_of_Memory_Core        {false} \
        CONFIG.Register_PortA_Output_of_Memory_Primitives  {false} \
        CONFIG.Register_PortB_Output_of_Memory_Core        {false} \
        CONFIG.Register_PortB_Output_of_Memory_Primitives  {false} \
        CONFIG.Remaining_Memory_Locations                  {0} \
        CONFIG.Reset_Memory_Latch_A                        {false} \
        CONFIG.Reset_Memory_Latch_B                        {false} \
        CONFIG.Reset_Priority_A                            {CE} \
        CONFIG.Reset_Priority_B                            {CE} \
        CONFIG.Reset_Type                                  {SYNC} \
        CONFIG.Use_AXI_ID                                  {false} \
        CONFIG.Use_Byte_Write_Enable                       {false} \
        CONFIG.Use_Error_Injection_Pins                    {false} \
        CONFIG.Use_REGCEA_Pin                              {false} \
        CONFIG.Use_REGCEB_Pin                              {false} \
        CONFIG.Use_RSTA_Pin                                {false} \
        CONFIG.Use_RSTB_Pin                                {false} \
        CONFIG.Write_Depth_A                               {16} \
        CONFIG.Write_Width_A                               {64} \
        CONFIG.Write_Width_B                               {64} \
        CONFIG.ecctype                                     {No_ECC} \
        CONFIG.register_porta_input_of_softecc             {false} \
        CONFIG.register_portb_output_of_softecc            {false} \
        CONFIG.softecc                                     {false} \
        CONFIG.use_bram_block                              {Stand_Alone} \
    ] [get_ips pr_read_mem]
    """)
}

trait WithVCU108ZqCalibMemIP {
    ElaborationArtefacts.add(
        "vcu108zqcalibmem.vivado.tcl",
    """
    create_ip -vendor xilinx.com -library ip -name blk_mem_gen -version 8.4 -module_name zq_calib_mem -dir $ipdir -force
    set_property -dict [list \
        CONFIG.AXILITE_SLAVE_S_AXI.INSERT_VIP              {0} \
        CONFIG.AXI_ID_Width                                {4} \
        CONFIG.AXI_SLAVE_S_AXI.INSERT_VIP                  {0} \
        CONFIG.AXI_Slave_Type                              {Memory_Slave} \
        CONFIG.AXI_Type                                    {AXI4_Full} \
        CONFIG.Additional_Inputs_for_Power_Estimation      {false} \
        CONFIG.Algorithm                                   {Minimum_Area} \
        CONFIG.Assume_Synchronous_Clk                      {false} \
        CONFIG.Byte_Size                                   {9} \
        CONFIG.CLK.ACLK.INSERT_VIP                         {0} \
        CONFIG.CTRL_ECC_ALGO                               {NONE} \
        CONFIG.Coe_File                                    {/home/kirbyydoge/GitHub/chipyard/generators/easydram/src/main/resources/vsrc/pr_zq.coe} \
        CONFIG.Collision_Warnings                          {ALL} \
        CONFIG.Disable_Collision_Warnings                  {false} \
        CONFIG.Disable_Out_of_Range_Warnings               {false} \
        CONFIG.ECC                                         {false} \
        CONFIG.EN_DEEPSLEEP_PIN                            {false} \
        CONFIG.EN_ECC_PIPE                                 {false} \
        CONFIG.EN_SAFETY_CKT                               {false} \
        CONFIG.EN_SHUTDOWN_PIN                             {false} \
        CONFIG.EN_SLEEP_PIN                                {false} \
        CONFIG.Enable_32bit_Address                        {false} \
        CONFIG.Enable_A                                    {Use_ENA_Pin} \
        CONFIG.Enable_B                                    {Always_Enabled} \
        CONFIG.Error_Injection_Type                        {Single_Bit_Error_Injection} \
        CONFIG.Fill_Remaining_Memory_Locations             {true} \
        CONFIG.Interface_Type                              {Native} \
        CONFIG.Load_Init_File                              {true} \
        CONFIG.MEM_FILE                                    {no_mem_loaded} \
        CONFIG.Memory_Type                                 {Single_Port_RAM} \
        CONFIG.Operating_Mode_A                            {WRITE_FIRST} \
        CONFIG.Operating_Mode_B                            {WRITE_FIRST} \
        CONFIG.Output_Reset_Value_A                        {0} \
        CONFIG.Output_Reset_Value_B                        {0} \
        CONFIG.PRIM_type_to_Implement                      {BRAM} \
        CONFIG.Pipeline_Stages                             {0} \
        CONFIG.Port_A_Clock                                {100} \
        CONFIG.Port_A_Enable_Rate                          {100} \
        CONFIG.Port_A_Write_Rate                           {50} \
        CONFIG.Port_B_Clock                                {0} \
        CONFIG.Port_B_Enable_Rate                          {0} \
        CONFIG.Port_B_Write_Rate                           {0} \
        CONFIG.Primitive                                   {8kx2} \
        CONFIG.RD_ADDR_CHNG_A                              {false} \
        CONFIG.RD_ADDR_CHNG_B                              {false} \
        CONFIG.READ_LATENCY_A                              {1} \
        CONFIG.READ_LATENCY_B                              {1} \
        CONFIG.RST.ARESETN.INSERT_VIP                      {0} \
        CONFIG.Read_Width_A                                {64} \
        CONFIG.Read_Width_B                                {64} \
        CONFIG.Register_PortA_Output_of_Memory_Core        {false} \
        CONFIG.Register_PortA_Output_of_Memory_Primitives  {false} \
        CONFIG.Register_PortB_Output_of_Memory_Core        {false} \
        CONFIG.Register_PortB_Output_of_Memory_Primitives  {false} \
        CONFIG.Remaining_Memory_Locations                  {0} \
        CONFIG.Reset_Memory_Latch_A                        {false} \
        CONFIG.Reset_Memory_Latch_B                        {false} \
        CONFIG.Reset_Priority_A                            {CE} \
        CONFIG.Reset_Priority_B                            {CE} \
        CONFIG.Reset_Type                                  {SYNC} \
        CONFIG.Use_AXI_ID                                  {false} \
        CONFIG.Use_Byte_Write_Enable                       {false} \
        CONFIG.Use_Error_Injection_Pins                    {false} \
        CONFIG.Use_REGCEA_Pin                              {false} \
        CONFIG.Use_REGCEB_Pin                              {false} \
        CONFIG.Use_RSTA_Pin                                {false} \
        CONFIG.Use_RSTB_Pin                                {false} \
        CONFIG.Write_Depth_A                               {64} \
        CONFIG.Write_Width_A                               {64} \
        CONFIG.Write_Width_B                               {64} \
        CONFIG.ecctype                                     {No_ECC} \
        CONFIG.register_porta_input_of_softecc             {false} \
        CONFIG.register_portb_output_of_softecc            {false} \
        CONFIG.softecc                                     {false} \
        CONFIG.use_bram_block                              {Stand_Alone} \
    ] [get_ips zq_calib_mem]
    """)
}

trait WithVCU108PrRefMemIP {
    ElaborationArtefacts.add(
        "vcu108prrefmem.vivado.tcl",
    """
    create_ip -vendor xilinx.com -library ip -name blk_mem_gen -version 8.4 -module_name pr_ref_mem -dir $ipdir -force
    set_property -dict [list \
        CONFIG.AXILITE_SLAVE_S_AXI.INSERT_VIP              {0} \
        CONFIG.AXI_ID_Width                                {4} \
        CONFIG.AXI_SLAVE_S_AXI.INSERT_VIP                  {0} \
        CONFIG.AXI_Slave_Type                              {Memory_Slave} \
        CONFIG.AXI_Type                                    {AXI4_Full} \
        CONFIG.Additional_Inputs_for_Power_Estimation      {false} \
        CONFIG.Algorithm                                   {Minimum_Area} \
        CONFIG.Assume_Synchronous_Clk                      {false} \
        CONFIG.Byte_Size                                   {9} \
        CONFIG.CLK.ACLK.INSERT_VIP                         {0} \
        CONFIG.CTRL_ECC_ALGO                               {NONE} \
        CONFIG.Coe_File                                    {/home/kirbyydoge/GitHub/chipyard/generators/easydram/src/main/resources/vsrc/pr_ref.coe} \
        CONFIG.Collision_Warnings                          {ALL} \
        CONFIG.Disable_Collision_Warnings                  {false} \
        CONFIG.Disable_Out_of_Range_Warnings               {false} \
        CONFIG.ECC                                         {false} \
        CONFIG.EN_DEEPSLEEP_PIN                            {false} \
        CONFIG.EN_ECC_PIPE                                 {false} \
        CONFIG.EN_SAFETY_CKT                               {false} \
        CONFIG.EN_SHUTDOWN_PIN                             {false} \
        CONFIG.EN_SLEEP_PIN                                {false} \
        CONFIG.Enable_32bit_Address                        {false} \
        CONFIG.Enable_A                                    {Use_ENA_Pin} \
        CONFIG.Enable_B                                    {Always_Enabled} \
        CONFIG.Error_Injection_Type                        {Single_Bit_Error_Injection} \
        CONFIG.Fill_Remaining_Memory_Locations             {true} \
        CONFIG.Interface_Type                              {Native} \
        CONFIG.Load_Init_File                              {true} \
        CONFIG.MEM_FILE                                    {no_mem_loaded} \
        CONFIG.Memory_Type                                 {Single_Port_RAM} \
        CONFIG.Operating_Mode_A                            {WRITE_FIRST} \
        CONFIG.Operating_Mode_B                            {WRITE_FIRST} \
        CONFIG.Output_Reset_Value_A                        {0} \
        CONFIG.Output_Reset_Value_B                        {0} \
        CONFIG.PRIM_type_to_Implement                      {BRAM} \
        CONFIG.Pipeline_Stages                             {0} \
        CONFIG.Port_A_Clock                                {100} \
        CONFIG.Port_A_Enable_Rate                          {100} \
        CONFIG.Port_A_Write_Rate                           {50} \
        CONFIG.Port_B_Clock                                {0} \
        CONFIG.Port_B_Enable_Rate                          {0} \
        CONFIG.Port_B_Write_Rate                           {0} \
        CONFIG.Primitive                                   {8kx2} \
        CONFIG.RD_ADDR_CHNG_A                              {false} \
        CONFIG.RD_ADDR_CHNG_B                              {false} \
        CONFIG.READ_LATENCY_A                              {1} \
        CONFIG.READ_LATENCY_B                              {1} \
        CONFIG.RST.ARESETN.INSERT_VIP                      {0} \
        CONFIG.Read_Width_A                                {64} \
        CONFIG.Read_Width_B                                {64} \
        CONFIG.Register_PortA_Output_of_Memory_Core        {false} \
        CONFIG.Register_PortA_Output_of_Memory_Primitives  {false} \
        CONFIG.Register_PortB_Output_of_Memory_Core        {false} \
        CONFIG.Register_PortB_Output_of_Memory_Primitives  {false} \
        CONFIG.Remaining_Memory_Locations                  {0} \
        CONFIG.Reset_Memory_Latch_A                        {false} \
        CONFIG.Reset_Memory_Latch_B                        {false} \
        CONFIG.Reset_Priority_A                            {CE} \
        CONFIG.Reset_Priority_B                            {CE} \
        CONFIG.Reset_Type                                  {SYNC} \
        CONFIG.Use_AXI_ID                                  {false} \
        CONFIG.Use_Byte_Write_Enable                       {false} \
        CONFIG.Use_Error_Injection_Pins                    {false} \
        CONFIG.Use_REGCEA_Pin                              {false} \
        CONFIG.Use_REGCEB_Pin                              {false} \
        CONFIG.Use_RSTA_Pin                                {false} \
        CONFIG.Use_RSTB_Pin                                {false} \
        CONFIG.Write_Depth_A                               {64} \
        CONFIG.Write_Width_A                               {64} \
        CONFIG.Write_Width_B                               {64} \
        CONFIG.ecctype                                     {No_ECC} \
        CONFIG.register_porta_input_of_softecc             {false} \
        CONFIG.register_portb_output_of_softecc            {false} \
        CONFIG.softecc                                     {false} \
        CONFIG.use_bram_block                              {Stand_Alone} \
    ] [get_ips pr_ref_mem]
    """)
}

trait WithVCU108InstrBlkMemIP {
    ElaborationArtefacts.add(
        "vcu108instrblkmem.vivado.tcl",
    """
    create_ip -vendor xilinx.com -library ip -name blk_mem_gen -version 8.4 -module_name instr_blk_mem -dir $ipdir -force
    set_property -dict [list \
        CONFIG.AXILITE_SLAVE_S_AXI.INSERT_VIP              {0} \
        CONFIG.AXI_ID_Width                                {4} \
        CONFIG.AXI_SLAVE_S_AXI.INSERT_VIP                  {0} \
        CONFIG.AXI_Slave_Type                              {Memory_Slave} \
        CONFIG.AXI_Type                                    {AXI4_Full} \
        CONFIG.Additional_Inputs_for_Power_Estimation      {false} \
        CONFIG.Algorithm                                   {Minimum_Area} \
        CONFIG.Assume_Synchronous_Clk                      {false} \
        CONFIG.Byte_Size                                   {9} \
        CONFIG.CLK.ACLK.INSERT_VIP                         {0} \
        CONFIG.CTRL_ECC_ALGO                               {NONE} \
        CONFIG.Coe_File                                    {no_coe_file_loaded} \
        CONFIG.Collision_Warnings                          {ALL} \
        CONFIG.Disable_Collision_Warnings                  {false} \
        CONFIG.Disable_Out_of_Range_Warnings               {false} \
        CONFIG.ECC                                         {false} \
        CONFIG.EN_DEEPSLEEP_PIN                            {false} \
        CONFIG.EN_ECC_PIPE                                 {false} \
        CONFIG.EN_SAFETY_CKT                               {false} \
        CONFIG.EN_SHUTDOWN_PIN                             {false} \
        CONFIG.EN_SLEEP_PIN                                {false} \
        CONFIG.Enable_32bit_Address                        {false} \
        CONFIG.Enable_A                                    {Use_ENA_Pin} \
        CONFIG.Enable_B                                    {Always_Enabled} \
        CONFIG.Error_Injection_Type                        {Single_Bit_Error_Injection} \
        CONFIG.Fill_Remaining_Memory_Locations             {false} \
        CONFIG.Interface_Type                              {Native} \
        CONFIG.Load_Init_File                              {false} \
        CONFIG.MEM_FILE                                    {no_mem_loaded} \
        CONFIG.Memory_Type                                 {Single_Port_RAM} \
        CONFIG.Operating_Mode_A                            {WRITE_FIRST} \
        CONFIG.Operating_Mode_B                            {WRITE_FIRST} \
        CONFIG.Output_Reset_Value_A                        {0} \
        CONFIG.Output_Reset_Value_B                        {0} \
        CONFIG.PRIM_type_to_Implement                      {BRAM} \
        CONFIG.Pipeline_Stages                             {0} \
        CONFIG.Port_A_Clock                                {100} \
        CONFIG.Port_A_Enable_Rate                          {100} \
        CONFIG.Port_A_Write_Rate                           {50} \
        CONFIG.Port_B_Clock                                {0} \
        CONFIG.Port_B_Enable_Rate                          {0} \
        CONFIG.Port_B_Write_Rate                           {0} \
        CONFIG.Primitive                                   {8kx2} \
        CONFIG.RD_ADDR_CHNG_A                              {false} \
        CONFIG.RD_ADDR_CHNG_B                              {false} \
        CONFIG.READ_LATENCY_A                              {1} \
        CONFIG.READ_LATENCY_B                              {1} \
        CONFIG.RST.ARESETN.INSERT_VIP                      {0} \
        CONFIG.Read_Width_A                                {64} \
        CONFIG.Read_Width_B                                {64} \
        CONFIG.Register_PortA_Output_of_Memory_Core        {false} \
        CONFIG.Register_PortA_Output_of_Memory_Primitives  {false} \
        CONFIG.Register_PortB_Output_of_Memory_Core        {false} \
        CONFIG.Register_PortB_Output_of_Memory_Primitives  {false} \
        CONFIG.Remaining_Memory_Locations                  {0} \
        CONFIG.Reset_Memory_Latch_A                        {false} \
        CONFIG.Reset_Memory_Latch_B                        {false} \
        CONFIG.Reset_Priority_A                            {CE} \
        CONFIG.Reset_Priority_B                            {CE} \
        CONFIG.Reset_Type                                  {SYNC} \
        CONFIG.Use_AXI_ID                                  {false} \
        CONFIG.Use_Byte_Write_Enable                       {false} \
        CONFIG.Use_Error_Injection_Pins                    {false} \
        CONFIG.Use_REGCEA_Pin                              {false} \
        CONFIG.Use_REGCEB_Pin                              {false} \
        CONFIG.Use_RSTA_Pin                                {false} \
        CONFIG.Use_RSTB_Pin                                {false} \
        CONFIG.Write_Depth_A                               {2048} \
        CONFIG.Write_Width_A                               {64} \
        CONFIG.Write_Width_B                               {64} \
        CONFIG.ecctype                                     {No_ECC} \
        CONFIG.register_porta_input_of_softecc             {false} \
        CONFIG.register_portb_output_of_softecc            {false} \
        CONFIG.softecc                                     {false} \
        CONFIG.use_bram_block                              {Stand_Alone} \
    ] [get_ips instr_blk_mem]
    """)
}

trait WithVCU108AxisClkConvIP {
    ElaborationArtefacts.add(
        "vcu108axisclkconv.vivado.tcl",
    """
    create_ip -vendor xilinx.com -library ip -name axis_clock_converter -version 1.1 -module_name axis_clock_converter -dir $ipdir -force
    set_property -dict [list \
        CONFIG.ACLKEN_CONV_MODE        {0} \
        CONFIG.ACLK_RATIO              {1:2} \
        CONFIG.HAS_TKEEP               {1} \
        CONFIG.HAS_TLAST               {1} \
        CONFIG.HAS_TSTRB               {0} \
        CONFIG.IS_ACLK_ASYNC           {1} \
        CONFIG.M_AXIS.INSERT_VIP       {0} \
        CONFIG.M_CLKIF.INSERT_VIP      {0} \
        CONFIG.M_RSTIF.INSERT_VIP      {0} \
        CONFIG.SYNCHRONIZATION_STAGES  {2} \
        CONFIG.S_AXIS.INSERT_VIP       {0} \
        CONFIG.S_CLKIF.INSERT_VIP      {0} \
        CONFIG.S_RSTIF.INSERT_VIP      {0} \
        CONFIG.TDATA_NUM_BYTES         {32} \
        CONFIG.TDEST_WIDTH             {0} \
        CONFIG.TID_WIDTH               {0} \
        CONFIG.TUSER_WIDTH             {0} \
    ] [get_ips axis_clock_converter]
    """)
}

trait WithVCU108RdBackFIFOIP {
    ElaborationArtefacts.add(
        "vcu108rdbackfifo.vivado.tcl",
    """
    create_ip -vendor xilinx.com -library ip -name fifo_generator -version 13.2 -module_name rdback_fifo -dir $ipdir -force
    set_property -dict [list \
        CONFIG.ADDRESS_WIDTH                                {32} \
        CONFIG.ARUSER_Width                                 {0} \
        CONFIG.AWUSER_Width                                 {0} \
        CONFIG.Add_NGC_Constraint_AXI                       {false} \
        CONFIG.Almost_Empty_Flag                            {false} \
        CONFIG.Almost_Full_Flag                             {false} \
        CONFIG.BUSER_Width                                  {0} \
        CONFIG.CORE_CLK.FREQ_HZ                             {100000000} \
        CONFIG.CORE_CLK.INSERT_VIP                          {0} \
        CONFIG.C_SELECT_XPM                                 {0} \
        CONFIG.Clock_Enable_Type                            {Slave_Interface_Clock_Enable} \
        CONFIG.Clock_Type_AXI                               {Common_Clock} \
        CONFIG.DATA_WIDTH                                   {64} \
        CONFIG.Data_Count                                   {false} \
        CONFIG.Data_Count_Width                             {10} \
        CONFIG.Disable_Timing_Violations                    {false} \
        CONFIG.Disable_Timing_Violations_AXI                {false} \
        CONFIG.Dout_Reset_Value                             {0} \
        CONFIG.Empty_Threshold_Assert_Value                 {4} \
        CONFIG.Empty_Threshold_Assert_Value_axis            {1022} \
        CONFIG.Empty_Threshold_Assert_Value_rach            {1022} \
        CONFIG.Empty_Threshold_Assert_Value_rdch            {1022} \
        CONFIG.Empty_Threshold_Assert_Value_wach            {1022} \
        CONFIG.Empty_Threshold_Assert_Value_wdch            {1022} \
        CONFIG.Empty_Threshold_Assert_Value_wrch            {1022} \
        CONFIG.Empty_Threshold_Negate_Value                 {5} \
        CONFIG.Enable_Common_Overflow                       {false} \
        CONFIG.Enable_Common_Underflow                      {false} \
        CONFIG.Enable_Data_Counts_axis                      {false} \
        CONFIG.Enable_Data_Counts_rach                      {false} \
        CONFIG.Enable_Data_Counts_rdch                      {false} \
        CONFIG.Enable_Data_Counts_wach                      {false} \
        CONFIG.Enable_Data_Counts_wdch                      {false} \
        CONFIG.Enable_Data_Counts_wrch                      {false} \
        CONFIG.Enable_ECC                                   {false} \
        CONFIG.Enable_ECC_Type                              {Hard_ECC} \
        CONFIG.Enable_ECC_axis                              {false} \
        CONFIG.Enable_ECC_rach                              {false} \
        CONFIG.Enable_ECC_rdch                              {false} \
        CONFIG.Enable_ECC_wach                              {false} \
        CONFIG.Enable_ECC_wdch                              {false} \
        CONFIG.Enable_ECC_wrch                              {false} \
        CONFIG.Enable_Reset_Synchronization                 {true} \
        CONFIG.Enable_Safety_Circuit                        {false} \
        CONFIG.Enable_TLAST                                 {false} \
        CONFIG.Enable_TREADY                                {true} \
        CONFIG.FIFO_Application_Type_axis                   {Data_FIFO} \
        CONFIG.FIFO_Application_Type_rach                   {Data_FIFO} \
        CONFIG.FIFO_Application_Type_rdch                   {Data_FIFO} \
        CONFIG.FIFO_Application_Type_wach                   {Data_FIFO} \
        CONFIG.FIFO_Application_Type_wdch                   {Data_FIFO} \
        CONFIG.FIFO_Application_Type_wrch                   {Data_FIFO} \
        CONFIG.FIFO_Implementation_axis                     {Common_Clock_Block_RAM} \
        CONFIG.FIFO_Implementation_rach                     {Common_Clock_Block_RAM} \
        CONFIG.FIFO_Implementation_rdch                     {Common_Clock_Block_RAM} \
        CONFIG.FIFO_Implementation_wach                     {Common_Clock_Block_RAM} \
        CONFIG.FIFO_Implementation_wdch                     {Common_Clock_Block_RAM} \
        CONFIG.FIFO_Implementation_wrch                     {Common_Clock_Block_RAM} \
        CONFIG.Fifo_Implementation                          {Common_Clock_Builtin_FIFO} \
        CONFIG.Full_Flags_Reset_Value                       {0} \
        CONFIG.Full_Threshold_Assert_Value                  {895} \
        CONFIG.Full_Threshold_Assert_Value_axis             {1023} \
        CONFIG.Full_Threshold_Assert_Value_rach             {1023} \
        CONFIG.Full_Threshold_Assert_Value_rdch             {1023} \
        CONFIG.Full_Threshold_Assert_Value_wach             {1023} \
        CONFIG.Full_Threshold_Assert_Value_wdch             {1023} \
        CONFIG.Full_Threshold_Assert_Value_wrch             {1023} \
        CONFIG.Full_Threshold_Negate_Value                  {894} \
        CONFIG.HAS_ACLKEN                                   {false} \
        CONFIG.HAS_TKEEP                                    {false} \
        CONFIG.HAS_TSTRB                                    {false} \
        CONFIG.ID_WIDTH                                     {0} \
        CONFIG.INTERFACE_TYPE                               {Native} \
        CONFIG.Inject_Dbit_Error                            {false} \
        CONFIG.Inject_Dbit_Error_axis                       {false} \
        CONFIG.Inject_Dbit_Error_rach                       {false} \
        CONFIG.Inject_Dbit_Error_rdch                       {false} \
        CONFIG.Inject_Dbit_Error_wach                       {false} \
        CONFIG.Inject_Dbit_Error_wdch                       {false} \
        CONFIG.Inject_Dbit_Error_wrch                       {false} \
        CONFIG.Inject_Sbit_Error                            {false} \
        CONFIG.Inject_Sbit_Error_axis                       {false} \
        CONFIG.Inject_Sbit_Error_rach                       {false} \
        CONFIG.Inject_Sbit_Error_rdch                       {false} \
        CONFIG.Inject_Sbit_Error_wach                       {false} \
        CONFIG.Inject_Sbit_Error_wdch                       {false} \
        CONFIG.Inject_Sbit_Error_wrch                       {false} \
        CONFIG.Input_Data_Width                             {512} \
        CONFIG.Input_Depth                                  {1024} \
        CONFIG.Input_Depth_axis                             {1024} \
        CONFIG.Input_Depth_rach                             {16} \
        CONFIG.Input_Depth_rdch                             {1024} \
        CONFIG.Input_Depth_wach                             {16} \
        CONFIG.Input_Depth_wdch                             {1024} \
        CONFIG.Input_Depth_wrch                             {16} \
        CONFIG.MASTER_ACLK.FREQ_HZ                          {100000000} \
        CONFIG.MASTER_ACLK.INSERT_VIP                       {0} \
        CONFIG.M_AXI.INSERT_VIP                             {0} \
        CONFIG.M_AXIS.INSERT_VIP                            {0} \
        CONFIG.Master_interface_Clock_enable_memory_mapped  {false} \
        CONFIG.Output_Data_Width                            {256} \
        CONFIG.Output_Depth                                 {2048} \
        CONFIG.Output_Register_Type                         {Embedded_Reg} \
        CONFIG.Overflow_Flag                                {false} \
        CONFIG.Overflow_Flag_AXI                            {false} \
        CONFIG.Overflow_Sense                               {Active_High} \
        CONFIG.Overflow_Sense_AXI                           {Active_High} \
        CONFIG.PROTOCOL                                     {AXI4} \
        CONFIG.Performance_Options                          {First_Word_Fall_Through} \
        CONFIG.Programmable_Empty_Type                      {Single_Programmable_Empty_Threshold_Constant} \
        CONFIG.Programmable_Empty_Type_axis                 {No_Programmable_Empty_Threshold} \
        CONFIG.Programmable_Empty_Type_rach                 {No_Programmable_Empty_Threshold} \
        CONFIG.Programmable_Empty_Type_rdch                 {No_Programmable_Empty_Threshold} \
        CONFIG.Programmable_Empty_Type_wach                 {No_Programmable_Empty_Threshold} \
        CONFIG.Programmable_Empty_Type_wdch                 {No_Programmable_Empty_Threshold} \
        CONFIG.Programmable_Empty_Type_wrch                 {No_Programmable_Empty_Threshold} \
        CONFIG.Programmable_Full_Type                       {Single_Programmable_Full_Threshold_Constant} \
        CONFIG.Programmable_Full_Type_axis                  {No_Programmable_Full_Threshold} \
        CONFIG.Programmable_Full_Type_rach                  {No_Programmable_Full_Threshold} \
        CONFIG.Programmable_Full_Type_rdch                  {No_Programmable_Full_Threshold} \
        CONFIG.Programmable_Full_Type_wach                  {No_Programmable_Full_Threshold} \
        CONFIG.Programmable_Full_Type_wdch                  {No_Programmable_Full_Threshold} \
        CONFIG.Programmable_Full_Type_wrch                  {No_Programmable_Full_Threshold} \
        CONFIG.READ_CLK.FREQ_HZ                             {100000000} \
        CONFIG.READ_CLK.INSERT_VIP                          {0} \
        CONFIG.READ_WRITE_MODE                              {READ_WRITE} \
        CONFIG.RUSER_Width                                  {0} \
        CONFIG.Read_Clock_Frequency                         {1} \
        CONFIG.Read_Data_Count                              {false} \
        CONFIG.Read_Data_Count_Width                        {11} \
        CONFIG.Register_Slice_Mode_axis                     {Fully_Registered} \
        CONFIG.Register_Slice_Mode_rach                     {Fully_Registered} \
        CONFIG.Register_Slice_Mode_rdch                     {Fully_Registered} \
        CONFIG.Register_Slice_Mode_wach                     {Fully_Registered} \
        CONFIG.Register_Slice_Mode_wdch                     {Fully_Registered} \
        CONFIG.Register_Slice_Mode_wrch                     {Fully_Registered} \
        CONFIG.Reset_Pin                                    {true} \
        CONFIG.Reset_Type                                   {Synchronous_Reset} \
        CONFIG.SLAVE_ACLK.FREQ_HZ                           {100000000} \
        CONFIG.SLAVE_ACLK.INSERT_VIP                        {0} \
        CONFIG.SLAVE_ARESETN.INSERT_VIP                     {0} \
        CONFIG.S_AXI.INSERT_VIP                             {0} \
        CONFIG.S_AXIS.INSERT_VIP                            {0} \
        CONFIG.Slave_interface_Clock_enable_memory_mapped   {false} \
        CONFIG.TDATA_NUM_BYTES                              {1} \
        CONFIG.TDEST_WIDTH                                  {0} \
        CONFIG.TID_WIDTH                                    {0} \
        CONFIG.TKEEP_WIDTH                                  {1} \
        CONFIG.TSTRB_WIDTH                                  {1} \
        CONFIG.TUSER_WIDTH                                  {4} \
        CONFIG.Underflow_Flag                               {false} \
        CONFIG.Underflow_Flag_AXI                           {false} \
        CONFIG.Underflow_Sense                              {Active_High} \
        CONFIG.Underflow_Sense_AXI                          {Active_High} \
        CONFIG.Use_Dout_Reset                               {true} \
        CONFIG.Use_Embedded_Registers                       {true} \
        CONFIG.Use_Embedded_Registers_axis                  {false} \
        CONFIG.Use_Extra_Logic                              {false} \
        CONFIG.Valid_Flag                                   {true} \
        CONFIG.Valid_Sense                                  {Active_High} \
        CONFIG.WRITE_CLK.FREQ_HZ                            {100000000} \
        CONFIG.WRITE_CLK.INSERT_VIP                         {0} \
        CONFIG.WUSER_Width                                  {0} \
        CONFIG.Write_Acknowledge_Flag                       {false} \
        CONFIG.Write_Acknowledge_Sense                      {Active_High} \
        CONFIG.Write_Clock_Frequency                        {1} \
        CONFIG.Write_Data_Count                             {false} \
        CONFIG.Write_Data_Count_Width                       {10} \
        CONFIG.asymmetric_port_width                        {true} \
        CONFIG.axis_type                                    {FIFO} \
        CONFIG.dynamic_power_saving                         {false} \
        CONFIG.ecc_pipeline_reg                             {false} \
        CONFIG.enable_low_latency                           {false} \
        CONFIG.enable_read_pointer_increment_by2            {false} \
        CONFIG.rach_type                                    {FIFO} \
        CONFIG.rdch_type                                    {FIFO} \
        CONFIG.synchronization_stages                       {2} \
        CONFIG.synchronization_stages_axi                   {2} \
        CONFIG.use_dout_register                            {false} \
        CONFIG.wach_type                                    {FIFO} \
        CONFIG.wdch_type                                    {FIFO} \
        CONFIG.wrch_type                                    {FIFO} \
    ] [get_ips rdback_fifo]
    """)
}

trait WithVCU108ILAInstrRbIP {
    ElaborationArtefacts.add(
        "vcu108ilaaxisclkip.vivado.tcl",
    """
    create_ip -vendor xilinx.com -library ip -name ila -version 6.2 -module_name ila_instr_rb -dir $ipdir -force
    set_property -dict [list \
        CONFIG.ALL_PROBE_SAME_MU          {TRUE} \
        CONFIG.ALL_PROBE_SAME_MU_CNT      {1} \
        CONFIG.C_ADV_TRIGGER              {FALSE} \
        CONFIG.C_CLKFBOUT_MULT_F          {10} \
        CONFIG.C_CLKOUT0_DIVIDE_F         {10} \
        CONFIG.C_CLK_FREQ                 {200} \
        CONFIG.C_CLK_PERIOD               {5} \
        CONFIG.C_DATA_DEPTH               {1024} \
        CONFIG.C_DDR_CLK_GEN              {FALSE} \
        CONFIG.C_DIVCLK_DIVIDE            {3} \
        CONFIG.C_ENABLE_ILA_AXI_MON       {false} \
        CONFIG.C_EN_DDR_ILA               {FALSE} \
        CONFIG.C_EN_STRG_QUAL             {0} \
        CONFIG.C_EN_TIME_TAG              {0} \
        CONFIG.C_ILA_CLK_FREQ             {2000000} \
        CONFIG.C_INPUT_PIPE_STAGES        {0} \
        CONFIG.C_MONITOR_TYPE             {Native} \
        CONFIG.C_NUM_MONITOR_SLOTS        {1} \
        CONFIG.C_NUM_OF_PROBES            {5} \
        CONFIG.C_PROBE0_MU_CNT            {1} \
        CONFIG.C_PROBE0_TYPE              {0} \
        CONFIG.C_PROBE0_WIDTH             {256} \
        CONFIG.C_PROBE1_MU_CNT            {1} \
        CONFIG.C_PROBE1_TYPE              {0} \
        CONFIG.C_PROBE1_WIDTH             {1} \
        CONFIG.C_PROBE2_MU_CNT            {1} \
        CONFIG.C_PROBE2_TYPE              {0} \
        CONFIG.C_PROBE2_WIDTH             {1} \
        CONFIG.C_PROBE3_MU_CNT            {1} \
        CONFIG.C_PROBE3_TYPE              {0} \
        CONFIG.C_PROBE3_WIDTH             {1} \
        CONFIG.C_PROBE4_MU_CNT            {1} \
        CONFIG.C_PROBE4_TYPE              {0} \
        CONFIG.C_PROBE4_WIDTH             {1} \
        CONFIG.C_SLOT_0_AXIS_TDATA_WIDTH  {32} \
        CONFIG.C_SLOT_0_AXIS_TDEST_WIDTH  {1} \
        CONFIG.C_SLOT_0_AXIS_TID_WIDTH    {1} \
        CONFIG.C_SLOT_0_AXIS_TUSER_WIDTH  {1} \
        CONFIG.C_SLOT_0_AXI_ADDR_WIDTH    {32} \
        CONFIG.C_SLOT_0_AXI_ARUSER_WIDTH  {1} \
        CONFIG.C_SLOT_0_AXI_AWUSER_WIDTH  {1} \
        CONFIG.C_SLOT_0_AXI_BUSER_WIDTH   {1} \
        CONFIG.C_SLOT_0_AXI_DATA_WIDTH    {32} \
        CONFIG.C_SLOT_0_AXI_ID_WIDTH      {1} \
        CONFIG.C_SLOT_0_AXI_PROTOCOL      {AXI4} \
        CONFIG.C_SLOT_0_AXI_RUSER_WIDTH   {1} \
        CONFIG.C_SLOT_0_AXI_WUSER_WIDTH   {1} \
        CONFIG.C_TIME_TAG_WIDTH           {32} \
        CONFIG.C_TRIGIN_EN                {false} \
        CONFIG.C_TRIGOUT_EN               {false} \
        CONFIG.C_XLNX_HW_PROBE_INFO       {DEFAULT} \
        CONFIG.EN_BRAM_DRC                {TRUE} \
        CONFIG.SIGNAL_CLOCK.FREQ_HZ       {100000000} \
        CONFIG.SIGNAL_CLOCK.INSERT_VIP    {0} \
        CONFIG.SLOT_0_AXI.INSERT_VIP      {0} \
        CONFIG.SLOT_0_AXIS.INSERT_VIP     {0} \
    ] [get_ips ila_instr_rb]
    """)
}


trait WithVCU108ILAPipelineIP {
    ElaborationArtefacts.add(
        "vcu108ilapipelineip.vivado.tcl",
    """
    create_ip -vendor xilinx.com -library ip -name ila -version 6.2 -module_name ila_pipeline -dir $ipdir -force
    set_property -dict [list \
        CONFIG.ALL_PROBE_SAME_MU          {TRUE} \
        CONFIG.ALL_PROBE_SAME_MU_CNT      {1} \
        CONFIG.C_ADV_TRIGGER              {FALSE} \
        CONFIG.C_CLKFBOUT_MULT_F          {10} \
        CONFIG.C_CLKOUT0_DIVIDE_F         {10} \
        CONFIG.C_CLK_FREQ                 {200} \
        CONFIG.C_CLK_PERIOD               {5} \
        CONFIG.C_DATA_DEPTH               {1024} \
        CONFIG.C_DDR_CLK_GEN              {FALSE} \
        CONFIG.C_DIVCLK_DIVIDE            {3} \
        CONFIG.C_ENABLE_ILA_AXI_MON       {FALSE} \
        CONFIG.C_EN_DDR_ILA               {FALSE} \
        CONFIG.C_EN_STRG_QUAL             {0} \
        CONFIG.C_EN_TIME_TAG              {0} \
        CONFIG.C_ILA_CLK_FREQ             {2000000} \
        CONFIG.C_INPUT_PIPE_STAGES        {0} \
        CONFIG.C_MONITOR_TYPE             {Native} \
        CONFIG.C_NUM_MONITOR_SLOTS        {1} \
        CONFIG.C_NUM_OF_PROBES            {15} \
        CONFIG.C_PROBE0_MU_CNT            {1} \
        CONFIG.C_PROBE0_TYPE              {0} \
        CONFIG.C_PROBE0_WIDTH             {4} \
        CONFIG.C_PROBE1_MU_CNT            {1} \
        CONFIG.C_PROBE1_TYPE              {0} \
        CONFIG.C_PROBE1_WIDTH             {4} \
        CONFIG.C_PROBE2_MU_CNT            {1} \
        CONFIG.C_PROBE2_TYPE              {0} \
        CONFIG.C_PROBE2_WIDTH             {4} \
        CONFIG.C_PROBE3_MU_CNT            {1} \
        CONFIG.C_PROBE3_TYPE              {0} \
        CONFIG.C_PROBE3_WIDTH             {4} \
        CONFIG.C_PROBE4_MU_CNT            {1} \
        CONFIG.C_PROBE4_TYPE              {0} \
        CONFIG.C_PROBE4_WIDTH             {4} \
        CONFIG.C_PROBE5_MU_CNT            {1} \
        CONFIG.C_PROBE5_TYPE              {0} \
        CONFIG.C_PROBE5_WIDTH             {4} \
        CONFIG.C_PROBE6_MU_CNT            {1} \
        CONFIG.C_PROBE6_TYPE              {0} \
        CONFIG.C_PROBE6_WIDTH             {4} \
        CONFIG.C_PROBE7_MU_CNT            {1} \
        CONFIG.C_PROBE7_TYPE              {0} \
        CONFIG.C_PROBE7_WIDTH             {4} \
        CONFIG.C_PROBE8_MU_CNT            {1} \
        CONFIG.C_PROBE8_TYPE              {0} \
        CONFIG.C_PROBE8_WIDTH             {4} \
        CONFIG.C_PROBE9_MU_CNT            {1} \
        CONFIG.C_PROBE9_TYPE              {0} \
        CONFIG.C_PROBE9_WIDTH             {4} \
        CONFIG.C_PROBE10_MU_CNT           {1} \
        CONFIG.C_PROBE10_TYPE             {0} \
        CONFIG.C_PROBE10_WIDTH            {32} \
        CONFIG.C_PROBE11_MU_CNT           {1} \
        CONFIG.C_PROBE11_TYPE             {0} \
        CONFIG.C_PROBE11_WIDTH            {32} \
        CONFIG.C_PROBE12_MU_CNT           {1} \
        CONFIG.C_PROBE12_TYPE             {0} \
        CONFIG.C_PROBE12_WIDTH            {32} \
        CONFIG.C_PROBE13_MU_CNT           {1} \
        CONFIG.C_PROBE13_TYPE             {0} \
        CONFIG.C_PROBE13_WIDTH            {32} \
        CONFIG.C_PROBE14_MU_CNT           {1} \
        CONFIG.C_PROBE14_TYPE             {1} \
        CONFIG.C_PROBE14_WIDTH            {512} \
        CONFIG.C_SLOT_0_AXIS_TDATA_WIDTH  {32} \
        CONFIG.C_SLOT_0_AXIS_TDEST_WIDTH  {1} \
        CONFIG.C_SLOT_0_AXIS_TID_WIDTH    {1} \
        CONFIG.C_SLOT_0_AXIS_TUSER_WIDTH  {1} \
        CONFIG.C_SLOT_0_AXI_ADDR_WIDTH    {32} \
        CONFIG.C_SLOT_0_AXI_ARUSER_WIDTH  {1} \
        CONFIG.C_SLOT_0_AXI_AWUSER_WIDTH  {1} \
        CONFIG.C_SLOT_0_AXI_BUSER_WIDTH   {1} \
        CONFIG.C_SLOT_0_AXI_DATA_WIDTH    {32} \
        CONFIG.C_SLOT_0_AXI_ID_WIDTH      {1} \
        CONFIG.C_SLOT_0_AXI_PROTOCOL      {AXI4} \
        CONFIG.C_SLOT_0_AXI_RUSER_WIDTH   {1} \
        CONFIG.C_SLOT_0_AXI_WUSER_WIDTH   {1} \
        CONFIG.C_TIME_TAG_WIDTH           {32} \
        CONFIG.C_TRIGIN_EN                {false} \
        CONFIG.C_TRIGOUT_EN               {false} \
        CONFIG.C_XLNX_HW_PROBE_INFO       {DEFAULT} \
        CONFIG.EN_BRAM_DRC                {TRUE} \
        CONFIG.SIGNAL_CLOCK.FREQ_HZ       {100000000} \
        CONFIG.SIGNAL_CLOCK.INSERT_VIP    {0} \
        CONFIG.SLOT_0_AXI.INSERT_VIP      {0} \
        CONFIG.SLOT_0_AXIS.INSERT_VIP     {0} \
    ] [get_ips ila_pipeline]
    """)
}

object DBDecode {
    val FU_CODE_OFFSET      = 48
    val BRANCH_OFFSET       = 62
    val DDR_OFFSET          = 63
    val INFO_OFFSET         = 61
    val MEM_OFFSET          = 60
    val BW_OFFSET           = 59
    val DEC_RS1             = 0
    val DEC_RS2             = 4
    val DEC_IMD1            = 4
    val DEC_IMD2            = 0
    val DEC_IMD3            = 24
    val DEC_RT              = 20
    val DEC_WO              = 20
    val DEC_JUMP_OFFSET     = 0
    val DEC_SLEEP_OFFSET    = 0
    val DEC_BR_TGT_OFFSET   = 8
    val SR_OFFSET           = 56
    val DDR_CODE_OFFSET     = 12
    val DEC_CAR             = 4
    val DEC_BAR             = 0
    val DEC_RAR             = 4
    val DEC_INC_BAR         = 10
    val DEC_PRE_ALL         = 11
    val DEC_INC_RAR         = 11
    val DEC_INC_CAR         = 11
    val DEC_AP              = 9
    val DEC_BL4             = 8
    val ADD                 = 0.U
    val ADDI                = 1.U
    val SUB                 = 2.U
    val SUBI                = 3.U
    val MV                  = 4.U
    val SRC                 = 5.U
    val LI                  = 6.U
    val LDWD                = 7.U
    val LDPC                = 8.U
    val SRE                 = 0.U     
    val SRX                 = 1.U   
    val BL                  = 0.U
    val BEQ                 = 1.U
    val JUMP                = 2.U
    val SLEEP               = 3.U
    val LD                  = 0.U
    val ST                  = 1.U
    val AND                 = 0.U
    val OR                  = 1.U
    val XOR                 = 2.U
    val WRITE               = 0.U
    val READ                = 1.U
    val PRE                 = 2.U
    val ACT                 = 3.U
    val ZQ                  = 4.U
    val REF                 = 5.U
    val NOP                 = 7.U
}

// HARDWARE EMULATION of DramBender and Main Memory
// It is not meant to be cycle accurate
class DRAMBenderSim()(implicit val p: DRAMBenderParams) extends Module
    with HasDRAMBenderBlackBoxIO {

    io.c0_ddr4_act_n := 0.U
    io.c0_ddr4_adr := 0.U
    io.c0_ddr4_ba := 0.U
    io.c0_ddr4_bg := 0.U
    io.c0_ddr4_cke := 0.U
    io.c0_ddr4_odt := 0.U
    io.c0_ddr4_cs_n := 0.U
    io.c0_ddr4_reset_n := 0.U
    io.c0_ddr4_parity := 0.U

    if (p.isSynthesis) {
        val obufds = Module(new OBUFDS)
        obufds.io.I := false.B
        io.c0_ddr4_ck_t := obufds.io.O
        io.c0_ddr4_ck_c := obufds.io.OB
    }
    else {
        io.c0_ddr4_ck_t := 0.U
        io.c0_ddr4_ck_c := 0.U
    }

    io.inst_ready_o := false.B
    io.db_init_calib_complete_o := true.B
    io.db_last_o := false.B
    io.db_valid_o := false.B
    io.db_data_o := 0.U(p.rdDataWidth.W)
    io.bender_done_o := false.B

    val cpu_idle :: cpu_execute :: cpu_rdback :: Nil = Enum(3)

    val NUM_REGS = 32
    val regFile = RegInit(VecInit(Seq.fill(NUM_REGS)(0.U(32.W))))
    val cpuState = RegInit(cpu_idle)

    val wideReg = RegInit(VecInit(Seq.fill(16)(0.U(32.W))))

    val INST_QUEUE_DEPTH = 256 
    val instQueue = Module(new Queue(UInt(72.W), INST_QUEUE_DEPTH))
    instQueue.io.deq.ready := false.B
    instQueue.io.enq.valid := false.B
    instQueue.io.enq.bits := 0.U
    
    val RDBACK_QUEUE_DEPTH = 16
    val sendValid = RegInit(false.B)
    val sendIdx = RegInit(0.U(1.W))
    val sendBuffer = RegInit(VecInit(Seq.fill(2)(0.U(256.W))))
    val rdBackQueue = Module(new Queue(UInt(512.W), RDBACK_QUEUE_DEPTH))
    rdBackQueue.io.deq.ready := false.B
    rdBackQueue.io.enq.valid := false.B
    rdBackQueue.io.enq.bits := 0.U

    val NUM_BANKS = 4
    val activeRowIdx = RegInit(VecInit(Seq.fill(NUM_BANKS)(0.U(32.W))))
    val activeRowStat = RegInit(VecInit(Seq.fill(NUM_BANKS)(false.B)))
    val lastRefCounter = RegInit(0.U(32.W))
    val totalRefCounter = RegInit(0.U(32.W))
    dontTouch(lastRefCounter)
    dontTouch(totalRefCounter)

    def bitSelect(data: UInt, start: Int, length: Int) = {
        data(start + length - 1, start)
    }

    val BANK_WIDTH = 2
    val COL_WIDTH = 5
    val ROW_WIDTH = 9
    val ADDR_WIDTH = COL_WIDTH + ROW_WIDTH + BANK_WIDTH
    val dram = Module(new BramDualPort(512, scala.math.pow(2, ADDR_WIDTH).toInt))
    val dramRdCmd = RegInit(false.B)

    dram.io.clk_i := clock
    dram.io.p0_data_i := wideReg.asUInt
    dram.io.p0_addr_i := 0.U
    dram.io.p0_mask_i := Cat(Seq.fill(64)(true.B)).asUInt
    dram.io.p0_wr_en_i := false.B
    dram.io.p0_cmd_en_i := true.B

    dram.io.p1_data_i := 0.U
    dram.io.p1_addr_i := 0.U
    dram.io.p1_mask_i := Cat(Seq.fill(64)(true.B)).asUInt
    dram.io.p1_wr_en_i := false.B
    dram.io.p1_cmd_en_i := false.B

    io.bypass_rd_data_o := dram.io.p1_data_o
    io.bypass_rd_valid_o := RegNext(dram.io.p1_cmd_en_i && !dram.io.p1_wr_en_i)

    rdBackQueue.io.enq.bits := dram.io.p0_data_o
    rdBackQueue.io.enq.valid := dramRdCmd

    dramRdCmd := false.B

    def dramToPhys(bank: UInt, row: UInt, col: UInt) = {
        val adjustedCol = col >> 6 // Each row is 512 bits
        (bank << (ROW_WIDTH + COL_WIDTH) | row(ROW_WIDTH-1, 0) << COL_WIDTH | adjustedCol(COL_WIDTH-1, 0))
    }

    def execNormal(inst: UInt) = {
        val opcode = bitSelect(inst, DBDecode.FU_CODE_OFFSET, 8)
        switch (opcode) {
            is (DBDecode.LI) {
                val index = bitSelect(inst, DBDecode.DEC_RT, 4)
                val imd0 = bitSelect(inst, DBDecode.DEC_IMD1, 16)
                val imd1 = bitSelect(inst, DBDecode.DEC_IMD3, 16)
                regFile(index) := Cat(imd1, imd0)
            }
            is (DBDecode.LDWD) {
                val index = bitSelect(inst, DBDecode.DEC_WO, 4)
                val rs1 = bitSelect(inst, DBDecode.DEC_RS1, 4)
                wideReg(index) := regFile(rs1)
            }
        }
    }

    def execDdr(inst: UInt) = {
        val opcode = bitSelect(inst, DBDecode.DDR_CODE_OFFSET, 3)
        switch(opcode) {
            is (DBDecode.ACT) {
                val incBAR = bitSelect(inst, DBDecode.DEC_INC_BAR, 1)
                val incRAR = bitSelect(inst, DBDecode.DEC_INC_RAR, 1)
                val BAR = bitSelect(inst, DBDecode.DEC_BAR, 4)
                val RAR = bitSelect(inst, DBDecode.DEC_RAR, 4)
                val bank = regFile(BAR)
                val row = regFile(RAR)
                activeRowIdx(bank) := row
                activeRowStat(bank) := true.B
                when (incBAR.asBool) { regFile(BAR) := regFile(BAR) + 1.U }
                when (incRAR.asBool) { regFile(RAR) := regFile(RAR) + 1.U }
            }
            is (DBDecode.PRE) {
                val preAll = bitSelect(inst, DBDecode.DEC_PRE_ALL, 1)
                val incBAR = bitSelect(inst, DBDecode.DEC_INC_BAR, 1)
                val BAR = bitSelect(inst, DBDecode.DEC_BAR, 4)
                val bank = regFile(BAR)
                activeRowStat(bank) := false.B
                when (incBAR.asBool) { regFile(BAR) := regFile(BAR) + 1.U }
                when (preAll.asBool) {
                    for (i <- 0 until NUM_BANKS) {
                        activeRowStat(i) := false.B
                    }
                }
            }
            is (DBDecode.READ) {
                val incBAR = bitSelect(inst, DBDecode.DEC_INC_BAR, 1)
                val incCAR = bitSelect(inst, DBDecode.DEC_INC_CAR, 1)
                val doAP = bitSelect(inst, DBDecode.DEC_AP, 1)
                val BAR = bitSelect(inst, DBDecode.DEC_BAR, 4)
                val CAR = bitSelect(inst, DBDecode.DEC_CAR, 4)
                val bank = regFile(BAR)
                val col = regFile(CAR)
                when (activeRowStat(bank)) {
                    dram.io.p0_addr_i := dramToPhys(bank, activeRowIdx(bank), col)
                    dramRdCmd := true.B
                }
                when (doAP.asBool) { activeRowStat(bank) := false.B }
                when (incBAR.asBool) { regFile(BAR) := regFile(BAR) + 1.U }
                when (incCAR.asBool) { regFile(CAR) := regFile(CAR) + 1.U }
            }
            is (DBDecode.WRITE) {
                val incBAR = bitSelect(inst, DBDecode.DEC_INC_BAR, 1)
                val incCAR = bitSelect(inst, DBDecode.DEC_INC_CAR, 1)
                val doAP = bitSelect(inst, DBDecode.DEC_AP, 1)
                val BAR = bitSelect(inst, DBDecode.DEC_BAR, 4)
                val CAR = bitSelect(inst, DBDecode.DEC_CAR, 4)
                val bank = regFile(BAR)
                val col = regFile(CAR)
                when (activeRowStat(bank)) {
                    dram.io.p0_addr_i := dramToPhys(bank, activeRowIdx(bank), col)
                    dram.io.p0_wr_en_i := true.B
                }
                when (doAP.asBool) { activeRowStat(bank) := false.B }
                when (incBAR.asBool) { regFile(BAR) := regFile(BAR) + 1.U }
                when (incCAR.asBool) { regFile(CAR) := regFile(CAR) + 1.U }
            }
            is (DBDecode.REF) {
                lastRefCounter := 0.U
                totalRefCounter := totalRefCounter + 1.U
            }
        }
    }

    val INST_WIDTH = 64
    val DDR_INST_WIDTH = 16
    lastRefCounter := lastRefCounter + 1.U
    switch(cpuState) {
        is (cpu_idle) {
            io.inst_ready_o := true.B
            instQueue.io.enq.valid := io.inst_valid_i
            instQueue.io.enq.bits := io.inst_data_i
            when (io.inst_valid_i && io.inst_ready_o && io.inst_last_i) {
                for (i <- 0 until NUM_REGS) {
                    regFile(i) := 0.U
                }
                cpuState := cpu_execute
            }
            dram.io.p1_addr_i := (io.bypass_addr_i >> 6.U)(ADDR_WIDTH-1, 0)
            dram.io.p1_data_i := io.bypass_wr_data_i
            dram.io.p1_wr_en_i := io.bypass_wr_en_i
            dram.io.p1_cmd_en_i := true.B
        }
        is (cpu_execute) {
            instQueue.io.deq.ready := true.B
            val inst = instQueue.io.deq.bits(71, 0)
            when (bitSelect(inst, DBDecode.DDR_OFFSET, 1).asBool) {
                for (i <- 0 until 4) {
                    execDdr(inst(i * DDR_INST_WIDTH + DDR_INST_WIDTH - 1, i * DDR_INST_WIDTH))
                }
            }
            .otherwise {
                execNormal(inst(INST_WIDTH - 1, 0))
            }
            when (!instQueue.io.deq.valid) {
                sendValid := false.B
                sendIdx := 0.U
                cpuState := cpu_rdback
            }
        }
        is (cpu_rdback) {
            sendIdx := Mux(io.db_ready_i && io.db_valid_o, sendIdx + 1.U, sendIdx)
            sendValid := Mux(io.db_ready_i && io.db_valid_o, sendIdx === 0.U, sendValid)
            io.db_data_o := sendBuffer(sendIdx)
            io.db_valid_o := sendValid
            when (!sendValid || (sendIdx === 1.U && io.db_ready_i)) {
                rdBackQueue.io.deq.ready := true.B
                sendValid := rdBackQueue.io.deq.valid
                sendBuffer(1) := rdBackQueue.io.deq.bits(511, 256)
                sendBuffer(0) := rdBackQueue.io.deq.bits(255, 0)
            }
            when (!sendValid && !rdBackQueue.io.deq.valid) {
                cpuState := cpu_idle
                io.bender_done_o := true.B
                // DRAM Bender sends 1 dummy beat with last asserted at the end
                io.db_valid_o := true.B
                io.db_last_o := true.B
            }
        }
    }
}