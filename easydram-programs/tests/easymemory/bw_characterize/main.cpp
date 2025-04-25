#include <stdint.h>

#include "kprintf.h"
#include "EasyBitwise.h"
#include "EasyDebug.h"
#include "ProgressBar.h"
#include <cmath>

#define INVALID_tRCD -1.0f

#define MEM_BASE	0x81000000
#define ROW_BYTES   (2 * 1024)
#define COL_MASK    0x00000FFF
#define WR_PATTERN  0x55555555

#define CACHE_SIZE  (512 * 1024)
#define CACHE_WAYS  8	
#define CACHE_LINE	64
#define CACHE_SETS	(CACHE_SIZE / CACHE_WAYS / CACHE_LINE)

#define TEST_BYTES  (2 * 1024 * 1024)
#define MAX_ROWS	4096
#define N_BANKS     4

#define MAX_ITERS   5
#define PAGE_SIZE   (2 * 1024)
#define MAX_COLS    PAGE_SIZE / CACHE_LINE

#define NS_IN_SEC   1000000000
#define CLK_PERIOD  10
#define CLK_IN_SEC  (NS_IN_SEC / CLK_PERIOD)

#define READ_CSR(res, csr)\
__asm__ __volatile__ ("csrr %0, %1" : "=r"(res) : "n"(csr) : )

using BitwiseReq = easy::bitwise::req::Request;
using BitwiseResp = easy::bitwise::resp::Response;

int main(void) {
    kprintf("Started Workload\n");

    uint64_t begin, end;

	READ_CSR(begin, CSR_MCYCLE);

    easy::bitwise::req::set_init_pattern(0);
    easy::bitwise::req::set_write_pattern(WR_PATTERN);

    BitwiseReq req;

    Address base = get_addr_mapping(MEM_BASE);
    base.col = 0;

    req.row0 = base;
    req.row1 = base;

    for (int bank = 0; bank < 8; bank++) {
        kprintf("*** BANK %d ***\n", bank % 4);
        req.row0.bank = bank % 4;
        req.row1.bank = bank % 4;
        for (int off0 = 0; off0 <= 8; off0++) {
            for (int off1 = 0; off1 <= 8; off1++) {
                if (off0 == off1) {
                    continue;
                }
                req.row0.row = base.row + 0; // off0;
                req.row1.row = base.row + 8; // off1;
                kprintf("*** Testing rows %d - %d ***\n", req.row0.row, req.row1.row);
                for (int t1 = 1; t1 <= 16; t1++) {
                    for (int t2 = 1; t2 <= 16; t2++) {
                        req.t1 = t1;
                        req.t2 = t2;
                        easy::bitwise::req::push(req);
                        // kprintf("\n-- %d | %d | 0x%uld --\n",
                        //     REF64(easy::bitwise::req::EBW_HEAD_REG),
                        //     REF64(easy::bitwise::req::EBW_TAIL_REG),
                        //     REF64(easy::bitwise::req::EBW_DATA_BASE));
                        // BitwiseReq req = easy::bitwise::req::pop();
                        BitwiseResp resp = easy::bitwise::resp::pop();
                        // kprintf("-- %d | %d | 0x%uld --\n",
                        //     REF64(easy::bitwise::req::EBW_HEAD_REG),
                        //     REF64(easy::bitwise::req::EBW_TAIL_REG),
                        //     REF64(easy::bitwise::req::EBW_DATA_BASE));
                        kprintf("t1: %d t2: %d result: %d\n", t1, t2, resp.result);
                    }
                }
            }
        }
    }

	READ_CSR(end, CSR_MCYCLE);
	kprintf("End Cycle: %uld\n", end);
	kprintf("Overall benchmark took: %uld cycles.\n", end - begin);
	kprintf("Total Request Count: %uld\n", EasyDebug::schedCount);
	kprintf("Main Memory Read Reqs: %d\nTotal Memory Write Reqs: %d\n", EasyDebug::schedRdCount, EasyDebug::schedWrCount);

    kprintf("Done\n");
    return 0;
}
