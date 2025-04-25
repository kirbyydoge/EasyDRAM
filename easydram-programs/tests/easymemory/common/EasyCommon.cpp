#include "EasyCommon.h"

void easy_sleep_cycles(volatile uint32_t ticks) {
    while(ticks--);
}
