#Run the reference BBMAP on e_coli_1000.fq
java -ea -Xmx4g -cp "/Users/anders/mu_bbmap/src/bbmap/current" align2.BBMap in=/Users/anders/mu_bbmap/src/bbmap/resources/e_coli_1000.fq out=/Users/anders/mu_bbmap/out.sam overwrite=t build=1 path=/Users/anders/mu_bbmap/src/bbmap
