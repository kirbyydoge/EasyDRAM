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

#define EASY_PRG_SEQ 	"EASYPROG"
#define EASY_MEM_SEQ 	"EASYMEMC"
#define EASY_WR_SEQ		"EASYWRTE"
#define EASY_RD_SEQ		"EASYREAD"

#define CMD_LEN			8

#define S_WAIT_CMD		0
#define S_WAIT_LEN		1
#define S_RECV_MEM		2
#define S_RECV_PRG		3
#define S_WRITE_TEST	4
#define S_READ_TEST		5

#define EASY_TRUE		(1==1)
#define EASY_FALSE		(1!=1)

#define EASY_BUFLEN		64

#define DEBUG_BUILD

#ifdef DEBUG_BUILD
	#define DBG(fmt, ...)  	kprintf(fmt, __VA_ARGS__)
#else
	#define DBG(fmt, ...)	do {} while(0)
#endif

int easy_strlen(char* s) {
	int len = 0;
	while(*s) len++;
	return len;
}

int circular_check(char* buf, int begin, int buf_len, char* seq) {
	for (int i = 0; i < buf_len && seq[i]; i++) {
		if (buf[(begin + i) % buf_len] != seq[i]) {
			return EASY_FALSE;
		}
	}
	return EASY_TRUE;
}

int main(void) {
	volatile uint8_t* mcInstPtr = (volatile uint8_t*) 0x40000000;
	volatile uint8_t* exeInstPtr = (volatile uint8_t*) 0x80000000;
	volatile uint32_t* mcStartReg = (volatile uint32_t*) 0x41000000;
	volatile uint32_t* mcStopReg = (volatile uint32_t*) 0x41000004;

	char easy_mem_seq[] = EASY_MEM_SEQ;
	char easy_prg_seq[] = EASY_PRG_SEQ;
	char easy_wr_seq[] = EASY_WR_SEQ;
	char easy_rd_seq[] = EASY_RD_SEQ;

	char cmd_buf[CMD_LEN];

	REG32(uart, UART_REG_TXCTRL) = UART_TXEN;
	REG32(uart, UART_REG_RXCTRL) = UART_RXEN;

	int cmd_idx = 0;
	int state = S_WAIT_CMD;
	int jump_state = S_WAIT_CMD;
	uint32_t txn_len = 0;
	uint32_t txn_ctr = 0;

	uint8_t prog_buf[EASY_BUFLEN];

	DBG("%s\n", "Started Frontend.");

	int is_running = EASY_TRUE;
	while (is_running) {
		switch (state) {
		case S_WAIT_CMD:
			cmd_buf[cmd_idx] = kgetc();
			cmd_idx = (cmd_idx + 1) % CMD_LEN;
			if (circular_check(cmd_buf, cmd_idx, CMD_LEN, easy_mem_seq)) {
				*mcStopReg = 1;
				jump_state = S_RECV_MEM;
				state = S_WAIT_LEN;
				txn_len = 0;
				txn_ctr = 0;
			}
			if (circular_check(cmd_buf, cmd_idx, CMD_LEN, easy_prg_seq)) {
				jump_state = S_RECV_PRG;
				state = S_WAIT_LEN;
				txn_len = 0;
				txn_ctr = 0;
			}
			if (circular_check(cmd_buf, cmd_idx, CMD_LEN, easy_wr_seq)) {
				state = S_WRITE_TEST;
			}
			if (circular_check(cmd_buf, cmd_idx, CMD_LEN, easy_rd_seq)) {
				state = S_READ_TEST;
			}
			break;
		case S_WAIT_LEN: 
			txn_len = (txn_len << 8) | kgetc();
			txn_ctr++;
			if (txn_ctr == 4) {
				state = jump_state;
				txn_ctr = 0;
			}
			break;
		case S_RECV_MEM:
			kputc(EASY_BUFLEN);
			for (int i = 0; i < EASY_BUFLEN && i + txn_ctr < txn_len; i++) {
				prog_buf[i] = kgetc();
			}
			for (int i = 0; i < EASY_BUFLEN && txn_ctr < txn_len; i++) {
				mcInstPtr[txn_ctr++] = prog_buf[i];
			}
			if (txn_ctr >= txn_len) {
				DBG("COMMAND: %s\n", "DONE READING");
				state = S_WAIT_CMD;
				*mcStartReg = 1;
			}
			break;
		case S_RECV_PRG:
			kputc(EASY_BUFLEN);
			for (int i = 0; i < EASY_BUFLEN && i + txn_ctr < txn_len; i++) {
				prog_buf[i] = kgetc();
			}
			for (int i = 0; i < EASY_BUFLEN && txn_ctr < txn_len; i++) {
				exeInstPtr[txn_ctr++] = prog_buf[i];
			}
			if (txn_ctr >= txn_len) {
				state = S_WAIT_CMD;
				DBG("COMMAND: %s\n", "DONE READING");
				__asm__ __volatile__ ("fence.i" ::: "memory");
				return 0;
				// int (*workload)(void) = (int (*)(void))exeInstPtr;
				// workload();
			}
			break;
		case S_WRITE_TEST:
			DBG("COMMAND: %s\n", "BEGIN WRITING");
			for (int i = 0; i < 1024; i++) {
				exeInstPtr[i] = i + 1;
			}
			state = S_WAIT_CMD;
			DBG("COMMAND: %s\n", "DONE WRITING");
			break;
		case S_READ_TEST:
			DBG("COMMAND: %s\n", "BEGIN READING");
			for (int i = 0; i < 16; i++) {
				for (int j = 0; j < 64; j++) {
					DBG("%x ", exeInstPtr[i * 64 + 63 - j]);					
				}
				DBG("%c", '\n');
			}
			state = S_WAIT_CMD;
			DBG("COMMAND: %s\n", "DONE READING");
			break;
		}
	}
	return 0;
}
