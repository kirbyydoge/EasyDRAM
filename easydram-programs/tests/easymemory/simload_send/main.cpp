#include <stdint.h>
#include <stdio.h>

#include "EasyCommon.h"
#define TEST_BYTES      (1024 * 1024)
#define TEST_LEN        (TEST_BYTES / sizeof(int))

#define PATTERN         0x5fce10f5

#define READ_CSR(res, csr)\
__asm__ __volatile__ ("csrr %0, %1" : "=r"(res) : "n"(csr) : )

#define EASY_BUFLEN     64

int main(void) {
    volatile uint8_t* exeInstPtr = (volatile uint8_t*) 0x80000000;
    uint8_t prog_buf[EASY_BUFLEN];
    int data_ctr = PATTERN;
    int txn_ctr = 0;
    int txn_len = 8192;
    while (txn_ctr < txn_len) {
        for (int i = 0; i < EASY_BUFLEN && i + txn_ctr < txn_len; i++) {
            prog_buf[i] = data_ctr++;
        }
        for (int i = 0; i < EASY_BUFLEN && txn_ctr < txn_len; i++) {
            exeInstPtr[txn_ctr++] = prog_buf[i];
        }
    }
	printf("Done\n");
    return 0;
}
