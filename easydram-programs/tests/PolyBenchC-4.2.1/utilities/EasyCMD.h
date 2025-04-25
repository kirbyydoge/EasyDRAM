#ifndef EASYCMD_H_
#define EASYCMD_H_

#include <stdint.h>

volatile uint64_t* globalCounter = (volatile uint64_t*) 0x41000030L; 
volatile uint64_t* statCounter0 = (volatile uint64_t*) 0x41001000L; 
volatile uint64_t* statCounter1 = (volatile uint64_t*) 0x41001008L; 
volatile uint64_t* statCounter2 = (volatile uint64_t*) 0x41001010L; 
volatile uint64_t* statCounter3 = (volatile uint64_t*) 0x41001018L;
volatile uint64_t* statCounter4 = (volatile uint64_t*) 0x41001020L;
volatile uint64_t* statCounter5 = (volatile uint64_t*) 0x41001028L;

#endif //EASYCMD_H_