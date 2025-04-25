#ifndef EASYCOMMON_H_
#define EASYCOMMON_H_

#include <stdint.h>

#define TL_OP_GET           4
#define TL_OP_ACKD          1
#define TL_OP_PUTF          0
#define TL_OP_PUTP          1
#define TL_OP_ACK           0

#define L2_CTRL_BASE_ADDR   0x02010000
#define L2_CTRL_FL64_OFF    0x200
#define L2_CTRL_FL32_OFF    0x240

#define DEFAULT_TIMEOUT     10000
#define TIMEOUT_PATTERN     0xefdaefda

#define REQ_META_DEFAULT    0
#define REQ_META_ROWCLONE   1

#define CSR_MCYCLE 		    0xb00
#define CSR_MINSTRET        0xb02

#define CACHELINE_BYTES     64

#define REF64(x) (*((volatile uint64_t*) (long) (x)))
#define REF32(x) (*((volatile uint32_t*) (long) (x)))
#define REF16(x) (*((volatile uint16_t*) (long) (x)))
#define REF8(x)  (*((volatile uint8_t*) (long) (x)))


#define FENCE() __asm__ __volatile__ ("fence": : :"memory")

#define READ_CSR(res, csr)\
__asm__ __volatile__ ("csrr %0, %1" : "=r"(res) : "n"(csr) : )

#define USE_ATTR_INLINE
#if     defined(USE_ATTR_INLINE)
#define _INLINE(x) inline x __attribute__((always_inline))
#elif   defined(USE_DEFAULT_INLINE)
#define _INLINE(x) inline x
#else
#define _INLINE(x) x
#endif

struct Address {
    int bank;
    int row;
    int col;
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

_INLINE(uint8_t) read8(uint32_t addr) {
    return REF8(addr);
} 

_INLINE(void) write8(uint32_t addr, uint8_t data) {
    REF8(addr) = data;
}

_INLINE(uint16_t) read16(uint32_t addr) {
    return REF16(addr);
} 

_INLINE(void) write16(uint32_t addr, uint16_t data) {
    REF16(addr) = data;
}

_INLINE(uint32_t) read32(uint32_t addr) {
    return REF32(addr);
} 

_INLINE(void) write32(uint32_t addr, uint32_t data) {
    REF32(addr) = data;
}

_INLINE(uint64_t) read64(uint32_t addr) {
    return REF64(addr);
} 

_INLINE(void) write64(uint32_t addr, uint64_t data) {
    REF64(addr) = data;
}

// Address mappıngs
_INLINE(uint32_t) get_addr_bank(uint32_t addr) {
    return (addr & 0x00006000) >> 13;
}//                0x81000040

_INLINE(uint32_t) get_addr_row(uint32_t addr) {
    return (addr & 0x3FFF8000) >> 15;
}//                0x81000040

_INLINE(uint32_t) get_addr_col(uint32_t addr) {
    return (addr & 0x00001FC0) >> 3;
}//                0x81000180

// // Address mappıngs
// _INLINE(uint32_t) get_addr_bank(uint32_t addr) {
//     return (addr & 0x0C000000) >> 26;
// }//                0x81000040

// _INLINE(uint32_t) get_addr_row(uint32_t addr) {
//     return (addr & 0x03FFE000) >> 13;
// }//                0x81000040

// _INLINE(uint32_t) get_addr_col(uint32_t addr) {
//     return (addr & 0x00001FC0) >> 3;
// }//                0x81000180

_INLINE(Address) get_addr_mapping(uint32_t addr) {
    Address address;
    address.bank = get_addr_bank(addr);
    address.row = get_addr_row(addr);
    address.col = get_addr_col(addr);
    return address;
}

// Renamed for now
_INLINE(Address) addr_to_mapping(uint32_t addr) {
    return get_addr_mapping(addr);
}

_INLINE(uint32_t) mapping_to_addr(uint32_t bank, uint32_t row, uint32_t col) {
    return 0x80000000 | (row << 15) | (bank << 13) | (col << 3); 
}

// _INLINE(uint32_t) mapping_to_addr(uint32_t bank, uint32_t row, uint32_t col) {
//     return 0x80000000 | (bank << 26) | (row << 13) | (col << 3); 
// }

_INLINE(uint32_t) mapping_to_addr(Address& addr) {
    return mapping_to_addr(addr.bank, addr.row, addr.col); 
}

void easy_sleep_cycles(uint32_t sleep_duration);

static void clflush64(uint64_t base) {
	REF64(L2_CTRL_BASE_ADDR + L2_CTRL_FL64_OFF) = base;
}

static void flush_all() {
    for (uint32_t flush = 0x80000000; flush < 0x90000000; flush += CACHELINE_BYTES) {
        clflush64(flush);
    }
    __asm__ __volatile__ ("fence" ::: "memory");
}

#endif //EASYCOMMON_H_