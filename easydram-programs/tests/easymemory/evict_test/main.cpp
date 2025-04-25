#include <stdint.h>

#include "kprintf.h"
#include "EasyClone.h"

#define MEM_BASE	0x81000000
#define ROW_BYTES   (2 * 1024)
#define WR_PATTERN	0x55555555

#define CACHE_SIZE	(512 * 1024)
#define CACHE_WAYS	8	
#define CACHE_LINE	64
#define CACHE_SETS	(CACHE_SIZE / CACHE_WAYS / CACHE_LINE)

#define ITERS		10
#define MAX_ROWS	10

#define FLUSH_BASE  0x83000000
#define FLUSH_SIZE	(1 * 1024 * 1024)

#define FLUSH_START_SKIP  	32
#define FLUSH_PAGE_SKIP		4
#define FLUSH_PAGE_TRIES	16

#define READ_CSR(res, csr)\
__asm__ __volatile__ ("csrr %0, %1" : "=r"(res) : "n"(csr) : )

void set_row(long base, int pattern) {
	int* row = (int*) base;
	for (int i = 0; i < ROW_BYTES / sizeof(int); i++) {
		row[i] = WR_PATTERN;
	}
}

void evict_cache_line(long base) {
	volatile uint8_t* block = (volatile uint8_t*) (base + FLUSH_START_SKIP * CACHE_SIZE);
	for (int i = 0; i < FLUSH_PAGE_TRIES; i++) {
		for (int j = 0; j < CACHE_LINE; j++) {
			uint8_t temp = *(block + j);
		}
		block += FLUSH_PAGE_SKIP * CACHE_SIZE;
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

int main(void) {
	kprintf("Started Workload\n");

	uint64_t begin, end;

	int result = 0;

	READ_CSR(begin, CSR_MCYCLE);
	// result += REF32(MEM_BASE - 3 * ROW_BYTES);
	set_row(MEM_BASE - 3 * ROW_BYTES, 0);
	READ_CSR(end, CSR_MCYCLE);
	kprintf("Warmup: %uld cycles.\n", end - begin);

	for (int i = 0; i < ITERS; i++) {
		READ_CSR(begin, CSR_MCYCLE);
		// result += REF32(MEM_BASE);
		set_row(MEM_BASE, WR_PATTERN);
		READ_CSR(end, CSR_MCYCLE);
		kprintf("Read: %uld cycles.\n", end - begin);
	}

	kprintf("Evicting... ");
	READ_CSR(begin, CSR_MCYCLE);
	// evict_cache_line(MEM_BASE);
	// evict_row(MEM_BASE);
	clflush64_row(MEM_BASE);
	// cache_flush();
	READ_CSR(end, CSR_MCYCLE);
	kprintf("took %uld cycles.\n", end - begin);

	for (int i = 0; i < ITERS; i++) {
		READ_CSR(begin, CSR_MCYCLE);
		// result += REF32(MEM_BASE);
		set_row(MEM_BASE, WR_PATTERN);
		READ_CSR(end, CSR_MCYCLE);
		kprintf("Read: %uld cycles.\n", end - begin);
	}
	
	kprintf("Done\n");
	return 0;
}
