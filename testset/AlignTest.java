import java.lang.Runtime;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.Arrays;
import java.util.Random;
import java.util.HashMap;

import org.junit.Test;
import org.junit.BeforeClass;
import org.junit.AfterClass;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.equalTo;

import picard.sam.*;
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

    @BeforeClass
    public static void setUpClass() {

        try {
            //Ensure gold standard version exists
            String ref_dir = BASE_DIR + "sams";
            if (!(new File(ref_dir + "/gold.sam").exists())) {
                throw new FileNotFoundException();
            }
            //Sort the reference file
            String gold = ref_dir + "/gold.sam";
            String gold_sorted = ref_dir + "/gold_sorted.sam";
            String[] gold_args = {"I="+gold, "O="+gold_sorted, "SO=queryname"};
            new SortSam().instanceMain(gold_args);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
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
                BASE_DIR + "altered_reads/e_coli_1000_removal.fq");
        } catch (Exception e) {
            System.out.println("Problem invoking pre funcion");
            e.printStackTrace();
        }

        String[] original_args = {
            "in=" + BASE_DIR + "src/bbmap/resources/e_coli_1000.fq",
            "out=" + BASE_DIR + "sams/original.sam",
            "overwrite=t",
            "build=1",
            "path=" + BASE_DIR + "src/bbmap"};

        String[] modified_args = {
            "in=" + BASE_DIR + "altered_reads/e_coli_1000_removal.fq",
            "out=" + BASE_DIR + "sams/modified.sam",
            "overwrite=t",
            "build=1",
            "path=" + BASE_DIR + "src/bbmap"};

        //make alginments to compare
        BBMap.main(original_args);
        BBMap.main(modified_args);

        String original = BASE_DIR + "sams/original.sam";
        String original_sorted = BASE_DIR + "sams/original_sorted.sam";

        String modified = BASE_DIR + "sams/modified.sam";
        String modified_sorted = BASE_DIR + "sams/modified_sorted.sam";

        String[] original_sort_args = {"I="+original, "O="+original_sorted, "SO=queryname"};
        String[] modified_sort_args = {"I="+modified, "O="+modified_sorted, "SO=queryname"};

        new SortSam().instanceMain(original_sort_args);
        new SortSam().instanceMain(modified_sort_args);
    
        Boolean result = false;
        try {
            mr_post_method = thisClass.getMethod(mr_post, String.class, String.class);
            result = (Boolean)mr_post_method.invoke(null, original_sorted, modified_sorted);
        } catch(Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public static void removal_of_reads_pre(String input_filename, String output_filename) {
        Random rand = new Random();
        try {    
            PrintWriter writer = new PrintWriter(output_filename);
            BufferedReader reader = new BufferedReader(new FileReader(input_filename));
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
            System.out.println("New reads file written");
            reader.close();
            writer.close();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean removal_of_reads_post(String original_sam, String output_sam) {
        try {
            BufferedReader reader_original = new BufferedReader(new FileReader(original_sam));
            BufferedReader reader_modified = new BufferedReader(new FileReader(output_sam));
            for (int i = 1; i <= 3; i++) { //headers
                reader_original.readLine();
                reader_modified.readLine();
            }

            HashMap<String, String> map = new HashMap<String, String>();
            String line;
            String[] lineArr;

            //build map for original
            while ((line = reader_original.readLine()) != null) {
                lineArr = line.split("\t");
                map.put(lineArr[0], lineArr[3]);
            }

            //compare modified SAM
            String index;
            while ((line = reader_modified.readLine()) != null) {
                lineArr = line.split("\t");
                index = map.get(lineArr[0]);
                if (index == null) {
                    System.out.println("Read " + lineArr[0] + " from reduced output not found in original mapping");
                } else if (!index.equals(lineArr[3])) {
                    System.out.println("Modified read " + lineArr[0] + " index of " + lineArr[3] + " does not match original index of " + index);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
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
