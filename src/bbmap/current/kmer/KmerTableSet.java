package kmer;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import jgi.BBMerge;
import stream.ByteBuilder;
import stream.ConcurrentReadInputStream;
import stream.FastaReadInputStream;
import stream.Read;
import align2.IntList;
import align2.ListNum;
import align2.LongList;
import align2.ReadStats;
import align2.Shared;
import align2.Tools;
import align2.TrimRead;
import bloom.KmerCountAbstract;
import dna.AminoAcid;
import dna.CoverageArray3;
import dna.Parser;
import dna.Timer;
import fileIO.ByteStreamWriter;
import fileIO.FileFormat;
import fileIO.ReadWrite;


/**
 * Loads and holds kmers for Tadpole
 * @author Brian Bushnell
 * @date Jun 22, 2015
 *
 */
public class KmerTableSet extends AbstractKmerTableSet {
	
	/**
	 * Code entrance from the command line.
	 * @param args Command line arguments
	 */
	public static void main(String[] args){
		
		args=Parser.parseConfig(args);
		if(Parser.parseHelp(args)){
			printOptions();
			System.exit(0);
		}
		
		Timer t=new Timer(), t2=new Timer();
		t.start();
		t2.start();
		
		//Create a new CountKmersExact instance
		KmerTableSet set=new KmerTableSet(args);
		t2.stop();
		outstream.println("Initialization Time:      \t"+t2);
		
		///And run it
		set.process(t);
	}
	
	
	/**
	 * Constructor.
	 * @param args Command line arguments
	 */
	private KmerTableSet(String[] args){
		this(args, 12);//+5 if using ownership and building contigs
	}
	
	
	/**
	 * Constructor.
	 * @param args Command line arguments
	 */
	public KmerTableSet(String[] args, final int bytesPerKmer_){
		System.err.println("Executing "+getClass().getName()+" "+Arrays.toString(args)+"\n");
		
		/* Initialize local variables with defaults */
		Parser parser=new Parser();
		boolean prealloc_=false;
		int k_=31;
		int ways_=-1;
		int filterMax_=2;
		boolean ecco_=false, merge_=false;
		boolean rcomp_=true;
		double minProb_=defaultMinprob;
		
		/* Parse arguments */
		for(int i=0; i<args.length; i++){

			final String arg=args[i];
			String[] split=arg.split("=");
			String a=split[0].toLowerCase();
			String b=split.length>1 ? split[1] : null;
			if("null".equalsIgnoreCase(b)){b=null;}
			while(a.charAt(0)=='-' && (a.indexOf('.')<0 || i>1 || !new File(a).exists())){a=a.substring(1);}
			
			if(Parser.isJavaFlag(arg)){
				//jvm argument; do nothing
			}else if(Parser.parseCommonStatic(arg, a, b)){
				//do nothing
			}else if(Parser.parseZip(arg, a, b)){
				//do nothing
			}else if(Parser.parseQuality(arg, a, b)){
				//do nothing
			}else if(Parser.parseFasta(arg, a, b)){
				//do nothing
			}else if(parser.parseInterleaved(arg, a, b)){
				//do nothing
			}else if(parser.parseTrim(arg, a, b)){
				//do nothing
			}else if(a.equals("in") || a.equals("in1")){
				in1.clear();
				if(b!=null){
					String[] s=b.split(",");
					for(String ss : s){
						in1.add(ss);
					}
				}
			}else if(a.equals("in2")){
				in2.clear();
				if(b!=null){
					String[] s=b.split(",");
					for(String ss : s){
						in2.add(ss);
					}
				}
			}else if(a.equals("append") || a.equals("app")){
				append=ReadStats.append=Tools.parseBoolean(b);
			}else if(a.equals("overwrite") || a.equals("ow")){
				overwrite=Tools.parseBoolean(b);
			}else if(a.equals("initialsize")){
				initialSize=(int)Tools.parseKMG(b);
			}else if(a.equals("showstats") || a.equals("stats")){
				showStats=Tools.parseBoolean(b);
			}else if(a.equals("ways")){
				ways_=(int)Tools.parseKMG(b);
			}else if(a.equals("buflen") || a.equals("bufflen") || a.equals("bufferlength")){
				buflen=(int)Tools.parseKMG(b);
			}else if(a.equals("k")){
				assert(b!=null) : "\nk needs an integer value from 1 to 31, such as k=27.  Default is 31.\n";
				k_=(int)Tools.parseKMG(b);
			}else if(a.equals("threads") || a.equals("t")){
				THREADS=(b==null || b.equalsIgnoreCase("auto") ? Shared.threads() : Integer.parseInt(b));
			}else if(a.equals("showspeed") || a.equals("ss")){
				showSpeed=Tools.parseBoolean(b);
			}else if(a.equals("ecco")){
				ecco_=Tools.parseBoolean(b);
			}else if(a.equals("merge")){
				merge_=Tools.parseBoolean(b);
			}else if(a.equals("verbose")){
//				assert(false) : "Verbose flag is currently static final; must be recompiled to change.";
				verbose=Tools.parseBoolean(b);
			}else if(a.equals("verbose2")){
//				assert(false) : "Verbose flag is currently static final; must be recompiled to change.";
				verbose2=Tools.parseBoolean(b);
			}else if(a.equals("minprob")){
				minProb_=Double.parseDouble(b);
			}else if(a.equals("minprobprefilter") || a.equals("mpp")){
				minProbPrefilter=Tools.parseBoolean(b);
			}else if(a.equals("minprobmain") || a.equals("mpm")){
				minProbMain=Tools.parseBoolean(b);
			}else if(a.equals("reads") || a.startsWith("maxreads")){
				maxReads=Tools.parseKMG(b);
			}else if(a.equals("prealloc") || a.equals("preallocate")){
				if(b==null || b.length()<1 || Character.isAlphabetic(b.charAt(0))){
					prealloc_=Tools.parseBoolean(b);
				}else{
					preallocFraction=Tools.max(0, Double.parseDouble(b));
					prealloc_=(preallocFraction>0);
				}
			}else if(a.equals("prefilter")){
				if(b==null || b.length()<1 || !Character.isDigit(b.charAt(0))){
					prefilter=Tools.parseBoolean(b);
				}else{
					filterMax_=(int)Tools.parseKMG(b);
					prefilter=filterMax_>0;
				}
			}else if(a.equals("prefiltersize") || a.equals("prefilterfraction") || a.equals("pff")){
				prefilterFraction=Tools.max(0, Double.parseDouble(b));
				assert(prefilterFraction<=1) : "prefiltersize must be 0-1, a fraction of total memory.";
				prefilter=prefilterFraction>0;
			}else if(a.equals("prehashes") || a.equals("hashes")){
				prehashes=(int)Tools.parseKMG(b);
			}else if(a.equals("prefilterpasses") || a.equals("prepasses")){
				if(b.equalsIgnoreCase("auto")){
					prepasses=-1;
				}else{
					prepasses=(int)Tools.parseKMG(b);
				}
			}else if(a.equals("onepass")){
				onePass=Tools.parseBoolean(b);
			}else if(a.equals("passes")){
				int passes=(int)Tools.parseKMG(b);
				onePass=(passes<2);
			}else if(a.equals("rcomp")){
				rcomp_=Tools.parseBoolean(b);
			}else if(IGNORE_UNKNOWN_ARGS){
				//Do nothing
			}else{
				throw new RuntimeException("Unknown parameter "+args[i]);
			}
		}
		
		{//Process parser fields
			Parser.processQuality();
			
			qtrimLeft=parser.qtrimLeft;
			qtrimRight=parser.qtrimRight;
			trimq=parser.trimq;
			
			minAvgQuality=parser.minAvgQuality;
			minAvgQualityBases=parser.minAvgQualityBases;
		}
		
		if(prepasses==0 || !prefilter){
			prepasses=0;
			prefilter=false;
		}
		
		{
			long memory=Runtime.getRuntime().maxMemory();
			double xmsRatio=Shared.xmsRatio();
//			long tmemory=Runtime.getRuntime().totalMemory();
			usableMemory=(long)Tools.max(((memory-96000000)*(xmsRatio>0.97 ? 0.82 : 0.75)), memory*0.45);
			if(prepasses==0 || !prefilter){
				filterMemory0=filterMemory1=0;
			}else{
				double low=Tools.min(prefilterFraction, 1-prefilterFraction);
				double high=1-low;
				if(prepasses<0 || (prepasses&1)==1){//odd passes
					filterMemory0=(long)(usableMemory*low);
					filterMemory1=(long)(usableMemory*high);
				}else{//even passes
					filterMemory0=(long)(usableMemory*high);
					filterMemory1=(long)(usableMemory*low);
				}
			}
			tableMemory=(long)(usableMemory*.95-filterMemory0);
		}

		prealloc=prealloc_;
		bytesPerKmer=bytesPerKmer_;
		if(ways_<1){
			long maxKmers=(2*tableMemory)/bytesPerKmer;
			long minWays=Tools.min(10000, maxKmers/Integer.MAX_VALUE);
			ways_=(int)Tools.max(31, (int)(THREADS*2.5), minWays);
			ways_=(int)Primes.primeAtLeast(ways_);
			assert(ways_>0);
//			System.err.println("ways="+ways_);
		}
		
		/* Set final variables; post-process and validate argument combinations */
		
		onePass=onePass&prefilter;
		ways=ways_;
		filterMax=Tools.min(filterMax_, 0x7FFFFFFF);
		ecco=ecco_;
		merge=merge_;
		minProb=(float)minProb_;
		rcomp=rcomp_;
		estimatedKmerCapacity=(long)((tableMemory*1.0/bytesPerKmer)*((prealloc && preallocFraction==1) ? 0.9 : 0.6));
//		System.err.println("tableMemory="+tableMemory+", bytesPerKmer="+bytesPerKmer+", estimatedKmerCapacity="+estimatedKmerCapacity);
		KmerCountAbstract.minProb=(minProbPrefilter ? minProb : 0);
		k=k_;
		k2=k-1;
		
		if(k<1 || k>31){throw new RuntimeException("\nk needs an integer value from 1 to 31, such as k=27.  Default is 31.\n");}
		
		if(initialSize<1){
			final long memOverWays=tableMemory/(bytesPerKmer*ways);
			final double mem2=(prealloc ? preallocFraction : 1)*tableMemory;
			initialSize=(prealloc || memOverWays<initialSizeDefault ? (int)Tools.min(2142000000, (long)(mem2/(bytesPerKmer*ways))) : initialSizeDefault);
			if(initialSize!=initialSizeDefault){
				System.err.println("Initial size set to "+initialSize);
			}
		}
		
		/* Adjust I/O settings and filenames */
		
		assert(FastaReadInputStream.settingsOK());
		
		if(in1.isEmpty()){
			printOptions();
			throw new RuntimeException("Error - at least one input file is required.");
		}
		
		for(int i=0; i<in1.size(); i++){
			String s=in1.get(i);
			if(s!=null && s.contains("#") && !new File(s).exists()){
				int pound=s.lastIndexOf('#');
				String a=s.substring(0, pound);
				String b=s.substring(pound+1);
				in1.set(i, a+1+b);
				in2.add(a+2+b);
			}
		}
		
		{
			boolean allowDuplicates=true;
			if(!Tools.testInputFiles(allowDuplicates, true, in1, in2)){
				throw new RuntimeException("\nCan't read to some input files.\n");
			}
		}
		assert(THREADS>0);
		
		if(DISPLAY_PROGRESS){
			outstream.println("Initial:");
			outstream.println("Ways="+ways+", initialSize="+initialSize+", prefilter="+(prefilter ? "t" : "f")+", prealloc="+(prealloc ? (""+preallocFraction) : "f"));
			Shared.printMemory();
			outstream.println();
		}
		
	}
	
	/*--------------------------------------------------------------*/
	/*----------------         Inner Methods        ----------------*/
	/*--------------------------------------------------------------*/
	
	@Override
	protected void allocateTables(){
		assert(tables==null);
		tables=null;
		final int tableType=AbstractKmerTable.ARRAY1D;
		tables=AbstractKmerTable.preallocate(ways, tableType, initialSize, (!prealloc || preallocFraction<1));
	}
	
	/**
	 * Load reads into tables, using multiple LoadThread.
	 */
	public long loadKmers(String fname1, String fname2){
		
		/* Create read input stream */
		final ConcurrentReadInputStream cris;
		{
			FileFormat ff1=FileFormat.testInput(fname1, FileFormat.FASTQ, null, true, true);
			FileFormat ff2=FileFormat.testInput(fname2, FileFormat.FASTQ, null, true, true);
			cris=ConcurrentReadInputStream.getReadInputStream(maxReads, false, ff1, ff2);
			cris.start(); //4567
		}
		
//		/* Optionally skip the first reads, since initial reads may have lower quality */
//		if(skipreads>0){
//			long skipped=0;
//
//			ListNum<Read> ln=cris.nextList();
//			ArrayList<Read> reads=(ln!=null ? ln.list : null);
//			
//			while(skipped<skipreads && reads!=null && reads.size()>0){
//				skipped+=reads.size();
//				
//				cris.returnList(ln.id, ln.list.isEmpty());
//				ln=cris.nextList();
//				reads=(ln!=null ? ln.list : null);
//			}
//			cris.returnList(ln.id, ln.list.isEmpty());
//			if(reads==null || reads.isEmpty()){
//				ReadWrite.closeStreams(cris);
//				System.err.println("Skipped all of the reads.");
//				System.exit(0);
//			}
//		}
		
		/* Create ProcessThreads */
		ArrayList<LoadThread> alpt=new ArrayList<LoadThread>(THREADS);
		for(int i=0; i<THREADS; i++){alpt.add(new LoadThread(cris));}
		for(LoadThread pt : alpt){pt.start();}
		
		long added=0;
		
		/* Wait for threads to die, and gather statistics */
		for(LoadThread pt : alpt){
			while(pt.getState()!=Thread.State.TERMINATED){
				try {
					pt.join();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			added+=pt.added;
			
			readsIn+=pt.readsInT;
			basesIn+=pt.basesInT;
			lowqReads+=pt.lowqReadsT;
			lowqBases+=pt.lowqBasesT;
			readsTrimmed+=pt.readsTrimmedT;
			basesTrimmed+=pt.basesTrimmedT;
		}
		
		/* Shut down I/O streams; capture error status */
		errorState|=ReadWrite.closeStreams(cris);
		return added;
	}
	
	/*--------------------------------------------------------------*/
	/*----------------         Inner Classes        ----------------*/
	/*--------------------------------------------------------------*/

	/**
	 * Loads kmers. 
	 */
	private class LoadThread extends Thread{
		
		/**
		 * Constructor
		 * @param cris_ Read input stream
		 */
		public LoadThread(ConcurrentReadInputStream cris_){
			cris=cris_;
			table=new HashBuffer(tables, buflen, k, false);
		}
		
		@Override
		public void run(){
			ListNum<Read> ln=cris.nextList();
			ArrayList<Read> reads=(ln!=null ? ln.list : null);
			
			//While there are more reads lists...
			while(reads!=null && reads.size()>0){
				
				//For each read (or pair) in the list...
				for(int i=0; i<reads.size(); i++){
					Read r1=reads.get(i);
					Read r2=r1.mate;
					
					if(verbose){System.err.println("Considering read "+r1.id+" "+new String(r1.bases));}
					
					readsInT++;
					basesInT+=r1.length();
					if(r2!=null){
						readsInT++;
						basesInT+=r2.length();
					}
					
					//Determine whether to discard the reads based on average quality
					if(minAvgQuality>0){
						if(r1!=null && r1.quality!=null && r1.avgQuality(false, minAvgQualityBases)<minAvgQuality){r1.setDiscarded(true);}
						if(r2!=null && r2.quality!=null && r2.avgQuality(false, minAvgQualityBases)<minAvgQuality){r2.setDiscarded(true);}
					}
					
					if(r1!=null){
						if(qtrimLeft || qtrimRight){
							int x=TrimRead.trimFast(r1, qtrimLeft, qtrimRight, trimq, 1);
							basesTrimmedT+=x;
							readsTrimmedT+=(x>0 ? 1 : 0);
						}
						if(r1.length()<k){r1.setDiscarded(true);}
					}
					if(r2!=null){
						if(qtrimLeft || qtrimRight){
							int x=TrimRead.trimFast(r2, qtrimLeft, qtrimRight, trimq, 1);
							basesTrimmedT+=x;
							readsTrimmedT+=(x>0 ? 1 : 0);
						}
						if(r2.length()<k){r2.setDiscarded(true);}
					}
					
					if((ecco || merge) && r1!=null && r2!=null && !r1.discarded() && !r2.discarded()){
						if(merge){
							final int insert=BBMerge.findOverlapStrict(r1, r2, false);
							if(insert>0){
								r2.reverseComplement();
								r1=r1.joinRead(insert);
								r2=null;
							}
						}else if(ecco){
							BBMerge.findOverlapStrict(r1, r2, true);
						}
					}

					if(r1!=null){
						if(r1.discarded()){
							lowqBasesT+=r1.length();
							lowqReadsT++;
						}else{
							long temp=addKmersToTable(r1);
							added+=temp;
							if(verbose){System.err.println("A: Added "+temp);}
						}
					}
					if(r2!=null){
						if(r2.discarded()){
							lowqBasesT+=r2.length();
							lowqReadsT++;
						}else{
							long temp=addKmersToTable(r2);
							added+=temp;
							if(verbose){System.err.println("B: Added "+temp);}
						}
					}
				}
				
				//Fetch a new read list
				cris.returnList(ln.id, ln.list.isEmpty());
				ln=cris.nextList();
				reads=(ln!=null ? ln.list : null);
			}
			cris.returnList(ln.id, ln.list.isEmpty());
			long temp=table.flush();
			if(verbose){System.err.println("Flush: Added "+temp);}
			added+=temp;
		}
		
		
		private final int addKmersToTable(final Read r){
			if(onePass){return addKmersToTable_onePass(r);}
			if(r==null || r.bases==null){return 0;}
			final float minProb2=(minProbMain ? minProb : 0);
			final byte[] bases=r.bases;
			final byte[] quals=r.quality;
			final int shift=2*k;
			final int shift2=shift-2;
			final long mask=~((-1L)<<shift);
			long kmer=0;
			long rkmer=0;
			int created=0;
			int len=0;
			
			if(bases==null || bases.length<k){return -1;}
			
			/* Loop through the bases, maintaining a forward and reverse kmer via bitshifts */
			float prob=1;
			for(int i=0; i<bases.length; i++){
				final byte b=bases[i];
				final long x=AminoAcid.baseToNumber[b];
				final long x2=AminoAcid.baseToComplementNumber[b];

				//Update kmers
				kmer=((kmer<<2)|x)&mask;
				rkmer=(rkmer>>>2)|(x2<<shift2);

				if(minProb2>0 && quals!=null){//Update probability
					prob=prob*PROB_CORRECT[quals[i]];
					if(len>k){
						byte oldq=quals[i-k];
						prob=prob*PROB_CORRECT_INVERSE[oldq];
					}
				}

				//Handle Ns
				if(x<0){
					len=0;
					kmer=rkmer=0;
					prob=1;
				}else{len++;}

				if(verbose){System.err.println("Scanning i="+i+", len="+len+", kmer="+kmer+", rkmer="+rkmer+"\t"+new String(bases, Tools.max(0, i-k2), Tools.min(i+1, k)));}
				if(len>=k && prob>=minProb2){
					final long key=toValue(kmer, rkmer);
					if(!prefilter || prefilterArray.read(key)>filterMax2){
						int temp=table.incrementAndReturnNumCreated(key);
						created+=temp;
						if(verbose){System.err.println("C: Added "+temp);}
					}
				}
			}
			
			return created;
		}
		
		
		private final int addKmersToTable_onePass(final Read r){
			assert(prefilter);
			if(r==null || r.bases==null){return 0;}
			final byte[] bases=r.bases;
			final byte[] quals=r.quality;
			final int shift=2*k;
			final int shift2=shift-2;
			final long mask=~((-1L)<<shift);
			long kmer=0;
			long rkmer=0;
			int created=0;
			int len=0;

			if(bases==null || bases.length<k){return -1;}
			
			/* Loop through the bases, maintaining a forward and reverse kmer via bitshifts */
			float prob=1;
			for(int i=0; i<bases.length; i++){
				final byte b=bases[i];
				final long x=AminoAcid.baseToNumber[b];
				final long x2=AminoAcid.baseToComplementNumber[b];

				//Update kmers
				kmer=((kmer<<2)|x)&mask;
				rkmer=(rkmer>>>2)|(x2<<shift2);

				if(minProb>0 && quals!=null){//Update probability
					prob=prob*PROB_CORRECT[quals[i]];
					if(len>k){
						byte oldq=quals[i-k];
						prob=prob*PROB_CORRECT_INVERSE[oldq];
					}
				}

				//Handle Ns
				if(x<0){
					len=0;
					kmer=rkmer=0;
					prob=1;
				}else{len++;}

				if(verbose){System.err.println("Scanning i="+i+", len="+len+", kmer="+kmer+", rkmer="+rkmer+"\t"+new String(bases, Tools.max(0, i-k2), Tools.min(i+1, k)));}
				if(len>=k){
					final long key=toValue(kmer, rkmer);
					int count=prefilterArray.incrementAndReturnUnincremented(key, 1);
					if(count>=filterMax2){
						int temp=table.incrementAndReturnNumCreated(key);
						created+=temp;
						if(verbose){System.err.println("D: Added "+temp);}
					}
				}
			}
			return created;
		}
		
		/*--------------------------------------------------------------*/
		
		/** Input read stream */
		private final ConcurrentReadInputStream cris;
		
		private final HashBuffer table;
		
		public long added=0;
		
		private long readsInT=0;
		private long basesInT=0;
		private long lowqReadsT=0;
		private long lowqBasesT=0;
		private long readsTrimmedT=0;
		private long basesTrimmedT=0;
		
	}
	
	
	/*--------------------------------------------------------------*/
	/*----------------          Convenience         ----------------*/
	/*--------------------------------------------------------------*/
	
	public void regenerateKmers(byte[] bases, LongList kmers, IntList counts, final int a){
		final int loc=a+k;
		final int lim=Tools.min(counts.size, a+k+1);
		final int shift=2*k;
		final int shift2=shift-2;
		final long mask=~((-1L)<<shift);
		long kmer=kmers.get(a);
		long rkmer=rcomp(kmer);
		int len=k;
		
//		assert(false) : a+", "+loc+", "+lim;
		
		/* Loop through the bases, maintaining a forward and reverse kmer via bitshifts */
		for(int i=loc, j=a+1; j<lim; i++, j++){
			final byte b=bases[i];
			final long x=AminoAcid.baseToNumber[b];
			final long x2=AminoAcid.baseToComplementNumber[b];
			kmer=((kmer<<2)|x)&mask;
			rkmer=(rkmer>>>2)|(x2<<shift2);
			if(x<0){
				len=0;
				kmer=rkmer=0;
			}else{len++;}
			
			if(len>=k){
				assert(kmers.get(j)!=kmer);
				kmers.set(j, kmer);
				int count=getCount(kmer, rkmer);
				counts.set(j, count);
			}else{
				kmers.set(j, -1);
				counts.set(j, 0);
			}
		}
	}
	
	/** Returns number of valid kmers */
	public int fillKmers(byte[] bases, LongList kmers){
		final int blen=bases.length;
		if(blen<k){return 0;}
		final int min=k-1;
		final int shift=2*k;
		final int shift2=shift-2;
		final long mask=~((-1L)<<shift);
		long kmer=0;
		long rkmer=0;
		int len=0;
		int valid=0;

		kmers.clear();

		/* Loop through the bases, maintaining a forward and reverse kmer via bitshifts */
		for(int i=0; i<blen; i++){
			final byte b=bases[i];
			final long x=AminoAcid.baseToNumber[b];
//			final long x2=AminoAcid.baseToComplementNumber[b];
			kmer=((kmer<<2)|x)&mask;
//			rkmer=(rkmer>>>2)|(x2<<shift2);
			if(x<0){
				len=0;
				kmer=rkmer=0;
			}else{len++;}
			if(i>=min){
				if(len>=k){
					kmers.add(kmer);
					valid++;
				}else{
					kmers.add(-1);
				}
			}
		}
		return valid;
	}
	
	public void fillCounts(LongList kmers, IntList counts){
		counts.clear();
		for(int i=0; i<kmers.size; i++){
			long kmer=kmers.get(i);
			if(kmer>=0){
				long rkmer=rcomp(kmer);
				int count=getCount(kmer, rkmer);
				counts.add(count);
			}else{
				counts.add(0);
			}
		}
	}
	
	
	/*--------------------------------------------------------------*/
	/*----------------        Helper Methods        ----------------*/
	/*--------------------------------------------------------------*/
	
	public long regenerate(){
		long sum=0;
		for(AbstractKmerTable akt : tables){
			sum+=akt.regenerate();
		}
		return sum;
	}

	public HashArray1D getTableForKey(long key){
		return (HashArray1D) tables[(int)(key%ways)];
	}
	
	public HashArray1D getTable(int tnum){
		return (HashArray1D) tables[tnum];
	}
	
	public long[] fillHistogram(int histMax) {
		long[] ca=new long[histMax+1];
		for(AbstractKmerTable set : tables){
			set.fillHistogram(ca, histMax);
		}
		return ca;
	}
	
	public void initializeOwnership(){
		for(AbstractKmerTable akt : tables){
			akt.initializeOwnership();
		}
	}
	
	public void clearOwnership(){
		for(AbstractKmerTable akt : tables){
			akt.clearOwnership();
		}
	}
	
	public long rightmostKmer(final ByteBuilder bb){
		return rightmostKmer(bb.array, bb.length());
	}
	
	public long rightmostKmer(final byte[] bases, final int blen){
		if(blen<k){return -1;}
		final int shift=2*k;
		final int shift2=shift-2;
		final long mask=~((-1L)<<shift);
		long kmer=0;
		long rkmer=0;
		int len=0;

		/* Loop through the bases, maintaining a forward and reverse kmer via bitshifts, to get the rightmost kmer */
		{
			for(int i=blen-k; i<blen; i++){
				final byte b=bases[i];
				final long x=AminoAcid.baseToNumber[b];
				final long x2=AminoAcid.baseToComplementNumber[b];
				kmer=((kmer<<2)|x)&mask;
				rkmer=(rkmer>>>2)|(x2<<shift2);
				if(x<0){
					len=0;
					kmer=rkmer=0;
				}else{len++;}
				if(verbose){outstream.println("Scanning i="+i+", len="+len+", kmer="+kmer+", rkmer="+rkmer+"\t"+new String(bases, Tools.max(0, i-k2), Tools.min(i+1, k)));}
			}
		}
		
		if(len<k){return -1;}
		else{assert(len==k);}
		return kmer;
	}
	
	public long leftmostKmer(final ByteBuilder bb){
		return leftmostKmer(bb.array, bb.length());
	}
	
	public long leftmostKmer(final byte[] bases, final int blen){
		if(blen<k){return -1;}
		final int shift=2*k;
		final int shift2=shift-2;
		final long mask=~((-1L)<<shift);
		long kmer=0;
		long rkmer=0;
		int len=0;

		/* Loop through the bases, maintaining a forward and reverse kmer via bitshifts, to get the leftmost kmer */
		{
			for(int i=0; i<k; i++){
				final byte b=bases[i];
				final long x=AminoAcid.baseToNumber[b];
				final long x2=AminoAcid.baseToComplementNumber[b];
				kmer=((kmer<<2)|x)&mask;
				rkmer=(rkmer>>>2)|(x2<<shift2);
				if(x<0){
					len=0;
					kmer=rkmer=0;
				}else{len++;}
				if(verbose){outstream.println("Scanning i="+i+", len="+len+", kmer="+kmer+", rkmer="+rkmer+"\t"+new String(bases, Tools.max(0, i-k2), Tools.min(i+1, k)));}
			}
		}
		
		if(len<k){return -1;}
		else{assert(len==k);}
		return kmer;
	}
	
	public boolean doubleClaim(final ByteBuilder bb, final int id){
		return doubleClaim(bb.array, bb.length(), id);
	}
	
	/** Ensures there can be only one owner. */
	public boolean doubleClaim(final byte[] bases, final int blength, final int id){
		boolean success=claim(bases, blength, id, true);
		if(verbose){outstream.println("success1="+success+", id="+id+", blength="+blength);}
		if(!success){return false;}
		success=claim(bases, blength, id+CLAIM_OFFSET, true);
		if(verbose){outstream.println("success2="+success+", id="+id+", blength="+blength);}
		return success;
	}
	
	public boolean claim(final ByteBuilder bb, final int id, final boolean exitEarly){
		return claim(bb.array, bb.length(), id, exitEarly);
	}
	
	public float calcCoverage(final byte[] bases, final int blength){
		final int shift=2*k;
		final int shift2=shift-2;
		final long mask=~((-1L)<<shift);
		long kmer=0, rkmer=0;
		int len=0;
		long sum=0, max=0;
		int kmers=0;
		
		/* Loop through the bases, maintaining a forward and reverse kmer via bitshifts */
		for(int i=0; i<blength; i++){
			final byte b=bases[i];
			final long x=AminoAcid.baseToNumber[b];
			final long x2=AminoAcid.baseToComplementNumber[b];
			kmer=((kmer<<2)|x)&mask;
			rkmer=(rkmer>>>2)|(x2<<shift2);
			if(x<0){
				len=0;
				kmer=rkmer=0;
			}else{len++;}
			if(len>=k){
				int count=getCount(kmer, rkmer);
				sum+=count;
				max=Tools.max(count, max);
				kmers++;
			}
		}
		return sum==0 ? 0 : sum/(float)kmers;
	}
	
	public boolean claim(final byte[] bases, final int blength, final int id, boolean exitEarly){
		if(verbose){outstream.println("Thread "+id+" claim start.");}
		final int shift=2*k;
		final int shift2=shift-2;
		final long mask=~((-1L)<<shift);
		long kmer=0, rkmer=0;
		int len=0;
		boolean success=true;
		/* Loop through the bases, maintaining a forward and reverse kmer via bitshifts */
		for(int i=0; i<blength && success; i++){
			final byte b=bases[i];
			final long x=AminoAcid.baseToNumber[b];
			final long x2=AminoAcid.baseToComplementNumber[b];
			kmer=((kmer<<2)|x)&mask;
			rkmer=(rkmer>>>2)|(x2<<shift2);
			if(x<0){
				len=0;
				kmer=rkmer=0;
			}else{len++;}
			if(verbose){System.err.println("Scanning i="+i+", len="+len+", kmer="+kmer+", rkmer="+rkmer+"\t"+new String(bases, Tools.max(0, i-k2), Tools.min(i+1, k)));}
			if(len>=k){
				success=claim(kmer, rkmer, id/*, rid, i*/);
				success=(success || !exitEarly);
			}
		}
		return success;
	}
	
	public boolean claim(final long kmer, final long rkmer, final int id/*, final long rid, final int pos*/){
		//TODO: rid and pos are just for debugging.
		final long key=toValue(kmer, rkmer);
		final int way=(int)(key%ways);
		final AbstractKmerTable table=tables[way];
		final int count=table.getValue(key);
		assert(count==-1 || count>0) : count;
//		if(verbose  /*|| true*/){outstream.println("Count="+count+".");}
		if(count<0){return true;}
		assert(count>0) : count;
		final int owner=table.setOwner(key, id);
		if(verbose){outstream.println("owner="+owner+".");}
//		assert(owner==id) : id+", "+owner+", "+rid+", "+pos;
		return owner==id;
	}
	
	public void release(ByteBuilder bb, final int id){
		release(bb.array, bb.length(), id);
	}
	
	public void release(final byte[] bases, final int blength, final int id){
		if(verbose  /*|| true*/){outstream.println("*Thread "+id+" release start.");}
		final int shift=2*k;
		final int shift2=shift-2;
		final long mask=~((-1L)<<shift);
		long kmer=0, rkmer=0;
		int len=0;
		/* Loop through the bases, maintaining a forward and reverse kmer via bitshifts */
		for(int i=0; i<blength; i++){
			final byte b=bases[i];
			final long x=AminoAcid.baseToNumber[b];
			final long x2=AminoAcid.baseToComplementNumber[b];
			kmer=((kmer<<2)|x)&mask;
			rkmer=(rkmer>>>2)|(x2<<shift2);
			if(x<0){
				len=0;
				kmer=rkmer=0;
			}else{len++;}
			if(verbose){System.err.println("Scanning i="+i+", len="+len+", kmer="+kmer+", rkmer="+rkmer+"\t"+new String(bases, Tools.max(0, i-k2), Tools.min(i+1, k)));}
			if(len>=k){
				release(kmer, rkmer, id);
			}
		}
	}
	
	public boolean release(final long kmer, final long rkmer, final int id){
		return release(toValue(kmer, rkmer), id);
	}
	
	public boolean release(final long key, final int id){
		final int way=(int)(key%ways);
		final AbstractKmerTable table=tables[way];
		final int count=table.getValue(key);
//		if(verbose  /*|| true*/){outstream.println("Count="+count+".");}
		if(count<1){return true;}
		return table.clearOwner(key, id);
	}
	
	public int findOwner(ByteBuilder bb, final int id){
		return findOwner(bb.array, bb.length(), id);
	}
	
	public int findOwner(final byte[] bases, final int blength, final int id){
		final int shift=2*k;
		final int shift2=shift-2;
		final long mask=~((-1L)<<shift);
		long kmer=0, rkmer=0;
		int len=0;
		int maxOwner=-1;
		/* Loop through the bases, maintaining a forward and reverse kmer via bitshifts */
		for(int i=0; i<blength; i++){
			final byte b=bases[i];
			final long x=AminoAcid.baseToNumber[b];
			final long x2=AminoAcid.baseToComplementNumber[b];
			kmer=((kmer<<2)|x)&mask;
			rkmer=(rkmer>>>2)|(x2<<shift2);
			if(x<0){
				len=0;
				kmer=rkmer=0;
			}else{len++;}
			if(verbose){System.err.println("Scanning i="+i+", len="+len+", kmer="+kmer+", rkmer="+rkmer+"\t"+new String(bases, Tools.max(0, i-k2), Tools.min(i+1, k)));}
			if(len>=k){
				int owner=findOwner(kmer, rkmer);
				maxOwner=Tools.max(owner, maxOwner);
				if(maxOwner>id){break;}
			}
		}
		return maxOwner;
	}
	
	public int findOwner(final long kmer){
		return findOwner(kmer, rcomp(kmer));
	}
	
	public int findOwner(final long kmer, final long rkmer){
		final long key=toValue(kmer, rkmer);
		final int way=(int)(key%ways);
		final AbstractKmerTable table=tables[way];
		final int count=table.getValue(key);
		if(count<0){return -1;}
		final int owner=table.getOwner(key);
		return owner;
	}

	public int getCount(long kmer, long rkmer){
		long key=toValue(kmer, rkmer);
		int way=(int)(key%ways);
		return tables[way].getValue(key);
	}
	
	public int getCount(long key){
		int way=(int)(key%ways);
		return tables[way].getValue(key);
	}
	
	public int fillRightCounts(long kmer, long rkmer, int[] counts, long mask, int shift2){
		assert(kmer==rcomp(rkmer));
		if(verbose){outstream.println("fillRightCounts:   "+toText(kmer)+",   "+toText(rkmer));}
		kmer=(kmer<<2)&mask;
		rkmer=(rkmer>>>2);
		int max=-1, maxPos=0;
		
		for(int i=0; i<=3; i++){
			long kmer2=kmer|((long)i);
			long rkmer2=rkmer|(((long)AminoAcid.numberToComplement[i])<<shift2);
			if(verbose){outstream.println("kmer:               "+toText(kmer2)+", "+toText(rkmer2));}
			assert(kmer2==(kmer2&mask));
			assert(rkmer2==(rkmer2&mask));
			assert(kmer2==rcomp(rkmer2));
			long key=toValue(kmer2, rkmer2);
			int way=(int)(key%ways);
			int count=tables[way].getValue(key);
			assert(count==NOT_PRESENT || count>=0);
			count=Tools.max(count, 0);
			counts[i]=count;
			if(count>max){
				max=count;
				maxPos=i;
			}
		}
		return maxPos;
	}
	
	public int fillLeftCounts(long kmer, long rkmer, int[] counts, long mask, int shift2){
		assert(kmer==rcomp(rkmer));
		if(verbose){outstream.println("fillLeftCounts:    "+toText(kmer)+",   "+toText(rkmer));}
		rkmer=(rkmer<<2)&mask;
		kmer=(kmer>>>2);
		int max=-1, maxPos=0;
//		assert(false) : shift2+", "+k;
		for(int i=0; i<=3; i++){
			long rkmer2=rkmer|((long)AminoAcid.numberToComplement[i]);
			long kmer2=kmer|(((long)i)<<shift2);
			if(verbose){outstream.println("kmer:             "+toText(kmer2)+",     "+toText(rkmer2));}
			assert(kmer2==(kmer2&mask));
			assert(rkmer2==(rkmer2&mask));
			assert(kmer2==rcomp(rkmer2)) : "\n"+"kmer:      \t"+toText(rcomp(rkmer2))+", "+toText(rcomp(kmer2));
			long key=toValue(rkmer2, kmer2);
			int way=(int)(key%ways);
			int count=tables[way].getValue(key);
			assert(count==NOT_PRESENT || count>=0);
			count=Tools.max(count, 0);
			counts[i]=count;
			if(count>max){
				max=count;
				maxPos=i;
			}
		}
		return maxPos;
	}
	
	/*--------------------------------------------------------------*/
	/*----------------       Printing Methods       ----------------*/
	/*--------------------------------------------------------------*/
	
	public boolean dumpKmersAsText(String fname, int minToDump, boolean printTime){
		if(fname==null){return false;}
		Timer t=new Timer();
		t.start();
		
		ByteStreamWriter bsw=new ByteStreamWriter(fname, overwrite, false, true);
		bsw.start();
		for(AbstractKmerTable set : tables){
			set.dumpKmersAsBytes(bsw, k, minToDump);
		}
		bsw.poisonAndWait();
		
		t.stop();
		if(printTime){outstream.println("Kmer Dump Time:             \t"+t);}
		return bsw.errorState;
	}
	
	/*--------------------------------------------------------------*/
	/*----------------        Recall Methods        ----------------*/
	/*--------------------------------------------------------------*/

	private final long rcomp(long kmer){return AminoAcid.reverseComplementBinaryFast(kmer, k);}
	private final StringBuilder toText(long kmer){return AbstractKmerTable.toText(kmer, k);}
	
	/*--------------------------------------------------------------*/
	/*----------------        Static Methods        ----------------*/
	/*--------------------------------------------------------------*/
	
	/**
	 * Transforms a kmer into a canonical value stored in the table.  Expected to be inlined.
	 * @param kmer Forward kmer
	 * @param rkmer Reverse kmer
	 * @param lengthMask Bitmask with single '1' set to left of kmer
	 * @return Canonical value
	 */
	public final long toValue(long kmer, long rkmer){
		long value=(rcomp ? Tools.max(kmer, rkmer) : kmer);
		return value;
	}
	
	/*--------------------------------------------------------------*/
	/*----------------       Final Primitives       ----------------*/
	/*--------------------------------------------------------------*/
	
	public int kbig(){return k;}
	public long filterMemory(int pass){return ((pass&1)==0) ? filterMemory0 : filterMemory1;}
	public boolean ecco(){return ecco;}
	public boolean qtrimLeft(){return qtrimLeft;}
	public boolean qtrimRight(){return qtrimRight;}
	public byte minAvgQuality(){return minAvgQuality;}
	public long tableMemory(){return tableMemory;}
	public long estimatedKmerCapacity(){return estimatedKmerCapacity;}
	
	/** Hold kmers.  A kmer X such that X%WAYS=Y will be stored in tables[Y] */
	private AbstractKmerTable[] tables;
	
	private final int bytesPerKmer;

	private final long usableMemory;
	private final long filterMemory0;
	private final long filterMemory1;
	private final long tableMemory;
	private final long estimatedKmerCapacity;
	
	/** Number of tables (and threads, during loading) */ 
	private final boolean prealloc;
	
	/** Number of tables (and threads, during loading) */ 
	public final int ways;
	
	/** Normal kmer length */
	public final int k;
	/** k-1; used in some expressions */
	public final int k2;
	
	/** Look for reverse-complements as well as forward kmers.  Default: true */
	private final boolean rcomp;
	
	/** Quality-trim the left side */
	public final boolean qtrimLeft;
	/** Quality-trim the right side */
	public final boolean qtrimRight;
	/** Trim bases at this quality or below.  Default: 4 */
	public final byte trimq;
	
	/** Throw away reads below this average quality before trimming.  Default: 0 */
	public final byte minAvgQuality;
	/** If positive, calculate average quality from the first X bases only.  Default: 0 */
	public final int minAvgQualityBases;
	
	/** Ignore kmers with probability of correctness less than this */
	public final float minProb;
	
	/** Correct via overlap */
	private final boolean ecco;
	
	/** Attempt to merge via overlap prior to counting kmers */
	private final boolean merge;
	
}