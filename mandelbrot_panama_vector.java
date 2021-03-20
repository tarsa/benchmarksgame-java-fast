/**
 * The Computer Language Benchmarks Game
 * https://salsa.debian.org/benchmarksgame-team/benchmarksgame/
 * <p>
 * inner vectorized loop loosely inspired by "mandelbrot Rust #7 program"
 */

import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorSpecies;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public class mandelbrot_panama_vector {

    private static final VectorSpecies<Double> SPECIES =
            DoubleVector.SPECIES_PREFERRED.length() <= 8 ?
                    DoubleVector.SPECIES_PREFERRED : DoubleVector.SPECIES_512;

    private static final int LANES = SPECIES.length();

    private static final int LANES_LOG = Integer.numberOfTrailingZeros(LANES);

    public static void main(String[] args) throws IOException {
        if ((LANES > 8) || (LANES != (1 << LANES_LOG))) {
            var errorMsg = "LANES must be a power of two and at most 8. " +
                    "Change SPECIES in the source code.";
            throw new RuntimeException(errorMsg);
        }
        // benchmarks game mandelbrot run
        var sideLen = Integer.parseInt(args[0]);
        try (var out = new BufferedOutputStream(makeOut2())) {
            var headerStr = String.format("P4\n%d %d\n", sideLen, sideLen);
            out.write(headerStr.getBytes());
            out.write(computeRows(sideLen));
        }
    }

    @SuppressWarnings("unused")
    // the version that avoids mixing up output with JVM diagnostic messages
    private static OutputStream makeOut1() throws IOException {
        return Files.newOutputStream(Path.of("mandelbrot_simd_1.pbm"));
    }

    @SuppressWarnings("unused")
    // the version that is compatible with benchmark requirements
    private static OutputStream makeOut2() {
        return System.out;
    }

    private static byte[] computeRows(int sideLen) {
        var threadRowChunks =
                ThreadLocal.withInitial(() -> new long[sideLen / 64]);
        var rowOutputSize = (sideLen + 7) / 8;
        var rowsMerged = new byte[sideLen * rowOutputSize];
        var numCpus = Runtime.getRuntime().availableProcessors();
        var fac = 2.0 / sideLen;
        var aCr = IntStream.range(0, sideLen).parallel()
                .mapToDouble(x -> x * fac - 1.5).toArray();
        var bitsReversalMapping = computeBitsReversalMapping();
        var computeEc = Executors.newWorkStealingPool(numCpus);
        for (var i = 0; i < sideLen; i++) {
            var y = i;
            computeEc.submit(() -> {
                var rowChunks = threadRowChunks.get();
                var rowOffset = y * rowOutputSize;
                var Ci = y * fac - 1.0;
                try {
                    computeRow(Ci, aCr, bitsReversalMapping,
                            rowChunks, rowsMerged, rowOffset);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(-1);
                }
            });
        }
        computeEc.shutdown();
        while (!computeEc.isTerminated()) {
            try {
                @SuppressWarnings("unused")
                var ignored = computeEc.awaitTermination(1, TimeUnit.DAYS);
            } catch (InterruptedException ignored) {
            }
        }
        return rowsMerged;
    }

    private static byte[] computeBitsReversalMapping() {
        var bitsReversalMapping = new byte[256];
        for (var i = 0; i < 256; i++) {
            bitsReversalMapping[i] = (byte) (Integer.reverse(i) >>> 24);
        }
        return bitsReversalMapping;
    }

    private static void computeRow(double Ci, double[] aCr,
                                   byte[] bitsReversalMapping, long[] rowChunks,
                                   byte[] rowsMerged, int rowOffset) {
        computeChunksVector(Ci, aCr, rowChunks);
        transferRowFlags(rowChunks, bitsReversalMapping, rowsMerged, rowOffset);
        computeRemainderScalar(Ci, aCr, rowsMerged, rowOffset);
    }

    private static void computeChunksVector(double Ci, double[] aCr,
                                            long[] rowChunks) {
        var sideLen = aCr.length;
        var vCi = DoubleVector.broadcast(SPECIES, Ci);
        var vZeroes = DoubleVector.zero(SPECIES);
        var vFours = DoubleVector.broadcast(SPECIES, 4.0);
        var zeroMask = VectorMask.fromLong(SPECIES, 0);
        // (1 << 6) = 64 = length of long in bits
        for (var xBase = 0; xBase < (sideLen & -(1 << 6)); xBase += (1 << 6)) {
            var cmpFlags = 0L;
            for (var xInc = 0; xInc < (1 << 6); xInc += LANES * 2) {
                var vZr1 = vZeroes;
                var vZr2 = vZeroes;
                var vZi1 = vZeroes;
                var vZi2 = vZeroes;
                var vCr1 = DoubleVector.fromArray(
                        SPECIES, aCr, xBase + xInc);
                var vCr2 = DoubleVector.fromArray(
                        SPECIES, aCr, xBase + xInc + LANES);
                var vZrN1 = vZeroes;
                var vZrN2 = vZeroes;
                var vZiN1 = vZeroes;
                var vZiN2 = vZeroes;
                var cmpMask1 = zeroMask;
                var cmpMask2 = zeroMask;
                var stop = false;
                for (var outer = 0; !stop && outer < 10; outer++) {
                    for (var inner = 0; inner < 5; inner++) {
                        vZi1 = vZr1.add(vZr1).mul(vZi1).add(vCi);
                        vZi2 = vZr2.add(vZr2).mul(vZi2).add(vCi);
                        vZr1 = vZrN1.sub(vZiN1).add(vCr1);
                        vZr2 = vZrN2.sub(vZiN2).add(vCr2);
                        vZiN1 = vZi1.mul(vZi1);
                        vZiN2 = vZi2.mul(vZi2);
                        vZrN1 = vZr1.mul(vZr1);
                        vZrN2 = vZr2.mul(vZr2);
                    }
                    // I'm doing here: cmpMask = cmpMask.or(newValue);
                    // instead of just: cmpMask = newValue;
                    // because 4.lt(NaN) gives false
                    // NaN comes from Infinity - Infinity
                    // Infinity comes from numeric overflows
                    cmpMask1 = cmpMask1.or(vFours.lt(vZiN1.add(vZrN1)));
                    cmpMask2 = cmpMask2.or(vFours.lt(vZiN2.add(vZrN2)));
                    stop = cmpMask1.allTrue() & cmpMask2.allTrue();
                }
                cmpFlags |= cmpMask1.toLong() << xInc;
                cmpFlags |= cmpMask2.toLong() << (xInc + LANES);
            }
            rowChunks[xBase >> 6] = cmpFlags;
        }
    }

    private static void transferRowFlags(long[] rowChunks,
                                         byte[] bitsReversalMapping,
                                         byte[] rowsMerged, int rowOffset) {
        for (var i = 0; i < rowChunks.length; i++) {
            var group = ~rowChunks[i];
            for (var j = 7; j >= 0; j--) {
                rowsMerged[rowOffset + i * 8 + j] =
                        bitsReversalMapping[0xff & (byte) (group >>> (j * 8))];
            }
        }
    }

    private static void computeRemainderScalar(double Ci, double[] aCr,
                                               byte[] rowsMerged,
                                               int rowOffset) {
        computeScalar(Ci, aCr, rowsMerged, rowOffset, true);
    }

    private static void computeScalar(double Ci, double[] aCr,
                                      byte[] rowsMerged, int rowOffset,
                                      boolean remainderOnly) {
        var sideLen = aCr.length;
        var startX = remainderOnly ? sideLen & -(1 << 6) : 0;
        var bits = 0;
        for (var x = startX; x < sideLen; x++) {
            var Zr = 0.0;
            var Zi = 0.0;
            var Cr = aCr[x];
            var i = 50;
            var ZrN = 0.0;
            var ZiN = 0.0;
            do {
                Zi = 2.0 * Zr * Zi + Ci;
                Zr = ZrN - ZiN + Cr;
                ZiN = Zi * Zi;
                ZrN = Zr * Zr;
            } while (ZiN + ZrN <= 4.0 && --i > 0);
            bits <<= 1;
            bits += i == 0 ? 1 : 0;
            if (x % 8 == 7) {
                rowsMerged[rowOffset + x / 8] = (byte) bits;
                bits = 0;
            }
        }
        if (sideLen % 8 != 0) {
            rowsMerged[rowOffset + sideLen / 8] = (byte) bits;
        }
    }
}
