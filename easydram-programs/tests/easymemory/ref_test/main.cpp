#include <stdint.h>

#include <platform.h>

#include "common.h"

#define DEBUG
#include "kprintf.h"
#include "EasyClone.h"
#include "EasyDebug.h"
// #include "ProgressBar.h"

#define TEST_BEGIN      0x81000000
#define TEST_END        0x8f000000
#define WR_PATTERN      0xdeadbeef
#define NS_IN_MS        1000000
#define CLK_PERIOD      20
#define WAIT_MS         (64 * 2)

#define PATTERN			0x0

#define PRINT_REG64(reg) kprintf(#reg ": %uld\n", REF64(reg))
#define PRINT_REG32(reg) kprintf(#reg ": %uld\n", REF32(reg))

int main(void) {
	kprintf("Started Workload\n");
	flush_all();
    kprintf("Flushed\n");
    for (uint32_t* mem = (uint32_t*) TEST_BEGIN; (long) mem < TEST_END; mem++) {
        *mem = WR_PATTERN;
    }
    kprintf("Wrote\n");
	flush_all();
    kprintf("Flushed\n");
    uint32_t iter = 1;
    while (true) {
        uint64_t time = 0;
        READ_CSR(time, CSR_MCYCLE);
        uint64_t target = time + WAIT_MS * NS_IN_MS / CLK_PERIOD;
        kprintf("Waiting...\n");
        while (time < target) {
            READ_CSR(time, CSR_MCYCLE);
        }
        kprintf("ITER %d\n", iter++);
        for (uint32_t* mem = (uint32_t*) TEST_BEGIN; (long) mem < TEST_END; mem++) {
            uint32_t val = *mem;
            if (val != WR_PATTERN) {
                kprintf("[RET] %x: found %x\n", (long) mem, val);
            }
        }   
    }
	return 0;
}