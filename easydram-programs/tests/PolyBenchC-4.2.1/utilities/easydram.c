#include "easydram.h"

char* MEM_BASE = (char*) 0x81000000;
char* MEM_HIGH = (char*) 0x8fffffff;

int easy_memallign(void** ptr, long allign, long size) {
    long mem_addr = (long) MEM_BASE;
    long extra = (mem_addr % allign);
    if (extra) {
        mem_addr += allign - extra;
    }
    *ptr = (void*) mem_addr;
    MEM_BASE = (char*) (mem_addr + size);
    return MEM_BASE > MEM_HIGH;
}