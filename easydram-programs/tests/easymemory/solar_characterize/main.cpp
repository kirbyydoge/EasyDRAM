#include <stdint.h>

#include "kprintf.h"
#include "EasySolar.h"
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
#define MAX_ROWS	8192
#define N_BANKS     4

#define MAX_ITERS   5
#define PAGE_SIZE   (2 * 1024)
#define MAX_COLS    PAGE_SIZE / CACHE_LINE

#define READ_CSR(res, csr)\
__asm__ __volatile__ ("csrr %0, %1" : "=r"(res) : "n"(csr) : )

static uint32_t results[MAX_ROWS];
static float lowest[MAX_ITERS][MAX_ROWS];

void solar_characterize(Address start, float start_tRCD, float min_tRCD, float step, int numCols, int numRows, int numIters) {
    ProgressBar pbar;
    for (int i = 0; i < MAX_ITERS; i++) {
        for (int j = 0; j < MAX_ROWS; j++) {
            lowest[i][j] = INVALID_tRCD;
        }
    }
    Address target = start;
    int drawSteps = numCols * std::abs((start_tRCD - min_tRCD) / step);
    int drawCtr = 0;
    for (int iter = 0; iter < numIters; iter++) {
        kprintf("*** ITERATION %d ***\n", iter);
        for (int col = 0; col < numCols; col++) {
            float cur_tRCD = start_tRCD;
            while (cur_tRCD > min_tRCD) {
                easy::solar::req::set_tRCD(cur_tRCD);
                int resultPtr = 0;
                for (int i = 0; i < numRows; i++) {
                    while(!easy::solar::resp::is_empty()) {
                        auto resp = easy::solar::resp::pop();
                        results[resultPtr++] = resp.result;
                    }
                    target.row = start.row + i;
                    easy::solar::req::push(target);
                }
                while(resultPtr < numRows) {
                    if(!easy::solar::resp::is_empty()) {
                        auto resp = easy::solar::resp::pop();
                        results[resultPtr++] = resp.result;
                    }
                }
                int readFail = 0;
                int timingFail = 0;
                for (int i = 0; i < numRows; i++) {
                    if (results[i] > 0) {
                        readFail++;
                    }
                    else {
                        lowest[iter][i] = cur_tRCD;
                    }
                }
                cur_tRCD += step;
            }
        }
    }
    kprintf("\n\n\n=== ROW CHARACTERIZATION RESULTS ===\n");
    for (int i = 0; i < numRows; i++) {
        float min = lowest[0][i];
        for (int j = 1; j < numIters; j++) {
            float cur = lowest[j][i];
            if (cur < min) {
                min = cur;
            }
        }
        target.row = start.row + i;
        kprintf("ROW %d (0x%x): ", i, mapping_to_addr(target)); kfloat(min); kprintf("\n");
    }
    kprintf("\n\n\n=== ROW CHARACTERIZATION RESULTS ===\n\n\n");
}
int main(void) {
    kprintf("Started Workload\n");

    uint64_t begin, end;

	READ_CSR(begin, CSR_MCYCLE);

    Address base = get_addr_mapping(MEM_BASE);
    base.col = 0;

    for (int bank = 0; bank < N_BANKS; bank++) {
        base.bank = bank;
        base.col = 0;
        kprintf("Characterizing bank %d:\n", bank);
        solar_characterize(base, 15.0f, 3.0f, -1.5f, MAX_COLS, MAX_ROWS, MAX_ITERS);
    }

	READ_CSR(end, CSR_MCYCLE);
	kprintf("End Cycle: %uld\n", end);
	kprintf("Overall benchmark took: %uld cycles.\n", end - begin);
	kprintf("Total Request Count: %uld\n", EasyDebug::schedCount);
	kprintf("Main Memory Read Reqs: %d\nTotal Memory Write Reqs: %d\n", EasyDebug::schedRdCount, EasyDebug::schedWrCount);

    kprintf("Done\n");
    return 0;
}
