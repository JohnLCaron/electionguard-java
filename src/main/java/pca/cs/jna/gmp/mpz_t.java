package pca.cs.jna.gmp;

import com.sun.jna.Pointer;

/**
 * The data type to pass to libgmp functions (gmp integers of type mpz_t)
 * 
 * Prototype taken from jna-gmp github project (https://github.com/square/jna-gmp)
 * 
 * You do not need to edit this class.
 * 
 * @author Adi Pacurar
 *
 */
public class mpz_t extends Pointer {
	public static final int SIZE = 16;//size in bytes of the native structures
	
	/**
	 * Construct a long from a native address.
	 * @param peer the address of a block of native memory at least SIZE bytes large
	 */
	public mpz_t(long peer) {
		super(peer);
	}
	
	/**
	 * Constructs mpz_t from a Pointer
	 * @param from
	 */
	public mpz_t(Pointer from) {
		this(Pointer.nativeValue(from));
	}
}
