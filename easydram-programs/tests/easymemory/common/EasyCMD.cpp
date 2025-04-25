#include "EasyCMD.h"

#define DECLARE(x) volatile EasyCMDConfig::reg_t& EasyCMD::x = REF32(EasyCMDConfig::x)
#define DECLARE64(x) volatile int64_t& EasyCMD::x = *(int64_t*) EasyCMDConfig::x
#define DECLAREU64(x) volatile uint64_t& EasyCMD::x = REF64(EasyCMDConfig::x)

DECLARE(mcStart);
DECLARE(mcStop);

DECLAREU64(mcTicks);
DECLAREU64(tileTicks);
DECLAREU64(mcTileRatio);
DECLAREU64(globalTicks);

DECLARE64(statCounter0);
DECLARE64(statCounter1);
DECLARE64(statCounter2);
DECLARE64(statCounter3);
DECLARE64(statCounter4);
DECLARE64(statCounter5);