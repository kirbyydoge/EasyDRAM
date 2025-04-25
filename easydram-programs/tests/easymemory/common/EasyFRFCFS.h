#ifndef EASY_FRFCFS_H_
#define EASY_FRFCFS_H_

#include "EasyAPI.h"
#include "BloomFilter.h"

namespace FRFCFS {

enum DRAMCommand {
    NOP = 0, PRE, ACT, RD, WR
};

struct DRAMState {
    int* open_rows;
    DRAMCommand* last_cmds;
    uint64_t* issue_tick;
};

struct SolarDRAM {
    BloomFilter* filter;
    float strongTRCD;
    float weakTRCD;
    bool enabled;
};

struct Scheduler {
    int table_size;
    int num_banks;
    int sched_delay;
    DRAMState dram_state;
    SolarDRAM solar;
    TLRequest* req_table;
};

extern float timing_table[DRAMCommand::WR + 1][DRAMCommand::WR + 1];

void heap_check(int depth = 0);

void init_timing_table();
Scheduler& create_scheduler(int table_size, int num_banks, int sched_delay, bool use_solar);
void init_scheduler(Scheduler& mc, int table_size, int num_banks, int sched_delay, bool use_solar);
bool add_req(Scheduler& mc, TLRequest& req);
bool schedule(Scheduler& mc);
int frfcfs_ticks(Scheduler& state, TLRequest& req);
void execute_request(Scheduler& mc, TLRequest& req);
int get_first_ready(Scheduler& mc);
int get_first_come(Scheduler& mc);
int get_empty_index(Scheduler& mc);
bool is_row_hit(Scheduler& mc, TLRequest& req);
void update_state(Scheduler& mc, TLRequest& req, DRAMCommand cmd);
float get_timing(DRAMCommand prev, DRAMCommand cur);
void timing_check(Scheduler& mc, TLRequest& req, DRAMCommand cmd);

};

#endif //EASY_FRFCFS_H_