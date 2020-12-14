package pca.cs.jna.gmp;

import java.math.BigInteger;
import java.util.Random;

/**
 * Facilitates translation between BigInteger and mpz_t
 * 
 * Prototype taken from jna-gmp github project (https://github.com/square/jna-gmp)
 * 
 * You do not need to edit this class.
 * 
 * @author Adi Pacurar
 *
 */
public class GInteger extends BigInteger {	
	private final MPZMemory memory = new MPZMemory();
	
	{
		GMP.INSTANCE.get().mpzImport(memory.peer, super.signum(), super.abs().toByteArray());
	}
	
	mpz_t getPeer() {
		return memory.peer;
	}
	
	/**
	 * Various constructors
	 */
	public GInteger (BigInteger other) {
		super(other.toByteArray());
	}
	
	public GInteger(byte[] val) {
		super(val);
	}
	public GInteger(int signum, byte[] magnitude) {
		super(signum, magnitude);
	}
	public GInteger(String val, int radix) {
		super(val, radix);
	}
	public GInteger(String val) {
		super(val);
	}
	public GInteger(int numbits, Random r) {
		super(numbits, r);
	}
	public GInteger (int bitlength, int certainty, Random r) {
		super(bitlength, certainty, r);
	}
}
