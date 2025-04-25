`include "parameters.vh"
`include "project.vh"

module DRAMBender #(
  parameter tCK = 1500, SIM = "false"
)(
  // common signals
  input c0_sys_clk_p,
  input c0_sys_clk_n,
  input sys_rst,
  
  // iob <> ddr4 sdram ip signals
  output                        c0_ddr4_act_n,
  output [`ROW_ADDR_WIDTH-1:0]  c0_ddr4_adr,
  output [1:0]                  c0_ddr4_ba,
  output                        c0_ddr4_bg,
  output [`CKE_WIDTH-1:0]       c0_ddr4_cke,
  output [`ODT_WIDTH-1:0]       c0_ddr4_odt,
  output [`CS_WIDTH-1:0]        c0_ddr4_cs_n,
  output [`CK_WIDTH-1:0]        c0_ddr4_ck_t,
  output [`CK_WIDTH-1:0]        c0_ddr4_ck_c,
  output                        c0_ddr4_reset_n,

  inout  [7:0]                  c0_ddr4_dqs_c,
  inout  [7:0]                  c0_ddr4_dqs_t,
  inout  [63:0]                 c0_ddr4_dq,
  inout  [7:0]                  c0_ddr4_dm_dbi_n,  
  output                        c0_ddr4_parity,
  // chipyard signals
  input                         batch_clk_i,
  input                         batch_rstn_i,

  input  [255:0]                inst_data_i,
  input                         inst_valid_i,
  input                         inst_last_i,
  output                        inst_ready_o,

  output                        db_init_calib_complete_o,
  output [255:0]                db_data_o,
  output                        db_valid_o,
  output                        db_last_o,
  input                         db_ready_i,

  output                        bender_done_o
);
  
  assign c0_ddr4_odt[1] = 1'b0;
  assign c0_ddr4_cs_n[1] = 1'b1;
  assign c0_ddr4_cke[1] = 1'b0;
  // Frontend control signals
  wire softmc_fin;
  wire user_rst;

  // Frontend <-> Fetch signals
  wire [`IMEM_ADDR_WIDTH-1:0] fr_addr_in;
  wire                        fr_valid_in;
  wire [`INSTR_WIDTH-1:0]     fr_data_out;
  wire                        fr_valid_out;
  wire [`IMEM_ADDR_WIDTH-1:0] fr_addr_out;
  wire                        fr_ready_out;

  // Frontend <-> misc. control signals
  wire                        per_rd_init;
  wire                        per_zq_init;
  wire                        per_ref_init;
  wire                        rbe_switch_mode;
  wire                        toggle_dll;  

  // AXI streaming ports
  wire [`XDMA_AXI_DATA_WIDTH-1:0]   m_axis_h2c_tdata_0;
  wire                              m_axis_h2c_tlast_0;
  wire                              m_axis_h2c_tvalid_0;
  wire                              m_axis_h2c_tready_0;
  wire [`XDMA_AXI_DATA_WIDTH/8-1:0] m_axis_h2c_tkeep_0;
  wire [`XDMA_AXI_DATA_WIDTH-1:0]   s_axis_c2h_tdata_0; 
  wire                              s_axis_c2h_tlast_0;
  wire                              s_axis_c2h_tvalid_0;
  wire                              s_axis_c2h_tready_0;
  wire [`XDMA_AXI_DATA_WIDTH/8-1:0] s_axis_c2h_tkeep_0;
 
  // ddr_pipeline <-> outer module if
  wire [3:0]                ddr_write;
  wire [3:0]                ddr_read;
  wire [3:0]                ddr_pre;
  wire [3:0]                ddr_act;
  wire [3:0]                ddr_ref;
  wire [3:0]                ddr_zq;
  wire [3:0]                ddr_nop;
  wire [3:0]                ddr_ap;
  wire [3:0]                ddr_pall;
  wire [3:0]                ddr_half_bl;
  wire [4*`BG_WIDTH-1:0]    ddr_bg;
  wire [4*`BANK_WIDTH-1:0]  ddr_bank;
  wire [4*`COL_WIDTH-1:0]   ddr_col;
  wire [4*`ROW_WIDTH-1:0]   ddr_row;
  wire [511:0]              ddr_wdata;
 
  // periodic maintenance signals
  wire                      ddr_maint_read;
 
  // phy <-> ddr adapter and xdma app signals
  // dlltoggler
  wire                      clk_sel = 0;
  wire [7:0]                dllt_mc_ACT_n;
  wire [135:0]              dllt_mc_ADR;
  wire [15:0]               dllt_mc_BA;
  wire [15:0]               dllt_mc_BG;
  wire [7:0]                dllt_mc_CKE;
  wire [7:0]                dllt_mc_CS_n;
  wire                      dllt_done;
  // ddr adapter 
  wire [4:0]                dBufAdr;
  wire [`DQ_WIDTH*8-1:0]    wrData;
  wire [`DQ_WIDTH-1:0]      wrDataMask;
  wire [511:0]              rdData;
  wire [4:0]                rdDataAddr;
  wire [0:0]                rdDataEn;
  wire [0:0]                rdDataEnd;
  wire [0:0]                per_rd_done;
  wire [0:0]                rmw_rd_done;
  wire [4:0]                wrDataAddr;
  wire [0:0]                wrDataEn;
  wire [7:0]                mc_ACT_n;
  wire [135:0]              mc_ADR;
  wire [15:0]               mc_BA;
  wire [15:0]               mc_BG;
  wire [`CKE_WIDTH*8-1:0]   mc_CKE;
  wire [`CS_WIDTH*8-1:0]    mc_CS_n;
  wire [`ODT_WIDTH*8-1:0]   mc_ODT;
  wire [0:0]                mcRdCAS;
  wire [0:0]                mcWrCAS;
  wire [0:0]                winInjTxn;
  wire [0:0]                winRmw;
  wire [4:0]                winBuf;
  wire [1:0]                winRank;
  wire [5:0]                tCWL;
  wire                      dbg_clk;
  wire                      c0_wr_rd_complete;
  wire                      c0_ddr4_clk;
  wire                      c0_ddr4_dll_off_clk;
  wire                      ddr4_ui_clk;
  wire                      c0_ddr4_rst;
  wire [511:0]              dbg_bus;        
  wire [1:0]                mcCasSlot;
  wire                      mcCasSlot2;
  wire                      gt_data_ready;
  
  wire         read_seq_incoming; // next few instructions will read from DRAM
  wire [11:0]  incoming_reads;    // how many reads next few instructions will issue
  wire [11:0]  buffer_space;      // remaining buffer size
 
  wire c0_init_calib_complete;
  
  // There is a possibility that these signals are on
  // the critical path as observed in 
  // the previous iteration of SoftMC
  reg c0_init_calib_complete_r, sys_rst_r;
  wire iq_full, processing_iseq, rdback_fifo_empty;

  // ila_pipeline debug_pipeline (
  //   .clk(c0_ddr4_clk),
  //   .probe0(ddr_write),
  //   .probe1(ddr_read),
  //   .probe2(ddr_pre),
  //   .probe3(ddr_act),
  //   .probe4(ddr_ref),
  //   .probe5(ddr_zq),
  //   .probe6(ddr_nop),
  //   .probe7(ddr_ap),
  //   .probe8(ddr_pall),
  //   .probe9(ddr_half_bl),
  //   .probe10(ddr_bg),
  //   .probe11(ddr_bank),
  //   .probe12(ddr_col),
  //   .probe13(ddr_row),
  //   .probe14(ddr_wdata)
  // );

  // ila_adapter debug_adapter (
  //   .clk(c0_ddr4_clk),
  //   .probe0(wrData),
  //   .probe1(wrDataMask),
  //   .probe2(rdData),
  //   .probe3(rdDataAddr),
  //   .probe4(rdDataEn),
  //   .probe5(rdDataEnd),
  //   .probe6(per_rd_done),
  //   .probe7(rmw_rd_done),
  //   .probe8(wrDataAddr),
  //   .probe9(wrDataEn),
  //   .probe10(mc_ACT_n),
  //   .probe11(mc_ADR),
  //   .probe12(mc_BA),
  //   .probe13(mc_BG),
  //   .probe14(mc_CKE),
  //   .probe15(mc_CS_n),
  //   .probe16(mc_ODT),
  //   .probe17(mcRdCAS),
  //   .probe18(mcWrCAS),
  //   .probe19(c0_init_calib_complete_r),
  //   .probe20(sys_rst)
  // );
  
  reg softmc_end_r;

  always @(posedge c0_ddr4_clk) begin
    c0_init_calib_complete_r <= c0_init_calib_complete;
    sys_rst_r <= sys_rst;  
    softmc_end_r <= (softmc_end_r || softmc_fin) && !(c0_ddr4_rst || user_rst); 
  end
  
  assign db_init_calib_complete_o = c0_init_calib_complete_r;
  assign bender_done_o = softmc_end_r;

  reg dllt_active = 1'b0;
  
  `ifdef ENABLE_DLL_TOGGLER
  always @(posedge c0_ddr4_clk) begin
    if(toggle_dll) begin
      dllt_active <= ~dllt_active;
    end
    if(dllt_done) begin
      dllt_active <= ~dllt_active;
    end
  end
  `endif

   phy_ddr4_comp phy_ddr4_i (
    .sys_rst                  (sys_rst),
    .c0_sys_clk_p             (c0_sys_clk_p),
    .c0_sys_clk_n             (c0_sys_clk_n),
  
    `ifdef ENABLE_DLL_TOGGLER
    .c0_ddr4_ui_clk           (ddr4_ui_clk),
    .addn_ui_clkout1          (c0_ddr4_dll_off_clk),
    `else
    .c0_ddr4_ui_clk           (c0_ddr4_clk),
    `endif
    .c0_ddr4_ui_clk_sync_rst  (c0_ddr4_rst),
    .c0_init_calib_complete   (c0_init_calib_complete),
    .dbg_clk                  (dbg_clk),
    .c0_ddr4_act_n            (c0_ddr4_act_n),
    .c0_ddr4_adr              (c0_ddr4_adr),
    .c0_ddr4_ba               (c0_ddr4_ba),
    .c0_ddr4_bg               (c0_ddr4_bg),
    .c0_ddr4_cke              (c0_ddr4_cke),
    .c0_ddr4_odt              (c0_ddr4_odt),
    .c0_ddr4_cs_n             (c0_ddr4_cs_n),
    .c0_ddr4_ck_t             (c0_ddr4_ck_t),
    .c0_ddr4_ck_c             (c0_ddr4_ck_c),
    .c0_ddr4_reset_n          (c0_ddr4_reset_n),
    .c0_ddr4_dq               (c0_ddr4_dq),
    .c0_ddr4_dqs_c            (c0_ddr4_dqs_c),
    .c0_ddr4_dqs_t            (c0_ddr4_dqs_t),
    .c0_ddr4_dm_dbi_n         (c0_ddr4_dm_dbi_n),
  
    .dBufAdr                  (dBufAdr),
    .wrData                   (wrData),
    .rdData                   (rdData),
    .rdDataAddr               (rdDataAddr),
    .rdDataEn                 (rdDataEn),
    .rdDataEnd                (rdDataEnd),
    .per_rd_done              (per_rd_done),
    .rmw_rd_done              (rmw_rd_done),
    .wrDataAddr               (wrDataAddr),
    .wrDataEn                 (wrDataEn),
    .wrDataMask               (wrDataMask),
    
    .mc_ACT_n                 (dllt_active ? dllt_mc_ACT_n : mc_ACT_n),
    .mc_ADR                   (dllt_active ? dllt_mc_ADR : mc_ADR),
    .mc_BA                    (dllt_active ? dllt_mc_BA : mc_BA),
    .mc_BG                    (dllt_active ? dllt_mc_BG : mc_BG),
    // DRAM CKE. 8 bits for each DRAM pin. The mc_CKE signal is always set to '1'.
    .mc_CKE                   (dllt_active ? dllt_mc_CKE : {8{1'b1}}),
    .mc_CS_n                  (dllt_active ? dllt_mc_CS_n : mc_CS_n),
    .mc_ODT                   (mc_ODT),
    // CAS command slot select. Slot0 is enabled for example design.
    .mcCasSlot                (dllt_active ? 0 : mcCasSlot),
    // CAS slot 2 select.  mcCasSlot2 serves a similar purpose as the mcCasSlot[1:0] signal, but mcCasSlot2 is used in timing 
    // critical logic in the Phy. Slot0 is enabled for example design.
    .mcCasSlot2               (dllt_active ? 0 : mcCasSlot2),
    .mcRdCAS                  (dllt_active ? 0 : mcRdCAS),
    .mcWrCAS                  (dllt_active ? 0 : mcWrCAS),
    // Optional read command type indication. The winInjTxn signal is set to '0' for example design.
    .winInjTxn                ({1{1'b0}}),
    // Optional read command type indication. The winRmw signal is set to '0' for example design.
    .winRmw                   ({1{1'b0}}),
    // Update VT Tracking. The gt_data_ready signal is set to '0' in this example design.
    // This signal must be asserted periodically to keep the DQS Gate aligned as voltage and temperature drift.
    // For more information, Refer to PG150 document.
    .gt_data_ready            (gt_data_ready),
    .winBuf                   (winBuf),
    .winRank                  (winRank),
    .tCWL                     (tCWL),
    // Debug Port
    .dbg_bus                  (dbg_bus)                                    
  );
  
  softmc_pipeline pipeline(
    .clk(c0_ddr4_clk),
    .rst(c0_ddr4_rst || user_rst),
   
    .softmc_end(softmc_fin),
    .read_size(incoming_reads),
    .read_seq_incoming(read_seq_incoming),
    .buffer_space(buffer_space),
    
    .addr_out(fr_addr_in),
    .valid_out(fr_valid_in),
    .data_in(fr_data_out),
    .valid_in(fr_valid_out),
    .addr_in(fr_addr_out),
    .ready_out(fr_ready_out),
   
    .ddr_write(ddr_write),  
    .ddr_read(ddr_read),  
    .ddr_pre(ddr_pre),    
    .ddr_act(ddr_act),    
    .ddr_ref(ddr_ref),    
    .ddr_zq(ddr_zq),    
    .ddr_nop(ddr_nop),    
    .ddr_ap(ddr_ap),    
    .ddr_pall(ddr_pall),    
    .ddr_half_bl(ddr_half_bl),
    .ddr_bg(ddr_bg),    
    .ddr_bank(ddr_bank),  
    .ddr_col(ddr_col),    
    .ddr_row(ddr_row),    
    .ddr_wdata(ddr_wdata)
  );
 
  `ifdef ENABLE_DLL_TOGGLER
  //BUFGMUX:GeneralClockMuxBuffer
  //UltraScale
  //XilinxHDLLibrariesGuide, version2014.4
  BUFGMUX#(.CLK_SEL_TYPE("SYNC")  //ASYNC,SYNC
  )BUFGMUX_inst(
    .O(c0_ddr4_clk),        //1-bitoutput:Clockoutput
    .I0(ddr4_ui_clk),    //1-bitinput:Clockinput(S=0)
    .I1(c0_ddr4_dll_off_clk),   //1-bitinput:Clockinput(S=1)
    .S(clk_sel)            //1-bitinput:Clockselect
    );
    //End of BUFGMUX_inst instantiation
  `endif
 
 
  wire frontend_ready;
  
  softmc_frontend #(.SIM_MEM(SIM)) frontend(
    .clk(c0_ddr4_clk),
    .rst(c0_ddr4_rst),
    
    .init_calib_complete(c0_init_calib_complete_r),
    .softmc_fin(softmc_fin),
    .user_rst(user_rst),
   
    .dllt_begin(toggle_dll),
   
    // indicates read_back unit is ready for the next iseq
    .frontend_ready(frontend_ready),
   
    // frontend <-> fetch stage if
    .addr_in(fr_addr_in),
    .valid_in(fr_valid_in),
    .data_out(fr_data_out),
    .valid_out(fr_valid_out),
    .addr_out(fr_addr_out),
    .ready_in(fr_ready_out),
    
    // frontend <-> xdma interface
    .h2c_tdata_0(m_axis_h2c_tdata_0),
    .h2c_tlast_0(m_axis_h2c_tlast_0),
    .h2c_tvalid_0(m_axis_h2c_tvalid_0),
    .h2c_tready_0(m_axis_h2c_tready_0),
    .h2c_tkeep_0(m_axis_h2c_tkeep_0),
    
    .per_rd_init(per_rd_init),
    .per_zq_init(per_zq_init),
    .per_ref_init(per_ref_init),
    .rbe_switch_mode(rbe_switch_mode)
  );

  ddr4_adapter#(
    .DQ_WIDTH(`DQ_WIDTH)
  ) ddr4_adapter
  (
   .clk(c0_ddr4_clk),
   .rst(c0_ddr4_rst || user_rst), 
   .init_calib_complete(c0_init_calib_complete_r),
   //.io_config_strobe,
   //.io_config,
   .dBufAdr(dBufAdr),   // Reserved. Should be tied low.
   .wrData(wrData),    // DRAM write data. There are 8 bits for each DQ lane on the DRAM bus.
   .wrDataMask(wrDataMask),// DRAM write DM/DBI port.There is one bit for each byte of the wrData port.
   .wrDataEn(wrDataEn),  // Write data Enable. The Phy will assert this port for one cycle for each write CAS command.
   .mc_ACT_n(mc_ACT_n),  // DRAM ACT_n command signal for four DRAM clock cycles.
   .mc_ADR(mc_ADR),    // DRAM address. There are 8 bits in the fabric interface for each address bit on the DRAM bus.
   .mc_BA(mc_BA),     // DRAM bank address. 8 bits for each DRAM bank address.
   .mc_BG(mc_BG),     // DRAM bank group address.
   .mc_CS_n(mc_CS_n),   // DRAM CS_n
   //.mc_ODT(mc_ODT),    // DRAM ODT
   .mcRdCAS(mcRdCAS),   // Read CAS command issued.
   .mcWrCAS(mcWrCAS),   // Write CAS command issued.
   .winRank(winRank),   // Target rank for CAS commands. This value indicates which rank a CAS command is issued to.
   .winBuf(winBuf),    // Optional control signal. When either mcRdCAS or mcWrCAS is asserted, the Phy will store the value on the winBuf signal.  
   //.rdData(rdData),    // DRAM read data.
   .rdDataEn(rdDataEn),  // Read data valid. This signal asserts for one fabric cycle for each completed read operation.
   .rdDataEnd(rdDataEnd),  // Unused.  Tied high.
   .mcCasSlot(mcCasSlot),
   .mcCasSlot2(mcCasSlot2),
   .gt_data_ready(gt_data_ready),
   .ddr_write(ddr_write),  
   .ddr_read(ddr_read),  
   .ddr_pre(ddr_pre),    
   .ddr_act(ddr_act),    
   .ddr_ref(ddr_ref),    
   .ddr_zq(ddr_zq),    
   .ddr_nop(ddr_nop),    
   .ddr_ap(ddr_ap),    
   .ddr_pall(ddr_pall),    
   .ddr_half_bl(ddr_half_bl),
   .ddr_bg(ddr_bg),    
   .ddr_bank(ddr_bank),  
   .ddr_col(ddr_col),    
   .ddr_row(ddr_row),    
   .ddr_wdata(ddr_wdata),
 
   .ddr_maint_read(per_rd_init)
  );
  
    localparam          ODTWRDEL    = 5'd9;
    localparam          ODTWRDUR    = 4'd6;
    localparam          ODTWRODEL   = 5'd9;
    localparam          ODTWRODUR   = 4'd6;
    localparam          ODTRDDEL    = 5'd10;
    localparam          ODTRDDUR    = 4'd6;
    localparam          ODTRDODEL   = 5'd9;
    localparam          ODTRDODUR   = 4'd6;
    localparam          ODTNOP      = 16'h0000;
    localparam          ODTWR       = 16'h0001;
    localparam          ODTRD       = 16'h0000; 
    
    wire tranSentC;
    assign tranSentC = mcRdCAS | mcWrCAS;
 
  //synthesis translate_on
  //*******************************************************************************
  ddr4_mc_odt # (
    .ODTWR     (ODTWR)
    ,.ODTWRDEL  (ODTWRDEL)
    ,.ODTWRDUR  (ODTWRDUR)
    ,.ODTWRODEL (ODTWRODEL)
    ,.ODTWRODUR (ODTWRODUR)
   
    ,.ODTRD     (ODTRD)
    ,.ODTRDDEL  (ODTRDDEL)
    ,.ODTRDDUR  (ODTRDDUR)
    ,.ODTRDODEL (ODTRDODEL)
    ,.ODTRDODUR (ODTRDODUR)
   
    ,.ODTNOP    (ODTNOP)
    ,.ODTBITS   (`ODT_WIDTH)
    ,.TCQ       (0.1)
  )u_ddr_tb_odt(
    .clk       (c0_ddr4_clk)
    ,.rst       (c0_ddr4_rst)
    ,.mc_ODT    (mc_ODT)
    ,.casSlot   (mcCasSlot)
    ,.casSlot2  (mcCasSlot2)
    ,.rank      (winRank)
    ,.winRead   (mcRdCAS)
    ,.winWrite  (mcWrCAS)
    ,.tranSentC (tranSentC)
  );

  // ila_instr_rb debug_sft0 (
  //     .clk        (c0_ddr4_clk),
  //     .probe0     (s_axis_c2h_tdata_0),
  //     .probe1     (s_axis_c2h_tvalid_0),
  //     .probe2     (s_axis_c2h_tready_0),
  //     .probe3     (s_axis_c2h_tlast_0),
  //     .probe4     (softmc_end_r)
  // );

  // ila_instr_rb debug_sft1 (
  //     .clk        (c0_ddr4_clk),
  //     .probe0     (m_axis_h2c_tdata_0),
  //     .probe1     (m_axis_h2c_tvalid_0),
  //     .probe2     (m_axis_h2c_tready_0),
  //     .probe3     (m_axis_h2c_tlast_0),
  //     .probe4     (c0_init_calib_complete_r)
  // );

  // ila_instr_rb debug_sft2 (
  //     .clk        (batch_clk_i),
  //     .probe0     (inst_data_i),
  //     .probe1     (inst_valid_i),
  //     .probe2     (inst_ready_o),
  //     .probe3     (inst_last_i),
  //     .probe4     (batch_rstn_i)
  // );

  // Clock converter for the c2h interface
  axis_clock_converter axis_clk_conv_i0
  (
    .s_axis_tvalid(s_axis_c2h_tvalid_0),
    .s_axis_tlast(s_axis_c2h_tlast_0),
    .s_axis_tdata(s_axis_c2h_tdata_0),
    .s_axis_tkeep(s_axis_c2h_tkeep_0),
    .s_axis_tready(s_axis_c2h_tready_0),
    .m_axis_tvalid(db_valid_o),
    .m_axis_tlast(db_last_o),
    .m_axis_tdata(db_data_o),
    .m_axis_tkeep(),
    .m_axis_tready(db_ready_i),
    .s_axis_aresetn(~c0_ddr4_rst),
    .s_axis_aclk(c0_ddr4_clk),
    .m_axis_aresetn(batch_rstn_i),
    .m_axis_aclk(batch_clk_i)
  );

  // Clock converter for the h2c interface
  axis_clock_converter axis_clk_conv_i1
  (
    .m_axis_tvalid(m_axis_h2c_tvalid_0),
    .m_axis_tlast(m_axis_h2c_tlast_0),
    .m_axis_tdata(m_axis_h2c_tdata_0),
    .m_axis_tkeep(m_axis_h2c_tkeep_0),
    .m_axis_tready(m_axis_h2c_tready_0),
    .s_axis_tvalid(inst_valid_i),
    .s_axis_tlast(inst_last_i),
    .s_axis_tdata(inst_data_i),
    .s_axis_tkeep(256'hFFFF_FFFF_FFFF_FFFF_FFFF_FFFF_FFFF_FFFF_FFFF_FFFF_FFFF_FFFF_FFFF_FFFF_FFFF_FFFF),
    .s_axis_tready(inst_ready_o),
    .m_axis_aresetn(~c0_ddr4_rst),
    .m_axis_aclk(c0_ddr4_clk),
    .s_axis_aresetn(batch_rstn_i),
    .s_axis_aclk(batch_clk_i)
  );
  
  readback_engine rbe(
    
  // common signals
  .clk(c0_ddr4_clk),
  .rst(c0_ddr4_rst || user_rst),
  
  // other ctrl signals
  .flush(frontend_ready),
  .switch_mode(rbe_switch_mode),
  .read_seq_incoming(read_seq_incoming), // next few instructions will read from DRAM
  .incoming_reads(incoming_reads),    // how many reads next few instructions will issue
  .buffer_space(buffer_space),      // remaining buffer size
  // DRAM <-> engine if
  .rd_data(rdData),
  .rd_valid(rdDataEn),
   
  // rbe <-> rf interface  
  .ddr_wdata(ddr_wdata),
  
  .per_rd_init(per_rd_init),
  .per_zq_init(per_zq_init),
  .per_ref_init(per_ref_init),
  
  // rbe <-> chipyard
  .c2h_tdata_0(s_axis_c2h_tdata_0),  
  .c2h_tlast_0(s_axis_c2h_tlast_0),
  .c2h_tvalid_0(s_axis_c2h_tvalid_0),
  .c2h_tready_0(s_axis_c2h_tready_0),
  .c2h_tkeep_0(s_axis_c2h_tkeep_0)
  
  );

  `ifdef ENABLE_DLL_TOGGLER
  dll_toggler dllt
   (
     .clk(c0_ddr4_clk),
     .rst(c0_ddr4_rst || user_rst || ~c0_init_calib_complete_r),
     .toggle_valid(toggle_dll),
     .mc_ACT_n(dllt_mc_ACT_n),  // DRAM ACT_n command signal for four DRAM clock cycles.
     .mc_ADR(dllt_mc_ADR),    // DRAM address. There are 8 bits in the fabric interface for each address bit on the DRAM bus.
     .mc_BA(dllt_mc_BA),     // DRAM bank address. 8 bits for each DRAM bank address.
     .mc_BG(dllt_mc_BG),     // DRAM bank group address.
     .mc_CS_n(dllt_mc_CS_n),   // DRAM CS_n    
     .mc_CKE(dllt_mc_CKE),
     .clk_sel(clk_sel),
     .dllt_done(dllt_done)
   );
   `endif
endmodule
