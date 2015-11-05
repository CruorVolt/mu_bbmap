import java.lang.Runtime;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.FileOutputStream;

import java.nio.channels.FileChannel;

import java.util.Random;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Test;
import org.junit.BeforeClass;
import org.junit.AfterClass;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.equalTo;

import align2.BBMap;

public class AlignTest {

    static final String BASE_DIR = "/Users/anders/mu_bbmap/";
    static final String CLEAN_BBMAP = BASE_DIR + "src/bbmap/current ";
    static final String[] GOLD_ARGS = {"in=" + BASE_DIR + "src/bbmap/resources/e_coli_1000.fq",
            "out=" + BASE_DIR + "sams/gold.sam",
            "overwrite=t",
            "build=1",
            "path=" + BASE_DIR + "src/bbmap"};

    @Test
    public void test_removal_of_reads() {
        assertTrue("Removal of Reads Failure!", 
            test_mr("removal_of_reads_pre", "removal_of_reads_post"));
    }

    @Test
    public void test_addition_of_reads() {
        assertTrue("Addition of Reads Failure!", 
            test_mr("addition_of_reads_pre", "addition_of_reads_post"));
    }

    @Test
    public void test_unmapped_reads() {
        assertTrue("Unmapped Reads Failure!", 
            test_mr("unmapped_reads_pre", "unmapped_reads_post"));
    }

    @Test
    public void test_mapped_reads() {
        assertTrue("Mapped Reads Failure!", 
            test_mr("mapped_reads_pre", "mapped_reads_post"));
    }

    @Test
    public void test_permutation_of_reads() {
        assertTrue("Permutation of Reads Failure!", 
            test_mr("permutation_of_reads_pre", "permutation_of_reads_post"));
    }

    @BeforeClass
    public static void setUpClass() {
        try {
            //truncate original output file
            FileChannel outChan = new FileOutputStream(new File("/Users/anders/mu_bbmap/sams/original.sam"), true).getChannel();
            outChan.truncate(0);
            outChan.close();
        } catch (IOException e) {
            System.out.println("Could not truncate output file");
        }

        //Create this mutant's reference input
        String[] original_args = {
            "in=" + BASE_DIR + "src/bbmap/resources/e_coli_1000.fq",
            "out=" + BASE_DIR + "sams/original.sam",
            "overwrite=t",
            "build=1",
            "path=" + BASE_DIR + "src/bbmap",
            "threads=1"};
        BBMap.main(original_args);

    }

    @AfterClass
    public static void tearDownClass() {
        //remove all SAM files
    }

    public static Boolean test_mr(String mr_pre, String mr_post) {

        Method mr_pre_method, mr_post_method;
        Class thisClass = AlignTest.class;
        try {
            mr_pre_method = thisClass.getMethod(mr_pre, String.class, String.class); 

            //apply mutation - make new input file
            mr_pre_method.invoke(null,
                BASE_DIR + "src/bbmap/resources/e_coli_1000.fq", 
                BASE_DIR + "altered_reads/e_coli_1000_altered.fq");
        } catch (Exception e) {
            System.err.println("Problem invoking pre funcion");
            e.printStackTrace();
        }

        String[] modified_args = {
            "in=" + BASE_DIR + "altered_reads/e_coli_1000_altered.fq",
            "out=" + BASE_DIR + "sams/modified.sam",
            "overwrite=t",
            "build=1",
            "path=" + BASE_DIR + "src/bbmap",
            "threads=1"};

        //truncate modified output file
        try {
            //truncate original output file
            FileChannel outChan = new FileOutputStream(new File("/Users/anders/mu_bbmap/sams/modified.sam"), true).getChannel();
            outChan.truncate(0);
            outChan.close();
        } catch (IOException e) {
            System.out.println("Could not truncate output file");
        }

        //Create comparison SAM
        BBMap.main(modified_args);
    
        //Perform the relation test
        Boolean result = false;
        try {
            String original = BASE_DIR + "sams/original.sam";
            String modified = BASE_DIR + "sams/modified.sam";
            mr_post_method = thisClass.getMethod(mr_post, String.class, String.class);
            result = (Boolean)mr_post_method.invoke(null, original, modified);
        } catch(Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public static void removal_of_reads_pre(String input_filename, String output_filename) {
        Random rand = new Random();
        try {    
            PrintWriter writer = new PrintWriter(output_filename);
            FileReader file_reader = new FileReader(input_filename);
            BufferedReader reader = new BufferedReader(file_reader);
            while (true) {
                String line = reader.readLine();
                if (line == null) { break; }
                if ((line.charAt(0) == '@') && (rand.nextDouble() > 0.5)) { //write this read
                        writer.println(line);
                        writer.println(reader.readLine());
                        writer.println(reader.readLine());
                        writer.println(reader.readLine());
                }
            }
            reader.close();
            file_reader.close();
            writer.close();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean removal_of_reads_post(String original_sam, String output_sam) {
        System.out.println("\nRemoval of reads comparison");
        boolean pass = true;
        int count = 0;
        HashMap<String, String> original_map = build_sam_mapping(original_sam);
        HashMap<String, String> modified_map = build_sam_mapping(output_sam);
        try {
            //compare modified SAM
            for (Object key : modified_map.keySet().toArray()) {
                String original = original_map.get((String)key);
                count++;
                if ( (original == null) || (!original.equals(modified_map.get((String)key))) ) {
                    //System.out.println("Key " + key + " is " + modified_map.get((String)key) + " in modified, was " + original + " in original");
                    pass = false;
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("Removal of reads compared " + count + " new mappings agains the original " + original_map.keySet().size() + " mappings and will return " + pass);
        return pass;
    }

    public static void mapped_reads_pre(String input_filename, String output_filename) {
        //scan the fastq file and output the unmapped reads

        String original_sam = BASE_DIR + "sams/original.sam";

        try {    
            PrintWriter writer = new PrintWriter(output_filename);

            FileReader sam_file_reader = new FileReader(original_sam);
            BufferedReader sam_reader = new BufferedReader(sam_file_reader);

            FileReader fq_file_reader = new FileReader(input_filename);
            BufferedReader fq_reader = new BufferedReader(fq_file_reader);

            //scan sam file
            String line;
            String[] lineParts;
            int index;
            ArrayList<String> mappedList = new ArrayList<String>();
            sam_reader.readLine(); sam_reader.readLine(); sam_reader.readLine();
            while ((line = sam_reader.readLine()) != null) {
                lineParts = line.split("\t");
                index = Integer.valueOf(lineParts[3]);
                if (index != 0) {
                    mappedList.add(lineParts[0]);
                }
            }
            sam_reader.close();
            sam_file_reader.close();

            //scan fq file to find matches and output
            while ((line = fq_reader.readLine()) != null) {
                if (line.charAt(0) == '@') { //this line is a read
                    if (mappedList.contains(line.substring(1))) { //this read was mapped - add it to the new fastq file
                        writer.println(line); //label
                        writer.println(fq_reader.readLine()); //read head
                        writer.println(fq_reader.readLine()); //read ext
                        writer.println(fq_reader.readLine()); //read tail
                    }
                }
            }
            writer.close();
            fq_reader.close();
            fq_file_reader.close();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean mapped_reads_post(String original_sam, String output_sam) {
        System.out.println("\nMapped reads comparison");
        boolean pass = true;
        HashMap<String, String> modified_map = build_sam_mapping(output_sam);
        try {

            //modified sam should have only mappings
            for (String val : modified_map.values()) {
                System.out.println( "\tThis shouldn't be zero: " + Integer.valueOf(val));
                if (Integer.valueOf(val).equals(0)) {
                    pass = false;
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("Mapped reads mapped " + modified_map.keySet().size() + " of the new reads and returns " + pass);
        return pass;
    }

    public static void unmapped_reads_pre(String input_filename, String output_filename) {
        //scan the fastq file and output the unmapped reads

        String original_sam = BASE_DIR + "sams/original.sam";

        try {    
            PrintWriter writer = new PrintWriter(output_filename);

            FileReader sam_file_reader = new FileReader(original_sam);
            BufferedReader sam_reader = new BufferedReader(sam_file_reader);

            FileReader fq_file_reader = new FileReader(input_filename);
            BufferedReader fq_reader = new BufferedReader(fq_file_reader);

            //scan sam file
            String line;
            String[] lineParts;
            int index;
            ArrayList<String> unmappedList = new ArrayList<String>();
            sam_reader.readLine(); sam_reader.readLine(); sam_reader.readLine();
            while ((line = sam_reader.readLine()) != null) {
                lineParts = line.split("\t");
                index = Integer.valueOf(lineParts[3]);
                if (index == 0) {
                    unmappedList.add(lineParts[0]);
                }
            }
            sam_reader.close();
            sam_file_reader.close();

            //scan fq file to find matches and output
            while ((line = fq_reader.readLine()) != null) {
                if (line.charAt(0) == '@') { //this line is a read
                    if (unmappedList.contains(line.substring(1))) { //this read was unmapped - add it to the new fastq file
                        writer.println(line); //label
                        writer.println(fq_reader.readLine()); //read head
                        writer.println(fq_reader.readLine()); //read ext
                        writer.println(fq_reader.readLine()); //read tail
                    }
                }
            }
            writer.close();
            fq_reader.close();
            fq_file_reader.close();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean unmapped_reads_post(String original_sam, String output_sam) {
        System.out.println("Unmapped reads comparison");
        boolean pass = true;
        HashMap<String, String> modified_map = build_sam_mapping(output_sam);
        try {

            //modified sam should have no mappings
            for (String val : modified_map.values()) {
                System.out.println("\tThis should be zero: " + Integer.valueOf(val));
                if (!Integer.valueOf(val).equals(0)) {
                    pass = false;
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("Unmapped reads mapped " + modified_map.keySet().size() + " reads and will return " + pass);
        return pass;
    }

    public static void addition_of_reads_pre(String input_filename, String output_filename) {
        try {    
            PrintWriter writer = new PrintWriter(output_filename);
            FileReader file_reader = new FileReader(input_filename);
            BufferedReader reader = new BufferedReader(file_reader);
            String readName, readHead, readExt, readTail;
            while (true) {
                String line = reader.readLine();
                if (line == null) { break; }
                if (line.charAt(0) == '@') {
                
                        //write original version of read
                        readName = line;
                        writer.println(line);
                        readHead = reader.readLine();
                        writer.println(readHead);
                        readExt = reader.readLine();
                        writer.println(readExt);
                        readTail = reader.readLine();
                        writer.println(readTail);
        
                        //write this read's duplicate
                        writer.println(readName + "v2");
                        writer.println(readHead);
                        writer.println(readExt);
                        writer.println(readTail);
                }
            }
            reader.close();
            file_reader.close();
            writer.close();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean addition_of_reads_post(String original_sam, String output_sam) {
        System.out.println("Addition of reads comparison");
        boolean pass = true;
        HashMap<String, String> original_map = build_sam_mapping(original_sam);
        HashMap<String, String> modified_map = build_sam_mapping(output_sam);
        for (String key: original_map.keySet()) {
            if (!modified_map.containsKey(key) ||
                !modified_map.get(key).equals(original_map.get(key)) ||
                !modified_map.get(key).equals(modified_map.get(key + "v2"))) {
                
                pass = false;
                break;
            }
        }
        System.out.println("Addition of reads compared the " + original_map.keySet().size() + " mappings against " + modified_map.keySet().size() + " modified mappings and will return " + pass);
        return pass;
    }

    public static void permutation_of_reads_pre(String input_filename, String output_filename) {

        HashMap<String, String[]> map = new HashMap<String, String[]>();

        try {    
            PrintWriter writer = new PrintWriter(output_filename);
            FileReader file_reader = new FileReader(input_filename);
            BufferedReader reader = new BufferedReader(file_reader);

            String line;
            String[] fullRead;
            while( (line = reader.readLine()) != null ) {
                if (line.charAt(0) == '@') { //this line starts a read
                    fullRead = new String[4];
                    fullRead[0] = line.substring(1);
                    fullRead[1] = reader.readLine();
                    fullRead[2] = reader.readLine();
                    fullRead[3] = reader.readLine();
                    map.put(line.substring(1), fullRead);
                }
            }
            
            //shuffle keys
            int index;
            Random random = new Random();
            String temp;
            String[] keys =map.keySet().toArray(new String[0]);
            for (int i = keys.length - 1; i > 0; i--) {
                index = random.nextInt(i + 1);
                temp = keys[index];
                keys[index] = keys[i];
                keys[i] = temp;
            }

            //write new fastq file using new ordering
            for (String key : keys) {
                fullRead = map.get(key);
                writer.println(fullRead[0]);
                writer.println(fullRead[1]);
                writer.println(fullRead[2]);
                writer.println(fullRead[3]);
            }

            reader.close();
            file_reader.close();
            writer.close();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean permutation_of_reads_post(String original_sam, String output_sam) {
        System.out.println("Permutation of reads comparison");
        boolean pass = true;
        HashMap<String, String> original_map = build_sam_mapping(original_sam);
        HashMap<String, String> modified_map = build_sam_mapping(output_sam);
        
        //mappings should all be the same
        if (original_map.size() != modified_map.size()) {
            return false;
        }

        for (String key: original_map.keySet()) {
            if (!original_map.get(key).equals(modified_map.get(key))) {    
                pass = false;
                break;
            }
        }
        System.out.println("Permutation of reads tested whether the " + original_map.keySet().size() + " original mappings were identical to the " + modified_map.keySet().size() + " mappings and will return " + pass);
        return pass;
    }

    public static HashMap<String, String> build_sam_mapping(String filename) {
        HashMap<String, String> map = new HashMap<String, String>();
        try {
            BufferedReader reader= new BufferedReader(new FileReader(filename));
            for (int i = 1; i <= 3; i++) { //headers
                reader.readLine();
            }
            String line;
            String[] lineArr;
            //build map for original
            while ((line = reader.readLine()) != null) {
                lineArr = line.split("\t");
                map.put(lineArr[0], lineArr[3]);
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return map;
    }

    public static void build_reference_alignment() {
        try {
            //make alignment SAM using pristine src/bbmap/current version
            String alignString = "java -ea -Xmx5g -cp " + CLEAN_BBMAP + String.join(" ", GOLD_ARGS);
            Runtime.getRuntime().exec(alignString);
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        BBMap.main(GOLD_ARGS);
    }
}
