/**
 * The Computer Language Benchmarks Game
 * https://salsa.debian.org/benchmarksgame-team/benchmarksgame/
 * <p>
 * based on "binary-trees Java #7 program" (I/O and parallelism)
 * uses tree node implementation from "binary-trees C# .NET #6 program"
 */

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class binarytrees_valhalla {

    private static final int MIN_DEPTH = 4;
    private static final ExecutorService EXECUTOR_SERVICE =
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    public static void main(final String[] args) throws Exception {
        int n = 0;
        if (0 < args.length) {
            n = Integer.parseInt(args[0]);
        }

        final int maxDepth = Math.max(n, (MIN_DEPTH + 2));
        final int stretchDepth = maxDepth + 1;

        System.out.println("stretch tree of depth " + stretchDepth + "\t check: "
                + TreeNode.create(stretchDepth).itemCheck());

        final TreeNode longLivedTree = TreeNode.create(maxDepth);

        final String[] results = new String[(maxDepth - MIN_DEPTH) / 2 + 1];

        for (int d = MIN_DEPTH; d <= maxDepth; d += 2) {
            final int depth = d;
            EXECUTOR_SERVICE.execute(() -> {
                int check = 0;

                final int iterations = 1 << (maxDepth - depth + MIN_DEPTH);
                for (int i = 1; i <= iterations; ++i) {
                    final TreeNode treeNode1 = TreeNode.create(depth);
                    check += treeNode1.itemCheck();
                }
                results[(depth - MIN_DEPTH) / 2] =
                        iterations + "\t trees of depth " + depth + "\t check: " + check;
            });
        }

        EXECUTOR_SERVICE.shutdown();
        EXECUTOR_SERVICE.awaitTermination(120L, TimeUnit.SECONDS);

        for (final String str : results) {
            System.out.println(str);
        }

        System.out.println("long lived tree of depth " + maxDepth +
                "\t check: " + longLivedTree.itemCheck());
    }

    private static class Next {
        private final TreeNode left, right;

        private Next(TreeNode left, TreeNode right) {
            this.left = left;
            this.right = right;
        }
    }

    private primitive static final class TreeNode {

        private final Next next;

        private TreeNode(TreeNode left, TreeNode right) {
            next = new Next(left, right);
        }

        private TreeNode() {
            next = null;
        }

        private static TreeNode create(int d) {
            return d == 1 ? new TreeNode(new TreeNode(), new TreeNode())
                    : new TreeNode(create(d - 1), create(d - 1));
        }

        private int itemCheck() {
            int c = 1;
            var current = next;
            while (current != null) {
                c += current.right.itemCheck() + 1;
                current = current.left.next;
            }
            return c;
        }
    }
}
