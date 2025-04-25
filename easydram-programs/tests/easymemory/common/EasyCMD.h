#ifndef EASYCMD_H_
#define EASYCMD_H_

#include "EasyCommon.h"

namespace EasyCMDConfig {
    using reg_t = uint32_t;
    constexpr long regLen = sizeof(EasyCMDConfig::reg_t);
    constexpr long mcStart = 0x41000000;
    constexpr long mcStop = 0x41000004;
    constexpr long mcTicks = 0x41000008;
    constexpr long tileTicks = 0x41000010;
    constexpr long mcTileRatio = 0x41000018;
    constexpr long mcClockFreq = 0x41000020;
    constexpr long tileClockFreq = 0x41000028;
    constexpr long globalTicks = 0x41000030;
    constexpr long statCounter0 = 0x41001000; 
    constexpr long statCounter1 = 0x41001008; 
    constexpr long statCounter2 = 0x41001010; 
    constexpr long statCounter3 = 0x41001018;
    constexpr long statCounter4 = 0x41001020;
    constexpr long statCounter5 = 0x41001028;
};

namespace EasyCMD {
    constexpr long cmdBase = 0x41000000;
    constexpr long freeBase = 0x41001000;

    extern volatile EasyCMDConfig::reg_t& mcStart;
    extern volatile EasyCMDConfig::reg_t& mcStop;
    extern volatile uint64_t& mcTicks;
    extern volatile uint64_t& tileTicks;
    extern volatile uint64_t& mcTileRatio;
    extern volatile uint64_t& globalTicks;
    extern volatile int64_t& statCounter0;
    extern volatile int64_t& statCounter1;
    extern volatile int64_t& statCounter2;
    extern volatile int64_t& statCounter3;
    extern volatile int64_t& statCounter4;
    extern volatile int64_t& statCounter5;
};

#endif //EASYCMD_H_