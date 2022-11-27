# License

License is BSD-3-Clause License because that's the license of "[The Computer Language Benchmarks Game](https://benchmarksgame-team.pages.debian.net/benchmarksgame/)" itself, and I want to keep license compatibility. If there's any violation of licenses, let me know.

# Why this project exists?

Maintainer of [The Computer Language Benchmarks Game](https://salsa.debian.org/benchmarksgame-team/benchmarksgame) rejected my submission of Java program that was based on not yet standardized features of OpenJDK. I believe that the features will be incorporated to JDK standard sooner or later in a way that doesn't require major restructuring, so it makes sense to keep the implementations here.

# Test system

Hardware:
- CPU: Intel Core i5-4670 @ 3.80 GHz
- RAM: 32 GiB 1600 MHz

Software:
- OS: Ubuntu 18.04.6
- Java builds from https://jdk.java.net/

# List of benchmark programs implementations

- [binary-trees](#binary-trees) using Project Valhalla to implement inline value types
- [mandelbrot](#mandelbrot) using Vector API from Project Panama to implement explicit SIMD vectorization
- [regex-redux](#regex-redux) using Foreign APIs from Project Panama to integrate with native libraries

## binary-trees

binarytrees_7.java is the [binary-trees Java #7 program](https://benchmarksgame-team.pages.debian.net/benchmarksgame/program/binarytrees-java-7.html) that is currently (i.e. as of 27th November 2022) the fastest **multithreaded** Java implementation according to [official results](https://benchmarksgame-team.pages.debian.net/benchmarksgame/performance/binarytrees.html).

binarytrees_valhalla.java is a modification of above program that incorporates a tree node allocation reducing trick from [binary-trees C# .NET #6 program](https://benchmarksgame-team.pages.debian.net/benchmarksgame/program/binarytrees-csharpcore-6.html), which is in turn based on [binary-trees F# .NET #5 program](https://benchmarksgame-team.pages.debian.net/benchmarksgame/program/binarytrees-fsharpcore-5.html). The trick halves allocation count, i.e. 2x fewer objects are allocated on the heap.

To compile and run binarytrees_valhalla.java, you first need to get a build of [Project Valhalla](https://openjdk.org/projects/valhalla/) of OpenJDK. Builds are available on [Project Valhalla Early-Access Builds](https://jdk.java.net/valhalla/) page. The tested build is `Build 20-valhalla+20-75 (2022/11/7)` ([direct link](https://download.java.net/java/early_access/valhalla/20/openjdk-20-valhalla+20-75_linux-x64_bin.tar.gz), [checksum](https://download.java.net/java/early_access/valhalla/20/openjdk-20-valhalla+20-75_linux-x64_bin.tar.gz.sha256)).

Results:
```
$ ~/devel/jdk-19.0.1/bin/javac binarytrees_7.java
$ ~/devel/jdk-20-valhalla/bin/javac -XDenablePrimitiveClasses binarytrees_valhalla.java

$ time ~/devel/jdk-19.0.1/bin/java binarytrees_7 21
stretch tree of depth 22	 check: 8388607
2097152	 trees of depth 4	 check: 65011712
524288	 trees of depth 6	 check: 66584576
131072	 trees of depth 8	 check: 66977792
32768	 trees of depth 10	 check: 67076096
8192	 trees of depth 12	 check: 67100672
2048	 trees of depth 14	 check: 67106816
512	 trees of depth 16	 check: 67108352
128	 trees of depth 18	 check: 67108736
32	 trees of depth 20	 check: 67108832
long lived tree of depth 21	 check: 4194303

real	0m2,420s
user	0m7,089s
sys	0m0,850s

$ time ~/devel/jdk-20-valhalla/bin/java -XX:+EnablePrimitiveClasses binarytrees_valhalla 21
stretch tree of depth 22	 check: 8388607
2097152	 trees of depth 4	 check: 65011712
524288	 trees of depth 6	 check: 66584576
131072	 trees of depth 8	 check: 66977792
32768	 trees of depth 10	 check: 67076096
8192	 trees of depth 12	 check: 67100672
2048	 trees of depth 14	 check: 67106816
512	 trees of depth 16	 check: 67108352
128	 trees of depth 18	 check: 67108736
32	 trees of depth 20	 check: 67108832
long lived tree of depth 21	 check: 4194303

real	0m1,301s
user	0m3,341s
sys	0m0,629s

$ time for run in {1..10}; do ~/devel/jdk-19.0.1/bin/java binarytrees_7 21 > /dev/null; done

real	0m24,203s
user	1m11,790s
sys	0m7,545s

$ time for run in {1..10}; do ~/devel/jdk-20-valhalla/bin/java -XX:+EnablePrimitiveClasses binarytrees_valhalla 21 > /dev/null; done

real	0m13,640s
user	0m34,020s
sys	0m5,720s

```

## mandelbrot

mandelbrot_2.java is the [mandelbrot Java #2 program](https://benchmarksgame-team.pages.debian.net/benchmarksgame/program/mandelbrot-java-2.html) that is currently (i.e. as of 27th November 2022) the fastest **multithreaded** Java implementation according to [official results](https://benchmarksgame-team.pages.debian.net/benchmarksgame/performance/mandelbrot.html).

mandelbrot_panama_vector.java is a reimplementation with the inner vectorized loop loosely inspired by [mandelbrot Rust #7 program](https://benchmarksgame-team.pages.debian.net/benchmarksgame/program/mandelbrot-rust-7.html). It uses Vector API from Project Panama to implement explicit SIMD vectorization using optimal vector sizes (matching CPU vector registers size).

Note: Vector API has (currently?) much longer warmup than ordinary scalar code. Major chunk of time is spent on executing unoptimized code and on JIT optimizations. Running the code with bigger parameters will result in the performance improving as proportionally less time will be spent in unoptimized code and JIT compiler. I've included performance results for size 16000 (as in benchmark rules) and 64000 (to show how performance gap vs scalar code is growing).

```
$ ~/devel/jdk-19.0.1/bin/javac mandelbrot_2.java
$ ~/devel/jdk-19.0.1/bin/javac --add-modules jdk.incubator.vector mandelbrot_panama_vector.java
warning: using incubating module(s): jdk.incubator.vector
1 warning

$ ~/devel/jdk-19.0.1/bin/java mandelbrot_2 16000 > mandelbrot_16000_2.pbm
$ ~/devel/jdk-19.0.1/bin/java --add-modules jdk.incubator.vector mandelbrot_panama_vector 16000 > mandelbrot_16000_panama_vector.pbm
WARNING: Using incubator modules: jdk.incubator.vector
$ md5sum *.pbm
8c2ed8883de64eccd3154ac612021fe8  mandelbrot_16000_2.pbm
8c2ed8883de64eccd3154ac612021fe8  mandelbrot_16000_panama_vector.pbm

$ time for run in {1..10}; do ~/devel/jdk-19.0.1/bin/java mandelbrot_2 16000 > /dev/null; done

real	0m29,870s
user	1m56,981s
sys	0m0,387s

$ time for run in {1..10}; do ~/devel/jdk-19.0.1/bin/java --add-modules jdk.incubator.vector mandelbrot_panama_vector 16000 > /dev/null; done
WARNING: Using incubator modules: jdk.incubator.vector
WARNING: Using incubator modules: jdk.incubator.vector
WARNING: Using incubator modules: jdk.incubator.vector
WARNING: Using incubator modules: jdk.incubator.vector
WARNING: Using incubator modules: jdk.incubator.vector
WARNING: Using incubator modules: jdk.incubator.vector
WARNING: Using incubator modules: jdk.incubator.vector
WARNING: Using incubator modules: jdk.incubator.vector
WARNING: Using incubator modules: jdk.incubator.vector
WARNING: Using incubator modules: jdk.incubator.vector

real	0m10,243s
user	0m36,442s
sys	0m1,181s

$ time ~/devel/jdk-19.0.1/bin/java mandelbrot_2 64000 > /dev/null

real	0m46,673s
user	3m4,783s
sys	0m0,256s

$ time ~/devel/jdk-19.0.1/bin/java --add-modules jdk.incubator.vector mandelbrot_panama_vector 64000 > /dev/null
WARNING: Using incubator modules: jdk.incubator.vector

real	0m11,253s
user	0m43,073s
sys	0m0,312s
```

## regex-redux

regexredux_3.java is the [regex-redux Java #3 program](https://benchmarksgame-team.pages.debian.net/benchmarksgame/program/regexredux-java-3.html) that is currently (i.e. as of 27th November 2022) the fastest **multithreaded** Java implementation according to [official results](https://benchmarksgame-team.pages.debian.net/benchmarksgame/performance/regexredux.html).

regexredux_panama_foreign.java is an implementation written from scratch using Foreign APIs (i.e. both Foreign-Memory Access API and Foreign Linker API) that integrates with [PCRE2 library](https://www.pcre.org/current/doc/html/).

To compile and run regexredux_panama_foreign.java you first need to generate glue code using jextract utility from [Project Panama](https://openjdk.org/projects/panama/), which can be found on [Project Jextract Early-Access Builds](https://jdk.java.net/jextract/). I've used `Build 19-jextract+2-3 (2022/7/19)` ([direct link](https://download.java.net/java/early_access/jextract/2/openjdk-19-jextract+2-3_linux-x64_bin.tar.gz), [checksum](https://download.java.net/java/early_access/jextract/2/openjdk-19-jextract+2-3_linux-x64_bin.tar.gz.sha256)).

```
$ # use jextract utility from OpenJDK build from Project Panama
$ # to generate glue code in compiled form (*.class files)
$ mkdir pcre2.jextract
$ cd pcre2.jextract
$ ~/devel/jdk-19-jextract/bin/jextract -D "PCRE2_CODE_UNIT_WIDTH=8" -l pcre2-8 /usr/include/pcre2.h
WARNING: A restricted method in java.lang.foreign.Linker has been called
WARNING: java.lang.foreign.Linker::nativeLinker has been called by module org.openjdk.jextract
WARNING: Use --enable-native-access=org.openjdk.jextract to avoid a warning for this module

WARNING: skipping strtold because of unsupported type usage: long double
WARNING: skipping qecvt because of unsupported type usage: long double
WARNING: skipping qfcvt because of unsupported type usage: long double
WARNING: skipping qgcvt because of unsupported type usage: long double
WARNING: skipping qecvt_r because of unsupported type usage: long double
WARNING: skipping qfcvt_r because of unsupported type usage: long double
$ cd ..

$ # generate test data using some valid fasta benchmark program implementation
$ ~/devel/jdk-19.0.1/bin/java fasta_2.java 5000000 > regexredux-input-5000000.txt

$ # compile main classes
$ ~/devel/jdk-19.0.1/bin/javac regexredux_3.java
$ ~/devel/jdk-19.0.1/bin/javac --release 19 --enable-preview -cp pcre2.jextract regexredux_panama_foreign.java
Note: regexredux_panama_foreign.java uses preview features of Java SE 19.
Note: Recompile with -Xlint:preview for details.

$ # run

$ time ~/devel/jdk-19.0.1/bin/java regexredux_3 < regexredux-input-5000000.txt
agggtaaa|tttaccct 356
[cgt]gggtaaa|tttaccc[acg] 1250
a[act]ggtaaa|tttacc[agt]t 4252
ag[act]gtaaa|tttac[agt]ct 2894
agg[act]taaa|ttta[agt]cct 5435
aggg[acg]aaa|ttt[cgt]ccct 1537
agggt[cgt]aa|tt[acg]accct 1431
agggta[cgt]a|t[acg]taccct 1608
agggtaa[cgt]|[acg]ttaccct 2178

50833411
50000000
27388361

real	0m4,210s
user	0m12,709s
sys	0m0,232s

$ time ~/devel/jdk-19.0.1/bin/java --enable-preview --enable-native-access=ALL-UNNAMED -Dforeign.restricted=permit -cp .:pcre2.jextract -Djava.library.path=/usr/lib/x86_64-linux-gnu/ regexredux_panama_foreign < regexredux-input-5000000.txt
agggtaaa|tttaccct 356
[cgt]gggtaaa|tttaccc[acg] 1250
a[act]ggtaaa|tttacc[agt]t 4252
ag[act]gtaaa|tttac[agt]ct 2894
agg[act]taaa|ttta[agt]cct 5435
aggg[acg]aaa|ttt[cgt]ccct 1537
agggt[cgt]aa|tt[acg]accct 1431
agggta[cgt]a|t[acg]taccct 1608
agggtaa[cgt]|[acg]ttaccct 2178

50833411
50000000
27388361

real	0m1,095s
user	0m2,317s
sys	0m0,153s
```
