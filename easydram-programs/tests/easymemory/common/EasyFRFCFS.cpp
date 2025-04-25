#include "EasyFRFCFS.h"
#include "EasyCMD.h"
#include <climits>
#include "mc_uart.h"
#include "filter.h"

namespace FRFCFS {

float timing_table[DRAMCommand::WR + 1][DRAMCommand::WR + 1];

void init_timing_table() {
    for (int i = 0; i <= DRAMCommand::WR; i++) {
        for (int j = 0; j <= DRAMCommand::WR; j++) {
            timing_table[i][j] = tSAFETY;
        }
    }
    timing_table[DRAMCommand::PRE][DRAMCommand::ACT] = tRP;
    timing_table[DRAMCommand::ACT][DRAMCommand::RD] = tRCD;
    timing_table[DRAMCommand::ACT][DRAMCommand::WR] = tRCD;
    timing_table[DRAMCommand::ACT][DRAMCommand::PRE] = tRAS;
    timing_table[DRAMCommand::RD][DRAMCommand::PRE] = tRTP;
    timing_table[DRAMCommand::RD][DRAMCommand::WR] = tCCD;
    timing_table[DRAMCommand::RD][DRAMCommand::RD] = tCCD;
    timing_table[DRAMCommand::WR][DRAMCommand::PRE] = tWL + tWR + tBL;
    timing_table[DRAMCommand::WR][DRAMCommand::WR] = tCCD;
    timing_table[DRAMCommand::WR][DRAMCommand::RD] = tCCD;
 }

Scheduler& create_scheduler(int table_size, int num_banks, int sched_delay, bool use_solar) {
    Scheduler* mc = (Scheduler*) malloc(sizeof(Scheduler));
    if (!mc) {
        ee_printf("Couldn't allocate MC\n");
        exit(0);
    }
    init_scheduler(*mc, table_size, num_banks, sched_delay, use_solar);
    return *mc;
}

void heap_check(int depth) {
    char c;
    char *ptr = (char *) malloc(1);
    ee_printf("stack at %p, heap at %p\n", &c, ptr);
    if (depth <= 0) return;
    heap_check(depth-1);
}

void init_scheduler(Scheduler& mc, int table_size, int num_banks, int sched_delay, bool use_solar) {
    ee_printf("Start.\n");
    mc.table_size = table_size;
    mc.num_banks = num_banks;
    mc.sched_delay = sched_delay;
    mc.req_table = (TLRequest*) malloc(sizeof(TLRequest) * table_size);
    if (!mc.req_table) {
        ee_printf("Couldn't allocate req_table\n");
        exit(0);
    }
    mc.dram_state.open_rows = (int*) malloc(sizeof(int) * num_banks);
    if (!mc.dram_state.open_rows) {
        ee_printf("Couldn't allocate open_rows\n");
        exit(0);
    }
    mc.dram_state.last_cmds = (DRAMCommand*) malloc(sizeof(DRAMCommand) * num_banks);
    if (!mc.dram_state.last_cmds) {
        ee_printf("Couldn't allocate last_cmds\n");
        exit(0);
    }
    mc.dram_state.issue_tick = (uint64_t*) malloc(sizeof(uint64_t) * num_banks);
    if (!mc.dram_state.issue_tick) {
        ee_printf("Couldn't allocate issue_tick\n");
        exit(0);
    }
    mc.solar.enabled = false;
    if (use_solar) {
        mc.solar.filter = (BloomFilter*) malloc(sizeof(BloomFilter));
        if (!mc.solar.filter) {
            ee_printf("Couldn't allocate bloom_filter\n");
            exit(0);
        }
        mc.solar.filter->load(pre_size, pre_hash, pre_filter);
        mc.solar.strongTRCD = 9.0f;
        mc.solar.weakTRCD = 10.5f;
        mc.solar.enabled = true;
    }
    for (int i = 0; i < table_size; i++) {
        mc.req_table[i].valid = false;
    }
    for (int i = 0; i < num_banks; i++) {
        mc.dram_state.open_rows[i] = -1;
        mc.dram_state.last_cmds[i] = DRAMCommand::NOP;
        mc.dram_state.issue_tick[i] = 0;
    }
    heap_check();
}

bool add_req(Scheduler& mc, TLRequest& req) {
    for (int i = 0; i < mc.table_size; i++) {
        if (!mc.req_table[i].valid) {
            mc.req_table[i] = req;
            return true;
        }
    }
    return false;
}

// Temporary, will be removed with API overhaul.
#define PERIOD_NS   10
// #define tREF        (7800 * 4)
#define tREF        (1953)
#define tRFC        200

int frfcfs_ticks(Scheduler& mc, TLRequest& req) {
    SolarDRAM& solar = mc.solar;
    DRAMState& state = mc.dram_state;
    Address& addr = req.addr;
    float duration = 0;
    DRAMCommand last_cmd = DRAMCommand::NOP;
    if (req.is_write)   EasyCMD::statCounter2++; // Write Req
    EasyCMD::statCounter3++; // Schedule
    uint64_t refPos = (EasyCMD::mcTicks * PERIOD_NS) % tREF; // Location in refresh window
    if (refPos < tRFC) {
        EasyCMD::statCounter4 += tRFC - refPos; // Cycles caused for refresh
        EasyCMD::statCounter5++; // Requests that fell victim to refresh
        duration += tRFC - refPos;
    }
    if (state.open_rows[addr.bank] != addr.row) {
        if (state.open_rows[addr.bank] >= 0) {
            duration += tRP;
            last_cmd = DRAMCommand::PRE;
            EasyCMD::statCounter1++; // Row Conflict
        }
        if (solar.enabled) {
            Address check = req.addr;
            check.col = 0;
            bool may_contain = solar.filter->contains(mapping_to_addr(check));
            if (may_contain) {
                duration += solar.weakTRCD;
            }
            else {
                duration += solar.strongTRCD;
            }
        }
        else {
            duration += tRCD;
        }
        last_cmd = DRAMCommand::ACT;
    }
    else {
        EasyCMD::statCounter0++; // Row Hit
    }
    if (req.is_write) {
        duration += tWL + tWR + tBL;
    }
    else {
        duration += tRTP;
    }
    return ((int) (duration / NOP_DURATION) + static_latency);
}

bool schedule(Scheduler& mc) {
    int fr_idx = get_first_ready(mc);
    int req_idx = fr_idx >= 0 ? fr_idx : get_first_come(mc);
    if (req_idx >= 0) {
        SolarDRAM& solar = mc.solar;
        DRAMState& state = mc.dram_state;
        TLRequest& req = mc.req_table[req_idx];
        req.valid = false;
        int duration = frfcfs_ticks(mc, req);
        pre_schedule();
        float curTRCD = tRCD;
        if (solar.enabled && state.open_rows[req.addr.bank] != req.addr.row) {
            Address check = req.addr;
            check.col = 0;
            bool may_contain = solar.filter->contains(mapping_to_addr(check));
            // curTRCD = may_contain ? solar.weakTRCD : solar.strongTRCD;
            curTRCD = solar.weakTRCD;
        }
        if (req.is_write) {
            basic_solar_write(req, curTRCD);
        }
        else {
            basic_solar_read(req, curTRCD);
        }
        if (!flush_commands()) {
            ee_printf("[CRITICAL WARNING] Flush timeout.\n");
        }
        req.tick = EasyCMD::mcTicks + duration * EasyCMD::mcTileRatio;
        if (!enqueue_response(req)) {
            ee_printf("[CRITICAL WARNING] Enqueue timeout.\n"); 
        }
        state.open_rows[req.addr.bank] = req.addr.row;
        EasyCMD::mcTicks += mc.sched_delay;
        return true;
    }
    return false;
}

void execute_request(Scheduler& mc, TLRequest& req) {
    ddr_init_regs(req.addr);
    timing_check(mc, req, DRAMCommand::ACT);
    ddr_activate(req.addr);
    update_state(mc, req, DRAMCommand::ACT);
    if (req.is_write) {
        timing_check(mc, req, DRAMCommand::WR);
        ddr_load_data(req.tl_data);
        ddr_write(req.addr);
        update_state(mc, req, DRAMCommand::WR);
    }
    else {
        timing_check(mc, req, DRAMCommand::RD);
        ddr_read(req.addr);
        update_state(mc, req, DRAMCommand::RD);
    }
    timing_check(mc, req, DRAMCommand::PRE);
    ddr_precharge(req.addr);
    update_state(mc, req, DRAMCommand::PRE);
}

int get_first_ready(Scheduler& mc) {
    int fr_idx = -1;
    uint64_t fr_ticks = ULLONG_MAX;
    for (int i = 0; i < mc.table_size; i++) {
        TLRequest& req = mc.req_table[i];
        if (req.valid && req.tick < fr_ticks && is_row_hit(mc, req)) {
            fr_idx = i;
            fr_ticks = req.tick;
        }
    }
    return fr_idx;
}

int get_first_come(Scheduler& mc) {
    int fc_idx = -1;
    uint64_t fc_ticks = ULLONG_MAX;
    for (int i = 0; i < mc.table_size; i++) {
        TLRequest& req = mc.req_table[i];
        if (req.valid && req.tick < fc_ticks) {
            fc_idx = i;
            fc_ticks = req.tick;
        }
    }
    return fc_idx;
}

int get_empty_index(Scheduler& mc) {
    for (int i = 0; i < mc.table_size; i++) {
        TLRequest& req = mc.req_table[i];
        if (!req.valid) {
            return i;
        }
    }
    return -1;
}

bool is_row_hit(Scheduler& mc, TLRequest& req) {
    return mc.dram_state.open_rows[req.addr.bank] == req.addr.row;
}

void update_state(Scheduler& mc, TLRequest& req, DRAMCommand cmd) {
    Address& addr = req.addr;
    if (cmd == DRAMCommand::ACT) {
        mc.dram_state.open_rows[addr.bank] = addr.row;
    }
    if (cmd == DRAMCommand::PRE) {
        mc.dram_state.open_rows[addr.bank] = -1;
    }
    mc.dram_state.last_cmds[addr.bank] = cmd;
    mc.dram_state.issue_tick[addr.bank] = get_ddr_tick();
}

float get_timing(DRAMCommand prev, DRAMCommand cur) {
    return timing_table[prev][cur];
}

#define EPSILON 0.0001

void timing_check(Scheduler& mc, TLRequest& req, DRAMCommand cmd) {
    // DRAMState& state = mc.dram_state;
    // DRAMCommand prev_cmd = state.last_cmds[req.addr.bank];
    // uint64_t prev_tick = state.issue_tick[req.addr.bank];
    // float tgt_time = prev_tick * DDR_TICK_RATE + get_timing(prev_cmd, cmd);
    // float cur_time = get_ddr_tick() * DDR_TICK_RATE;
    // if (tgt_time - cur_time > EPSILON) {
    //     ddr_wait(tgt_time - cur_time);
    // }
    ddr_wait(tSAFETY);
}

};