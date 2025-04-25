#ifndef PROGRESSBAR_H_
#define PROGRESSBAR_H_

#include "kprintf.h"

#define BUF_LEN 256

struct ProgressBar {
    int maxLen;
    int curLen;

    char infoBuf[BUF_LEN];
    char fill;
    char empty;
    char leftMargin;
    char rightMargin;

    void init(int maxLen, char fill, char empty, char leftMargin, char rightMargin, const char* preText = "") {
        char* cpyPtr = &infoBuf[0];
        while((*cpyPtr++ = *preText++));
        this->curLen = -1;
        this->maxLen = maxLen;
        this->fill = fill;
        this->empty = empty;
        this->leftMargin = leftMargin;
        this->rightMargin = rightMargin;
    }

    void draw(int curStep, int maxSteps) {
        int progress = (curStep + 1) * maxLen / maxSteps;
        if (progress == curLen) {
            return;
        }
        curLen = progress;
        kprintf("\r");
        char* p = &infoBuf[0];
        while(*p) kputc(*p++);
        kprintf("%c", leftMargin);
        if (maxLen < curLen) {
            curLen = maxLen;
        }
        for(int i = 0; i < curLen; i++) {
            kprintf("%c", fill);
        }
        for(int i = 0; i < maxLen - curLen; i++) {
            kprintf("%c", empty);
        }
        kprintf("%c", rightMargin);
        if (curLen == maxLen) {
            kprintf("\n");
        }
    }
};

#endif //PROGRESSBAR_H_