#include "EasyAPI.h"
#include "EasyBitwise.h"
#include "EasyDebug.h"
#include <climits>

#define REQ_TABLE_SIZE  16

#define SCHED_CYCLES    5
#define DATA_CYCLES     100

#define EMPTY_REQ       0
#define MEM_REQ         1
#define SPEC_REQ        2

using BitwiseReq = easy::bitwise::req::Request;
using BitwiseResp = easy::bitwise::resp::Response;

TLRequest req_table[REQ_TABLE_SIZE];
BitwiseReq bitwise_req_table[REQ_TABLE_SIZE];

template <typename T>
int get_empty_index(T* table, int size) {
    for (int i = 0; i < size; i++) {
        if (!table[i].valid) return i;
    }
    return -1;
}

template <typename T>
void insert_req(T* table, T req, int idx) {
    table[idx] = req;
}

template <typename T>
int get_earliest_req(T* table, int size) {
    int idx = -1;
    uint64_t tick = ~0;
    for (int i = 0; i < size; i++) {
        T req = table[i];
        if (req.valid && req.tick < tick) {
            idx = i;
            tick = req.tick;
        }
    }
    return idx;
}

void init_target(BitwiseReq& req, uint32_t pattern) {
    write_inst(SMC_LI(req.row0.bank, BAR0));
    write_inst(SMC_LI(req.row0.row, RAR0));
    write_inst(SMC_LI(req.row0.col, CAR0));
    write_inst(SMC_LI(req.row1.bank, BAR1));
    write_inst(SMC_LI(req.row1.row, RAR1));
    write_inst(SMC_LI(req.row1.col, CAR1));
    for (int i = 0; i < 16; i++) {
        write_inst(SMC_LI(pattern, PATTERN_REG));
        write_inst(SMC_LDWD(PATTERN_REG, i));
    }
    write_inst(SMC_ACT(BAR0, 0, RAR0, 0));
    write_wait(tRCD);
    write_inst(SMC_WRITE(BAR0, 0, CAR0, 0, 0, 0));
    write_wait(tWL + tWR + tBL);
    write_inst(SMC_PRE(BAR0, 0, 0));
    write_wait(tRP);
    write_inst(SMC_ACT(BAR1, 0, RAR1, 0));
    write_wait(tRCD);
    write_inst(SMC_WRITE(BAR1, 0, CAR1, 0, 0, 0));
    write_wait(tWL + tWR + tBL);
    write_inst(SMC_PRE(BAR1, 0, 0));
    write_wait(tRP);
};

#define CL_SIZE         64
#define CL_BLOCKS(x)    (CL_SIZE / sizeof(x))

#define SUCCESS 0
#define TIMEOUT 0xffff

uint32_t res0[CL_BLOCKS(uint32_t)];
uint32_t res1[CL_BLOCKS(uint32_t)];

uint32_t bitwise_check(BitwiseReq& req) {
    uint32_t init_pattern = easy::bitwise::req::get_init_pattern();
    uint32_t write_pattern = easy::bitwise::req::get_write_pattern();
    pre_schedule();
    init_target(req, init_pattern);
    bulk_write(req.row0, req.row1, req.t1, req.t2, write_pattern);
    basic_read_schedule(req.row0.bank, req.row0.row, req.row0.col);
    basic_read_schedule(req.row1.bank, req.row1.row, req.row1.col);
    flush_commands();
    if(!rdback_cacheline((uint64_t*) res0)) {
        return TIMEOUT;
    }
    if(!rdback_cacheline((uint64_t*) res1)) {
        return TIMEOUT;
    }
    for (int i = 0; i < CL_BLOCKS(uint32_t); i++) {
        if (res0[i] != write_pattern) {
            return i + 1;
        }
    }
    for (int i = 0; i < CL_BLOCKS(uint32_t); i++) {
        if (res1[i] != write_pattern) {
            return i + 1 + 16;
        }
    }
    return SUCCESS;
}

void schedule() {
    int req_idx;
    // *** CHARACTERIZE ***
    req_idx = get_earliest_req(bitwise_req_table, REQ_TABLE_SIZE);
    if (req_idx >= 0 && !easy::bitwise::resp::is_full()) {
        BitwiseReq& req = bitwise_req_table[req_idx];
        BitwiseResp resp;
        resp.addr = req.row1;
        resp.result = bitwise_check(req);
        easy::bitwise::resp::push(resp);
        req.valid = false;
        EasyCMD::mcTicks += SCHED_CYCLES;
        EasyDebug::schedCount++;
        return;
    }
    // *** DEFAULT ***
    req_idx = get_earliest_req(req_table, REQ_TABLE_SIZE);
    if (req_idx >= 0) {
        TLRequest& req = req_table[req_idx];
        if (req.is_write) EasyDebug::schedWrCount++;
        else              EasyDebug::schedRdCount++;
        basic_auto_schedule(req);
        req.valid = false;
        EasyCMD::mcTicks += SCHED_CYCLES;
        EasyDebug::schedCount++;
        return;
    }
    set_scheduling_state(false);
}

int main(void) {
    EasyDebug::schedEn = true;
    EasyDebug::schedCount = 0;
    EasyDebug::schedMax = ULLONG_MAX;
    EasyDebug::schedRdCount = 0;
    EasyDebug::schedWrCount = 0;
    int empty_idx;
    while(true) {
        if (!EasyDebug::schedEn || EasyDebug::schedCount >= EasyDebug::schedMax) continue;
        empty_idx = get_empty_index(bitwise_req_table, REQ_TABLE_SIZE);
        if (empty_idx >= 0 && !easy::bitwise::req::is_empty()) {
            set_scheduling_state(true);
            insert_req(bitwise_req_table, easy::bitwise::req::pop(), empty_idx);
            continue;
        }
        empty_idx = get_empty_index(req_table, REQ_TABLE_SIZE);
        if (empty_idx >= 0 && !is_req_empty()) {
            set_scheduling_state(true);
            insert_req(req_table, get_request(), empty_idx);
            continue;
        }
        schedule();
    }
	return 0;
}
