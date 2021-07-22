package io.fermibubble.fst;

import com.google.common.primitives.Ints;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HashUtilsTest {
    @Test
    public void testBloomFilterBasic() {
        final int n = 10;
        final int range = 100;
        int[] hashes = HashUtils.generateNHashes("testKey", 3, n, range);
        assertEquals(n, hashes.length);
        for(final int k : hashes) {
            assertTrue(k > 0);
            assertTrue(k < range);
        }
    }

    @Test
    public void testOverlapCount() {
        final int n = 3;
        final int range = 30;
        final Map<List<Integer>, Integer> combinations = new HashMap<>();
        for(int i = 0; i < 1000; i++) {
            final List<Integer> hashes = Ints.asList(HashUtils.generateNHashes("testKey" + i
                    , ThreadLocalRandom.current().nextInt(), n, range));
            final int current = combinations.getOrDefault(hashes, 0);
            combinations.put(hashes, current + 1);
        }
        for(Map.Entry<List<Integer>, Integer> entry : combinations.entrySet()) {
            assertTrue(entry.getValue() <= 5);
        }
    }

    @Test
    public void testBloomFilterChiSq() {
        final int n = 10000;
        final int k = 33;
        final int[] hashes = HashUtils.generateNHashes("testKey", ThreadLocalRandom.current().nextInt(), n, k);
        final int[] buckets = new int[k];
        for(final int hash : hashes)
            buckets[hash] += 1;
        final double chiSq = getChiSq(n, buckets);
        //With 33 degrees of freedom, P(chi_sq > 60) < 1/10000
        assertTrue(chiSq < 70);
    }

    @Test
    public void testTweaked() {
        final int range = 100;
        for(int i = 0; i < 1000; i++) {
            final int hash = HashUtils.tweakedHash("" + i, ThreadLocalRandom.current().nextInt(), range);
            assertTrue(hash >= 0);
            assertTrue(hash <= range);
        }
    }

    @Test
    public void testTweakedChiSq() {
        final int n = 10000;
        final int range = 100;
        final int[] buckets = new int[range];
        for(int i = 0; i < n; i++)
            buckets[HashUtils.tweakedHash("" + i, 3, range)] += 1;
        final double chiSq = getChiSq(n, buckets);
        //With 100 degrees of freedom, P(chi_sq > 160) < 1/10000
        assertTrue(chiSq < 160);
    }

    private double getChiSq(final int n, final int[] buckets) {
        final double expected = n / (double) buckets.length;
        double chiSq = 0.0d;
        for(final int bucket : buckets) {
            chiSq += ((bucket - expected) * (bucket - expected)) / expected;
        }
        return chiSq;
    }
}
