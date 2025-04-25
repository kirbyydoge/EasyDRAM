#include "EasyDebug.h"

#define DECLARE(x) volatile EasyDebugConfig::reg_t& EasyDebug::x = REF64(EasyDebugConfig::x)

DECLARE(schedCount);
DECLARE(schedMax);
DECLARE(schedEn);
DECLARE(schedRdCount);
DECLARE(schedWrCount);