#ifndef SIMCOMMON_H_
#define SIMCOMMON_H_

#include <stdint.h>

#ifdef WORKLOAD_DEBUG
#include <stdio.h>
#endif

#define DEFAULT_TIMEOUT     10000
#define TIMEOUT_PATTERN     0xefdaefda

#define L2_CTRL_BASE_ADDR   0x02010000
#define L2_CTRL_FL64_OFF    0x200
#define L2_CTRL_FL32_OFF    0x240

#define MC_TICKS    0x41000008
#define TILE_TICKS  0x41000010

#define REQ_META_DEFAULT    0
#define REQ_META_ROWCLONE   1

#define CSR_MCYCLE 		    0xb00

#define CACHELINE_BYTES     64

#define REF64(x) (*((volatile uint64_t*) (long) (x)))
#define REF32(x) (*((volatile uint32_t*) (long) (x)))
#define REF16(x) (*((volatile uint16_t*) (long) (x)))
#define REF8(x)  (*((volatile uint8_t*) (long) (x)))

#define EC_HEAD_REG     (0x41001000)
#define EC_TAIL_REG     (0x41001000 + 8)
#define EC_DATA_BASE    (0x41001000 + 16)
#define EC_DATA_SIZE    3 // source, target, tick
#define EC_REG_SIZE     8
#define EC_QUEUE_SIZE   8
#define EC_SRC_OFF      0
#define EC_TGT_OFF      (EC_QUEUE_SIZE)
#define EC_TICK_OFF     (2 * EC_QUEUE_SIZE)

#define EC_RC_STATUS    (EC_DATA_BASE + (EC_QUEUE_SIZE * EC_DATA_SIZE) * EC_REG_SIZE)

struct Address {
    uint32_t bank;
    uint32_t row;
    uint32_t col;
};

struct TileLink {
    bool write;
};

struct BaseRequest {
    uint64_t tick;
    bool valid;
};

struct TLRequest : BaseRequest {
    Address addr;
    bool is_write;
    uint32_t tl_opcode;
    uint32_t tl_source;
    uint32_t tl_size;
    uint32_t tl_addr;
    uint32_t tl_data[CACHELINE_BYTES / sizeof(uint32_t)];
};

struct RowcloneRequest {
    Address source;
    Address target;
    uint64_t tick;
    bool valid;
};

uint8_t read8(uint32_t addr) {
    return REF8(addr);
} 

void write8(uint32_t addr, uint8_t data) {
    REF8(addr) = data;
}

uint16_t read16(uint32_t addr) {
    return REF16(addr);
} 

void write16(uint32_t addr, uint16_t data) {
    REF16(addr) = data;
}

uint32_t read32(uint32_t addr) {
    return REF32(addr);
} 

void write32(uint32_t addr, uint32_t data) {
    REF32(addr) = data;
}

uint64_t read64(uint32_t addr) {
    return REF64(addr);
} 

void write64(uint32_t addr, uint64_t data) {
    REF64(addr) = data;
}

// Address mappıngs
inline uint32_t get_addr_bank(uint32_t addr) {
    return (addr & 0x00006000) >> 13;
}//                0x81000040

inline uint32_t get_addr_row(uint32_t addr) {
    return (addr & 0x3FFF8000) >> 15;
}//                0x81000040

inline uint32_t get_addr_col(uint32_t addr) {
    return (addr & 0x00001FC0) >> 3;
}//                0x81000180

inline Address get_addr_mapping(uint32_t addr) {
    Address address;
    address.bank = get_addr_bank(addr);
    address.row = get_addr_row(addr);
    address.col = get_addr_col(addr);
    return address;
}

inline uint32_t mapping_to_addr(uint32_t bank, uint32_t row, uint32_t col) {
    return 0x80000000 | (row << 15) | (bank << 13) | (col << 3); 
}

inline uint32_t mapping_to_addr(Address& addr) {
    return mapping_to_addr(addr.bank, addr.row, addr.col); 
}

bool is_rowclone_full() {
    return REF64(EC_HEAD_REG) == ((REF64(EC_TAIL_REG) + 1) % EC_QUEUE_SIZE);
}

bool is_rowclone_empty() {
    return REF64(EC_HEAD_REG) == REF64(EC_TAIL_REG);
}

void set_rowclone_idle(bool is_idle) {
    REF64(EC_RC_STATUS) = is_idle;
}

bool is_rowclone_idle() {
    return REF64(EC_RC_STATUS);
}

void push_rowclone_req(uint32_t src, uint32_t tgt) {
    uint64_t tick = read64(TILE_TICKS);
    while(is_rowclone_full());
    uint64_t offset = REF64(EC_TAIL_REG);
    uint64_t push_addr = EC_DATA_BASE + offset * EC_DATA_SIZE * EC_REG_SIZE;
    REF64(push_addr + EC_SRC_OFF) = src;
    REF64(push_addr + EC_TGT_OFF) = tgt;
    REF64(push_addr + EC_TICK_OFF) = tick;
    REF64(EC_TAIL_REG) = (offset + 1) % EC_QUEUE_SIZE;
    __asm__ __volatile__ ("fence": : :"memory");
}

RowcloneRequest pop_rowclone_req() {
    RowcloneRequest req;
    while(is_rowclone_empty());
    uint64_t offset = REF64(EC_HEAD_REG);
    uint64_t pop_addr = EC_DATA_BASE + offset * EC_DATA_SIZE * EC_REG_SIZE;
    req.source = get_addr_mapping(REF64(pop_addr + EC_SRC_OFF));
    req.target = get_addr_mapping(REF64(pop_addr + EC_TGT_OFF));
    req.tick = REF64(pop_addr + EC_TICK_OFF);
    req.valid = true;
    REF64(EC_HEAD_REG) = (offset + 1) % EC_QUEUE_SIZE;
    return req;
}

#endif //SIMCOMMON_H_