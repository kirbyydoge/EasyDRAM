#include <stdint.h>

#include "SimCommon.h"
#include "proghelp.h"
#define TEST_BYTES	    (1024)
#define TEST_LEN		(TEST_BYTES / sizeof(int))

#define PATTERN			0x5fce10f5

#define READ_CSR(res, csr)\
__asm__ __volatile__ ("csrr %0, %1" : "=r"(res) : "n"(csr) : )

void restore_state(volatile int* open_rows, int num_banks) {
    for (int i = 0; i < num_banks; i++) {
        if (open_rows[i] >= 0) {
            printf("DUM DUM\n");
        }
    }
	printf("Restored State\n");
}

int main(void) {
	program_mc();
	printf("Started Workload\n");

	volatile int check[] = {-3, -3, -3, -3};
	restore_state(check, 4);
	int* src = (int*) 0x80010000;
	int* dst = (int*) (long) (src + TEST_LEN);
	printf("src: %x dst: %x\n", src, dst);
	
	uint64_t begin, end;
	printf("Starting Init\n");
	READ_CSR(begin, CSR_MCYCLE);

	for (int i = 0; i < TEST_LEN; i++) {
		src[i] = PATTERN;
    }

	uint64_t init_ckpt = 0;
	READ_CSR(init_ckpt, CSR_MCYCLE);

	for (int i = 0; i < TEST_LEN; i++) {
		dst[i] = src[i];
	}
	
	READ_CSR(end, CSR_MCYCLE);
	printf("Begin Cycle: %u\n", begin);
	printf("End Cycle: %u\n", end);
	printf("Initalization took: %u cycles.\n", init_ckpt - begin);
	printf("Copying took: %u cycles.\n", end - init_ckpt);
	printf("Overall operation took: %u cycles.\n", end - begin);
	
    return 0;
}
