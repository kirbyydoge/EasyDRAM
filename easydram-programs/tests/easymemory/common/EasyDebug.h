#ifndef EASY_DEBUG_H_
#define EASY_DEBUG_H_

#include "EasyCommon.h"
#include "EasyCMD.h"

namespace EasyDebugConfig {
    using reg_t = uint64_t;
    constexpr long regLen = sizeof(EasyDebugConfig::reg_t);
    constexpr long debugBase = EasyCMD::freeBase + 64 * EasyDebugConfig::regLen;
    constexpr long schedCount = debugBase;
    constexpr long schedMax = debugBase + 2 * regLen;
    constexpr long schedEn = debugBase + 3 * regLen;
    constexpr long schedRdCount = debugBase + 4 * regLen;
    constexpr long schedWrCount = debugBase + 5 * regLen;
};

namespace EasyDebug {
    extern volatile EasyDebugConfig::reg_t& schedCount;
    extern volatile EasyDebugConfig::reg_t& schedMax;
    extern volatile EasyDebugConfig::reg_t& schedEn; // Extremely stupid control, basically a kill switch if you miss caches while instruction fetching
    extern volatile EasyDebugConfig::reg_t& schedRdCount; 
    extern volatile EasyDebugConfig::reg_t& schedWrCount;
};

#endif //EASY_DEBUG_H_