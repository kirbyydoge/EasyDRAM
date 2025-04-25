#include "EasyCommon.h"
#include "EasyCMD.h"

#include "kprintf.h"

#define NLD 1024 * 1024 * 4 // number of loads to execute in the measure LOOP
#define BLOCK 0             // Flag to enable randomization within the page and reduce TLB misses
#define RAND 1              // Flag to enable randomization of the sequence
#define SWAP 64             // Parameter to control the number of swaps in the random sequence generation

// A cache line is 64 Bytes
// A *long long int* is 8 Bytes -> you can check with "sizeof(long long int)"
typedef unsigned long long int uint_64;

static char* MEM_BASE = (char*) (long) 0x81000000;

void* easy_alligned_alloc(unsigned long allignment, unsigned long size) {
	MEM_BASE = MEM_BASE + (allignment - ((long)MEM_BASE % allignment));
	void* ptr = MEM_BASE;
	MEM_BASE += size;
	return ptr;
}

void* easy_malloc(unsigned long size) {
	void* ptr = MEM_BASE;
	MEM_BASE += size;
	return ptr;
}

void reset_alloc() {
	for (uint64_t i = 0x81000000; i < (uint64_t) MEM_BASE; i += 8) {
		clflush64(i);
	}
}

// Applies the random sequence seq of size ncache_lines to generate a pointer chasing in data
void init_data(uint_64 *data, int *seq, int ncache_lines)
{
    int it = 0;
    int step = 64 / sizeof(uint_64); // we work at a cache line granularity
    for (it = 0; it < ncache_lines; it++)
    {
        data[it * step] = seq[it] * 8;
    }
}

unsigned int prev_hash = 0;

unsigned int pcg_hash() {
    unsigned int state = prev_hash * 747796405u + 2891336453u;
    unsigned int word = ((state >> ((state >> 28u) + 4u)) ^ state) * 277803737u;
    return (word >> 22u) ^ word;
}

// Generates a random sequence seq of size ncache_lines
void randomize_sequence(int *sequence, int ncache_lines)
{
    int* internal_sequence = (int*) easy_malloc(sizeof(int) * ncache_lines);
    int it;
    int swaps = ncache_lines * SWAP; // the higher, the more swaps, the more random the sequence
    int ind1, ind2;
    int val1, val2;
    int block = 512;
    int index;
    // Check if the array  includes multiple pages (1 page = 4 kB)
    // if BLOCK and RAND flags are enabled, randomize within the page limits
    if (((ncache_lines * 8 > 4096) && BLOCK) && RAND)
    {
        // printf("Info: Randomozing within multiple (%d) pages\n",ncache_lines*8 / 4096 );
        for (it = 0; it < ncache_lines; it++)
        {
            sequence[it] = it;
        }

        // random swaps
        for (it = 0; it < swaps; it++)
        {
            ind1 = pcg_hash() % block;
            ind2 = pcg_hash() % block;
            val1 = sequence[ind1];
            val2 = sequence[ind2];
            sequence[ind1] = val2;
            sequence[ind2] = val1;
        }
        index = 0;

        for (it = 0; it < ncache_lines; it++)
        {
            if (sequence[index] == 0)
            {
                index++;
            }
            internal_sequence[it] = sequence[index % block] + (it / block) * block;
            index++;
        }
        internal_sequence[it - 1] = 0;
    }
    else
    {
        for (it = 0; it < ncache_lines; it++)
        {
            sequence[it] = it;
        }
        if (RAND)
        {
            // random swaps
            for (it = 0; it < swaps; it++)
            {
                ind1 = pcg_hash() % ncache_lines;
                ind2 = pcg_hash() % ncache_lines;
                val1 = sequence[ind1];
                val2 = sequence[ind2];
                sequence[ind1] = val2;
                sequence[ind2] = val1;
            }
        }
        index = 0;
        for (it = 0; it < ncache_lines; it++)
        {
            if (sequence[index] == 0)
            {
                index++;
            }
            internal_sequence[it] = sequence[index];
            index++;
        }
        internal_sequence[it - 1] = 0;
    }

    // pass the sequence to the output variable
    for (it = 0; it < ncache_lines - 1; it++)
    {
        sequence[internal_sequence[it]] = internal_sequence[it + 1];
    }
    sequence[0] = internal_sequence[0];
}

int main()
{

    uint_64 acc = 0;

    /* Print profiling results */
    kprintf("Cycles, Ins, NLoads, Size[KB], Cycles/LD\n");

    uint64_t stat_ctr_0_begin, stat_ctr_0_end;
    uint64_t stat_ctr_1_begin, stat_ctr_1_end;
    uint64_t stat_ctr_2_begin, stat_ctr_2_end;
    uint64_t stat_ctr_3_begin, stat_ctr_3_end;
    uint64_t stat_ctr_4_begin, stat_ctr_4_end;
    uint64_t stat_ctr_5_begin, stat_ctr_5_end;
    stat_ctr_0_begin = EasyCMD::statCounter0;
    stat_ctr_1_begin = EasyCMD::statCounter1;
    stat_ctr_2_begin = EasyCMD::statCounter2;
    stat_ctr_3_begin = EasyCMD::statCounter3;
    stat_ctr_4_begin = EasyCMD::statCounter4;
    stat_ctr_5_begin = EasyCMD::statCounter5;
    for (int kbs = 1; kbs < 16384 * 2; kbs = kbs * 2) // sweep over different sizes of the data array
    {
        /* Make the chasing pointer array */
        
        int n = kbs * (1024 / sizeof(uint_64));
        uint_64 *data = (uint_64*) easy_alligned_alloc(4096, n * sizeof(uint_64));   // allocate data array

        int *seq = (int*) easy_malloc(n * sizeof(int)); // allocate sequence for pointer chasing
        
        int ncache_lines = n / 8;  // calculate the number of cache lines in data
        int nloads = NLD;
        uint_64 addr = 0;   // initialize the pointer chasing index

        // srand(time(0));
        // srand(0);           // init randomization
		prev_hash = 0; // init randomiozation

        /* Generate a random sequence */
        randomize_sequence(seq, ncache_lines);
        
        /* Apply the random sequence to generate a pointer chasing in the data array */
        init_data(data, seq, ncache_lines);

        /*******************************************/
        /**********    Measured LOOP     ***********/
		uint64_t cycle_begin, cycle_end;
		uint64_t retire_begin, retire_end;
		READ_CSR(cycle_begin, CSR_MCYCLE);
		READ_CSR(retire_begin, CSR_MINSTRET);
        asm("\n# Begin loop code");
        for (int it = 0; it < nloads; it = it + 1)
        {
            addr = data[addr];
            // printf("it %d = %d\n", it, addr/8);
        }
        asm("\n# End loop code");
		READ_CSR(cycle_end, CSR_MCYCLE);
		READ_CSR(retire_end, CSR_MINSTRET);
        /********** End of measured LOOP ***********/
        /*******************************************/

        uint64_t cycles = cycle_end - cycle_begin;
        uint64_t instns = retire_end - retire_begin;
        /* Print out the profiling results for further analysis */
        kprintf("%lld, %lld, %d, %d, ", cycles, instns, nloads, n * 8 / 1024);
        kfloat(((float)cycles) / (nloads));
        kprintf("\n");
        
        acc += addr; // trick to avoid that the compiler optimizes our code away (it addr is not used, our LOOP won't execute)
		reset_alloc();
	}
    stat_ctr_0_end = EasyCMD::statCounter0;
    stat_ctr_1_end = EasyCMD::statCounter1;
    stat_ctr_2_end = EasyCMD::statCounter2;
    stat_ctr_3_end = EasyCMD::statCounter3;
    stat_ctr_4_end = EasyCMD::statCounter4;
    stat_ctr_5_end = EasyCMD::statCounter5;
    kprintf("stat0: %lu ", stat_ctr_0_end - stat_ctr_0_begin);
    kprintf("stat1: %lu ", stat_ctr_1_end - stat_ctr_1_begin);
    kprintf("stat2: %lu ", stat_ctr_2_end - stat_ctr_2_begin);
    kprintf("stat3: %lu ", stat_ctr_3_end - stat_ctr_3_begin);
    kprintf("stat4: %lu ", stat_ctr_4_end - stat_ctr_4_begin);
    kprintf("stat5: %lu\n", stat_ctr_5_end - stat_ctr_5_begin);
    kprintf("Done (%ld)\n", acc); // trick to avoid that the compiler optimizes our code away

    return 0;
}
