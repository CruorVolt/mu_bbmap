package driver;

import java.io.File;
import java.lang.Thread.State;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import align2.Shared;
import align2.Tools;

import dna.Parser;
import dna.Timer;

/**
 * @author Brian Bushnell
 * @date Sep 25, 2014
 *
 */
public class TestLockSpeed {
	
	public static void main(String[] args){
		
		int mode=0;
		long max=1000000000;
		int threads=Shared.threads();
		
		for(int i=0; i<args.length; i++){
			String arg=args[i];
			String[] split=arg.split("=");
			String a=split[0].toLowerCase();
			String b=split.length>1 ? split[1] : null;
			if(b==null || b.equalsIgnoreCase("null")){b=null;}
			while(a.startsWith("-")){a=a.substring(1);} //In case people use hyphens

			if(Parser.isJavaFlag(arg)){
				//do nothing
			}else if(a.equals("null")){
				// do nothing
			}else if(a.equals("mode")){
				mode=Integer.parseInt(b);
			}else if(a.equals("threads") || a.equals("t")){
				threads=Integer.parseInt(b);
			}else if(a.equals("max")){
				max=Tools.parseKMG(b);
			}else{
				System.err.println("Unknown parameter "+args[i]);
				assert(false) : "Unknown parameter "+args[i];
				//				throw new RuntimeException("Unknown parameter "+args[i]);
			}
		}
		
		CountBox box;
		if(mode==UNLOCKED || mode==LOCKED){
			box=new LockBox();
		}else if(mode==ATOMIC){
			box=new AtomBox();
		}else if(mode==VOLATILE || mode==FIELD || mode==STATICFIELD){
			box=new VolatileBox();
		}else{
			throw new RuntimeException("Unknown mode "+mode);
		}
		
		ArrayList<CountThread> list=new ArrayList<CountThread>(threads);
		for(int i=0; i<threads; i++){
			list.add(new CountThread(box, max, mode));
		}

		Timer t=new Timer();
		t.start();
		
		for(CountThread ct : list){ct.start();}
		for(CountThread ct : list){
			while(ct.getState()!=State.TERMINATED){
				try {
					ct.join();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		
		t.stop();
		System.out.println("Time:  \t"+t);
		System.out.println("Count: \t"+box.value());
		System.out.println("Speed: \t"+String.format("%.3f", (threads*max*1.0)/(t.elapsed))+" giga per second");
		
	}
	
	static class CountThread extends Thread{
		
		public CountThread(CountBox box_, long max_, int mode_){
			box0=box_;
			max=max_;
			mode=mode_;
		}
		
		public void run(){
			if(mode==UNLOCKED){
				final LockBox box=(LockBox)box0;
				for(long i=0; i<max; i++){
					box.counter++;
				}
			}else if(mode==LOCKED){
				final LockBox box=(LockBox)box0;
				for(long i=0; i<max; i++){
					box.increment();
				}
			}else if(mode==ATOMIC){
				final AtomBox box=(AtomBox)box0;
				for(long i=0; i<max; i++){
					box.increment();
				}
			}else if(mode==VOLATILE){
				final VolatileBox box=(VolatileBox)box0;
				for(long i=0; i<max; i++){
					box.counter++;
				}
			}else if(mode==FIELD){
				final VolatileBox box=(VolatileBox)box0;
				final AtomicLongFieldUpdater<VolatileBox> updater=AtomicLongFieldUpdater.newUpdater(VolatileBox.class, "counter");
				for(long i=0; i<max; i++){
					updater.incrementAndGet(box);
				}
			}else if(mode==STATICFIELD){
				final VolatileBox box=(VolatileBox)box0;
				for(long i=0; i<max; i++){
					box.increment();
				}
			}
		}
		
		final CountBox box0;
		final long max;
		final int mode;
	}
	
	abstract static class CountBox{
		abstract void increment();
		abstract long value();
	}
	
	static class LockBox extends CountBox{
		synchronized void increment(){counter++;}
		long value(){return counter;}
		long counter;
	}
	
	static class AtomBox extends CountBox{
		void increment(){counter.incrementAndGet();}
		long value(){return counter.longValue();}
		AtomicLong counter=new AtomicLong(0);
	}
	
	static class VolatileBox extends CountBox{
		void increment(){updater.incrementAndGet(this);}
		long value(){return counter;}
		volatile long counter;
		static final AtomicLongFieldUpdater<VolatileBox> updater=AtomicLongFieldUpdater.newUpdater(VolatileBox.class, "counter");
	}
	
	static final int UNLOCKED=0, LOCKED=1, ATOMIC=2, VOLATILE=3, FIELD=4, STATICFIELD=5;
	
}
