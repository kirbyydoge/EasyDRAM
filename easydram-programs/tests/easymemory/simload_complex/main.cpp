#include <stdint.h>

// #include <platform.h>

// #include "common.h"

#define DEBUG
#include <stdio.h>
// #include "kprintf.h"
// #include "EasyClone.h"
// #include "EasyDebug.h"
// #include "ProgressBar.h"
// #include "proghelp.h"

#define TEST_BYTES  (64 * 1024)
#define TEST_LEN	(TEST_BYTES / sizeof(int))
#define ROW_BYTES	(2 * 1024) 
#define TEST_TILING	(ROW_BYTES / sizeof(uint64_t))

#define PATTERN			0x0

#define PRINT_REG64(reg) printf(#reg ": %lu\n", REF64(reg))
#define PRINT_REG32(reg) printf(#reg ": %lu\n", REF32(reg))

int main(void) {
	printf("Started Workload\n");

	volatile uint8_t* mem_base = (volatile uint8_t*) 0x81000000;
	for (int i = 0; i < TEST_BYTES; i++) {
		mem_base[i] = i;
	}

	printf("Finished\n");
	return 0;
}
