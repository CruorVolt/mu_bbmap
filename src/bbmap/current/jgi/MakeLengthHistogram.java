package jgi;

import java.io.File;
import java.util.ArrayList;

import stream.ConcurrentReadInputStream;
import stream.FastaReadInputStream;
import stream.Read;

import dna.Data;
import dna.Parser;
import dna.Timer;
import fileIO.FileFormat;
import fileIO.ReadWrite;
import fileIO.TextStreamWriter;

import align2.ListNum;
import align2.ReadStats;
import align2.Shared;
import align2.Tools;

/**
 * @author Brian Bushnell
 * @date Jul 16, 2012
 *
 */
public class MakeLengthHistogram {
	
	public static void main(String[] args){
		Timer t=new Timer();
		t.start();
		
		String in1=null, in2=null;
		String out=null;
		
		Data.GENOME_BUILD=-1;
		ReadWrite.USE_UNPIGZ=true;
		Shared.READ_BUFFER_LENGTH=Tools.mid(1, Shared.READ_BUFFER_LENGTH, 20);
		
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
			}else if(Parser.parseZip(arg, a, b)){
				//do nothing
			}else if(Parser.parseQuality(arg, a, b)){
				//do nothing
			}else if(a.equals("reads") || a.equals("maxreads")){
				maxReads=Tools.parseKMG(b);
			}else if(a.equals("append") || a.equals("app")){
				append=ReadStats.append=Tools.parseBoolean(b);
			}else if(a.equals("overwrite") || a.equals("ow")){
				overwrite=Tools.parseBoolean(b);
			}else if(a.equals("in") || a.equals("in1")){
				in1=b;
			}else if(a.equals("in2")){
				in2=b;
			}else if(a.equals("out") || a.equals("hist") || a.equals("lhist")){
				out=b;
			}else if(a.equals("max") || a.equals("maxlength")){
				MAX_LENGTH=Integer.parseInt(b);
			}else if(a.equals("nzo") || a.equals("nonzeroonly")){
				NON_ZERO_ONLY=Tools.parseBoolean(b);
			}else if(a.startsWith("mult") || a.startsWith("div") || a.startsWith("bin")){
				MULT=Integer.parseInt(b);
			}else if(a.equals("round")){
				ROUND_BINS=Tools.parseBoolean(b);
			}else if(i==0 && !arg.contains("=")){
				in1=arg;
			}else if(i==1 && !arg.contains("=")){
				in2=arg;
			}else if(i==3 && !arg.contains("=")){
				out=arg;
			}else{
				throw new RuntimeException("Unknown argument: "+arg);
			}
		}
		
		{//Process parser fields
			Parser.processQuality();
		}
		
		MAX_LENGTH/=MULT;
		
		calc(in1, in2, out);
		t.stop();
		System.err.println("Time: \t"+t);
	}
	
	public static void calc(String in1, String in2, String out){
		
		FastaReadInputStream.MIN_READ_LEN=1;
		
		final ConcurrentReadInputStream cris;
		{
			FileFormat ff1=FileFormat.testInput(in1, FileFormat.FASTQ, null, true, true);
			FileFormat ff2=FileFormat.testInput(in2, FileFormat.FASTQ, null, true, true);
			cris=ConcurrentReadInputStream.getReadInputStream(maxReads, true, ff1, ff2);
//			if(verbose){System.err.println("Started cris");}
			cris.start(); //4567
		}
		boolean paired=cris.paired();
//		if(verbose){System.err.println("Paired: "+paired);}
		
		
		final int max=MAX_LENGTH;
		long[] readHist=new long[max+1];
		long[] baseHist=new long[max+1];
		
		int maxFound=0;
		int minFound=Integer.MAX_VALUE;
		
		{
			ListNum<Read> ln=cris.nextList();
			ArrayList<Read> reads=(ln!=null ? ln.list : null);
			
			if(reads!=null && !reads.isEmpty()){
				Read r=reads.get(0);
				assert(paired==(r.mate!=null));
			}
			
			while(reads!=null && reads.size()>0){
				//System.err.println("reads.size()="+reads.size());
				for(Read r1 : reads){
					final Read r2=r1.mate;
					
//					System.out.println("Processing read "+r.numericID);
					
					if(r1!=null && r1.bases!=null){
						readsProcessed++;
						final int x=r1.length();
						final int y=Tools.min(max, ((ROUND_BINS ? x+MULT/2 : x))/MULT);
						readHist[y]++;
						baseHist[y]+=x;
						maxFound=Tools.max(maxFound, x);
						minFound=Tools.min(minFound, x);
					}
					
					if(r2!=null && r2.bases!=null){
						readsProcessed++;
						final int x=r2.length();
						final int y=Tools.min(max, ((ROUND_BINS ? x+MULT/2 : x))/MULT);
						readHist[y]++;
						baseHist[y]+=x;
						maxFound=Tools.max(maxFound, x);
						minFound=Tools.min(minFound, x);
					}
					
				}
				//System.err.println("returning list");
				cris.returnList(ln.id, ln.list.isEmpty());
				//System.err.println("fetching list");
				ln=cris.nextList();
				reads=(ln!=null ? ln.list : null);
			}
			if(verbose){System.err.println("Finished reading");}
			cris.returnList(ln.id, ln.list.isEmpty());
			if(verbose){System.err.println("Returned list");}
			ReadWrite.closeStream(cris);
			if(verbose){System.err.println("Closed stream");}
			System.err.println("Processed "+readsProcessed+" reads.");
		}
		
		if(readsProcessed<1){minFound=0;}
		double stdev=Tools.standardDeviationHistogram(readHist)*MULT;
		int median=Tools.percentile(readHist, 0.5)*MULT;
		int mode=Tools.calcMode(readHist)*MULT;

		double[] readHistF=new double[max+1];
		long[] readHistC=new long[max+1];
		double[] readHistCF=new double[max+1];
		
		double[] baseHistF=new double[max+1];
		long[] baseHistC=new long[max+1];
		double[] baseHistCF=new double[max+1];
		

		readHistC[max]=readHist[max];
		baseHistC[max]=baseHist[max];
		for(int i=max; i>0; i--){
			readHistC[i-1]=readHistC[i]+readHist[i-1];
			baseHistC[i-1]=baseHistC[i]+baseHist[i-1];
		}
		for(int i=0; i<=max; i++){
			readHistCF[i]=readHistC[i]*100d/readHistC[0];
			readHistF[i]=readHist[i]*100d/readHistC[0];
			baseHistCF[i]=baseHistC[i]*100d/baseHistC[0];
			baseHistF[i]=baseHist[i]*100d/baseHistC[0];
		}

		TextStreamWriter tsw=new TextStreamWriter(out==null ? "stdout" : out, overwrite, append, false);
		tsw.start();
		tsw.println("#Reads:\t"+readsProcessed);
		tsw.println("#Bases:\t"+baseHistC[0]);
		tsw.println("#Max:\t"+maxFound);
		tsw.println("#Min:\t"+minFound);
		tsw.println("#Avg:\t"+String.format("%.1f",(baseHistC[0]*1d/readsProcessed)));
		tsw.println("#Median:\t"+median);
		tsw.println("#Mode:\t"+mode);
		tsw.println("#Std_Dev:\t"+String.format("%.1f",stdev));
		tsw.println("#Read Length Histogram:");
		tsw.println("#Length\treads\tpct_reads\tcum_reads\tcum_pct_reads\tbases\tpct_bases\tcum_bases\tcum_pct_bases");
		for(int i=0; i<=max; i++){
			if(readHist[i]>0 || !NON_ZERO_ONLY){
				tsw.println((i*MULT)+"\t"+readHist[i]+String.format("\t%.3f%%", readHistF[i])+"\t"+readHistC[i]+String.format("\t%.3f%%", readHistCF[i])+
						"\t"+baseHist[i]+String.format("\t%.3f%%", baseHistF[i])+"\t"+baseHistC[i]+String.format("\t%.3f%%", baseHistCF[i]));
			}
			if(i*MULT>=maxFound){break;}
		}
		tsw.poisonAndWait();
	}
	
	public static long maxReads=-1;
	public static long readsProcessed=0;
	public static int MAX_LENGTH=80000;
	public static int MULT=10;
	public static boolean ROUND_BINS=false;
	public static boolean NON_ZERO_ONLY=false;
	
	public static boolean append=false;
	public static boolean overwrite=true;
	public static boolean verbose=false;
	
}
