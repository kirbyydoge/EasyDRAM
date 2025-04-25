#ifndef EASY_BITWISE_H_
#define EASY_BITWISE_H_

#include "EasyCMD.h"

namespace easy {

#define REG_ADDR(x) (EasyCMD::freeBase + x * 8)

namespace bitwise {

namespace req {

struct Request {
    Address row0;
    Address row1;
    uint8_t t1;
    uint8_t t2;
    uint32_t tick;
    bool valid;
};

constexpr long EBW_INITPT_REG   = REG_ADDR(0);
constexpr long EBW_WRITEPT_REG  = REG_ADDR(1);
constexpr long EBW_HEAD_REG     = REG_ADDR(2);
constexpr long EBW_TAIL_REG     = REG_ADDR(3);
constexpr long EBW_DATA_BASE    = REG_ADDR(6);
constexpr long EBW_REG_SIZE     = 8;
constexpr long EBW_DATA_SIZE    = 4;
constexpr long EBW_QUEUE_SIZE   = 4;
constexpr long EBW_ROW0_OFF     = (0 * EBW_REG_SIZE);
constexpr long EBW_ROW1_OFF     = (1 * EBW_REG_SIZE);
constexpr long EBW_TICK_OFF     = (2 * EBW_REG_SIZE);
constexpr long EBW_TIME_OFF     = (3 * EBW_REG_SIZE);

_INLINE(bool) is_full() {
    return REF64(EBW_HEAD_REG) == ((REF64(EBW_TAIL_REG) + 1) % EBW_QUEUE_SIZE);
}

_INLINE(bool) is_empty() {
    return REF64(EBW_HEAD_REG) == REF64(EBW_TAIL_REG);
}

void set_init_pattern(uint64_t pattern) {
    REF64(EBW_INITPT_REG) = pattern;
}

uint64_t get_init_pattern() {
    return REF64(EBW_INITPT_REG);
}

void set_write_pattern(uint64_t pattern) {
    REF64(EBW_WRITEPT_REG) = pattern;
}

uint64_t get_write_pattern() {
    return REF64(EBW_WRITEPT_REG);
}

void push(Request req) {
    uint64_t tick = EasyCMD::tileTicks;
    uint64_t params = (uint64_t) req.t2 << 8 | req.t1;
    while(is_full());
    uint64_t offset = REF64(EBW_TAIL_REG);
    uint64_t push_addr = EBW_DATA_BASE + offset * EBW_REG_SIZE * EBW_DATA_SIZE;
    REF64(push_addr + EBW_ROW0_OFF) = mapping_to_addr(req.row0);
    REF64(push_addr + EBW_ROW1_OFF) = mapping_to_addr(req.row1);
    REF64(push_addr + EBW_TICK_OFF) = tick;
    REF64(push_addr + EBW_TIME_OFF) = params;
    REF64(EBW_TAIL_REG) = (offset + 1) % EBW_QUEUE_SIZE;
}

Request pop() {
    Request req;
    while(is_empty());
    uint64_t offset = REF64(EBW_HEAD_REG);
    uint64_t pop_addr = EBW_DATA_BASE + offset * EBW_REG_SIZE * EBW_DATA_SIZE;
    req.row0 = get_addr_mapping(REF64(pop_addr + EBW_ROW0_OFF));
    req.row1 = get_addr_mapping(REF64(pop_addr + EBW_ROW1_OFF));
    req.tick = REF64(pop_addr + EBW_TICK_OFF);
    uint64_t params = REF64(pop_addr + EBW_TIME_OFF);
    req.t1 = params & 0xff;
    req.t2 = (params >> 8) & 0xff;
    req.valid = true;
    REF64(EBW_HEAD_REG) = (offset + 1) % EBW_QUEUE_SIZE;
    return req;
}

}; // namespace req

namespace resp {

struct Response {
    Address addr;
    uint32_t result;
};

constexpr long EBW_HEAD_REG   = REG_ADDR(4);
constexpr long EBW_TAIL_REG   = REG_ADDR(5);
constexpr long EBW_DATA_BASE  = REG_ADDR(64);
constexpr long EBW_REG_SIZE   = 8;
constexpr long EBW_DATA_SIZE  = 2;
constexpr long EBW_QUEUE_SIZE = 4;
constexpr long EBW_SRC_OFF    = (0 * EBW_REG_SIZE);
constexpr long EBW_RES_OFF    = (1 * EBW_REG_SIZE);

_INLINE(bool) is_full() {
    return REF64(EBW_HEAD_REG) == ((REF64(EBW_TAIL_REG) + 1) % EBW_QUEUE_SIZE);
}

_INLINE(bool) is_empty() {
    return REF64(EBW_HEAD_REG) == REF64(EBW_TAIL_REG);
}

void push(Response resp) {
    while(is_full());
    uint64_t offset = REF64(EBW_TAIL_REG);
    uint64_t push_addr = EBW_DATA_BASE + offset * EBW_DATA_SIZE * EBW_REG_SIZE;
    REF64(push_addr + EBW_SRC_OFF) = mapping_to_addr(resp.addr);
    REF64(push_addr + EBW_RES_OFF) = resp.result;
    REF64(EBW_TAIL_REG) = (offset + 1) % EBW_QUEUE_SIZE;
}

Response pop() {
    Response resp;
    while(is_empty());
    uint64_t offset = REF64(EBW_HEAD_REG);
    uint64_t pop_addr = EBW_DATA_BASE + offset * EBW_DATA_SIZE * EBW_REG_SIZE;
    resp.addr = get_addr_mapping(REF64(pop_addr + EBW_SRC_OFF));
    resp.result = REF64(pop_addr + EBW_RES_OFF);
    REF64(EBW_HEAD_REG) = (offset + 1) % EBW_QUEUE_SIZE;
    return resp;
}

}; // namespace resp

}; // namespace bitwise
}; // namespace easy

#endif //EASY_BITWISE_H_