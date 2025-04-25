`timescale 1ns/1ps

module mul_test (
    input         clk_sys_p,
    input         clk_sys_n,
    input  [15:0] in0,
    input  [15:0] in1,
    input         sel,
    inout  [63:0] inout_test, 
    output [31:0] out
);

wire clk100;
wire locked;

vcu108clk wiz (
    .clk_in1_p(clk_sys_p),
    .clk_in1_n(clk_sys_n),
    .reset(1'b0),
    .locked(locked),
    .clk_out1(clk100)
);

reg [31:0] ctr_r;

initial begin
    ctr_r <= 'hcccc_cccc;
end

always @(posedge clk100) begin
    if (!locked) begin
        ctr_r <= 'haaaa_aaaa;
    end
    else begin
        ctr_r <= 'hbbbb_bbbb;
    end
end

// vcu108mul mul (
//     .CLK(clk100),
//     .A(in0),
//     .B(in1),
//     .P(out)
// );

assign inout_test = sel ? 'hffff_ffff : 'bz;
assign out = sel ? ctr_r : inout_test;

endmodule