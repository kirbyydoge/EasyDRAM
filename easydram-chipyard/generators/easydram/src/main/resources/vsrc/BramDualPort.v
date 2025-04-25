`timescale 1ns/1ps

module BramDualPort #(
	parameter DATA_WIDTH = 32,
	parameter BRAM_DEPTH = 128,
	parameter INIT_FILE = "",
	parameter HARDCORE_DEBUG = "FALSE",

	localparam ADDR_WIDTH = $clog2(BRAM_DEPTH)
)(
	input 						clk_i,
	
	input	[DATA_WIDTH-1:0]	p0_data_i,
	input	[ADDR_WIDTH-1:0]	p0_addr_i,
	input	[DATA_WIDTH/8-1:0]	p0_mask_i,
	input						p0_wr_en_i,
	input						p0_cmd_en_i,
	output	[DATA_WIDTH-1:0]	p0_data_o,
	
	input	[DATA_WIDTH-1:0]	p1_data_i,
	input	[ADDR_WIDTH-1:0]	p1_addr_i,
	input	[DATA_WIDTH/8-1:0]	p1_mask_i,
	input						p1_wr_en_i,
	input						p1_cmd_en_i,
	output	[DATA_WIDTH-1:0]	p1_data_o
);

wire [DATA_WIDTH-1:0] UNDEFINED;
generate
if (HARDCORE_DEBUG == "TRUE") begin
	assign UNDEFINED = {DATA_WIDTH{1'bZ}};
end
else begin
	assign UNDEFINED = {DATA_WIDTH{1'b0}};
end
endgenerate

reg [DATA_WIDTH-1:0] mem_r [0:BRAM_DEPTH-1];
reg [DATA_WIDTH-1:0] p0_data_r;
reg [DATA_WIDTH-1:0] p1_data_r;

integer i;

initial begin
	if (INIT_FILE != "") begin
		$readmemh(INIT_FILE, mem_r);
	end
end

always @(posedge clk_i) begin
	if (p0_cmd_en_i) begin
		if (p0_wr_en_i) begin
			for (i = 0; i < DATA_WIDTH/8; i = i + 1) begin
				if (p0_mask_i[i]) begin
					mem_r[p0_addr_i][i * 8 +: 8] <= p0_data_i[i * 8 +: 8];
				end
			end
			if (HARDCORE_DEBUG == "TRUE") begin
				p0_data_r <= UNDEFINED;
			end
		end
		else begin
			p0_data_r <= mem_r[p0_addr_i];
		end
	end
end

always @(posedge clk_i) begin
	if (p1_cmd_en_i) begin
		if (p1_wr_en_i) begin
			for (i = 0; i < DATA_WIDTH/8; i = i + 1) begin
				if (p1_mask_i[i]) begin
					mem_r[p1_addr_i][i * 8 +: 8] <= p1_data_i[i * 8 +: 8];
				end
			end
			if (HARDCORE_DEBUG == "TRUE") begin
				p1_data_r <= UNDEFINED;
			end
		end
		else begin
			p1_data_r <= mem_r[p1_addr_i];
		end
	end
end

assign p0_data_o = p0_data_r;
assign p1_data_o = p1_data_r;

endmodule
