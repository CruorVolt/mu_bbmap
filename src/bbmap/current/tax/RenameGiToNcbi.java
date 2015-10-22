package tax;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;

import kmer.HashArray1D;

import stream.ConcurrentGenericReadInputStream;
import stream.FASTQ;
import stream.FastaReadInputStream;
import align2.ReadStats;
import align2.Shared;
import align2.Tools;
import dna.Parser;
import dna.Timer;
import fileIO.ByteFile;
import fileIO.ByteFile1;
import fileIO.ByteFile2;
import fileIO.ByteStreamWriter;
import fileIO.FileFormat;
import fileIO.ReadWrite;

/**
 * @author Brian Bushnell
 * @date Mar 10, 2015
 *
 */
public class RenameGiToNcbi {
	
	public static void main(String[] args){
		Timer t=new Timer();
		t.start();
		RenameGiToNcbi mb=new RenameGiToNcbi(args);
		mb.process(t);
	}
	
	public RenameGiToNcbi(String[] args){
		
		args=Parser.parseConfig(args);
		if(Parser.parseHelp(args)){
			printOptions();
			System.exit(0);
		}
		
		outstream.println("Executing "+getClass().getName()+" "+Arrays.toString(args)+"\n");

		
		
		Shared.READ_BUFFER_LENGTH=Tools.min(200, Shared.READ_BUFFER_LENGTH);
		Shared.capBuffers(4);
		ReadWrite.USE_PIGZ=ReadWrite.USE_UNPIGZ=true;
		ReadWrite.MAX_ZIP_THREADS=Shared.threads();
		ReadWrite.ZIP_THREAD_DIVISOR=2;
		FASTQ.TEST_INTERLEAVED=FASTQ.FORCE_INTERLEAVED=false;
		
		Parser parser=new Parser();
		for(int i=0; i<args.length; i++){
			String arg=args[i];
			String[] split=arg.split("=");
			String a=split[0].toLowerCase();
			String b=split.length>1 ? split[1] : null;
			if(b==null || b.equalsIgnoreCase("null")){b=null;}
			while(a.startsWith("-")){a=a.substring(1);} //In case people use hyphens

			if(parser.parse(arg, a, b)){
				//do nothing
			}else if(a.equals("prefix")){
				prefix=Tools.parseBoolean(b);
			}else if(a.equals("table") || a.equals("gi")){
				tableFile=b;
			}else if(a.equals("invalid")){
				outInvalid=b;
			}else if(a.equals("verbose")){
				verbose=Tools.parseBoolean(b);
				ByteFile1.verbose=verbose;
				ByteFile2.verbose=verbose;
				stream.FastaReadInputStream.verbose=verbose;
				ConcurrentGenericReadInputStream.verbose=verbose;
//				align2.FastaReadInputStream2.verbose=verbose;
				stream.FastqReadInputStream.verbose=verbose;
				ReadWrite.verbose=verbose;
			}else if(parser.in1==null && i==0 && !arg.contains("=") && (arg.toLowerCase().startsWith("stdin") || new File(arg).exists())){
				parser.in1=arg;
			}else if(parser.out1==null && i==1 && !arg.contains("=")){
				parser.out1=arg;
			}else{
				outstream.println("Unknown parameter "+args[i]);
				assert(false) : "Unknown parameter "+args[i];
				//				throw new RuntimeException("Unknown parameter "+args[i]);
			}
		}
		
		{//Process parser fields
			Parser.processQuality();
			
			maxReads=parser.maxReads;
			
			overwrite=ReadStats.overwrite=parser.overwrite;
			append=ReadStats.append=parser.append;
			
			in1=parser.in1;

			out1=parser.out1;
		}
		
		assert(FastaReadInputStream.settingsOK());
		
		if(in1==null){
			printOptions();
			throw new RuntimeException("Error - at least one input file is required.");
		}
		if(!ByteFile.FORCE_MODE_BF1 && !ByteFile.FORCE_MODE_BF2){
			ByteFile.FORCE_MODE_BF2=false;
			ByteFile.FORCE_MODE_BF1=true;
		}

		if(out1!=null && out1.equalsIgnoreCase("null")){out1=null;}
		
		if(!Tools.testOutputFiles(overwrite, append, false, out1)){
			outstream.println((out1==null)+", "+out1);
			throw new RuntimeException("\n\noverwrite="+overwrite+"; Can't write to output files "+out1+"\n");
		}

		ffout1=FileFormat.testOutput(out1, FileFormat.FA, null, true, overwrite, append, false);
		ffoutInvalid=FileFormat.testOutput(outInvalid, FileFormat.FA, null, true, overwrite, append, false);
		ffin1=FileFormat.testInput(in1, FileFormat.FA, null, true, true);
		
		GiToNcbi.initialize(tableFile);
	}
	
	void process(Timer t){
		
		ByteFile bf=ByteFile.makeByteFile(ffin1, false);
		ByteStreamWriter bsw=new ByteStreamWriter(ffout1);
		bsw.start();
		
		ByteStreamWriter bswInvalid=null;
		if(ffoutInvalid!=null){
			bswInvalid=new ByteStreamWriter(ffoutInvalid);
			bswInvalid.start();
		}
		
		final HashArray1D counts=(countTable && !prefix) ? new HashArray1D(256000, true) : null;
		
		long readsProcessed=0, basesProcessed=0;
		
		byte[] line=bf.nextLine();
		boolean valid=false;
		while(line!=null){
			if(line.length>0 && line[0]=='>'){
				readsProcessed++;
				if(maxReads>0 && readsProcessed>maxReads){break;}
				final int number=GiToNcbi.getCarrot(line);
				valid=(number>=0);
				if(valid){
					validReads++;
					bsw.print(ncbi);
					bsw.print(number);
					if(prefix){
						bsw.print('|');
						for(int i=1; i<line.length; i++){
							bsw.print(line[i]);
						}
					}else if(counts!=null){
						bsw.print('|');
						int count=counts.increment(number);
						bsw.print(count);
						if(count==1){taxaCounted++;}
					}
					bsw.println();
				}else{
					invalidReads++;
					if(bswInvalid!=null){
						bswInvalid.println(line);
					}
				}
			}else{
				basesProcessed+=line.length;
				if(valid){
					validBases+=line.length;
					bsw.println(line);
				}else{
					invalidBases+=line.length;
					if(bswInvalid!=null && keepInvalidSequence){
						bswInvalid.println(line);
					}
				}
			}
			line=bf.nextLine();
		}
		
		errorState|=bf.close();
		if(bsw!=null){errorState|=bsw.poisonAndWait();}
		if(bswInvalid!=null){errorState|=bswInvalid.poisonAndWait();}
		
		t.stop();
		
		double rpnano=readsProcessed/(double)(t.elapsed);
		double bpnano=basesProcessed/(double)(t.elapsed);

		String rpstring=(readsProcessed<100000 ? ""+readsProcessed : readsProcessed<100000000 ? (readsProcessed/1000)+"k" : (readsProcessed/1000000)+"m");
		String bpstring=(basesProcessed<100000 ? ""+basesProcessed : basesProcessed<100000000 ? (basesProcessed/1000)+"k" : (basesProcessed/1000000)+"m");

		while(rpstring.length()<8){rpstring=" "+rpstring;}
		while(bpstring.length()<8){bpstring=" "+bpstring;}
		
		outstream.println("Time:                         \t"+t);
		outstream.println("Reads Processed:    "+rpstring+" \t"+String.format("%.2fk reads/sec", rpnano*1000000));
		outstream.println("Bases Processed:    "+bpstring+" \t"+String.format("%.2fm bases/sec", bpnano*1000));
		
		outstream.println();
		outstream.println("Valid Sequences:   \t"+validReads);
		outstream.println("Valid Bases:       \t"+validBases);
		outstream.println("Invalid Sequences: \t"+invalidReads);
		outstream.println("Invalid Bases:     \t"+invalidBases);
		if(counts!=null){
			outstream.println("Unique Taxa:       \t"+taxaCounted);
		}
		
		if(errorState){
			throw new RuntimeException(getClass().getName()+" terminated in an error state; the output may be corrupt.");
		}
	}
	
	/*--------------------------------------------------------------*/
	
	private void printOptions(){
		assert(false) : "printOptions: TODO";
//		outstream.println("Syntax:\n");
//		outstream.println("java -ea -Xmx512m -cp <path> jgi.ReformatReads in=<infile> in2=<infile2> out=<outfile> out2=<outfile2>");
//		outstream.println("\nin2 and out2 are optional.  \nIf input is paired and there is only one output file, it will be written interleaved.\n");
//		outstream.println("Other parameters and their defaults:\n");
//		outstream.println("overwrite=false  \tOverwrites files that already exist");
//		outstream.println("ziplevel=4       \tSet compression level, 1 (low) to 9 (max)");
//		outstream.println("interleaved=false\tDetermines whether input file is considered interleaved");
//		outstream.println("fastawrap=70     \tLength of lines in fasta output");
//		outstream.println("qin=auto         \tASCII offset for input quality.  May be set to 33 (Sanger), 64 (Illumina), or auto");
//		outstream.println("qout=auto        \tASCII offset for output quality.  May be set to 33 (Sanger), 64 (Illumina), or auto (meaning same as input)");
//		outstream.println("outsingle=<file> \t(outs) Write singleton reads here, when conditionally discarding reads from pairs.");
	}
	
	
	/*--------------------------------------------------------------*/
	
	private String in1=null;
	private String out1=null;
	private String outInvalid=null;

	private String tableFile=null;
	
	/*--------------------------------------------------------------*/

	private long maxReads=-1;

	private long validReads=0;
	private long validBases=0;
	private long invalidReads=0;
	private long invalidBases=0;
	private long taxaCounted=0;

	private boolean prefix=false;
	private boolean countTable=true;
	private boolean keepInvalidSequence=false;
	
	/*--------------------------------------------------------------*/
	
	private final FileFormat ffin1;
	private final FileFormat ffout1;
	private final FileFormat ffoutInvalid;
	
	
	/*--------------------------------------------------------------*/
	
	private PrintStream outstream=System.err;
	public static boolean verbose=false;
	public boolean errorState=false;
	private boolean overwrite=false;
	private boolean append=false;
	
	private static final byte[] ncbi=">ncbi|".getBytes();
	
}
