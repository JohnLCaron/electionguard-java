package pca.cs.jna.gmp;

import com.sun.jna.Memory;

/**
 * Prototype taken from jna-gmp github project (https://github.com/square/jna-gmp)
 * 
 * You do not need to edit this class
 * 
 * @author Adrian Pacurar
 *
 */
public class MPZMemory extends Memory {
	public final mpz_t peer;
	
	MPZMemory() {
		super(mpz_t.SIZE);
		peer = new mpz_t(this);
		LibGMP.__gmpz_init(peer);
	}
	
	@Override protected void finalize() {
		LibGMP.__gmpz_clear(peer);
		super.finalize();
	}
}
