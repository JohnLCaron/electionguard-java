package pca.cs.jna;

import pca.cs.jna.gmp.*;
import java.math.BigInteger;
import com.sun.jna.Library;
import com.sun.jna.Native;

public class Test {

	/* public interface CLibrary extends Library { //example on how to load win's kernel32.dll library
		CLibrary INSTANCE = Native.loadLibrary("libgmp.a", CLibrary.class);
		void Beep(int freq, int duration);//we use the beep function as example
	} */

	public static void main(String[] args) {
		try {
			// CLibrary.INSTANCE.Beep(400,1000);//beep at 400 Hz for 1000 ms

			BigInteger a = new BigInteger("18");
			BigInteger b = new BigInteger("60");
			BigInteger r;

			r = GMP.add(a, b);
			System.out.println(a + " + " + b + " = " + r);
			r = GMP.subtract(a, b);
			System.out.println(a + " - " + b + " = " + r);
			r = GMP.subtract(b, a);
			System.out.println(b + " - " + a + " = " + r);
			r = GMP.multiply(a, b);
			System.out.println(a + " * " + b + " = " + r);
			r = GMP.divide(b, a);
			System.out.println(b + " / " + a + " = " + r);
			r = GMP.remainder(b, a);
			System.out.println(b + " % " + a + " = " + r);
			r = GMP.divide(a, b);
			System.out.println(a + " / " + b + " = " + r);
			r = GMP.remainder(a, b);
			System.out.println(a + " % " + b + " = " + r);
			r = GMP.gcd(a, b);
			System.out.println("gcd(" + a + ", " + b + ") = " + r);

			BigInteger d = new BigInteger("51235111011101010101111101030313131313131313131313");
			System.out.println(GMP.isProbablePrime(d, 6) + "  " + d.isProbablePrime(6));
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

}
