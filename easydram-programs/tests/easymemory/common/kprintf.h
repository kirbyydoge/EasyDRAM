// See LICENSE.Sifive for license details.
#ifndef _SDBOOT_KPRINTF_H
#define _SDBOOT_KPRINTF_H

#include <platform.h>
#include <stdint.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdbool.h>

#define REG32(p, i)	((p)[(i) >> 2])

#ifndef UART_CTRL_ADDR
  #ifndef UART_NUM
    #define UART_NUM 0
  #endif

  #define _CONCAT3(A, B, C) A ## B ## C
  #define _UART_CTRL_ADDR(UART_NUM) _CONCAT3(UART, UART_NUM, _CTRL_ADDR)
  #define UART_CTRL_ADDR _UART_CTRL_ADDR(UART_NUM)
#endif
static volatile uint32_t * const uart = (uint32_t *)(UART_CTRL_ADDR);

unsigned char kgetc();
void kputc(char c);
void _kputs(const char *s);
void kputs(const char *s);
void kprintf(const char *fmt, ...);
void kfloat(float x, int sens = 3);

#ifdef DEBUG
#define dprintf(s, ...)	kprintf((s), ##__VA_ARGS__)
#define dputs(s)	kputs((s))
#else
#define dprintf(s, ...) do { } while (0)
#define dputs(s)	do { } while (0)
#endif

#endif /* _SDBOOT_KPRINTF_H */
