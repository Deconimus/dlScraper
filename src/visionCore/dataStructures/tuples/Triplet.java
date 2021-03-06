package visionCore.dataStructures.tuples;

import java.io.Serializable;

/**
A tuple with 3 elements.
This code was gracefully generated by a Python script.
@author	Deconimus
*/
public class Triplet <X, Y, Z> implements Serializable {
	
	private static final long serialVersionUID = 178239952901637573L;
	
	public X x;
	public Y y;
	public Z z;
	
	public Triplet() {}
	
	public Triplet(X x, Y y, Z z) {
		
		this.x = x;
		this.y = y;
		this.z = z;
	}
	
	@Override
	public boolean equals(Object o) {
		
		if (o == this) { return true; }
		if (o == null || !(o instanceof Triplet)) { return false; }
		
		Triplet t = (Triplet)o;
		
		return t.x.equals(x) && t.y.equals(y) && t.z.equals(z);
	}
	
}
