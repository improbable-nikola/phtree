/*
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v14;

import java.util.List;

import ch.ethz.globis.phtree.util.PhTreeStats;
import ch.ethz.globis.phtree.v14.bst.BSTIteratorMinMax;
import ch.ethz.globis.phtree.v14.bst.BSTree;
import ch.ethz.globis.phtree.v14.bst.BSTree.REMOVE_OP;
import ch.ethz.globis.phtree.v14.bst.LLEntry;

public class BSTHandler {

	public static class BSTEntry {
		private long[] kdKey;
		private Object value;
		public BSTEntry(long[] k, Object v) {
			kdKey = k;
			value = v;
		}
		public long[] getKdKey() {
			return kdKey;
		}
		public Object getValue() {
			return value;
		}
		public void setValue(Object v) {
			this.value = v;
		}
		public void setKdKey(long[] key) {
			this.kdKey = key;
			
		}
		
	}
	
	static Object addEntry(BSTree<BSTEntry> ind, long hcPos, long[] kdKey, Object value, Node phNode) {
		//TODO for replace, can we reuse the existing key???
		BSTEntry be = ind.put(hcPos, new BSTEntry(kdKey, value), (BSTEntry oldEntry, BSTEntry newEntry) -> {
			//This returns the value to be returned for B) and C)
			
			//A) 
			if (oldEntry == null) {
				//In this case, BSTree need to assign the new Entry
				if (phNode != null) {
					phNode.incEntryCount();
				}
				return null;
			}
			
			//B)
			if (phNode == null) {
				oldEntry.setValue(value);
				oldEntry.setKdKey(kdKey);
				return null;
			}
			
			//C)
			//We have two entries in the same location (local hcPos).
			//Now we need to compare the kdKeys.
			//If they are identical, we either replace the VALUE or return the SUB-NODE
			// (that's actually the same, simply return the VALUE)
			//If the kdKey differs, we have to split, insert a newSubNode and return null.

			//C)
			Object localVal = oldEntry.getValue();
			if (localVal instanceof Node) {
				Node subNode = (Node) localVal;
				long mask = phNode.calcInfixMask(subNode.getPostLen());
				return insertSplitPH(oldEntry, newEntry, mask, phNode);
			} else {
				if (phNode.getPostLen() > 0) {
					long mask = phNode.calcPostfixMask();
					return insertSplitPH(oldEntry, newEntry, mask, phNode);
				}
				//perfect match -> replace value
				//TODO is this swapping good/sensible???
				oldEntry.setValue(value);
				oldEntry.setKdKey(kdKey);
				newEntry.setValue(localVal);
				return newEntry;
			}
		});
		return be == null ? null : be.getValue();
		
		//Contract:
		//phNode==null -> replace instead of split! 
		//Otherwise:
		// - A) entry does not exist -> add / incCounter(phNode)
		// - B) entry exists, phNode == null -> replace with subnode
		// - C) entry exists: -> check infix/postfix
		//   - C.1) value==Node: conflict ? split;insert-new-node;return-null : return Node
		//   - C.2) value==obj:  conflict ? split;insert-new-node;return-null : return prevValue
		//
		//Summary:
		// - A) set T, return null; 
		// - B) set T, (return prev T)
		// - C.1) set newNode return null || set (), return (prev)T
		// - C.2) set newNode return null || set T, return prev T
		// A) is handled by BST.put(), B)/C) is handled by lambda
		
	}

	private static BSTEntry insertSplitPH(BSTEntry currentEntry, BSTEntry newEntry, long mask, Node phNode) {
		//TODO do we reallly need these masks?
		if (mask == 0) {
			//There won't be any split, no need to check.
			return currentEntry;
		}
		long[] localKdKey = currentEntry.getKdKey();
		long[] newKey = newEntry.getKdKey();
		Object currentValue = currentEntry.getValue();
		Object newValue = newEntry.getValue();
		int maxConflictingBits = Node.calcConflictingBits(newKey, localKdKey, mask);
		if (maxConflictingBits == 0) {
			if (!(currentValue instanceof Node)) {
				//replace value
				currentEntry.setValue(newValue);
				newEntry.setValue(currentValue);
				//newEntry now contains the previous value
				return newEntry;
			}
			return currentEntry;
		}
		
		Node newNode = phNode.createNode(newKey, newValue, 
						localKdKey, currentValue, maxConflictingBits);

		//replace value
		//TODO When using BSTree for new subnodes, we need to copy the kdKey here! 
		currentEntry.setValue(newNode);
		//entry did not exist
        return null;
	}
	
	static Object replaceEntry(BSTree<BSTEntry> ind, long hcPos, long[] kdKey, Object value) {
		//TODO for replace, can we reuse the existing BSTEntry???
		//
//		BSTEntry be = (BSTEntry) ind.get(hcPos).getValue();
//		Object prev = be.getValue();
//		System.arraycopy(kdKey, 0, be.getKdKey(), 0, kdKey.length);
//		be.setValue(value);
//		return prev;
		
		
		BSTEntry be = ind.put(hcPos, new BSTEntry(kdKey, value), null);
		return be == null ? null : be.getValue();
	}

	static Object removeEntry(BSTree<BSTEntry> ind, long hcPos, long[] key, 
			long[] newKey, int[] insertRequired, Node phNode) {
		//Only remove value-entries, node-entries are simply returned without removing them
		BSTEntry prev = ind.remove(hcPos, e -> {
			if (matches(e, key, phNode)) {
				if (e.getValue() instanceof Node) {
					return REMOVE_OP.KEEP_RETURN;
				}
				if (newKey != null) {
					//replace
					int bitPosOfDiff = Node.calcConflictingBits(key, newKey, -1L);
					if (bitPosOfDiff <= phNode.getPostLen()) {
						//replace
						//simply replace kdKey!!
						//Replacing the long[] should be correct (and fastest, and avoiding GC)
						e.setKdKey(newKey);
						return REMOVE_OP.KEEP_RETURN;
					} else {
						insertRequired[0] = bitPosOfDiff;
					}
				}
				return REMOVE_OP.REMOVE_RETURN;
			}
			return REMOVE_OP.KEEP_RETURN_NULL;
		});
		//return values: 
		// - null -> not found / remove failed
		// - Node -> recurse node
		// - T -> remove success
		//Node: removing a node is never necessary: When values are removed from the PH-Tree, nodes are replaced
		// with vales from sub-nodes, but they are never simply removed.
		//-> The BST.remove() needs to do:
		//  - Key not found: no delete, return null
		//  - No match: no delete, return null
		//  - Match Node: no delete, return Node
		//  - Match Value: delete, return value
		return prev == null ? null : prev.getValue();
	}

	static Object getEntry(BSTree<BSTEntry> ind, long hcPos, long[] outKey, long[] keyToMatch, Node node) {
		LLEntry e = ind.get(hcPos);
		if (e == null) {
			return null;
		}
		BSTEntry be = (BSTEntry) e.getValue();
		if (keyToMatch != null) {
			if (!matches(be, keyToMatch, node)) {
				return null;
			}
		}
		if (outKey != null) {  //TODO de we need outkey?
			System.arraycopy(be.getKdKey(), 0, outKey, 0, outKey.length);
		}
		return be.getValue(); 
	}

	private static boolean matches(BSTEntry be, long[] keyToMatch, Node node) {
		//This is always 0, unless we decide to put several keys into a single array
		final int offs = 0;
		if (be.getValue() instanceof Node) {
			Node sub = (Node) be.getValue();
			//TODO we are currently nmot setting this, so we can't read it...
//TODO				if (node.hasSubInfix(offs, dims)) {
				final long mask = node.calcInfixMask(sub.getPostLen());
				if (!readAndCheckKdKey(be.getKdKey(), offs, keyToMatch, mask, node.postLenStored())) {
					return false;
				}
//			}
		} else {
			final long mask = node.calcPostfixMask();
			if (!readAndCheckKdKey(be.getKdKey(), offs, keyToMatch, mask, node.postLenStored())) {
				return false;
			}
		}
		return true;
	}
	
	private static boolean readAndCheckKdKey(long[] allKeys, int offs, long[] keyToMatch, long mask, int postLen) {
		//TODO do we reallly need these masks?
		for (int i = 0; i < keyToMatch.length; i++) {
//			long k = Bits.readArray(allKeys, offs, postLen);
//			if (((k ^ keyToMatch[i]) & mask) != 0) {
//				return false;
//			}
//			offs += postLen;
			if (((allKeys[i] ^ keyToMatch[i]) & mask) != 0) {
				return false;
			}
		}
		return true;
	}


	static void getStats(BSTree<BSTEntry> ind, PhTreeStats stats, int dims, List<Object> entries) {
		BSTIteratorMinMax<BSTEntry> iter = ind.iterator();
		while (iter.hasNextULL()) {
			entries.add(iter.nextEntryReuse().getValue());
		}
	}
	
}