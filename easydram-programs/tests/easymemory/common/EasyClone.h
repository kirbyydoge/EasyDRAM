#ifndef EASYCLONE_H_
#define EASYCLONE_H_

#include "EasyCommon.h"
#include "EasyCMD.h"

namespace easy {

namespace rowclone {

// Currently using a single header file as binary size is not a concern (yet)
// *** GLOBAL DEFINITIONS ***
struct RowcloneRequest {
    Address source;
    Address target;
    uint64_t tick;
    bool valid;
};

constexpr long REQ_DELAY      = 80;
constexpr long EC_HEAD_REG    = (EasyCMD::freeBase);
constexpr long EC_TAIL_REG    = (EasyCMD::freeBase + 8);
constexpr long EC_RC_EN_REG   = (EasyCMD::freeBase + 16);
constexpr long EC_DATA_BASE   = (EasyCMD::freeBase + 24);
constexpr long EC_DATA_SIZE   = 3;
constexpr long EC_REG_SIZE    = 8;
constexpr long EC_QUEUE_SIZE  = 8;
constexpr long EC_SRC_OFF     = 0;
constexpr long EC_TGT_OFF     = (EC_REG_SIZE);
constexpr long EC_TICK_OFF    = (2 * EC_REG_SIZE);

constexpr long EC_RC_STATUS   = (EC_DATA_BASE + (EC_QUEUE_SIZE * EC_DATA_SIZE) * EC_REG_SIZE);

_INLINE(bool) is_full() {
    return REF64(EC_HEAD_REG) == ((REF64(EC_TAIL_REG) + 1) % EC_QUEUE_SIZE);
}

_INLINE(bool) is_empty() {
    return REF64(EC_HEAD_REG) == REF64(EC_TAIL_REG);
}

_INLINE(void) set_idle(bool is_idle) {
    REF64(EC_RC_STATUS) = is_idle;
}

_INLINE(bool) is_idle() {
    return REF64(EC_RC_STATUS);
}

_INLINE(void) set_enabled(bool enabled) {
    REF64(EC_RC_EN_REG) = enabled;
}

_INLINE(bool) is_enabled() {
    return REF64(EC_RC_EN_REG);
} 

uint64_t push(uint32_t src, uint32_t tgt) {
    uint64_t tick = EasyCMD::tileTicks;
    while(is_full());
    uint64_t offset = REF64(EC_TAIL_REG);
    uint64_t push_addr = EC_DATA_BASE + offset * EC_DATA_SIZE * EC_REG_SIZE;
    REF64(push_addr + EC_SRC_OFF) = src;
    REF64(push_addr + EC_TGT_OFF) = tgt;
    REF64(push_addr + EC_TICK_OFF) = tick;
    REF64(EC_TAIL_REG) = (offset + 1) % EC_QUEUE_SIZE;
    return REQ_DELAY;
}

RowcloneRequest pop() {
    RowcloneRequest req;
    while(is_empty());
    uint64_t offset = REF64(EC_HEAD_REG);
    uint64_t pop_addr = EC_DATA_BASE + offset * EC_DATA_SIZE * EC_REG_SIZE;
    req.source = get_addr_mapping(REF64(pop_addr + EC_SRC_OFF));
    req.target = get_addr_mapping(REF64(pop_addr + EC_TGT_OFF));
    req.tick = REF64(pop_addr + EC_TICK_OFF);
    req.valid = true;
    REF64(EC_HEAD_REG) = (offset + 1) % EC_QUEUE_SIZE;
    return req;
}

};

};

#endif //EASYCLONE_H_