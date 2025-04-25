#include "EasyAPI.h"
#include "EasyCMD.h"
#include "EasyFRFCFS.h"

// #define MC_DEBUG
#include "mc_uart.h"

#define REF_DUR      tREF
#define NS_IN_MS     1000000
#define N_ROWS       32768
#define REF_CLK_PER  10
#define N_REF_DUR_NS (64 * NS_IN_MS)
#define REF_PERIOD   (N_REF_DUR_NS / N_ROWS / REF_CLK_PER)
#define SCHED_CYCLES 5
#define CPU_HZ       100000000
#define BAUD_RATE    921600

#define SCHED_GAP    15

extern uint32_t __stack_size;

int main(void) {
    set_cfg_is_timescaled(false);
    uart_set_baud(CPU_HZ / BAUD_RATE);
    ee_printf("Hello There\n");
    static_latency = 140;
    FRFCFS::init_timing_table();
    FRFCFS::Scheduler& mc = FRFCFS::create_scheduler(16, 4, SCHED_CYCLES, false);
    TLRequest req;
    bool sched_state = false;
    bool req_valid = false;
    base_init(REF_DUR, REF_PERIOD);
    ee_printf("Begin Sched\n");
    while(true) {
        if (!is_req_empty() && !req_valid) {
            sched_state = true;
            set_scheduling_state(true); 
            req = get_request();
            ee_printf("%s %x 0x%x\n", req.is_write ? "W" : "R", req.tl_source, req.tl_addr);
            req_valid = true;
        }
        if (req_valid && FRFCFS::add_req(mc, req)) {
            req_valid = false;
            continue;
        }
        sched_state = FRFCFS::schedule(mc);
        set_scheduling_state(sched_state); 
    }
	return 0;
}
