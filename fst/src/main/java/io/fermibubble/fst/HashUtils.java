package io.fermibubble.fst;

import com.google.common.hash.Hashing;

import java.nio.charset.StandardCharsets;

public class HashUtils {

    /**
     * Generate 'n' hashes of 'key' in the range (0, 'range'), with the added 'tweak'.
     * The classic bloom filter implementation uses 'n' hashes, but to improve performance we use a seeded
     * LCG (Linear Congruential Generator) to generate the hashes from the initial hash seed.
     */
    @SuppressWarnings("UnstableApiUsage")
    static int[] generateNHashes(final String key, final int tweak, final int n, final int range) {
        final int[] result = new int[n];
        final long hash64 = Hashing.murmur3_128().hashString(key + tweak, StandardCharsets.UTF_8).asLong();
        final PCG pcg = new PCG(hash64, tweak);
        for(int i = 0; i < n; i++)
            result[i] = (pcg.nextInt() & Integer.MAX_VALUE) % range;
        return result;
    }

    /**
     * Generate deterministic value in the range (0, 'range'), given a 'key' string and an integer 'tweak' as input.
     */
    @SuppressWarnings("UnstableApiUsage")
    static int tweakedHash(final String key, final int tweak, final int range) {
        final int hash = Hashing.goodFastHash(32).newHasher()
                .putString(key, StandardCharsets.UTF_8)
                .putInt(tweak)
                .hash().asInt();
        return (hash & Integer.MAX_VALUE) % range;
    }

    /**
     * PCG implements the 32-bit flavor of O'Neill's PCG PRNG. See https://www.pcg-random.org/ for more information.
     */
    static class PCG {
        private long state;
        private final long inc;

        PCG(final long initState, final long initSeq) {
            state = 0;
            inc = (initSeq * 2) + 1;
            nextInt();
            state += initState;
            nextInt();
        }

        int nextInt() {
            final long oldState = state;
            state = oldState * 6364136223846793005L + inc;
            int xorShifted = (int) (((oldState >>> 18) ^ oldState) >>> 27);
            int rot = (int) (oldState >>> 59);
            return Integer.rotateRight(xorShifted, rot);
        }
    }
}
