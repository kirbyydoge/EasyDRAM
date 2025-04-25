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
#define ROW_BYTES	(2 * 1024)
#define TEST_TILING	(ROW_BYTES / sizeof(uint64_t))

#define PATTERN			0x0

#define PRINT_REG64(reg) kprintf(#reg ": %uld\n", REF64(reg))
#define PRINT_REG32(reg) kprintf(#reg ": %uld\n", REF32(reg))

int main(void) {
	kprintf("Started Workload\n");

	// flush_all();

	int* src = (int*) 0x84000000;
	int* dst = (int*) 0x88000000;
	Address asrc = addr_to_mapping((long) src);
	Address adst = addr_to_mapping((long) dst);
	adst.bank = (adst.bank + 2) % 4;
	dst = (int*) mapping_to_addr(adst);
	kprintf("src: %d %d %d\n", asrc.bank, asrc.row, asrc.col);
	kprintf("dst: %d %d %d\n", adst.bank, adst.row, adst.col);
	// Address addr = get_addr_mapping((long) (src + TEST_LEN));
	// addr.bank = (addr.bank + 1) % 4;
	// dst = (int*) (long) mapping_to_addr(addr);

    kprintf("label,size,initialize,evict,copy,overall,misc0,misc1,misc2,misc3,misc4,misc5\n");
	for (uint32_t kib = 4; kib <= 16 * 1024; kib *= 2) {
		flush_all();

		FENCE();

		uint64_t misc0_begin = EasyCMD::statCounter0;
		uint64_t misc1_begin = EasyCMD::statCounter1;
		uint64_t misc2_begin = EasyCMD::statCounter2;
		uint64_t misc3_begin = EasyCMD::statCounter3;
		uint64_t misc4_begin = EasyCMD::statCounter4;
		uint64_t misc5_begin = EasyCMD::statCounter5;
		uint64_t begin, end;
		READ_CSR(begin, CSR_MCYCLE);

		uint64_t* src_opt = (uint64_t*) src;
		uint64_t pattern_opt = (uint64_t) PATTERN << 32 | PATTERN;
		uint64_t pattern_inc = (uint64_t) 2 << 32 | 1;
		for (int i = 0; i < kib * 1024 / sizeof(uint64_t); i++) {
			src_opt[i] = pattern_opt;
			pattern_opt += pattern_inc;
		}

		uint64_t init_ckpt = 0;
		READ_CSR(init_ckpt, CSR_MCYCLE);

		src_opt = (uint64_t*) src;
		uint64_t* dst_opt = (uint64_t*) dst;
		volatile uint64_t cache_load = 0;
		for (int i = 0; i < kib * 1024 / sizeof(uint64_t); i++) {
			// for (int j = 0; j < TEST_TILING; j++) {
			// 	cache_load += src_opt[i + j];
			// }
			// for (int j = 0; j < TEST_TILING; j++) {
			// 	dst_opt[i + j] = src_opt[i + j];
			// }
			dst_opt[i] = src_opt[i];
		}
		
		READ_CSR(end, CSR_MCYCLE);

		uint64_t misc0_end = EasyCMD::statCounter0;
		uint64_t misc1_end = EasyCMD::statCounter1;
		uint64_t misc2_end = EasyCMD::statCounter2;
		uint64_t misc3_end = EasyCMD::statCounter3;
		uint64_t misc4_end = EasyCMD::statCounter4;
		uint64_t misc5_end = EasyCMD::statCounter5;

		uint64_t res_init = init_ckpt - begin;
		uint64_t res_evict = 0;
		uint64_t res_clone = end - init_ckpt;
		uint64_t res_overall = end - begin;
		uint64_t misc0 = misc0_end - misc0_begin;
		uint64_t misc1 = misc1_end - misc1_begin;
		uint64_t misc2 = misc2_end - misc2_begin;
		uint64_t misc3 = misc3_end - misc3_begin;
		uint64_t misc4 = misc4_end - misc4_begin;
		uint64_t misc5 = misc5_end - misc5_begin;
		kprintf("CPUCOPY,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d\n", kib, res_init, res_evict,
			res_clone, res_overall, misc0, misc1, misc2, misc3, misc4, misc5);
	}
	return 0;
}
