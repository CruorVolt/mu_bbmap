#java -ea -Xmx4g -cp "/Users/anders/mu_bbmap/src/bbmap/current" align2.BBMap in=/Users/anders/mu_bbmap/src/bbmap/resources/e_coli_1000.fq out=/Users/anders/mu_bbmap/sams/gold.sam overwrite=t build=1 path=/Users/anders/mu_bbmap/src/bbmap
java -Xmx8g -cp "openjava.jar:bin/" -Djava.library.path="openjava.jar:/Users/anders/mu_bbmap/src/bbmap/jni" mujava.gui.RunTestMain 1> out.log 2> err.log;
vim out.log;
