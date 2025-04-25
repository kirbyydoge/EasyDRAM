#include <stdint.h>

#include "EasyCommon.h"
#include "kprintf.h"
#include "EasyClone.h"
#include "EasyDebug.h"
#include "ProgressBar.h"

#define MEM_BASE	0x81000000
#define ROW_BYTES   (2 * 1024)
#define COL_MASK    0x00000FFF
#define WR_PATTERN  0x55555555

#define CACHE_SIZE  (512 * 1024)
#define CACHE_WAYS  8	
#define CACHE_LINE	64
#define CACHE_SETS	(CACHE_SIZE / CACHE_WAYS / CACHE_LINE)

#define TEST_BYTES  (2 * 1024 * 1024)
// #define MAX_ROWS	TEST_BYTES / ROW_BYTES
#define MAX_ROWS	10
#define N_BANKS     1

#define FLUSH_BASE  0x8f000000
#define FLUSH_SIZE	(8 * 1024 * 1024)

#define FLUSH_START_SKIP    32
#define FLUSH_PAGE_SKIP     1
#define FLUSH_PAGE_TRIES    95

#define READ_CSR(res, csr)\
__asm__ __volatile__ ("csrr %0, %1" : "=r"(res) : "n"(csr) : )

void set_row(long base, int pattern) {
    int* row = (int*) base;
    for (int i = 0; i < ROW_BYTES / sizeof(int); i++) {
        row[i] = pattern;
    }
}

void evict_cache_line(long base) {
    uint32_t stride = CACHE_SIZE / CACHE_WAYS;
    volatile uint8_t* block = (volatile uint8_t*) (base + FLUSH_START_SKIP * stride);
    for (int i = 0; i < FLUSH_PAGE_TRIES; i++) {
        uint8_t temp = *block;
        block += stride;
    }
}

void clflush64(uint64_t base) {
	REF64(L2_CTRL_BASE_ADDR + L2_CTRL_FL64_OFF) = base;
}

void clflush64_row(long base) {
	for (int i = 0; i < ROW_BYTES / CACHE_LINE; i++) {
		clflush64(base);
		base += CACHE_LINE;
	}
}

void cache_flush() {
    volatile uint8_t* unused = (volatile uint8_t*) FLUSH_BASE;
    for (int i = 0; i < FLUSH_SIZE; i++) {
        uint8_t temp = *unused;
        unused++;
    }
}

void evict_row(long base) {
    for (int i = 0; i < ROW_BYTES / CACHE_LINE; i++) {
        evict_cache_line(base);
        base += CACHE_LINE;
    }
}

bool is_rowclonable(Address src, Address tgt, int wr_pattern, int init_pattern) {
    int* base_row = (int*) (long) mapping_to_addr(src);
    int* copy_row = (int*) (long) mapping_to_addr(tgt);
    set_row((long) base_row, wr_pattern);
    set_row((long) copy_row, init_pattern);
    clflush64_row((long) base_row);
    clflush64_row((long) copy_row);
    push_rowclone_req((long) base_row, (long) copy_row);
    while(!is_rowclone_empty());
    for (int i = 0; i < ROW_BYTES / sizeof(int); i++) {
        if (copy_row[i] != wr_pattern) {
            // kprintf("[ERR] %x (i=%d) found %x but expected %x ", &copy_row[i], i, copy_row[i], wr_pattern);
            if (copy_row[i] == TIMEOUT_PATTERN) {
                kprintf("\n[CRITICAL] %x (i=%d) TIMEOUT_PATTERN found!", &copy_row[i], i, copy_row[i], TIMEOUT_PATTERN);
            }
            return false;
        }
    }
    return true;
}

#define GHz                 1000000000ULL
#define MHz                 1000000ULL
#define PS_IN_SECS          1000000000000UL
#define PS_IN_NS            1000UL

uint64_t check_ticks(uint64_t delay_ns, uint64_t target_freq) {
    uint64_t period_ps = PS_IN_SECS / target_freq;
    uint64_t delay_ps = PS_IN_NS * delay_ns;
    return delay_ps / period_ps / EasyCMD::mcTileRatio; 
}

int main(void) {
    kprintf("Started Workload\n");

    uint32_t prev_ddr_count = 50;
    kprintf("Scaling Test For %d\n", prev_ddr_count);
    kprintf("100 MHz: %d\n", check_ticks(prev_ddr_count, 100 * MHz));
    kprintf("1   GHz: %d\n", check_ticks(prev_ddr_count, 1   * GHz));

    uint64_t begin, end;

	READ_CSR(begin, CSR_MCYCLE);

    Address base = get_addr_mapping(MEM_BASE);
    base.col = 0;

    Address copy = get_addr_mapping(MEM_BASE);
    copy.col = 0;
    copy.row++;

    set_rowclone_enabled(false);

    push_rowclone_req(mapping_to_addr(base), mapping_to_addr(copy));
    push_rowclone_req(mapping_to_addr(base), mapping_to_addr(copy));
    push_rowclone_req(mapping_to_addr(base), mapping_to_addr(copy));
    push_rowclone_req(mapping_to_addr(base), mapping_to_addr(copy));
    push_rowclone_req(mapping_to_addr(base), mapping_to_addr(copy));
    push_rowclone_req(mapping_to_addr(base), mapping_to_addr(copy));
    push_rowclone_req(mapping_to_addr(base), mapping_to_addr(copy));
    push_rowclone_req(mapping_to_addr(base), mapping_to_addr(copy));
    push_rowclone_req(mapping_to_addr(base), mapping_to_addr(copy));
    push_rowclone_req(mapping_to_addr(base), mapping_to_addr(copy));

    __asm__ __volatile__ ("fence": : :"memory");

    set_rowclone_enabled(true);

	READ_CSR(begin, CSR_MCYCLE);
	kprintf("Begin Cycle: %uld\n", begin);
    
    while(!is_rowclone_empty() || !is_rowclone_idle());

    __asm__ __volatile__ ("fence": : :"memory");

	READ_CSR(end, CSR_MCYCLE);
	kprintf("End Cycle: %uld\n", end);
	kprintf("Overall benchmark took: %uld cycles.\n", end - begin);
    kprintf("Done\n");

    return 0;
}
