package kmer;

import dna.CoverageArray;
import fileIO.ByteStreamWriter;
import fileIO.TextStreamWriter;

/**
 * @author Brian Bushnell
 * @date Nov 22, 2013
 *
 */
public class HashBuffer extends AbstractKmerTable {
	
	/*--------------------------------------------------------------*/
	/*----------------        Initialization        ----------------*/
	/*--------------------------------------------------------------*/
	
	public HashBuffer(AbstractKmerTable[] tables_, int buflen_, int k_, boolean initValues){
		tables=tables_;
		buflen=buflen_;
		halflen=(int)Math.ceil(buflen*0.5);
		ways=tables.length;
		buffers=new KmerBuffer[ways];
		for(int i=0; i<ways; i++){
			buffers[i]=new KmerBuffer(buflen, k_, initValues);
		}
	}
	
	/*--------------------------------------------------------------*/
	/*----------------        Public Methods        ----------------*/
	/*--------------------------------------------------------------*/
	
	@Override
	public int incrementAndReturnNumCreated(long kmer) {
		final int way=(int)(kmer%ways);
		KmerBuffer buffer=buffers[way];
		final int size=buffer.add(kmer);
		if(size>=halflen && (size>=buflen || (size&15)==0)){
			return dumpBuffer(way, size>=buflen);
		}
		return 0;
	}
	
	@Override
	public final long flush(){
		long added=0;
		for(int i=0; i<ways; i++){added+=dumpBuffer(i, true);}
		return added;
	}
	
	@Override
	public int set(long kmer, int value) {
		throw new RuntimeException("Unimplemented method; this class lacks value buffers");
	}
	
	@Override
	public int set(long kmer, int[] vals) {
		throw new RuntimeException("Unimplemented method; this class lacks value buffers");
	}
	
	@Override
	public int setIfNotPresent(long kmer, int value) {
		throw new RuntimeException("Unimplemented method; this class lacks value buffers");
	}
	
	@Override
	public int getValue(long kmer) {
		final int way=(int)(kmer%ways);
		return tables[way].getValue(kmer);
	}
	
	@Override
	public int[] getValues(long kmer, int[] singleton){
		final int way=(int)(kmer%ways);
		return tables[way].getValues(kmer, singleton);
	}
	
	@Override
	public boolean contains(long kmer) {
		final int way=(int)(kmer%ways);
		return tables[way].contains(kmer);
	}
	

	
	/*--------------------------------------------------------------*/
	/*----------------          Ownership           ----------------*/
	/*--------------------------------------------------------------*/
	
	@Override
	public final void initializeOwnership(){
		for(AbstractKmerTable t : tables){t.initializeOwnership();}
	}
	
	@Override
	public final void clearOwnership(){
		for(AbstractKmerTable t : tables){t.clearOwnership();}
	}
	
	@Override
	public final int setOwner(final long kmer, final int newOwner){
		final int way=(int)(kmer%ways);
		return tables[way].setOwner(kmer, newOwner);
	}
	
	@Override
	public final boolean clearOwner(final long kmer, final int owner){
		final int way=(int)(kmer%ways);
		return tables[way].clearOwner(kmer, owner);
	}
	
	@Override
	public final int getOwner(final long kmer){
		final int way=(int)(kmer%ways);
		return tables[way].getOwner(kmer);
	}
	
	/*--------------------------------------------------------------*/
	/*----------------      Nonpublic Methods       ----------------*/
	/*--------------------------------------------------------------*/
	
	@Override
	Object get(long kmer) {
		final int way=(int)(kmer%ways);
		return tables[way].get(kmer);
	}
	
	/*--------------------------------------------------------------*/
	/*----------------       Private Methods        ----------------*/
	/*--------------------------------------------------------------*/
	
	private int dumpBuffer(final int way, boolean force){
		final KmerBuffer buffer=buffers[way];
		final AbstractKmerTable table=tables[way];
		final int lim=buffer.size();
		if(lim<0){return 0;}
		if(force){table.lock();}
		else if(!table.tryLock()){return 0;}
		final int x=dumpBuffer_inner(way);
		table.unlock();
		return x;
	}
	
	private int dumpBuffer_inner(final int way){
		if(verbose){System.err.println("Dumping buffer for way "+way+" of "+ways);}
		final KmerBuffer buffer=buffers[way];
		final int lim=buffer.size();
		if(lim<1){return 0;}
		final long[] kmers=buffer.kmers.array;
		final int[] values=(buffer.values==null ? null : buffer.values.array);
		if(lim<1){return 0;}
		int added=0;
		final AbstractKmerTable table=tables[way];
//		synchronized(table){
			if(values==null){
				for(int i=0; i<lim; i++){
					final long kmer=kmers[i];
					added+=table.incrementAndReturnNumCreated(kmer);
				}
			}else{
				for(int i=0; i<lim; i++){
					final long kmer=kmers[i];
					final int value=values[i];
					added+=table.setIfNotPresent(kmer, value);
				}
			}
//		}
		buffer.clear();
		return added;
	}
	
	/*--------------------------------------------------------------*/
	/*----------------   Resizing and Rebalancing   ----------------*/
	/*--------------------------------------------------------------*/
	
	@Override
	final boolean canResize() {return false;}
	
	@Override
	public final boolean canRebalance() {return false;}
	
	@Deprecated
	@Override
	public long size() {
		throw new RuntimeException("Unimplemented.");
	}
	
	@Deprecated
	@Override
	public int arrayLength() {
		throw new RuntimeException("Unimplemented.");
	}
	
	@Deprecated
	@Override
	void resize() {
		throw new RuntimeException("Unimplemented.");
	}
	
	@Deprecated
	@Override
	public void rebalance() {
		throw new RuntimeException("Unimplemented.");
	}
	
	public long regenerate(){
		long sum=0;
		for(AbstractKmerTable table : tables){
			sum+=table.regenerate();
		}
		return sum;
	}
	
	/*--------------------------------------------------------------*/
	/*----------------         Info Dumping         ----------------*/
	/*--------------------------------------------------------------*/
	
	@Override
	public boolean dumpKmersAsText(TextStreamWriter tsw, int k, int mincount){
		for(AbstractKmerTable table : tables){
			dumpKmersAsText(tsw, k, mincount);
		}
		return true;
	}
	
	@Override
	public boolean dumpKmersAsBytes(ByteStreamWriter bsw, int k, int mincount){
		for(AbstractKmerTable table : tables){
			dumpKmersAsBytes(bsw, k, mincount);
		}
		return true;
	}
	
	@Override
	public void fillHistogram(long[] ca, int max){
		for(AbstractKmerTable table : tables){
			fillHistogram(ca, max);
		}
	}
	
	/*--------------------------------------------------------------*/
	/*----------------       Invalid Methods        ----------------*/
	/*--------------------------------------------------------------*/
	
	@Override
	public int increment(long kmer) {
		throw new RuntimeException("Unsupported");
	}
	
	/*--------------------------------------------------------------*/
	/*----------------            Fields            ----------------*/
	/*--------------------------------------------------------------*/
	
	private final AbstractKmerTable[] tables;
	private final int buflen;
	private final int halflen;
	private final int ways;
	private final KmerBuffer[] buffers;

}
