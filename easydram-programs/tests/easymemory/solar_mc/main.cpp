#include "EasyAPI.h"
#include "EasySolar.h"
#include "EasyDebug.h"
#include "BloomFilter.h"
#include "filter.h"
#include <climits>

#define REQ_TABLE_SIZE  16

#define SCHED_CYCLES    5
#define DATA_CYCLES     100

#define EMPTY_REQ       0
#define MEM_REQ         1
#define SPEC_REQ        2

using SolarReq = easy::solar::req::Request;
using SolarResp = easy::solar::resp::Response;

TLRequest req_table[REQ_TABLE_SIZE];
SolarReq solar_req_table[REQ_TABLE_SIZE];
BloomFilter filter;
float tRCD_low = 9;
float tRCD_high = 10.5;

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

void init_target(Address& req, uint32_t pattern) {
    write_inst(SMC_LI(req.bank, BAR));
    write_inst(SMC_LI(req.row, RAR));
    write_inst(SMC_LI(req.col, CAR));
    write_inst(SMC_ACT(BAR, 0, RAR, 0));
    for (int i = 0; i < 16; i++) {
        write_inst(SMC_LI(pattern, PATTERN_REG));
        write_inst(SMC_LDWD(PATTERN_REG, i));
    }
    write_wait(tRCD);
    write_inst(SMC_WRITE(BAR, 0, CAR, 0, 0, 0));
    write_wait(tRAS);
    // write_wait(tWL + tWR + tBL);
    write_inst(SMC_PRE(BAR, 0, 0));
    write_wait(tRP);
};

#define CL_SIZE         64
#define CL_BLOCKS(x)    (CL_SIZE / sizeof(x))

#define SUCCESS 0
#define TIMEOUT 0xffff

uint32_t solar_check(Address& req, uint64_t timeout) {
    float reduced_tRCD = easy::solar::req::get_tRCD();
    uint32_t pattern = easy::solar::req::get_pattern();
    pre_schedule();
    init_target(req, pattern);
    write_inst(SMC_ACT(BAR, 0, RAR, 0));
    write_wait(reduced_tRCD);
    write_inst(SMC_READ(BAR, 0, CAR, 0, 0, 0));
    write_wait(tRTP);
    write_inst(SMC_PRE(BAR, 0, 0));
    write_wait(tRP);
    flush_commands();
    uint32_t res[CL_BLOCKS(uint32_t)];
    if(!rdback_cacheline((uint64_t*) res)) {
        return TIMEOUT;
    }
    for (int i = 0; i < CL_BLOCKS(uint32_t); i++) {
        if (res[i] != pattern) {
            return i + 1;
        }
    }
    return SUCCESS;
}

void solar_schedule(TLRequest& req, float curTRCD) {
    pre_schedule();
    write_inst(SMC_LI(req.addr.bank, BAR));
    write_inst(SMC_LI(req.addr.row, RAR));
    write_inst(SMC_LI(req.addr.col, CAR));
    write_inst(SMC_ACT(BAR, 0, RAR, 0));
    if (req.is_write) {
        for (int i = 0; i < 16; i++) {
            write_inst(SMC_LI(req.tl_data[i], PATTERN_REG));
            write_inst(SMC_LDWD(PATTERN_REG, i));
        }
        write_wait(curTRCD);
        write_inst(SMC_WRITE(BAR, 0, CAR, 0, 0, 0));
        write_wait(tWL + tWR + tBL);
    }
    else {
        write_wait(curTRCD);
        write_inst(SMC_READ(BAR, 0, CAR, 0, 0, 0));
        write_wait(tRTP);
    }
    write_inst(SMC_PRE(BAR, 0, 0));
    write_wait(tRP);
    flush_commands();
    auto_tick_update(req);
    enqueue_response(req);
}

void schedule() {
    int req_idx;
    // *** CHARACTERIZE ***
    req_idx = get_earliest_req(solar_req_table, REQ_TABLE_SIZE);
    if (req_idx >= 0 && !easy::solar::resp::is_full()) {
        SolarReq& req = solar_req_table[req_idx];
        SolarResp resp;
        resp.addr = req.addr;
        resp.result = solar_check(req.addr, DEFAULT_TIMEOUT);
        req.valid = false;
        easy::solar::resp::push(resp);
        EasyCMD::mcTicks += SCHED_CYCLES;
        EasyDebug::schedCount++;
        return;
    }
    // *** DEFAULT ***
    req_idx = get_earliest_req(req_table, REQ_TABLE_SIZE);
    if (req_idx >= 0) {
        TLRequest& req = req_table[req_idx];
        if (req.is_write)  EasyDebug::schedWrCount++;
        else            EasyDebug::schedRdCount++;
        Address check = req.addr;
        check.col = 0;
        float curTRCD = filter.contains(mapping_to_addr(check)) ? tRCD_high : tRCD_low;
        solar_schedule(req, curTRCD);
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
    filter.load(pre_size, pre_hash, pre_filter);
    while(true) {
        if (!EasyDebug::schedEn || EasyDebug::schedCount >= EasyDebug::schedMax) continue;
        empty_idx = get_empty_index(solar_req_table, REQ_TABLE_SIZE);
        if (empty_idx >= 0 && !easy::solar::req::is_empty()) {
            set_scheduling_state(true);
            insert_req(solar_req_table, easy::solar::req::pop(), empty_idx);
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
