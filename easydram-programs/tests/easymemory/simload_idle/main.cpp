#include <stdio.h>

int main() {
    printf("Starting...\n");
    volatile int x = 5000;
    while(--x) {
        if (x % 100 == 0) {
            printf("Heartbeat x=%d\n", x);
        }
    }
    printf("Done...\n");
    return 0;
}