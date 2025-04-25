#include <stdint.h>

#include "kprintf.h"
#include "EasyClone.h"

#define MEM_BASE	0x81000000
#define ROW_BYTES   (2 * 1024)
#define COL_MASK	0x00000FFF
#define WR_PATTERN	0x55555555

#define CACHE_SIZE	(512 * 1024)
#define CACHE_WAYS	8	
#define CACHE_LINE	64
#define CACHE_SETS	(CACHE_SIZE / CACHE_WAYS / CACHE_LINE)

#define FLUSH_BASE  0x83000000
#define FLUSH_SIZE	(8 * 1024 * 1024)

#define FLUSH_START_SKIP  	32
#define FLUSH_PAGE_SKIP		1
#define FLUSH_PAGE_TRIES	95

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
	
	Address base = get_addr_mapping(MEM_BASE);
	base.col = 0;
	int* mem = (int *) (long) mapping_to_addr(base);
	
	base.row++;
	int* copy_row = (int*) (long) mapping_to_addr(base);

	kprintf("Writing Row\n");
	set_row((long) mem, WR_PATTERN);
	set_row((long) copy_row, WR_PATTERN); // <-- !! DEBUG !! TODO: Don't forget reverting this

	// kprintf("Evicting Row\n");
	// evict_row((long) mem);
	// kprintf("Evicting Target Row\n");
	// evict_row((long) copy_row);

	kprintf("Cache Flush\n");
	cache_flush();

	// kprintf("Requesting RowClone\n");
	// push_rowclone_req((long) mem, (long) copy_row);

	kprintf("Checking...\n");
	for (int i = 0; i < ROW_BYTES / sizeof(int); i++) {
		if (copy_row[i] != WR_PATTERN) {
			kprintf("[ERR] At %x found %x but expected %x.\n", &copy_row[i], copy_row[i], WR_PATTERN);
		}
	}

	kprintf("Done\n");
	return 0;
}
