#!/usr/bin/perl

# Generates Makefile for each benchmark in polybench
# Expects to be executed from root folder of polybench
#
# Written by Tomofumi Yuki, 11/21 2014
#

my $GEN_CONFIG = 0;
my $TARGET_DIR = ".";

if ($#ARGV !=0 && $#ARGV != 1) {
   printf("usage perl makefile-gen.pl output-dir [-cfg]\n");
   printf("  -cfg option generates config.mk in the output-dir.\n");
   exit(1);
}

foreach my $arg (@ARGV) {
   if ($arg =~ /-cfg/) {
      $GEN_CONFIG = 1;
   } elsif (!($arg =~ /^-/)) {
      $TARGET_DIR = $arg;
   }
}

my %categories = (
   'linear-algebra/blas' => 3,
   'linear-algebra/kernels' => 3,
   'linear-algebra/solvers' => 3,
   'datamining' => 2,
   'stencils' => 2,
   'medley' => 2
);

my %extra_flags = (
   'cholesky' => '-lm',
   'gramschmidt' => '-lm',
   'correlation' => '-lm'
);

foreach $key (keys %categories) {
   my $target = $TARGET_DIR.'/'.$key;
   opendir DIR, $target or die "directory $target not found.\n";
   while (my $dir = readdir DIR) {
        next if ($dir=~'^\..*');
        next if (!(-d $target.'/'.$dir));

	my $kernel = $dir;
        my $file = $target.'/'.$dir.'/Makefile';
        my $polybenchRoot = '../'x$categories{$key};
        my $configFile = $polybenchRoot.'config.mk';
        my $utilityDir = $polybenchRoot.'utilities';

        open FILE, ">$file" or die "failed to open $file.";

print FILE << "EOF";
BENCHMARK_DIR=/home/kirbyydoge/github/easyprogs/install/riscv-bmarks

# compiler to use
CXX = riscv64-unknown-elf-gcc
CDUMP = riscv64-unknown-elf-objdump

# compiler flags
CXXFLAGS = -O2 -fno-common -fno-builtin-fprintf -fno-builtin-printf -specs=htif_nano.specs -I. -I$utilityDir/common -I$utilityDir

# target executable
TARGET = $kernel

# directories
COMMON_DIR = $utilityDir
OBJ_DIR = $utilityDir/objects

# source and object files
COMMON_SRCS = \$(wildcard \$(COMMON_DIR)/*.c)
COMMON_OBJS = \$(patsubst \$(COMMON_DIR)/%.c, \$(OBJ_DIR)/%.o, \$(COMMON_SRCS))
SRCS = $kernel.c
OBJS = $kernel.o

$kernel: \$(OBJS) \$(COMMON_OBJS)
	\$(CXX) \$(CXXFLAGS) -o $kernel.riscv \$^ -lm
	\$(CDUMP) -d $kernel.riscv > $kernel.riscv.dump
	cp $kernel.riscv \$(BENCHMARK_DIR)
	cp $kernel.riscv.dump \$(BENCHMARK_DIR)

\$(OBJS): \$(SRCS)
	\$(CXX) \$(CXXFLAGS) -c \$< -o \$@ -lm

\$(COMMON_OBJS): \$(OBJ_DIR)/%.o : \$(COMMON_DIR)/%.c
	\$(CXX) \$(CXXFLAGS) -c \$< -o \$@ -lm

default: $kernel

clean:
	@ rm -f $kernel.riscv

EOF

        close FILE;
   }


   closedir DIR;
}

if ($GEN_CONFIG) {
open FILE, '>'.$TARGET_DIR.'/config.mk';

print FILE << "EOF";
CC=gcc
CFLAGS=-O2 -DPOLYBENCH_DUMP_ARRAYS -DPOLYBENCH_USE_C99_PROTO
EOF

close FILE;

}

