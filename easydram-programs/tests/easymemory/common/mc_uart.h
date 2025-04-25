#ifndef KASIRGA_GOK
#define KASIRGA_GOK

#include <stdint.h>
#include <stdarg.h>

#ifndef CPU_HZ
    #define CPU_HZ      100000000
#endif

#ifndef BAUD_RATE
    #define BAUD_RATE   115200
#endif

#ifndef TRUE
    #define TRUE (1==1)
    #define FALSE (1!=1)
#endif //TRUE

#define TX_FULL_BIT     0
#define TX_EMPTY_BIT    1
#define RX_FULL_BIT     2
#define RX_EMPTY_BIT    3

static volatile uint32_t* UART_BAUD     = (uint32_t*) (0x20000000);
static volatile uint32_t* UART_IS_FULL  = (uint32_t*) (0x20000004);
static volatile uint32_t* UART_WRITE    = (uint32_t*) (0x20000008);

void handle_trap();
void uart_set_baud(uint16_t baud_div);
int uart_tx_full();
int uart_rx_full();
int uart_tx_empty();
int uart_rx_empty();
void uart_write(uint8_t data);
uint8_t uart_read();
void uart_print(const char* str);
//!! COREMARK PRINTF FONKSIYONLARI !!//
uint32_t strnlen(const char *s, uint32_t count);
int skip_atoi(const char **s);
char * number(char *str, long num, int base, int size, int precision, int type);
char * eaddr(char *str, unsigned char *addr, int size, int precision, int type);
char * iaddr(char *str, unsigned char *addr, int size, int precision, int type);
int ee_vsprintf(char *buf, const char *fmt, va_list args);
int ee_printf(const char *fmt, ...);
int force_printf(const char *fmt, ...);

#endif //KASIRGA_GOK