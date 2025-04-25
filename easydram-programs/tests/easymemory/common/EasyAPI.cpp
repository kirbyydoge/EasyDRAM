#include <cmath>

#include "EasyAPI.h"
#include "EasyCMD.h"
#include "mc_uart.h"

uint32_t prog_byte_offset = 0;
uint32_t prog_ddr_count = 0;
uint32_t prev_ddr_count = 0;
uint64_t total_ddr_count = 0;
uint64_t target_freq = 1 * GHz;
uint64_t static_latency = 140;

void base_init(uint32_t _tREF, uint32_t _tRFC) {
    write_inst(SMC_REF());
    write_wait(_tREF);
    write_inst(SMC_END());
    make_blueprint(0, _tRFC);
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

void write_inst(Mininst ins) {
    write16(PROG_MEM_BASE + prog_byte_offset, ins);
    prog_byte_offset += 2;
    prog_ddr_count += 1;
}

void write_wait(float nsTime) {
    uint32_t t = (uint32_t) ((nsTime + NOP_DURATION - 0.00001) / NOP_DURATION);
    for (uint32_t i = 0; i < t; i++) {
        write_inst(SMC_NOP());
    }
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

void basic_solar_read(uint32_t bank, uint32_t row, uint32_t col, float rcd) {
    write_inst(SMC_LI(bank, BAR));
    write_inst(SMC_LI(row, RAR));
    write_inst(SMC_LI(col, CAR));
    write_inst(SMC_ACT(BAR, 0, RAR, 0));
    write_wait(rcd);
    write_inst(SMC_READ(BAR, 0, CAR, 0, 0, 0));
    write_wait(tRTP);
    write_inst(SMC_PRE(BAR, 0, 0));
    write_wait(tRP);
}

void basic_solar_write(uint32_t bank, uint32_t row, uint32_t col, uint32_t* data, float rcd) {
    write_inst(SMC_LI(bank, BAR));
    write_inst(SMC_LI(row, RAR));
    write_inst(SMC_LI(col, CAR));
    for (int i = 0; i < 16; i++) {
        write_inst(SMC_LI(data[i], PATTERN_REG));
        write_inst(SMC_LDWD(PATTERN_REG, i));
    }
    write_inst(SMC_ACT(BAR, 0, RAR, 0));
    write_wait(rcd);
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

void basic_solar_read(TLRequest& req, float rcd) {
    basic_solar_read(req.addr.bank, req.addr.row, req.addr.col, rcd);
}

void basic_solar_write(TLRequest& req, float rcd) {
    basic_solar_write(req.addr.bank, req.addr.row, req.addr.col, (uint32_t*) req.tl_data, rcd);
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

void pre_schedule() {
    prog_byte_offset = 0;
    prog_ddr_count = 0;
    clear_rdback();
    write_inst(SMC_LI(1, BAR));
    write_inst(SMC_PRE(BAR, 0, 1));
    write_wait(tSAFETY);
}

// // ACT src -> sleep t1 -> PRE -> sleep t2 -> ACT dst 
// void rowclone(uint32_t src_bank, uint32_t src_row, uint32_t dst_bank, uint32_t dst_row, uint8_t t1, uint8_t t2) {
//     write_inst(SMC_LI(src_bank, BAR0));
//     write_inst(SMC_LI(src_row, RAR0));
//     write_inst(SMC_LI(dst_bank, BAR1));
//     write_inst(SMC_LI(dst_row, RAR1));
//     int size = 3 + t1 + t2;
//     size = size + (4 - (size % 4));
//     Mininst buf[size];
//     for (int i = 0; i < size; i++) {
//         buf[i] = SMC_NOP();
//     }
//     buf[0] = SMC_ACT(BAR0, 0, RAR0, 0);
//     buf[1 + t1] = SMC_PRE(BAR0, 0, 0);
//     buf[2 + t1 + t2] = SMC_ACT(BAR1, 0, RAR1, 0);
//     for (int i = 0; i < size; i++) {
//         write_inst(buf[i]);
//     }
//     write_wait(tRAS);
//     write_inst(SMC_PRE(BAR1, 0, 0));
// }

// ACT src -> sleep t1 -> PRE -> sleep t2 -> ACT dst 
void rowclone(Address src, Address dst, uint8_t t1, uint8_t t2) {
    write_inst(SMC_LI(src.bank, BAR0));
    write_inst(SMC_LI(src.row, RAR0));
    write_inst(SMC_LI(dst.bank, BAR1));
    write_inst(SMC_LI(dst.row, RAR1));
    write_inst(SMC_ACT(BAR0, 0, RAR0, 0));
    write_wait(t1);
    write_inst(SMC_PRE(BAR0, 0, 0));
    write_wait(t2);
    write_inst(SMC_ACT(BAR1, 0, RAR1, 0));
    write_wait(tRAS);
    write_inst(SMC_PRE(BAR1, 0, 0));
}

void bulk_write(Address row0, Address row1, uint8_t t1, uint8_t t2, uint32_t pattern) {
    write_inst(SMC_LI(row0.bank, BAR0));
    write_inst(SMC_LI(row0.row, RAR0));
    write_inst(SMC_LI(row0.col, CAR0));
    write_inst(SMC_LI(row1.bank, BAR1));
    write_inst(SMC_LI(row1.row, RAR1));
    write_inst(SMC_LI(row1.col, CAR1));
    for (int i = 0; i < 16; i++) {
        write_inst(SMC_LI(pattern, PATTERN_REG));
        write_inst(SMC_LDWD(PATTERN_REG, i));
    }
    write_inst(SMC_ACT(BAR0, 0, RAR0, 0));
    for (int i = 0; i < t1; i++) { write_inst(SMC_NOP()); }
    write_inst(SMC_PRE(BAR0, 0, 0));
    for (int i = 0; i < t2; i++) { write_inst(SMC_NOP()); }
    write_inst(SMC_ACT(BAR1, 0, RAR1, 0));
    write_wait(tRAS);
    write_inst(SMC_WRITE(BAR1, 0, CAR1, 0, 0, 0));
    write_wait(tWL + tWR + tBL);
    write_inst(SMC_PRE(BAR1, 0, 0));
    write_wait(tRP);
}

bool flush_commands(uint64_t timeout) {
    int temp_ddr_count = prog_ddr_count;
    write_wait(tSAFETY);
    write_inst(SMC_END());
    write32(PROG_LEN_ADDR, prog_byte_offset / 8);
    while(!read32(BENDER_DONE_ADDR) && timeout-- > 0);
    if (!timeout) return false;
    write32(PROG_FLUSH_ADDR, 1);
    prog_ddr_count = temp_ddr_count; 
    total_ddr_count += prog_ddr_count;
    prev_ddr_count = prog_ddr_count;
    prog_byte_offset = 0;
    prog_ddr_count = 0;
    return true;
}

void make_blueprint(int slot, uint32_t period) {
    static uint8_t* bpMem = (uint8_t*) BPRT_MEM_BASE;
    uint8_t* benderMem = (uint8_t*) PROG_MEM_BASE;
    write64(BPRT_REG_SLOT, slot);
    for (int i = 0; i < prog_byte_offset; i++) {
        bpMem[i] = benderMem[i];
    }
    write64(BPRT_REG_PERIOD, period);
    write64(BPRT_REG_OFFSET, (long) bpMem - BPRT_MEM_BASE);
    write64(BPRT_REG_PROGLEN, prog_byte_offset / 8);
    write64(BPRT_REG_VALID, true);
    bpMem += prog_byte_offset;
    prog_byte_offset = 0;
    prog_ddr_count = 0;
}

// uint8_t mem[ALLOC_SIZE];
// uint8_t* base_ptr = mem;
// uint8_t* end_ptr = (uint8_t*) base_ptr + ALLOC_SIZE;

// uint8_t* bare_malloc(uint32_t size) {
//     if (end_ptr - base_ptr < size) {
//         return nullptr; // Alloc failed
//     }
//     uint8_t* user_ptr = base_ptr;
//     base_ptr += size;
//     return user_ptr;
// }

void get_base_fields(BaseRequest& req) {
    req.tick = read64(SCHED_REQ_TICK); 
    req.valid = true;
}

void get_tilelink_fields(TLRequest& req) {
    req.tl_addr = read32(SCHED_REQ_ADDR);
    if (req.tl_addr >= 0x8fff0000) {
        uint32_t new_addr = req.tl_addr & 0x8000ffff;
        ee_printf("Adjusting 0x%x to 0x%x\n", req.tl_addr, new_addr);
        req.tl_addr = new_addr;
    }
    req.tl_opcode = read32(SCHED_REQ_OP);
    req.tl_source = read32(SCHED_REQ_SOURCE);
    req.tl_size = read32(SCHED_REQ_SIZE);
    req.is_write = req.tl_opcode == TL_OP_PUTF || req.tl_opcode == TL_OP_PUTP;
    req.addr = get_addr_mapping(req.tl_addr);
    if (req.is_write) {
        uint64_t* tgt = (uint64_t*) req.tl_data;
        for (uint32_t i = 0; i < CACHELINE_BYTES / sizeof(uint64_t); i++) {
            tgt[i] = read64(SCHED_REQ_DATA + sizeof(uint64_t) * i);
        }
    }
}

TLRequest get_request() {
    TLRequest req;
    get_base_fields(req);
    get_tilelink_fields(req);
    write32(SCHED_REQ_CONSUME, 1);
    return req;
}

bool rdback_cacheline(uint64_t* target, uint64_t timeout) {
    while(!read32(PROG_RDBACK_VALID) && timeout-- > 0);
    if (timeout) {
        for (int i = 0; i < 4; i++) {
            target[i] = read64(PROG_RDBACK_DATA + i * 8);
        }
        write64(PROG_RDBACK_CONSUME, 1);
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

bool is_rdback_empty() {
    return !read32(PROG_RDBACK_VALID);
}

void clear_rdback() {
    while(!is_rdback_empty()) {
        write32(PROG_RDBACK_CONSUME, 1);
    }
}

bool enqueue_response(TLRequest& req) {
    bool success = true;
    if (!req.is_write) {
        success = rdback_cacheline((uint64_t*) OUT_REQ_DATA, DEFAULT_TIMEOUT);
    }
    write32(OUT_REQ_OPCODE, req.tl_opcode);
    write32(OUT_REQ_SOURCE, req.tl_source);
    write32(OUT_REQ_SIZE, req.tl_size);
    write32(OUT_REQ_ADDR, req.tl_addr);
    write64(OUT_REQ_TICK, req.tick);
    while(read32(OUT_REQ_FULL));
    write32(OUT_REQ_ENQ, 1);
    return success;
}

uint64_t target_ticks(uint64_t delay_ns, uint64_t target_freq) {
    uint64_t period_ps = PS_IN_SECS / target_freq;
    uint64_t delay_ps = PS_IN_NS * delay_ns;
    return delay_ps / period_ps * EasyCMD::mcTileRatio;
}

void auto_tick_update(BaseRequest& req) {
    req.tick = EasyCMD::mcTicks + target_ticks(prev_ddr_count, target_freq) + static_latency * EasyCMD::mcTileRatio;
}

void set_scheduling_ddr_count(uint64_t duration) {
    write64(SCHED_REQ_CYCLES, duration);
}

void reset_ddr_count() {
    prog_ddr_count = 0;
}

uint32_t get_ddr_count() {
    return prog_ddr_count;
}

uint64_t get_ddr_tick() {
    return total_ddr_count + prog_ddr_count;
}

void restore_state(int* open_rows, int num_banks) {
    pre_schedule();
    for (int i = 0; i < num_banks; i++) {
        if (open_rows[i] >= 0) {
            write_inst(SMC_LI(i, BAR));
            write_inst(SMC_LI(open_rows[i], RAR));
            write_inst(SMC_ACT(BAR, 0, RAR, 0));
            write_wait(tSAFETY);
        }
    }
    write_wait(5 * tSAFETY);
    reset_ddr_count();
}

void ddr_init_regs(Address addr) {
    write_inst(SMC_LI(addr.bank, BAR));
    write_inst(SMC_LI(addr.row, RAR));
    write_inst(SMC_LI(addr.col, CAR));
}

void ddr_load_data(uint32_t* data) {
    for (int i = 0; i < 16; i++) {
        write_inst(SMC_LI(data[i], PATTERN_REG));
        write_inst(SMC_LDWD(PATTERN_REG, i));
    }
}

void ddr_precharge(Address addr, bool init) {
    if (init) {
        ddr_init_regs(addr);
    }
    write_inst(SMC_PRE(BAR, 0, 0));
}

void ddr_activate(Address addr, bool init) {
    if (init) {
        ddr_init_regs(addr);
    }
    write_inst(SMC_ACT(BAR, 0, RAR, 0));
}

void ddr_read(Address addr, bool init) {
    if (init) {
        ddr_init_regs(addr);
    }
    write_inst(SMC_READ(BAR, 0, CAR, 0, 0, 0));
}

void ddr_write(Address addr, uint32_t* data, bool init) {
    if (init) {
        ddr_init_regs(addr);
    }
    if (data) {
        ddr_load_data(data);
    }
    write_inst(SMC_WRITE(BAR, 0, CAR, 0, 0, 0));
}

void set_cfg_is_timescaled(bool is_timescaled) {
    write32(IDLE_REG_ADDR, is_timescaled);
}

// TODO: write_wait was a shitty name, will be removed in future versions
void ddr_wait(float t) {
    write_wait(t);
}
