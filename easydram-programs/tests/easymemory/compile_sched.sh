#!/bin/bash
unset files
for i in ./*.cpp; do
    if [[ $i != *"main.cpp"* ]]; then
        echo "Adding ${i}"
        files+=("$i")
    fi
done
# riscv64-unknown-elf-gcc -fno-common -fno-builtin-printf -specs=htif_nano.specs -c "${files[@]}"
# riscv64-unknown-elf-gcc -static -specs=htif_nano.specs *.o -o scheduler.riscv
riscv64-unknown-elf-gcc -fno-common -fno-builtin-printf -specs=htif_nano.specs -c scheduler.cpp
riscv64-unknown-elf-gcc -static -specs=htif_nano.specs scheduler.o -o scheduler.riscv
riscv64-unknown-elf-objdump -d scheduler.riscv > scheduler_assembly.txt