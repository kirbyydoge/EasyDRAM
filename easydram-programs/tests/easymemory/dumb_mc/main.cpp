#include "EasyAPI.h"
#include "EasyCMD.h"
#include "EasyDebug.h"

#define SCHED_CYCLES 5

int main(void) {
    target_freq = (uint64_t) (1.479f * GHz);
    while(true) {
        if (!is_req_empty()) {
            set_scheduling_state(true);
            TLRequest req = get_request();
            if (req.is_write)   EasyDebug::schedWrCount++;
            else                EasyDebug::schedRdCount++;
            EasyDebug::schedCount++;
            basic_auto_schedule(req);
            EasyCMD::mcTicks += SCHED_CYCLES;
            set_scheduling_state(false);
        }
    }
	return 0;
}