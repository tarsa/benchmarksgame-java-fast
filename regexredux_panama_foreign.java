/*
   The Computer Language Benchmarks Game
   https://salsa.debian.org/benchmarksgame-team/benchmarksgame/

   contributed by Piotr Tarsa
*/

import jdk.incubator.foreign.*;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;

public class regexredux_panama_foreign {
    private static final ExecutorService EXECUTOR_SERVICE =
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    public static void main(String[] args) throws Exception {
        final byte[] rawInput = System.in.readAllBytes();
        final int initialLength = rawInput.length;

        final var sequence =
                MemorySegment.allocateNative(initialLength).share();
        final int sequenceLength = withScope(scope -> {
            var rawInputBuffer =
                    scope.allocateArray(MemoryLayouts.JAVA_BYTE, rawInput);
            var compiledPattern = compilePattern(">.*\n|\n");
            return substitute(compiledPattern, rawInputBuffer, initialLength,
                    pcre2_h.NULL(), sequence, initialLength, "");
        });

        var magicRegExpsCount = EXECUTOR_SERVICE.submit(() -> {
            final Map<String, String> iub = new LinkedHashMap<>();
            iub.put("tHa[Nt]", "<4>");
            iub.put("aND|caN|Ha[DS]|WaS", "<3>");
            iub.put("a[NSt]|BY", "<2>");
            iub.put("<[^>]*>", "|");
            iub.put("\\|[^|][^|]*\\|", "-");

            return withScope(scope -> {
                var currentLength = sequenceLength;
                var bufLength = currentLength * 3 / 2;
                var buf1 = scope.allocate(bufLength, 1);
                var buf2 = scope.allocate(bufLength, 1);
                buf1.copyFrom(sequence);
                var flip = false;

                for (Entry<String, String> entry : iub.entrySet()) {
                    var pattern = entry.getKey();
                    var replacement = entry.getValue();

                    var compiledPattern = compilePattern(pattern);
                    currentLength = substitute(compiledPattern,
                            flip ? buf2 : buf1, currentLength,
                            pcre2_h.NULL(),
                            flip ? buf1 : buf2, bufLength,
                            replacement);
                    flip = !flip;
                }
                return currentLength;
            });
        });

        var variants = Arrays.asList("agggtaaa|tttaccct",
                "[cgt]gggtaaa|tttaccc[acg]",
                "a[act]ggtaaa|tttacc[agt]t",
                "ag[act]gtaaa|tttac[agt]ct",
                "agg[act]taaa|ttta[agt]cct",
                "aggg[acg]aaa|ttt[cgt]ccct",
                "agggt[cgt]aa|tt[acg]accct",
                "agggta[cgt]a|t[acg]taccct",
                "agggtaa[cgt]|[acg]ttaccct");

        var tasks = variants.stream().map(variant -> (Callable<String>) () -> {
            var compiledPattern = compilePattern(variant);
            var oVectorSize = 100;
            var matchData =
                    pcre2_h.pcre2_match_data_create_8(oVectorSize, pcre2_h.NULL());
            var oVectorPtr = pcre2_h.pcre2_get_ovector_pointer_8(matchData)
                    .asSegmentRestricted(16 * oVectorSize);
            MemoryAccess.setLongAtIndex(oVectorPtr, 1, 0);
            long count = 0;
            var result = 1;
            while ((result = pcre2_h.pcre2_jit_match_8(compiledPattern,
                    sequence, sequenceLength,
                    MemoryAccess.getLongAtIndex(oVectorPtr, 2L * result - 1), 0,
                    matchData, pcre2_h.NULL())) > 0) count += result;
            if (result != pcre2_h.PCRE2_ERROR_NOMATCH()) {
                showPcre2ErrorIfAny("jit match", result);
            }
            return variant + " " + count;
        }).toList();

        for (var result : EXECUTOR_SERVICE.invokeAll(tasks)) {
            System.out.println(result.get());
        }

        System.out.println();
        System.out.println(initialLength);
        System.out.println(sequenceLength);
        System.out.println(magicRegExpsCount.get());

        EXECUTOR_SERVICE.shutdown();
    }

    private static int substitute(
            MemoryAddress compiledPattern,
            MemorySegment inputBuffer, int inputLength,
            MemoryAddress matchContext,
            MemorySegment outputBuffer, int outputBufferLength,
            String replacement) {
        return withScope(scope -> {
            var replacementBytes =
                    replacement.getBytes(StandardCharsets.US_ASCII);
            var replacementBuffer = scope.allocateArray(
                    MemoryLayouts.JAVA_BYTE, replacementBytes);
            var outputLengthHolder = scope.allocate(
                    MemoryLayouts.JAVA_LONG, (long) outputBufferLength);
            var options = pcre2_h.PCRE2_SUBSTITUTE_GLOBAL() |
                    pcre2_h.PCRE2_NO_UTF_CHECK();
            var substitutionResult = pcre2_h.pcre2_substitute_8(
                    compiledPattern,
                    inputBuffer, inputLength,
                    0, options, pcre2_h.NULL(),
                    matchContext,
                    replacementBuffer, replacementBytes.length,
                    outputBuffer, outputLengthHolder);
            showPcre2ErrorIfAny("substitutionResult", substitutionResult);
            return substitutionResult < 0 ?
                    0 : MemoryAccess.getInt(outputLengthHolder);
        });
    }

    private static MemoryAddress compilePattern(String pattern) {
        return withScope(scope -> {
            var patternBytes = pattern.getBytes(StandardCharsets.US_ASCII);
            var patternLength = patternBytes.length;
            var bufPattern =
                    scope.allocateArray(MemoryLayouts.JAVA_BYTE, patternBytes);
            var bufErrorCode = scope.allocate(pcre2_h.int64_t);
            var bufErrorOffset = scope.allocate(pcre2_h.int64_t);
            var compiledPattern = pcre2_h.pcre2_compile_8(
                    bufPattern, patternLength, 0,
                    bufErrorCode, bufErrorOffset, pcre2_h.NULL());
            if (compiledPattern.equals(pcre2_h.NULL())) {
                showPcre2Error("pcre2_compile_8 failed at offset " +
                                MemoryAccess.getInt(bufErrorOffset),
                        MemoryAccess.getInt(bufErrorCode));
            }
            var jitCompileResult = pcre2_h.pcre2_jit_compile_8(
                    compiledPattern, pcre2_h.PCRE2_JIT_COMPLETE());
            showPcre2ErrorIfAny("pcre_2jit_compile_8", jitCompileResult);
            return compiledPattern;
        });
    }

    private static void showPcre2ErrorIfAny(
            String description, int resultOrErrorCode) {
        if (resultOrErrorCode < 0) {
            showPcre2Error(description, resultOrErrorCode);
        }
    }

    private static void showPcre2Error(String description, int errorCode) {
        withScope(scope -> {
            var bufSize = 1000;
            var buf = scope.allocate(bufSize, 1);
            var errorMsgLength = pcre2_h.pcre2_get_error_message_8(
                    errorCode, buf, bufSize);
            if (errorMsgLength >= 0) {
                var errorMsgBytes = new byte[errorMsgLength];
                buf.asByteBuffer().get(errorMsgBytes);
                var errorMsg = new String(errorMsgBytes, 0, errorMsgLength,
                        StandardCharsets.US_ASCII);
                new Exception(description + " " + errorCode +
                        " = " + errorMsg).printStackTrace(System.out);
            } else {
                new Exception(description +
                        " Error during getting error message: " +
                        errorMsgLength).printStackTrace(System.out);
            }
        });
    }

    private static void withScope(Consumer<NativeScope> body) {
        try (var scope = NativeScope.unboundedScope()) {
            body.accept(scope);
        }
    }

    private static <T> T withScope(Function<NativeScope, T> body) {
        try (var scope = NativeScope.unboundedScope()) {
            return body.apply(scope);
        }
    }
}
