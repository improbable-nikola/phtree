/*
 * Copyright 2011-2015 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.pht;

import java.util.ArrayList;
import java.util.List;

import ch.ethz.globis.pht.PhTree.PhIterator;
import ch.ethz.globis.pht.pre.EmptyPP;
import ch.ethz.globis.pht.pre.PreProcessorPoint;
import ch.ethz.globis.pht.util.PhIteratorBase;
import ch.ethz.globis.pht.util.PhMapper;
import ch.ethz.globis.pht.util.PhMapperK;

/**
 * k-dimensional index (quad-/oct-/n-tree).
 * Supports key/value pairs.
 *
 *
 * @author ztilmann (Tilmann Zaeschke)
 *
 */
public class PhTreeF<T> {

	private final PhTree<T> pht;
	private final PreProcessorPoint pre;
    
	/**
	 * Create a new tree with the specified number of dimensions.
	 * 
	 * @param dim number of dimensions
	 */
    public static <T> PhTreeF<T> create(int dim) {
    	return new PhTreeF<T>(dim);
    }

	private PhTreeF(int dim) {
		pht = PhTree.create(dim, Double.SIZE);
		pre = new EmptyPP();
	}
	
	public PhTreeF(PhTree<T> tree) {
		pht = tree;
		pre = new EmptyPP();
	}
	
    public int size() {
    	return pht.size();
    }
    
    /**
     * Insert an entry associated with a k dimensional key.
     * @param key
     * @param value
     * @return the previously associated value or {@code null} if the key was found
     */
    public T put(double[] key, T value) {
		long[] lKey = new long[key.length];
		pre.pre(key, lKey);
    	return pht.put(lKey, value);
    };

    public boolean contains(double ... key) {
		long[] lKey = new long[key.length];
		pre.pre(key, lKey);
		return pht.contains(lKey);
    }

    public T get(double ... key) {
		long[] lKey = new long[key.length];
		pre.pre(key, lKey);
		return pht.get(lKey);
    }

    
    /**
     * Remove the entry associated with a k dimensional key.
     * @param key
     * @return the associated value or {@code null} if the key was found
     */
    public T remove(double... key) {
		long[] lKey = new long[key.length];
		pre.pre(key, lKey);
		return pht.remove(lKey);
    }
    
	public PhIteratorF<T> queryExtent() {
		return new PhIteratorF<T>(pht.queryExtent(), pht.getDIM(), pre);
	}


	/**
	 * Performs a range query. The parameters are the min and max keys.
	 * @param min
	 * @param max
	 * @return Result iterator.
	 */
	public PhIteratorF<T> query(double[] min, double[] max) {
		long[] lMin = new long[min.length];
		long[] lMax = new long[max.length];
		pre.pre(min, lMin);
		pre.pre(max, lMax);
		return new PhIteratorF<>(pht.query(lMin, lMax), pht.getDIM(), pre);
	}

//	/**
//	 * Performs an inverse range query. The parameters are the min and max keys.
//	 * @param min
//	 * @param max
//	 * @return false if the range is empty.
//	 */
//	public boolean isRangeEmpty(double[] min, double[] max) {
//		long[] lKey = new long[key.length];
//		pre.pre(key, lKey);
//		
//	}

	public int getDim() {
		return pht.getDIM();
	}

	/**
	 * Locate nearest neighbours for a given point in space.
	 * @param nMin number of entries to be returned. More entries may be returned with several have
	 * 				the same distance.
	 * @param key
	 * @return List of neighbours.
	 */
	public List<double[]> nearestNeighbour(int nMin, double... key) {
		long[] lKey = new long[key.length];
		pre.pre(key, lKey);
		ArrayList<double[]> ret = new ArrayList<>();
		for (long[] l: pht.nearestNeighbour(nMin, lKey)) {
			double[] dKey = new double[l.length];
			pre.post(l, dKey);
			ret.add(dKey);
		}
		return ret;
	}
	
	public static class PhIteratorF<T> implements PhIteratorBase<double[], T, PhEntryF<T>> {
		private final PhIterator<T> iter;
		private final PreProcessorPoint pre;
		private final int DIM;
		
		private PhIteratorF(PhIterator<T> iter, int DIM, PreProcessorPoint pre) {
			this.iter = iter;
			this.pre = pre;
			this.DIM = DIM;
		}
		
		@Override
		public boolean hasNext() {
			return iter.hasNext();
		}
		
		@Override
		public T next() {
			return nextValue();
		}

		public PhEntryF<T> nextEntry() {
			double[] d = new double[DIM];
			PhEntry<T> e = iter.nextEntry();
			pre.post(e.getKey(), d);
			return new PhEntryF<T>(d, e.getValue());
		}
		
		public double[] nextKey() {
			double[] d = new double[DIM];
			pre.post(iter.nextKey(), d);
			return d;
		}
		
		public T nextValue() {
			return iter.nextValue();
		}
		
		@Override
		public void remove() {
			iter.remove();
		}
	}

	/**
	 * Entry class for Double entries.
	 * @author ztilmann
	 *
	 * @param <T>
	 */
	public static class PhEntryF<T> {
		private final double[] key;
		private final T value;
		public PhEntryF(double[] key, T value) {
			this.key = key;
			this.value = value;
		}
		
		public double[] getKey() {
			return key;
		}
		
		public T getValue() {
			return value;
		}
	}
	
	/**
	 * Update the key of an entry. Update may fail if the old key does not exist, or if the new
	 * key already exists.
	 * @param oldKey
	 * @param newKey
	 * @return the value (can be {@code null}) associated with the updated key if the key could be 
	 * updated, otherwise {@code null}.
	 */
	public T update(double[] oldKey, double[] newKey) {
		long[] oldL = new long[oldKey.length];
		long[] newL = new long[newKey.length];
		pre.pre(oldKey, oldL);
		pre.pre(newKey, newL);
		return pht.update(oldL, newL);
	}

	/**
	 * Same as {@link #queryIntersect(double[], double[])}, except that it returns a list
	 * instead of an iterator. This may be faster for small result sets. 
	 * @param lower
	 * @param upper
	 * @return List of query results
	 */
	public List<PhEntryF<T>> queryAll(double[] min, double[] max) {
		return queryAll(min, max, Integer.MAX_VALUE, PhPredicate.ACCEPT_ALL,
				((e) -> (new PhEntryF<T>(PhMapperK.toDouble(e.getKey()), e.getValue()))));
	}
	
	public <R> List<R> queryAll(double[] min, double[] max, int maxResults, 
			PhPredicate filter, PhMapper<T, R> mapper) {
		long[] lUpp = new long[min.length];
		long[] lLow = new long[max.length];
		pre.pre(min, lLow);
		pre.pre(max, lUpp);
		return pht.queryAll(lLow, lUpp, maxResults, filter, mapper);
	}

    /**
     * Clear the tree.
     */
	void clear() {
		pht.clear();
	}
	
}
