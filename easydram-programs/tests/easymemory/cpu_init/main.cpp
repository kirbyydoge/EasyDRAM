#include <stdint.h>

#include <platform.h>

#include "common.h"

#define DEBUG
#include "kprintf.h"
#include "EasyClone.h"
#include "EasyDebug.h"
// #include "ProgressBar.h"

#define TEST_BYTES  (32 * 1024 * 1024)
#define TEST_LEN	(TEST_BYTES / sizeof(int))

#define PATTERN			0x5fce10f5

#define PRINT_REG64(reg) kprintf(#reg ": %uld\n", REF64(reg))
#define PRINT_REG32(reg) kprintf(#reg ": %uld\n", REF32(reg))

void clflush64(uint64_t base) {
	REF64(L2_CTRL_BASE_ADDR + L2_CTRL_FL64_OFF) = base;
}

int main(void) {
	kprintf("Started Workload\n");

	int* src = (int*) 0x81000000;

    kprintf("label,size,initialize,copy,overall\n");
	for (uint32_t kib = 4; kib <= 16 * 1024; kib *= 2) {
		long src_base = (long) src;
		for (uint32_t flush = 0; flush < (kib * 1024); flush += CACHELINE_BYTES) {
			clflush64(src_base + flush * CACHELINE_BYTES);
		}

		uint64_t begin, end;
		READ_CSR(begin, CSR_MCYCLE);

		uint64_t* src_opt = (uint64_t*) src;
		uint64_t pattern_opt = (uint64_t) PATTERN << 32 | PATTERN;
		for (int i = 0; i < kib * 1024 / sizeof(uint64_t); i++) {
			src_opt[i] = pattern_opt;
		}
		
		READ_CSR(end, CSR_MCYCLE);

		uint64_t res_init = end - begin;
		uint64_t res_evict = 0;
		uint64_t res_clone = 0;
		uint64_t res_overall = end - begin;
		kprintf("CPUINIT,%d,%d,%d,%d,%d\n", kib, res_init, res_evict, res_clone, res_overall);
	}
	return 0;
}
