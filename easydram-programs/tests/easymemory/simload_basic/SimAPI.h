#ifndef SIMAPI_H_
#define SIMAPI_H_

#include <stdint.h>

#include "SimCommon.h"
#define PROG_MEM_BASE       0x30000000
#define PROG_RDBACK_VALID   0x30010000
#define PROG_RDBACK_CONSUME 0x30010004
#define PROG_RDBACK_DATA    0x30010008
#define PROG_FLUSH_ADDR     0x31000000
#define PROG_LEN_ADDR       0x31000004
#define BENDER_DONE_ADDR    0x31000008
#define PROG_CYCLES_ADDR    0x31000010
#define START_REG_ADDR      0x40000000
#define STOP_REG_ADDR       0x40000004
#define IDLE_REG_ADDR       0x40000008
#define PROG_DONE_ADDR      0x41000000
#define SCHED_STAT          0x50000000
#define SCHED_REQ_OP        0x50000004
#define SCHED_REQ_SOURCE    0x50000034
#define SCHED_REQ_SIZE      0x50000038
#define SCHED_REQ_ADDR      0x50000008
#define SCHED_REQ_CONSUME   0x5000000c
#define SCHED_REQ_TICK      0x50000040
#define SCHED_REQ_DATA      0x50000210
#define SCHED_REQ_CYCLES    0x50001110
#define SCHEDULING_STATE    0x50001114
#define MCTICKS_ADVANCE     0X50001118
#define OUT_REQ_FULL        0x50001100
#define OUT_REQ_OPCODE      0x50001104
#define OUT_REQ_SOURCE      0x50001134
#define OUT_REQ_SIZE        0x50001138
#define OUT_REQ_ADDR        0x50001108
#define OUT_REQ_ENQ         0x5000110c
#define OUT_REQ_TICK        0x50001140
#define OUT_REQ_DATA        0x50001210
#define INSTR_MEM_BASE      0x80000000
#define DATA_MEM_BASE       0x80010000
#define ALLOC_SIZE          4096

#define PS_IN_SECS          1000000000000UL
#define PS_IN_NS            1000UL

#define PATTERN_REG         12
#define BAR                 7
#define RAR                 6
#define CAR                 4

#define REG0                10
#define REG1                9
#define REG2                8

#define BAR0                7
#define RAR0                6
#define CAR0                4

#define BAR1                10
#define RAR1                9
#define CAR1                8

#define NOP_DURATION        1.5f
#define DDR_TICK_RATE       1.5f

#define TL_OP_GET           4
#define TL_OP_ACKD          1
#define TL_OP_PUTF          0
#define TL_OP_PUTP          1
#define TL_OP_ACK           0

#define tRCD                50.0f
#define tRP                 50.0f
#define tRAS                50.0f
#define tWL                 50.0f
#define tWR                 50.0f
#define tBL                 50.0f
#define tRTP                50.0f
#define tRRD_S              50.0f
#define tRRD_L              50.0f
#define tFAW                50.0f
#define tCCD                50.0f

#define tSAFETY             50.0f

#define GHz                 1000000000ULL
#define MHz                 1000000ULL

extern uint32_t program_byte_offset;
extern uint32_t prog_byte_offset;
extern uint32_t prog_ddr_count;
extern uint32_t prev_ddr_count;
extern uint64_t total_ddr_count;
extern uint64_t target_freq;

inline bool is_req_empty() {
    return REF32(SCHED_STAT) & 1;
}

static uint8_t* bare_malloc(uint32_t size) {
    static uint8_t mem[ALLOC_SIZE];
    static uint8_t* base_ptr = mem;
    static uint8_t* end_ptr = (uint8_t*) base_ptr + ALLOC_SIZE;
    if (end_ptr - base_ptr < size) {
        return nullptr;
    }
    uint8_t* user_ptr = base_ptr;
    base_ptr += size;
    return user_ptr;
}

void get_base_fields(BaseRequest& req) {
    req.tick = REF64(SCHED_REQ_TICK); 
    req.valid = true;
}

void get_tilelink_fields(TLRequest& req) {
    req.tl_addr = REF32(SCHED_REQ_ADDR);
    req.tl_opcode = REF32(SCHED_REQ_OP);
    req.tl_source = REF32(SCHED_REQ_SOURCE);
    req.tl_size = REF32(SCHED_REQ_SIZE);
    req.is_write = req.tl_opcode == TL_OP_PUTF || req.tl_opcode == TL_OP_PUTP;
    req.addr = get_addr_mapping(req.tl_addr);
    if (req.is_write) {
        uint64_t* tgt = (uint64_t*) req.tl_data;
        for (int i = 0; i < CACHELINE_BYTES / sizeof(uint64_t); i++) {
            tgt[i] = REF64(SCHED_REQ_DATA + sizeof(uint64_t) * i);
        }
    }
}

TLRequest get_request() {
    TLRequest req;
    get_base_fields(req);
    get_tilelink_fields(req);
    REF32(SCHED_REQ_CONSUME) = 1;
    return req;
}

// STRIPPED DRAM-BENDER API
// SoftMC decode
#define __OP_CODE 59
#define __FU_CODE 48
#define __IS_BR   62
#define __IS_DDR  63
#define __IS_MISC 61
#define __IS_MEM  60
#define __IS_BW   59
// EXE decode
#define __RS1     0
#define __RS2     4
#define __RT      20
#define __IMD1    4
#define __IMD2    0
#define __IMD3    24
#define __BR_TGT  8
#define __J_TGT   0
#define __SLP_AMT 0
// DDR decode
#define __DDR_CMD   12
#define __DDR_CAR   4
#define __DDR_BAR   0
#define __DDR_RAR   4
#define __DDR_IBAR  10
#define __DDR_ICAR  11
#define __DDR_IRAR  11
#define __DDR_PALL  11
#define __DDR_AP    9
#define __DDR_BL4   8
// EXE function codes
#define __ADD   0
#define __ADDI  1
#define __SUB   2
#define __SUBI  3
#define __MV    4
#define __SRC   5
#define __LI    6
#define __LDWD  7
#define __LDPC	8
#define __SRE   0x100
#define __SRX   0x101
#define __BL    0
#define __BEQ   1
#define __JUMP  2
#define __SLEEP 3
#define __INFO  0 // Various information about upcoming block
#define __AND   0
#define __OR    1
#define __XOR   2
#define __LD    0
#define __ST    1
// DDR function codes
#define __WRITE 8
#define __READ  9
#define __PRE   10
#define __ACT   11
#define __ZQ    12
#define __REF   13
#define __NOP   15

// 64 bit instructions
typedef uint64_t Inst;
// 16 bit mini ddr-instructions
typedef uint16_t Mininst;

Inst SMC_LI(uint32_t imd, int rt)
{
  Inst fu_code = (uint64_t)__LI << __FU_CODE;
  Inst imd1    = ((uint32_t)(imd<<16)>>16) << __IMD1;
  Inst imd2    = (uint64_t)(((uint32_t)imd)>>16) << __IMD3;
  Inst t_reg   = rt << __RT;

  Inst inst    = fu_code | imd1 | imd2 | t_reg;

  return inst;
}

Mininst SMC_NOP()
{
  Mininst fu_code   = ((uint64_t)__NOP) << __DDR_CMD;

  return fu_code;
}

Mininst SMC_PRE(int bar, int ibar, int pall)
{
  Mininst fu_code   = ((uint64_t)__PRE) << __DDR_CMD;
  Mininst i_bar     = bar;
  Mininst i_ibar    = ibar << __DDR_IBAR;
  Mininst i_pall    = pall << __DDR_PALL;

  Mininst inst = fu_code | i_bar | i_ibar | i_pall;

  return inst;
}

Mininst SMC_ACT(int bar, int ibar, int rar, int irar)
{
  Mininst fu_code   = ((uint64_t)__ACT) << __DDR_CMD;
  Mininst i_bar     = bar;
  Mininst i_rar     = rar << __DDR_RAR;
  Mininst i_ibar    = ibar << __DDR_IBAR;
  Mininst i_irar    = irar << __DDR_IRAR;

  Mininst inst = fu_code | i_bar | i_rar | i_ibar | i_irar;

  return inst;
}

Mininst SMC_READ(int bar, int ibar, int car, int icar, int BL4, int ap)
{
  Mininst fu_code   = ((uint64_t)__READ) << __DDR_CMD;
  Mininst i_bar     = bar;
  Mininst i_car     = car << __DDR_CAR;
  Mininst i_ibar    = ibar << __DDR_IBAR;
  Mininst i_icar    = icar << __DDR_ICAR;
  Mininst i_BL4     = BL4 << __DDR_BL4;
  Mininst i_ap      = ap <<__DDR_AP;

  Mininst inst = fu_code | i_bar | i_car | i_ibar |
          i_icar | i_BL4 | i_ap;

  return inst;
}

Inst SMC_LDWD(int rs1, int off)
{
  Inst fu_code = (uint64_t)__LDWD << __FU_CODE;
  Inst s_reg   = rs1;
  Inst offset  = off << __RT;

  Inst inst    = fu_code | s_reg | offset;

  return inst;
}

Mininst SMC_WRITE(int bar, int ibar, int car, int icar, int BL4, int ap)
{
  Mininst fu_code   = ((uint64_t)__WRITE) << __DDR_CMD;
  Mininst i_bar     = bar;
  Mininst i_car     = car << __DDR_CAR;
  Mininst i_ibar    = ibar << __DDR_IBAR;
  Mininst i_icar    = icar << __DDR_ICAR;
  Mininst i_BL4     = BL4 << __DDR_BL4;
  Mininst i_ap      = ap <<__DDR_AP;

  Mininst inst = fu_code | i_bar | i_car | i_ibar |
          i_icar | i_BL4 | i_ap;

  return inst;
}

Inst SMC_END()
{
  return 0;
}

Inst __pack_mininsts(Mininst i1, Mininst i2, Mininst i3, Mininst i4)
{
  return (uint64_t) i4 << 48 |
        (uint64_t) i3 << 32 |
        (uint64_t) i2 << 16 |
        i1  ;
}

void write_inst(Mininst ins) {
    write16(PROG_MEM_BASE + prog_byte_offset, ins);
    prog_byte_offset += 2;
    prog_ddr_count += 1;
}

void pad_nops() {
    while(prog_byte_offset % 8) {
        write_inst(SMC_NOP());
    }
}

void write_inst(Inst ins) {
    pad_nops();
    write64(PROG_MEM_BASE + prog_byte_offset, ins);
    prog_byte_offset += 8;
    // prog_ddr_count += 4;
}

void write_wait(float nsTime) {
    uint32_t t = (uint32_t) ((nsTime + NOP_DURATION - 1) / NOP_DURATION);
    for (int i = 0; i < t; i++) {
        write_inst(SMC_NOP());
    }
}

bool flush_commands(uint64_t timeout = DEFAULT_TIMEOUT) {
    write_inst(SMC_END());
    write32(PROG_LEN_ADDR, prog_byte_offset / 8);
    while(!read32(BENDER_DONE_ADDR) && timeout-- > 0);
    if (!timeout) return false;
    write32(PROG_FLUSH_ADDR, 1);
    total_ddr_count += prog_ddr_count;
    prev_ddr_count = prog_ddr_count;
    prog_byte_offset = 0;
    prog_ddr_count = 0;
    return true;
}

void set_scheduling_state(bool is_scheduling) {
    REF32(SCHEDULING_STATE) = is_scheduling;
}

void basic_read_schedule(uint32_t bank, uint32_t row, uint32_t col) {
    write_inst(SMC_LI(bank, BAR));
    write_inst(SMC_LI(row, RAR));
    write_inst(SMC_LI(col, CAR));
    write_inst(SMC_ACT(BAR, 0, RAR, 0));
    write_wait(tRCD);
    write_inst(SMC_READ(BAR, 0, CAR, 0, 0, 0));
    write_wait(tRTP);
    write_inst(SMC_PRE(BAR, 0, 0));
    write_wait(tRP);
}

void basic_write_schedule(uint32_t bank, uint32_t row, uint32_t col, uint32_t* data) {
    write_inst(SMC_LI(bank, BAR));
    write_inst(SMC_LI(row, RAR));
    write_inst(SMC_LI(col, CAR));
    write_inst(SMC_ACT(BAR, 0, RAR, 0));
    for (int i = 0; i < 16; i++) {
        write_inst(SMC_LI(data[i], PATTERN_REG));
        write_inst(SMC_LDWD(PATTERN_REG, i));
    }
    write_wait(tRCD);
    write_inst(SMC_WRITE(BAR, 0, CAR, 0, 0, 0));
    write_wait(tWL + tWR + tBL);
    write_inst(SMC_PRE(BAR, 0, 0));
    write_wait(tRP);
}

void basic_read_schedule(TLRequest& req) {
    basic_read_schedule(req.addr.bank, req.addr.row, req.addr.col);
}

void basic_write_schedule(TLRequest& req) {
    basic_write_schedule(req.addr.bank, req.addr.row, req.addr.col, (uint32_t*) req.tl_data);
}

void clear_rdback() {
    while(read32(PROG_RDBACK_VALID)) {
        write32(PROG_RDBACK_CONSUME, 1);
    }
}

void pre_schedule() {
    prog_byte_offset = 0;
    prog_ddr_count = 0;
    clear_rdback();
    write_inst(SMC_LI(1, BAR));
    write_inst(SMC_PRE(BAR, 0, 1));
    write_wait(tRP);
}

uint64_t target_ticks(uint64_t delay_ns, uint64_t target_freq) {
    uint64_t period_ps = PS_IN_SECS / target_freq;
    uint64_t delay_ps = PS_IN_NS * delay_ns;
    return delay_ps / period_ps; 
}

void auto_tick_update(BaseRequest& req) {
    uint32_t serve_latency = 50;
    uint32_t bus_latency = 3;
    req.tick += (serve_latency + bus_latency);
}

bool rdback_cacheline(uint64_t* target, uint64_t timeout = DEFAULT_TIMEOUT) {
    while(!read32(PROG_RDBACK_VALID) && timeout-- > 0);
    if (timeout) {
        for (int i = 0; i < 4; i++) {
            target[i] = read64(PROG_RDBACK_DATA + i * 8);
        }
        write32(PROG_RDBACK_CONSUME, 1);
    }
    while(!read32(PROG_RDBACK_VALID) && timeout-- > 0);
    if (timeout) { 
        for (int i = 0; i < 4; i++) {
            target[i + 4] = read64(PROG_RDBACK_DATA + i * 8);
        }
        write32(PROG_RDBACK_CONSUME, 1);
        return true;
    }
    for (int i = 0; i < 8; i++) {
        target[i] = TIMEOUT_PATTERN;
    }
    return false;
}

void enqueue_response(TLRequest& req) {
    if (!req.is_write) {
        rdback_cacheline((uint64_t*) OUT_REQ_DATA, DEFAULT_TIMEOUT);
    }
    write32(OUT_REQ_OPCODE, req.tl_opcode);
    write32(OUT_REQ_SOURCE, req.tl_source);
    write32(OUT_REQ_SIZE, req.tl_size);
    write32(OUT_REQ_ADDR, req.tl_addr);
    write64(OUT_REQ_TICK, req.tick);
    while(read32(OUT_REQ_FULL));
    write32(OUT_REQ_ENQ, 1);
}

void basic_auto_schedule(TLRequest& req) {
    pre_schedule();
    if (req.is_write) {
        basic_write_schedule(req);
    }
    else {
        basic_read_schedule(req);
    }
    flush_commands();
    auto_tick_update(req);
    enqueue_response(req);
}

// ACT src -> sleep t1 -> PRE -> sleep t2 -> ACT dst 
void rowclone(uint32_t src_bank, uint32_t src_row, uint32_t dst_bank, uint32_t dst_row, uint8_t t1, uint8_t t2) {
    write_inst(SMC_LI(src_bank, BAR));
    write_inst(SMC_LI(src_row, RAR));
    write_inst(SMC_LI(dst_bank, REG0));
    write_inst(SMC_LI(dst_row, REG1));
    int size = 3 + t1 + t2;
    size = size + (4 - (size % 4));
    Mininst buf[size];
    for (int i = 0; i < size; i++) {
        buf[i] = SMC_NOP();
    }
    buf[0] = SMC_ACT(BAR, 0, RAR, 0);
    buf[1 + t1] = SMC_PRE(BAR, 0, 0);
    buf[2 + t1 + t2] = SMC_ACT(REG0, 0, REG1, 0);
    for (int i = 0; i < size; i++) {
        write_inst(buf[i]);
    }
    write_wait(tRAS);
    write_inst(SMC_PRE(REG0, 0, 0));
}

void rowclone(Address src, Address dst, uint8_t t1, uint8_t t2) {
    rowclone(src.bank, src.row, dst.bank, dst.row, t1, t2);
}

void set_cfg_is_timescaled(bool is_timescaled) {
    write32(IDLE_REG_ADDR, is_timescaled);
}

#endif