# License

License is BSD-3-Clause License because that's the license of "The Computer Language Benchmarks Game" itself, and I want to keep license compatibility. If there's any violation of licenses, let me know.

# Why this project exists?

Maintainer of [The Computer Language Benchmarks Game](https://salsa.debian.org/benchmarksgame-team/benchmarksgame) rejected my submission of Java program that was based on not yet standardized features of OpenJDK. I believe that the features will be incorporated to JDK standard sooner or later in a way that doesn't require major restructuring, so it makes sense to keep the implementations here.

# List of benchmark programs implementations

- [binary-trees](#binary-trees) using Project Valhalla to implement inline value types
- [mandelbrot](#mandelbrot) using Vector API from Project Panama to implement explicit SIMD vectorization

## binary-trees

binarytrees_7.java is the [binary-trees Java #7 program](https://benchmarksgame-team.pages.debian.net/benchmarksgame/program/binarytrees-java-7.html) that is currently (i.e. as of 20th March 2021) the fastest Java implementation according to official results.

binarytrees_valhalla.java is a modification of above program that incorporates a tree node allocation reducing trick from [binary-trees C# .NET #6 program](https://benchmarksgame-team.pages.debian.net/benchmarksgame/program/binarytrees-csharpcore-6.html), which is in turn based on [binary-trees F# .NET #5 program](https://benchmarksgame-team.pages.debian.net/benchmarksgame/program/binarytrees-fsharpcore-5.html). The trick halves allocation count, i.e. 2x fewer objects are allocated on the heap.

To compile and run binarytrees_valhalla.java, you first need to get a build of Project Valhalla of OpenJDK. First you need to invoke `git clone https://github.com/openjdk/valhalla.git` (I've used commit 683bd428afa00aeb2f8e69fef028673004d3ae86 ). Then proceed with building that OpenJDK flavor: https://github.com/openjdk/valhalla/blob/lworld/doc/building.md

Results:
```
$ ~/devel/jdk-16/bin/javac binarytrees_7.java 
$ /dev/shm/valhalla/build/linux-x86_64-server-release/jdk/bin/javac binarytrees_valhalla.java 

$ time ~/devel/jdk-16/bin/java binarytrees_7 21
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

real	0m2,333s
user	0m6,928s
sys	0m0,689s

$ time /dev/shm/valhalla/build/linux-x86_64-server-release/jdk/bin/java binarytrees_valhalla 21
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

real	0m1,361s
user	0m3,357s
sys	0m0,679s

$ time for run in {1..10}; do ~/devel/jdk-16/bin/java binarytrees_7 21 > /dev/null; done

real	0m23,142s
user	1m8,759s
sys	0m7,008s

$ time for run in {1..10}; do /dev/shm/valhalla/build/linux-x86_64-server-release/jdk/bin/java binarytrees_valhalla 21 > /dev/null; done

real	0m14,478s
user	0m34,952s
sys	0m6,170s

```

## mandelbrot

mandelbrot_2.java is the [mandelbrot Java #2 program](https://benchmarksgame-team.pages.debian.net/benchmarksgame/program/mandelbrot-java-2.html) that is currently (i.e. as of 20th March 2021) the fastest Java implementation according to official results.

mandelbrot_panama_vector.java is a reimplementation with the inner vectorized loop loosely inspired by [mandelbrot Rust #7 program](https://benchmarksgame-team.pages.debian.net/benchmarksgame/program/mandelbrot-rust-7.html). It uses Vector API from Project Panama to implement explicit SIMD vectorization using optimal vector sizes (matching CPU vector registers size).

Note: Vector API has (currently?) much longer warmup than ordinary scalar code. Major chunk of time is spent on executing unoptimized code and on JIT optimizations. Running the code with bigger parameters will result in the performance improving as proportionally less time will be spent in unoptimized code and JIT compiler. I've included performance results for size 16000 (as in benchmark rules) and 64000 (to show how performance gap vs scalar code is growing).

```
$ ~/devel/jdk-16/bin/javac mandelbrot_2.java 
$ ~/devel/jdk-16/bin/javac --add-modules jdk.incubator.vector mandelbrot_panama_vector.java 
warning: using incubating module(s): jdk.incubator.vector
1 warning

$ ~/devel/jdk-16/bin/java mandelbrot_2 16000 > mandelbrot_16000_2.pbm
$ ~/devel/jdk-16/bin/java --add-modules jdk.incubator.vector mandelbrot_panama_vector 16000 > mandelbrot_16000_panama_vector.pbm
WARNING: Using incubator modules: jdk.incubator.vector
$ md5sum *.pbm
8c2ed8883de64eccd3154ac612021fe8  mandelbrot_16000_2.pbm
8c2ed8883de64eccd3154ac612021fe8  mandelbrot_16000_panama_vector.pbm

$ time for run in {1..10}; do ~/devel/jdk-16/bin/java mandelbrot_2 16000 > /dev/null; done

real	0m29,941s
user	1m56,997s
sys	0m0,352s

$ time for run in {1..10}; do ~/devel/jdk-16/bin/java --add-modules jdk.incubator.vector mandelbrot_panama_vector 16000 > /dev/null; done
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

real	0m12,780s
user	0m46,307s
sys	0m1,225s

$ time ~/devel/jdk-16/bin/java mandelbrot_2 64000 > /dev/null

real	0m46,806s
user	3m5,170s
sys	0m0,228s

$ time ~/devel/jdk-16/bin/java --add-modules jdk.incubator.vector mandelbrot_panama_vector 64000 > /dev/null
WARNING: Using incubator modules: jdk.incubator.vector

real	0m14,670s
user	0m56,406s
sys	0m0,392s
```