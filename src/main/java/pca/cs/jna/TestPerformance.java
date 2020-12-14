package pca.cs.jna;

import java.math.BigInteger;
import java.util.Random;
import com.google.common.base.Stopwatch;

import pca.cs.jna.gmp.GMP;

/**
 * This class is used to benchmark performance between Java's API vs GMP
 * @author Adi Pacurar
 *
 */
public class TestPerformance {
	static BigInteger ZERO = BigInteger.ZERO;
	static BigInteger ONE = BigInteger.ONE;
	static BigInteger TWO = ONE.add(ONE);
	
	public static void main(String[] args) {
		Random r = new Random(42);//fixed seed for reproducible results
		Stopwatch sw = Stopwatch.createUnstarted();
		int[] bits = { 128, 256, 512, 1024, 2048 };
		
		/*
		 * Addition
		 */
		int operationCount = 50000;
		
		System.out.println("Addition operation (" + operationCount + " times):");
		for (int i = 0; i < bits.length; i++) {
			System.out.print("   " + bits[i] + " bits | ");
			
			int count = operationCount;
			r = new Random(42);
			BigInteger a = new BigInteger(bits[i], r);
			BigInteger b = new BigInteger(bits[i], r);
			sw.start();
			while (count > 0) {
				a.add(b);
				count--;
			}
			sw.stop();
			System.out.print("Java: " + sw);
			sw.reset();
			
			count = operationCount;
			sw.start();
			while (count > 0) {
				GMP.add(a, b);
				count--;
			}
			sw.stop();
			System.out.print(" | GMP: " + sw);
			sw.reset();
			System.out.println();
		}
		System.out.println("Done.");
		
		
		/*
		 * Multiplication
		 */
		operationCount = 50000;
		
		System.out.println("Multiplication operation (" + operationCount + " times):");
		for (int i = 0; i < bits.length; i++) {
			System.out.print("   " + bits[i] + " bits | ");
			
			int count = operationCount;
			r = new Random(42);
			BigInteger a = new BigInteger(bits[i], r);
			BigInteger b = new BigInteger(bits[i], r);
			sw.start();
			while (count > 0) {
				a.multiply(b);
				count--;
			}
			sw.stop();
			System.out.print("Java: " + sw);
			sw.reset();
			
			count = operationCount;
			sw.start();
			while (count > 0) {
				GMP.multiply(a, b);
				count--;
			}
			sw.stop();
			System.out.print(" | GMP: " + sw);
			sw.reset();
			System.out.println();
		}
		System.out.println("Done.");
		
		
		/*
		 * Group operations: a^e mod n
		 */
		operationCount = 20000;
		
		System.out.println("Group operation a^e mod n ("+ operationCount + " times):");
		for (int i = 0; i < bits.length; i++) {
			System.out.print("   " + bits[i] + " bits | ");
			
			int count = operationCount;
			r = new Random(42);
			BigInteger a = new BigInteger(bits[i], r);
			BigInteger e = new BigInteger(32, r);
			BigInteger n = new BigInteger(bits[i], r);
			
			if (n.mod(TWO).equals(ZERO))//for GMP.modPowSecure() we need odd modulus
				n = n.add(ONE);
			
			sw.start();
			while (count > 0) {
				a.modPow(e, n);
				count--;
			}
			sw.stop();
			System.out.print("Java: " + sw);
			sw.reset();
			
			count = operationCount;
			sw.start();
			while (count > 0) {
				GMP.modPowInsecure(a, e, n);
				count--;
			}
			sw.stop();
			System.out.print(" | GMP (insecure): " + sw);
			sw.reset();
			
			count = operationCount;
			sw.start();
			while (count > 0) {
				GMP.modPowSecure(a, e, n);
				count--;
			}
			sw.stop();
			System.out.print(" | GMP (secure): " + sw);
			sw.reset();
			System.out.println();
		}
		System.out.println("Done.");
		
		
		/**
		 * Group operation: inverse
		 */
		operationCount = 10000;
		
		System.out.println("Group operation modInverse n (" + operationCount + " times):");
		for (int i = 0; i < bits.length; i++) {
			System.out.print("   " + bits[i] + " bits | ");
			
			int count = operationCount;
			r = new Random(42);
			BigInteger a = new BigInteger(bits[i], r);
			BigInteger n = new BigInteger(bits[i], r);
			while (!a.gcd(n).equals(ONE)) {//make sure 'a' is invertible
				a = new BigInteger(bits[i], r);
			}
			
			sw.start();
			while (count > 0) {
				a.modInverse(n);
				count--;
			}
			sw.stop();
			System.out.print("Java: " + sw);
			sw.reset();
			
			count = operationCount;
			sw.start();
			while (count > 0) {
				GMP.modInverse(a, n);
				count--;
			}
			sw.stop();
			System.out.print(" | GMP: " + sw);
			sw.reset();
			System.out.println();
		}
		System.out.println("Done.");
		
		
		/*
		 * Primality testing
		 */
		int certainty = 20;
		int primeCount = 5;
		
		System.out.println("Primality testing:");
		for (int i = 0; i < bits.length; i++) {
			System.out.print("   " + bits[i] + " bits | ");
			
			int count = primeCount;
			r = new Random(42);
			BigInteger n = new BigInteger(bits[i], r);
			BigInteger p = n.multiply(TWO).add(ONE);
			sw.start();
			while (count > 0) {
				if (p.isProbablePrime(certainty)) {
					count--;
				}
				n = n.add(ONE);
				p = n.multiply(TWO).add(ONE);
			}
			sw.stop();
			System.out.print("Java: " + sw);
			sw.reset();
			
			count = primeCount;
			r = new Random(42);
			n = new BigInteger(bits[i], r);
			p = n.multiply(TWO).add(ONE);
			sw.start();
			while (count > 0) {
				//libgmp probability is 1/4^certainty, whereas Java's is 1/2^certainty
				//so we divide the certainty by 2
				if (GMP.isProbablePrime(p, certainty/2) > 0) {
					count--;
				}
				n = n.add(ONE);
				p = n.multiply(TWO).add(ONE);
			}
			sw.stop();
			System.out.print(" | GMP: " + sw);
			sw.reset();
			System.out.println();
		}
		System.out.println("Done.");
	}//end main()

}
