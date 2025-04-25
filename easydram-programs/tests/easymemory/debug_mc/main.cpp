#include "EasyAPI.h"
#include "EasyCMD.h"
#include "EasyFRFCFS.h"
#include "mc_uart.h"

#define SCHED_CYCLES 5
#define CPU_HZ       100000000
#define BAUD_RATE    921600

#define SCHED_GAP    15

#define TEST_BYTES   458752
#define TEST_LEN     (TEST_BYTES / sizeof(int))

int* stack;
int* indexes;

unsigned int pcg_hash(unsigned int input) {
    unsigned int state = input * 747796405u + 2891336453u;
    unsigned int word = ((state >> ((state >> 28u) + 4u)) ^ state) * 277803737u;
    return (word >> 22u) ^ word;
}

void send_int(unsigned int val) {
    int digits = 0;
    do {
        stack[TEST_LEN + digits++] = (char) (val % 10) + '0';
        val /= 10;
    } while (val > 0);
    while (digits > 0) {
        ee_printf("%c", stack[TEST_LEN + (--digits)]);
    }
}
void target_check(long addr) {
    volatile uint64_t* ptr = (volatile uint64_t*) addr;
    ee_printf("Write to %x: ", addr);
    *ptr = 0xfeebdaeddeadbeefULL;
    ee_printf("done.\n");
    ee_printf("Read: %lx\n", *ptr);
}

int main() {
    uart_set_baud(CPU_HZ / BAUD_RATE);
    target_check(0x80010000);
    target_check(0x80010001);
    target_check(0x80010002);
    target_check(0x80010003);
    target_check(0x80010004);
    target_check(0x80010005);
    target_check(0x80010006);
    target_check(0x80010007);
    return 0;
}