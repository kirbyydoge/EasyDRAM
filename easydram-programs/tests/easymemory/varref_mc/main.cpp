#include "EasyAPI.h"
#include "EasyCMD.h"
#include "EasyFRFCFS.h"
#include "mc_uart.h"

#define SCHED_CYCLES 5
#define CPU_HZ       100000000
#define BAUD_RATE    921600

int main(void) {
    uart_set_baud(CPU_HZ / BAUD_RATE);
    ee_printf("Hello from FRFCFS!\n");
    target_freq = (uint64_t) (1.479f * GHz);
    // target_freq = (uint64_t) (50 * MHz);
    // static_latency = 0;
    FRFCFS::init_timing_table();
    FRFCFS::Scheduler& mc = FRFCFS::create_scheduler(16, 4, SCHED_CYCLES, true);
    TLRequest req;
    bool req_valid = false;
    int idle_ctr = 0;
    uint64_t sched_ctr = 0;
    int ctr = 0;
    while(true) {
        if (!is_req_empty() && !req_valid) {
            set_scheduling_state(true);
            req = get_request();
            req_valid = true;
        }
        if (req_valid && FRFCFS::add_req(mc, req)) {
            req_valid = false;
            continue;
        }
        bool sched_state = FRFCFS::schedule(mc);
        set_scheduling_state(sched_state);
        if (sched_state) {
            if (++sched_ctr % 100000 == 0) {
                ee_printf("Scheduling Log: %ld\n", sched_ctr);
            }
            idle_ctr = 0;
        }
        if (++idle_ctr % 1000000 == 0) {
            ee_printf("Idle tile:%lu mc:%lu sched:%d\n",
                EasyCMD::tileTicks, EasyCMD::mcTicks, sched_state);
        }
    }
	return 0;
}