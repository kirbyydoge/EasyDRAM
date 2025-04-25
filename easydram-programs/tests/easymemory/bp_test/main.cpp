#include "EasyAPI.h"
#include "EasyCMD.h"
#include "EasyFRFCFS.h"

#define MC_DEBUG
#include "mc_uart.h"

#define REF_DUR      tREF
#define NS_IN_MS     1000000
#define N_ROWS       32768
#define REF_CLK_PER  10
#define N_REF_DUR_NS (64 * NS_IN_MS)
#define REF_PERIOD   (N_REF_DUR_NS / N_ROWS / REF_CLK_PER)
#define SCHED_CYCLES 5
#define CPU_HZ       100000000
#define BAUD_RATE    921600

#define SCHED_GAP    15
#define WR_PATTERN   0x55555555
#define COL_SIZE_B   2 * 1024
#define CACHE_LINE_B 64

void write_whole_row(uint32_t bank, uint32_t row, uint32_t wr_pattern) {
    write_inst(SMC_LI(1, BAR));
    write_inst(SMC_PRE(BAR, 0, 1));
    write_wait(tSAFETY);
    write_inst(SMC_LI(bank, BAR));
    write_inst(SMC_LI(row, RAR));
    write_inst(SMC_LI(0, CAR));
    write_inst(SMC_LI(8, CASR));
    write_inst(SMC_ACT(BAR, 0, RAR, 0));
    for (int i = 0; i < 16; i++) {
        write_inst(SMC_LI(i, PATTERN_REG));
        write_inst(SMC_LDWD(PATTERN_REG, i));
    }
    write_wait(tRCD);
    for (int i = 0; i < COL_SIZE_B / 64; i++) {
        write_inst(SMC_WRITE(BAR, 0, CAR, 1, 0, 0));
        write_wait(tCCD);
    }
    write_wait(tWL + tWR + tBL);
    write_inst(SMC_PRE(BAR, 0, 0));
    write_wait(tRP);
    flush_commands(DEFAULT_TIMEOUT);
}

bool check_while_row(uint32_t bank, uint32_t row, uint32_t wr_pattern) {
    // write_inst(SMC_LI(1, BAR));
    // write_inst(SMC_PRE(BAR, 0, 1));
    // write_wait(tSAFETY);
    // write_inst(SMC_LI(bank, BAR));
    // write_inst(SMC_LI(row, RAR));
    // write_inst(SMC_LI(0, CAR));
    // write_inst(SMC_LI(8, CASR));
    // write_inst(SMC_ACT(BAR, 0, RAR, 0));
    // write_wait(tRCD);
    // for (int i = 0; i < COL_SIZE_B / 64; i++) {
    //     write_inst(SMC_READ(BAR, 0, CAR, 1, 0, 0));
    //     write_wait(tCCD);
    // }
    // write_wait(tRTP);
    // write_inst(SMC_PRE(BAR, 0, 0));
    // write_wait(tRP);
    // flush_commands(DEFAULT_TIMEOUT);
    pre_schedule();
    basic_read_schedule(0, 0, 0);
    flush_commands(DEFAULT_TIMEOUT);
    bool success = true;
    uint64_t compare_pattern = (uint64_t) wr_pattern << 32 | wr_pattern;
    for (int i = 0; i < COL_SIZE_B / 64; i++) {
        for (int j = 0; j < 8; j++) {
            ee_printf("[0] ");
            if (j == 4) write32(PROG_RDBACK_CONSUME, 1);
            ee_printf("[1] ");
            if ((j % 4) == 0) while(is_rdback_empty());
            ee_printf("[2] ");
            uint64_t rdback = read64(PROG_RDBACK_DATA + (j % 4) * 8);
            // if (rdback != compare_pattern) {
                ee_printf("[ERR] bank:%d row:%d col:%d rdback:%lx expect:%lx\n",
                    bank, row, i, rdback, compare_pattern);
                success = false;
            // }
        }
    }
    return success;
}

void easy_sleep_ns(uint32_t duration) {
    uint64_t mcycle = 0;
    READ_CSR(mcycle, CSR_MCYCLE);
    uint64_t target = mcycle + duration / REF_CLK_PER; 
    while(mcycle < target) {
        READ_CSR(mcycle, CSR_MCYCLE);
    }
}

int main(void) {
    clear_rdback();
    uart_set_baud(CPU_HZ / BAUD_RATE);
    // ee_printf("Hello There\n");
    uint64_t sched_ctr = 0;
    // base_init(REF_DUR, REF_PERIOD);
    // write_whole_row(0, 0, WR_PATTERN);
    bool is_running = true;
    while(is_running) {
        // ee_printf("---Begin Check---\n");
        is_running = check_while_row(0, 0, WR_PATTERN);
        // ee_printf("---End Check---\n");
    }
	return 0;
}