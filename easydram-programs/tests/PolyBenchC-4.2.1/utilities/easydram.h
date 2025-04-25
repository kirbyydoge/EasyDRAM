#ifndef EASYDRAM_POLY_H_
#define EASYDRAM_POLY_H_

#include <stdint.h>

extern char* MEM_BASE;

int easy_memallign(void** ptr, long allign, long size);

#endif