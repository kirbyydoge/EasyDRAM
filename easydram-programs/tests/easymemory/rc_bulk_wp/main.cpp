#include <stdint.h>
#include <stdlib.h>
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

#define TEST_BYTES  (16 * 1024 * 1024)
#define MAX_ROWS	TEST_BYTES / ROW_BYTES
#define N_BANKS     4

#define FLUSH_BASE  0x8f000000
#define FLUSH_SIZE	(8 * 1024 * 1024)

#define FLUSH_START_SKIP    32
#define FLUSH_PAGE_SKIP     1
#define FLUSH_PAGE_TRIES    95

#define READ_CSR(res, csr)\
__asm__ __volatile__ ("csrr %0, %1" : "=r"(res) : "n"(csr) : )

static long compatible_rows[MAX_ROWS];

void set_row(long base, int pattern) {
    int* row = (int*) base;
    for (int i = 0; i < ROW_BYTES / sizeof(int); i++) {
        row[i] = pattern;
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

bool is_rowclonable(Address src, Address tgt, int wr_pattern, int init_pattern) {
    int* base_row = (int*) (long) mapping_to_addr(src);
    int* copy_row = (int*) (long) mapping_to_addr(tgt);
    set_row((long) base_row, wr_pattern);
    set_row((long) copy_row, init_pattern);
    clflush64_row((long) base_row);
    clflush64_row((long) copy_row);
    __asm__ __volatile__ ("fence": : :"memory");
    easy::rowclone::push((long) base_row, (long) copy_row);
    __asm__ __volatile__ ("fence": : :"memory");
    while(!easy::rowclone::is_empty() || !easy::rowclone::is_idle());
    __asm__ __volatile__ ("fence": : :"memory");
    for (int i = 0; i < ROW_BYTES / sizeof(int); i++) {
        if (copy_row[i] != wr_pattern) {
            return false;
        }
    }
    return true;
}

void get_rowclone_pairs(long search_base_addr, int n_banks, int n_rows) {
    Address base = addr_to_mapping(search_base_addr);
    base.col = 0;
    Address copy = addr_to_mapping(search_base_addr);
    copy.col = 0;
    copy.row++;
    int compatible_ctr = 0;
    int copy_ctr = 0;
    ProgressBar pbar;
    pbar.init(50, '#', ' ', '[', ']', "Search: ");
    while(compatible_ctr < n_rows) {
        for (int i = 0; i < n_banks; i++) {
            base.bank = i;
            copy.bank = i;
            uint32_t src = mapping_to_addr(base);
            uint32_t dst = mapping_to_addr(copy);
            if(is_rowclonable(base, copy, WR_PATTERN + copy_ctr, copy_ctr)) {
                compatible_rows[2*compatible_ctr] = src;
                compatible_rows[2*compatible_ctr + 1] = dst;
                compatible_ctr++;
                pbar.draw(compatible_ctr, n_rows);
            }
            copy_ctr++;
        }
        base.row += 2;
        copy.row += 2;
    }
}

int main(void) {
    kprintf("Started Workload\n");

    get_rowclone_pairs((long) MEM_BASE, N_BANKS, MAX_ROWS);

    kprintf("label,size,initialize,copy,overall\n");
    for (uint64_t kib = 4; kib <= 32 * 1024; kib *= 2) {
        uint64_t begin, end;

        int compatible_ctr = (kib * 1024) / ROW_BYTES;

        for (int i = 0; i < compatible_ctr; i++) {
            clflush64_row(compatible_rows[2*i]);
            clflush64_row(compatible_rows[2*i + 1]);
        }

        __asm__ __volatile__ ("fence": : :"memory");

        READ_CSR(begin, CSR_MCYCLE);

        for (int i = 0; i < compatible_ctr; i++) {
            set_row(compatible_rows[2*i], compatible_rows[2*i]);
        }
        
        uint64_t init_ckpt = 0;
        READ_CSR(init_ckpt, CSR_MCYCLE);

        for (int i = 0; i < compatible_ctr; i++) {
            clflush64_row(compatible_rows[2*i]);
        }

        __asm__ __volatile__ ("fence": : :"memory");

        uint64_t evict_ckpt = 0;
        uint64_t req_begin, req_end = 0;
        uint64_t chipyard_bus_delay = 0;
        READ_CSR(evict_ckpt, CSR_MCYCLE);

        for (int i = 0; i < compatible_ctr; i++) {
            uint32_t src = compatible_rows[2*i];
            uint32_t dst = compatible_rows[2*i + 1];

            // Chipyard BUS has terrible latency, we remove those from our evaluation.
            chipyard_bus_delay += easy::rowclone::push(src, dst);
        }

        __asm__ __volatile__ ("fence": : :"memory");
        
        while(!easy::rowclone::is_empty() || !easy::rowclone::is_idle());

        __asm__ __volatile__ ("fence": : :"memory");

        READ_CSR(end, CSR_MCYCLE);
        uint64_t res_init = init_ckpt - begin;
        uint64_t res_evict = evict_ckpt - init_ckpt;
        uint64_t res_clone = end - evict_ckpt - chipyard_bus_delay;
        uint64_t res_overall = end - begin - chipyard_bus_delay;
        kprintf("ROWCOPY,%d,%d,%d,%d,%d\n", kib, res_init, res_evict, res_clone, res_overall);
        
    }
    return 0;
}
