#include "EasyAPI.h"
#include "EasyClone.h"
#include "EasyDebug.h"
#include "mc_uart.h"
#include <climits>

#define CPU_HZ       100000000
#define BAUD_RATE    921600

#define REQ_TABLE_SIZE      16

#define SCHED_DURATION      5

#define EMPTY_REQ           0
#define MEM_REQ             1
#define SPEC_REQ            2
#define NUM_BANKS           4

using RowCloneRequest = easy::rowclone::RowcloneRequest;

TLRequest req_table[REQ_TABLE_SIZE];
RowCloneRequest rc_req_table[REQ_TABLE_SIZE];
int open_rows[NUM_BANKS];

enum {NOP, ACT, PRE, RD, WR};

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

void schedule() {
    uint64_t mcTicks = EasyCMD::mcTicks;
    uint64_t tileTicks = EasyCMD::tileTicks;
    int req_idx;
    // *** ROWCLONE ***
    req_idx = get_earliest_req(rc_req_table, REQ_TABLE_SIZE);
    if (req_idx >= 0 && mcTicks <= tileTicks && easy::rowclone::is_enabled()) {
        RowCloneRequest& req = rc_req_table[req_idx];
        pre_schedule();
        rowclone(req.source, req.target, 24, 3);
        write_wait(tRAS);
        write_inst(SMC_LI(1, BAR));
        write_inst(SMC_PRE(BAR, 0, 1));
        flush_commands(DEFAULT_TIMEOUT);
        req.valid = false;
        EasyCMD::mcTicks += target_ticks(prev_ddr_count, target_freq);
        EasyDebug::schedCount++;
        return;
    }
    if (mcTicks <= tileTicks) {
        easy::rowclone::set_idle(true);
    }
    // *** DEFAULT ***
    req_idx = get_earliest_req(req_table, REQ_TABLE_SIZE);
    if (req_idx >= 0) {
        float duration = 0;
        TLRequest& req = req_table[req_idx];
        if (req.is_write)   EasyDebug::schedWrCount++;
        else                EasyDebug::schedRdCount++;
        pre_schedule();
        if (req.is_write) {
            duration += tWL + tWR + tBL;
            basic_write_schedule(req);
        }
        else {
            duration += tRTP;
            basic_read_schedule(req);
        }
        flush_commands();
        if (open_rows[req.addr.bank] != req.addr.row) {
            if (open_rows[req.addr.bank] >= 0) {
                duration += tRP;
            }
            duration += tRCD;
        }
        req.tick += ((int) (duration / NOP_DURATION) + static_latency) * EasyCMD::mcTileRatio;
        enqueue_response(req);
        req.valid = false;
        open_rows[req.addr.bank] = req.addr.row;
        EasyCMD::mcTicks += SCHED_DURATION;
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
    // target_freq = (uint64_t)  100 * MHz;
    // target_freq = (uint64_t) (1 * GHz);
    // target_freq = (uint64_t) (1.479f * GHz);
    target_freq = (uint64_t) (50 * MHz);
    int empty_idx;
    easy::rowclone::set_enabled(true);
    uart_set_baud(CPU_HZ / BAUD_RATE);
    ee_printf("Hello from RCMC!\n");
    while(true) {
        if (!EasyDebug::schedEn || EasyDebug::schedCount >= EasyDebug::schedMax) continue;
        empty_idx = get_empty_index(rc_req_table, REQ_TABLE_SIZE);
        if (empty_idx >= 0 && !easy::rowclone::is_empty()) {
            set_scheduling_state(true);
            easy::rowclone::set_idle(false);
            insert_req(rc_req_table, easy::rowclone::pop(), empty_idx);
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
