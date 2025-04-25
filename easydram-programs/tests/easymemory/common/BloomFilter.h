#ifndef BLOOM_FILTER_H_
#define BLOOM_FILTER_H_

#include "EasyAPI.h"
#include "mc_uart.h"
#include <cstdint>
#include <stdlib.h>

class BloomFilter {
public:
    void load(int size, int num_hashes, const char* filter) {
        this->size = size;
        this->num_hashes = num_hashes;
        bit_array = (bool*) malloc(size);
        if (!bit_array) {
            ee_printf("Couldn't allocate bit_array\n");
            exit(0);
        }
        for (int i = 0; i < size; i++) {
            bit_array[i] = filter[i] == '1' ? true : false;
        }
    }

    void add(uint32_t key) {
        for (int i = 0; i < num_hashes; i++) {
            bit_array[hash(i, key) % size] = true;
        }
    }

    bool contains(uint32_t key) const {
        for (int i = 0; i < num_hashes; i++) {
            if (!bit_array[hash(i, key) % size]) {
                return false;
            }
        }
        return true;
    }

    static uint32_t advised_hashes(uint64_t universal_size, uint64_t filter_size) {
        return universal_size * 0.693 / filter_size + 1;
    }

private:
    bool* bit_array;
    int size;
    int num_hashes;
    const uint32_t prime1 = 2654435761; // 2^32 * (sqrt(5) - 1) / 2
    const uint32_t prime2 = 2246822519; // 2^32 / golden_ratio

    uint32_t hash(int i, uint32_t key) const {
        uint32_t hash1 = mul_hash1(key);
        uint32_t hash2 = hash1 + i* ((mul_hash2(key) % (size - 1)) + 1);
        return hash2;
    }

    uint32_t mul_hash1(uint32_t key) const {
        return key * prime1;
    }

    uint32_t mul_hash2(uint32_t key) const {
        return key * prime2;
    }
};

#endif // BLOOM_FILTER_H_