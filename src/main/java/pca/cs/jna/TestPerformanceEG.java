package pca.cs.jna;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import pca.cs.jna.gmp.GMP;

import java.math.BigInteger;
import java.util.Random;

/** This class is used to benchmark performance between Java's API vs GMP for ElectionGuard Operations */
/*
Results 12/10/2020
multiplicative inverse mod p operation (5000 times):
   128 bits | Java: 3.040 s | GMP: 101.1 ms
   256 bits | Java: 3.118 s | GMP: 41.32 ms
   512 bits | Java: 3.370 s | GMP: 49.09 ms
   1024 bits | Java: 3.884 s | GMP: 71.07 ms
   2048 bits | Java: 4.787 s | GMP: 110.5 ms
pow_p: b^e mod p operation (1000 times):
   128 bits | Java: 1.266 s | GMP: 892.7 ms
   256 bits | Java: 2.054 s | GMP: 1.665 s
   512 bits | Java: 3.923 s | GMP: 3.330 s
   1024 bits | Java: 7.748 s | GMP: 6.455 s
   2048 bits | Java: 15.03 s | GMP: 12.55 s
mult_p operation (50000 times):
   128 bits | Java: 29.31 ms | GMP: 544.0 ms
   256 bits | Java: 9.977 ms | GMP: 577.4 ms
   512 bits | Java: 20.26 ms | GMP: 694.4 ms
   1024 bits | Java: 70.94 ms | GMP: 935.5 ms
   2048 bits | Java: 1.101 s | GMP: 1.480 s
Done.

 */
public class TestPerformanceEG {
	static BigInteger ZERO = BigInteger.ZERO;
	static BigInteger ONE = BigInteger.ONE;
	static BigInteger TWO = ONE.add(ONE);
	
	public static void main(String[] args) {
		Random r = new Random(42);//fixed seed for reproducible results
		Stopwatch sw = Stopwatch.createUnstarted();
		int[] bits = { 128, 256, 512, 1024, 2048 };

		int operationCount;

		operationCount = 5000;
		System.out.println("multiplicative inverse mod p operation (" + operationCount + " times):");
		for (int i = 0; i < bits.length; i++) {
			System.out.print("   " + bits[i] + " bits | ");

			int count = operationCount;
			r = new Random(42);
			BigInteger a = new BigInteger(bits[i], r);
			BigInteger b = new BigInteger(bits[i], r);
			sw.start();
			while (count > 0) {
				sunya.electionguard.Group.mult_inv_p(a);
				count--;
			}
			sw.stop();
			System.out.print("Java: " + sw);
			sw.reset();

			count = operationCount;
			sw.start();
			while (count > 0) {
				sunya.electionguard.Group.mult_inv_p_gmp(a);
				count--;
			}
			sw.stop();
			System.out.print(" | GMP: " + sw);
			sw.reset();
			System.out.println();
		}

		operationCount = 1000;
		System.out.println("pow_p: b^e mod p operation (" + operationCount + " times):");
		for (int i = 0; i < bits.length; i++) {
			System.out.print("   " + bits[i] + " bits | ");

			int count = operationCount;
			r = new Random(42);
			BigInteger a = new BigInteger(bits[i], r);
			BigInteger b = new BigInteger(bits[i], r);
			sw.start();
			while (count > 0) {
				sunya.electionguard.Group.pow_p(a, b);
				count--;
			}
			sw.stop();
			System.out.print("Java: " + sw);
			sw.reset();

			count = operationCount;
			sw.start();
			while (count > 0) {
				sunya.electionguard.Group.pow_p_gmp(a, b);
				count--;
			}
			sw.stop();
			System.out.print(" | GMP: " + sw);
			sw.reset();
			System.out.println();
		}

		operationCount = 50000;
		System.out.println("mult_p operation (" + operationCount + " times):");
		for (int i = 0; i < bits.length; i++) {
			System.out.print("   " + bits[i] + " bits | ");
			
			int count = operationCount;
			r = new Random(42);
			BigInteger a = new BigInteger(bits[i], r);
			BigInteger b = new BigInteger(bits[i], r);
			ImmutableList<BigInteger> list = ImmutableList.of(a, b);
			sw.start();
			while (count > 0) {
				sunya.electionguard.Group.mult_p(list);
				count--;
			}
			sw.stop();
			System.out.print("Java: " + sw);
			sw.reset();
			
			count = operationCount;
			sw.start();
			while (count > 0) {
				sunya.electionguard.Group.mult_p_gmp(list);
				count--;
			}
			sw.stop();
			System.out.print(" | GMP: " + sw);
			sw.reset();
			System.out.println();
		}
		System.out.println("Done.");
	}//end main()

}
