#ifndef PROGHELP_H_
#define PROGHELP_H_

#include "embed.h"
#include <stdio.h>

void program_mc() {
	volatile uint64_t* instPtr = (volatile uint64_t*) 0x40000000;
    volatile uint32_t* cmdPtr = (volatile uint32_t*) 0x41000000;
    printf("Sending MC.\n");
	const uint64_t* embedPrg = (const uint64_t*) bin_program;
	int embedPrgLen = (len_program + sizeof(uint64_t) - 1) / sizeof(uint64_t);
    for (int i = 0; i < embedPrgLen; i++) {
		instPtr[i] = embedPrg[i]; 
    }
	cmdPtr[0] = 1;
    printf("MC programmed.\n");
}

#endif