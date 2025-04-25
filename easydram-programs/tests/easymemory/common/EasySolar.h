#ifndef EASY_SOLAR_H_
#define EASY_SOLAR_H_

#include "EasyCMD.h"

namespace easy {
namespace solar {
    
namespace req {

struct Request {
    Address addr;
    uint32_t tick;
    bool valid;
};

constexpr long ES_TRCD_REG     = (EasyCMD::freeBase);
constexpr long ES_PATTERN_REG  = (EasyCMD::freeBase + 8);
constexpr long ES_HEAD_REG     = (EasyCMD::freeBase + 16);
constexpr long ES_TAIL_REG     = (EasyCMD::freeBase + 24);
constexpr long ES_DATA_BASE    = (EasyCMD::freeBase + 32);
constexpr long ES_REG_SIZE     = 8;
constexpr long ES_QUEUE_SIZE   = 4;

_INLINE(bool) is_full() {
    return REF64(ES_HEAD_REG) == ((REF64(ES_TAIL_REG) + 1) % ES_QUEUE_SIZE);
}

_INLINE(bool) is_empty() {
    return REF64(ES_HEAD_REG) == REF64(ES_TAIL_REG);
}

void set_tRCD(float val) {
    *(volatile float*) ES_TRCD_REG = val;
}

float get_tRCD() {
    return *(volatile float*) ES_TRCD_REG;
}

void set_pattern(uint64_t pattern) {
    REF64(ES_PATTERN_REG) = pattern;
}

float get_pattern() {
    return REF64(ES_PATTERN_REG);
}

void push(Address src) {
    while(is_full());
    uint64_t offset = REF64(ES_TAIL_REG);
    uint64_t push_addr = ES_DATA_BASE + offset * ES_REG_SIZE;
    REF64(push_addr) = mapping_to_addr(src);
    REF64(ES_TAIL_REG) = (offset + 1) % ES_QUEUE_SIZE;
}

Request pop() {
    Request req;
    req.tick = 0;
    while(is_empty());
    uint64_t offset = REF64(ES_HEAD_REG);
    uint64_t pop_addr = ES_DATA_BASE + offset * ES_REG_SIZE;
    req.addr = get_addr_mapping(REF64(pop_addr));
    req.valid = true;
    REF64(ES_HEAD_REG) = (offset + 1) % ES_QUEUE_SIZE;
    return req;
}

}; // namespace req

namespace resp {

struct Response {
    Address addr;
    uint32_t result;
};

constexpr long ES_HEAD_REG   = (EasyCMD::freeBase + 80);
constexpr long ES_TAIL_REG   = (EasyCMD::freeBase + 88);
constexpr long ES_DATA_BASE  = (EasyCMD::freeBase + 96);
constexpr long ES_DATA_SIZE  = 2;
constexpr long ES_REG_SIZE   = 8;
constexpr long ES_QUEUE_SIZE = 4;
constexpr long ES_SRC_OFF    = 0;
constexpr long ES_RES_OFF    = (ES_REG_SIZE);

_INLINE(bool) is_full() {
    return REF64(ES_HEAD_REG) == ((REF64(ES_TAIL_REG) + 1) % ES_QUEUE_SIZE);
}

_INLINE(bool) is_empty() {
    return REF64(ES_HEAD_REG) == REF64(ES_TAIL_REG);
}

void push(Response& resp) {
    while(is_full());
    uint64_t offset = REF64(ES_TAIL_REG);
    uint64_t push_addr = ES_DATA_BASE + offset * ES_DATA_SIZE * ES_REG_SIZE;
    REF64(push_addr + ES_SRC_OFF) = mapping_to_addr(resp.addr);
    REF64(push_addr + ES_RES_OFF) = resp.result;
    REF64(ES_TAIL_REG) = (offset + 1) % ES_QUEUE_SIZE;
}

Response pop() {
    Response resp;
    while(is_empty());
    uint64_t offset = REF64(ES_HEAD_REG);
    uint64_t pop_addr = ES_DATA_BASE + offset * ES_DATA_SIZE * ES_REG_SIZE;
    resp.addr = get_addr_mapping(REF64(pop_addr + ES_SRC_OFF));
    resp.result = REF64(pop_addr + ES_RES_OFF);
    REF64(ES_HEAD_REG) = (offset + 1) % ES_QUEUE_SIZE;
    return resp;
}

}; // namespace resp

}; // namespace solar
}; // namespace easy

#endif //EASY_SOLAR_H_