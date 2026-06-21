// See LICENSE.Sifive for license details.
#include <stdint.h>

#include <platform.h>

#include "common.h"

#define DEBUG
#include "kprintf.h"

// Total payload in B
#define PAYLOAD_SIZE_B (30 << 20) // default: 30MiB
// A sector is 512 bytes, so (1 << 11) * 512B = 1 MiB
#define SECTOR_SIZE_B 512
// Payload size in # of sectors
#define PAYLOAD_SIZE (PAYLOAD_SIZE_B / SECTOR_SIZE_B)

// The sector at which the BBL partition starts
#define BBL_PARTITION_START_SECTOR 34

#ifndef TL_CLK
#error Must define TL_CLK
#endif

#define F_CLK TL_CLK

int main(void)
{
	REG32(uart, UART_REG_TXCTRL) = UART_TXEN;

	uint64_t* easyMemBase = (uint64_t*) 0x80000000;
	uint64_t* easyMemData = (uint64_t*) 0x81000000;

	for (int i = 0; i < 16; i++) {
		kprintf("%lx\n", easyMemBase[i]);
	}

	for (int i = 0; i < 16; i++) {
		kprintf("%lx\n", easyMemData[i]);
	}

	__asm__ __volatile__ ("fence.i" : : : "memory");

	return 0;
}
