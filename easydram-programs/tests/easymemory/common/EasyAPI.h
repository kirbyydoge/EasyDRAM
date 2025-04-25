#ifndef EASYDRAM_H_
#define EASYDRAM_H_

#include <stdint.h>

#include "EasyCommon.h"
#include "instruction.h"

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
#define BPRT_MEM_BASE       0xf0000000
#define BPRT_REG_SLOT       0xf1000000
#define BPRT_REG_VALID      0xf1000008
#define BPRT_REG_PERIOD     0xf1000010
#define BPRT_REG_OFFSET     0xf1000018
#define BPRT_REG_PROGLEN    0xf1000020
// #define ALLOC_SIZE          (8 * 1024)

#define PS_IN_SECS          1000000000000ULL
#define NS_IN_SECS          1000000000ULL
#define PS_IN_NS            (PS_IN_SECS / NS_IN_SECS)

#define PATTERN_REG         12
#define BAR                 7
#define RAR                 6
#define CAR                 4
#define CASR                0
#define BASR                1
#define RASR                2

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

#define tRCD                15.0f
#define tRP                 15.0f
#define tRAS                35.0f
#define tWL                 9.0f
#define tWR                 12.0f
#define tBL                 4.0f
#define tRTP                12.0f
#define tRRD_S              5.0f
#define tRRD_L              6.0f
#define tFAW                20.0f
#define tCCD                4.0f
#define tSAFETY             35.0f
#define tREF                200.0f
#define tRFC                200.0f

#define GHz                 1000000000ULL
#define MHz                 1000000ULL

extern uint32_t prog_byte_offset;
extern uint32_t prog_ddr_count;
extern uint32_t prev_ddr_count;
extern uint64_t total_ddr_count;
extern uint64_t target_freq;
extern uint64_t static_latency;

// extern uint8_t mem[ALLOC_SIZE];
// extern uint8_t* base_ptr;
// extern uint8_t* end_ptr;
// uint8_t* bare_malloc(uint32_t size);

void base_init(uint32_t _tREF, uint32_t _tRFC);

void pad_nops();
void write_inst(Inst ins);
void write_inst(Mininst ins);
void write_wait(float t);

void basic_read_schedule(uint32_t bank, uint32_t row, uint32_t col);
void basic_write_schedule(uint32_t bank, uint32_t row, uint32_t col, uint32_t* data);
void basic_read_schedule(TLRequest& req);
void basic_write_schedule(TLRequest& req);
void basic_auto_schedule(TLRequest& req);

void basic_solar_read(uint32_t bank, uint32_t row, uint32_t col, float rcd);
void basic_solar_write(uint32_t bank, uint32_t row, uint32_t col, uint32_t* data, float rcd);
void basic_solar_read(TLRequest& req, float rcd);
void basic_solar_write(TLRequest& req, float rcd);

void ddr_init_regs(Address addr);
void ddr_load_data(uint32_t* data);
void ddr_precharge(Address addr, bool init = false);
void ddr_activate(Address addr, bool init = false);
void ddr_read(Address addr, bool init = false);
void ddr_write(Address addr, uint32_t* data = nullptr, bool init = false);
void ddr_wait(float t);

// ACT src -> sleep t1 -> PRE -> sleep t2 -> ACT dst 
void rowclone(uint32_t src_bank, uint32_t src_row, uint32_t dst_bank, uint32_t dst_row, uint8_t t1, uint8_t t2);
void rowclone(Address src, Address dst, uint8_t t1, uint8_t t2);
void bulk_write(Address row0, Address row1, uint8_t t1, uint8_t t2, uint32_t pattern);

void get_base_fields(BaseRequest& req);
void get_tilelink_fields(TLRequest& req);
TLRequest get_request();

void reset_ddr_count();
uint32_t get_ddr_count();
uint64_t get_ddr_tick();
uint64_t get_ref_delay();

void pre_schedule();
bool is_rdback_empty();
void clear_rdback();
void restore_state(int* open_rows, int num_banks);
bool rdback_cacheline(uint64_t* target, uint64_t timeout = DEFAULT_TIMEOUT);
bool flush_commands(uint64_t timeout = DEFAULT_TIMEOUT);
void make_blueprint(int slot, uint32_t period);
bool enqueue_response(TLRequest& req);

uint64_t target_ticks(uint64_t delay_ns, uint64_t target_freq);
void auto_tick_update(BaseRequest& req);
void set_scheduling_duration(uint64_t duration);

void set_cfg_is_timescaled(bool is_timescaled);

_INLINE(void) set_scheduling_state(bool is_scheduling) {
    REF32(SCHEDULING_STATE) = is_scheduling;
}

_INLINE(bool) is_req_empty() {
    return REF32(SCHED_STAT) & 1;
}

#endif //EASYDRAM_H_
