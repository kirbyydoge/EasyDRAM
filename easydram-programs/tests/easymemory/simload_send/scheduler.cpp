#include "SimAPI.h"
#include "SimCommon.h"
#include <climits>
#include "mc_uart.h"

#define REQ_TABLE_SIZE      16

#define SCHED_DURATION      100
#define TEST_RC_DURATION    1000

#define EMPTY_REQ           0
#define MEM_REQ             1
#define SPEC_REQ            2

TLRequest req_table[REQ_TABLE_SIZE];
RowcloneRequest rc_req_table[REQ_TABLE_SIZE];

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

uint32_t program_byte_offset = 0;
uint32_t prog_byte_offset = 0;
uint32_t prog_ddr_count = 0;
uint32_t prev_ddr_count = 0;
uint64_t total_ddr_count = 0;
uint64_t target_freq = 100 * MHz;

void schedule() {
    uint64_t mcTicks = read64(MC_TICKS);
    uint64_t tileTicks = read64(TILE_TICKS);
    int req_idx;
    // *** ROWCLONE ***
    req_idx = get_earliest_req(rc_req_table, REQ_TABLE_SIZE);
    if (req_idx >= 0 && mcTicks <= tileTicks) {
        RowcloneRequest& req = rc_req_table[req_idx];
        pre_schedule();
        rowclone(req.source, req.target, 24, 2);
        write_wait(tRAS);
        write_inst(SMC_LI(1, BAR));
        write_inst(SMC_PRE(BAR, 0, 1));
        flush_commands(DEFAULT_TIMEOUT);
        req.valid = false;
        write64(MC_TICKS, mcTicks + 10000);
        return;
    }
    if (mcTicks <= tileTicks) {
        set_rowclone_idle(true);
    }
    // *** DEFAULT ***
    req_idx = get_earliest_req(req_table, REQ_TABLE_SIZE);
    if (req_idx >= 0 && mcTicks <= tileTicks) {
        TLRequest& req = req_table[req_idx];
        basic_auto_schedule(req);
        req.valid = false;
        write64(MC_TICKS, mcTicks + 1);
        return;
    }
    set_scheduling_state(false);
}

int main(void) {
    int empty_idx;
    while(true) {
        empty_idx = get_empty_index(rc_req_table, REQ_TABLE_SIZE);
        if (empty_idx >= 0 && !is_rowclone_empty()) {
            set_scheduling_state(true);
            set_rowclone_idle(false);
            insert_req(rc_req_table, pop_rowclone_req(), empty_idx);
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
}

// int main(void) {
//     int ctr[] = {-1, -1, -1, -1};
//     while(true) {
//         if (!is_req_empty()) {
//             set_scheduling_state(true);
//             TLRequest req = get_request();
//             basic_auto_schedule(req);
//             set_scheduling_state(false);
//         }
//     } 
// 	return 0;
// }