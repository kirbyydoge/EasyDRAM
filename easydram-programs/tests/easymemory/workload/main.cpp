#include <stdint.h>

#include <platform.h>

#include "common.h"

#define DEBUG
#include "kprintf.h"
#include "EasyClone.h"
#include "EasyDebug.h"

#define TEST_BYTES	    (64 * 1024 * 1024)
#define TEST_LEN		(TEST_BYTES / sizeof(int))

#define PATTERN			0x5fce10f5

#define PRINT_REG64(reg) kprintf(#reg ": %uld\n", REF64(reg))
#define PRINT_REG32(reg) kprintf(#reg ": %uld\n", REF32(reg))

int main(void) {
	kprintf("Started Workload\n");

	int* vec0 = (int*) 0x81000000;
	kprintf("vec0: %x\n", vec0);

	Address addr = get_addr_mapping((uint64_t) vec0);
	addr.row++;
	
	uint64_t begin, end;
	READ_CSR(begin, CSR_MCYCLE);
	kprintf("Begin Cycle: %uld\n", begin);

	for (int i = 0; i < TEST_LEN; i++) {
		vec0[i] = PATTERN;
		if (i % (TEST_LEN / 100) == 0) {
			kprintf("Completed: %d%%\n", (i + 1) * 100 / TEST_LEN);
		}
	}

	uint64_t write_ckpt = 0;
	READ_CSR(write_ckpt, CSR_MCYCLE);
	kprintf("Done writing\n");
	
	int res = 0;

	for (int i = 0; i < TEST_LEN; i++) {
		if (vec0[i] != PATTERN) {
			kprintf("ERR: at %x, got %d\n", &vec0[i], vec0[i]);
		}
		if (i % (TEST_LEN / 100) == 0) {
			kprintf("Completed: %d%%\n", (i + 1) * 100 / TEST_LEN);
		}
		// kprintf("%x: %x\n", &vec0[i], vec0[i]);
	}
	
	kprintf("Done reading\n");
	READ_CSR(end, CSR_MCYCLE);
	kprintf("End Cycle: %uld\n", end);
	kprintf("Writing took: %uld cycles.\n", write_ckpt - begin);
	kprintf("Reading took: %uld cycles.\n", end - write_ckpt);
	kprintf("Operation took: %uld cycles.\n", end - begin);
	kprintf("Total Request Count: %uld\n", EasyDebug::schedCount);
	kprintf("Main Memory Read Reqs: %d\nTotal Memory Write Reqs: %d\n", EasyDebug::schedRdCount, EasyDebug::schedWrCount);
	return 0;
}
