require 'optparse'
require 'fileutils'

require_relative 'mutant'

@here = File.expand_path(File.dirname(__FILE__))
@results_dir = File.join( @here, "..", "result")
@package = "bbmap.current.align2"
@eclipse_bin = File.join( "~", "eclipse", "eclipse")
@prefs_file = File.join(@here, "java_format.prefs")
@classpath = File.join(@here, "..", "src", "bbmap", "current")

@good = 0
@total = 0
#Reformat the source with eclipse
#Scan the source file
    #make new copies of each faulty version and keep them in subdirectories here, not in the original source location
#Make class files using -cp ORIGINAL_LOCATION
#Move all the new source and binary files to the appropriate directory in /results/ so muJava can work with them

OptionParser.new do |options|
    options.banner = "Usage: generate_faults.rb <file1> <file2> ... [options]"
end.parse!

p ARGV

def parse_dir(dir)
    Dir.foreach(dir) do |file|
        if !([".", ".."].include? file)
            reformat_file File.join(dir, file)
            parse_file File.join(dir, file)
        end
    end
end

def copy_original(full_path)
    filename = File.split(full_path)[-1]
    new_path =  File.join(@results_dir, "#{@package}.#{filename[0..-6]}", "original", filename)
    FileUtils.copy(full_path, new_path)
    compile_java(new_path)
end

def reformat_file(full_path)
    puts "#{@eclipse_bin} -application org.eclipse.jdt.core.JavaCodeFormatter -config #{@prefs_file} #{full_path}"
    system "#{@eclipse_bin} -application org.eclipse.jdt.core.JavaCodeFormatter -config #{@prefs_file} #{full_path}"
end

def compile_java(full_path)
    #compile
    success = system "javac -cp #{@classpath} #{full_path} &> /dev/null"
    if success
        @good += 1
    else
        #Delete this dir and associated file
        path_parts = File.split(full_path)
        File::delete full_path
        Dir::delete File.join(path_parts[0...-1])
        puts "Deleted invalid mutant #{path_parts[-2]}"
    end
    @total += 1
    puts "Compiled #{@good} out of #{@total} source files"
    #change permissions
end

def parse_file(full_path)

    filename = File.split(full_path)[-1]
    puts "Parsing #{filename}"

    #ensure the correct directory structure exists for the results
    new_src_dir = File.join( @results_dir, "#{@package}.#{filename[0..-6]}") #strip ".java" from file
    FileUtils::mkdir_p File.join(new_src_dir, "original")
    FileUtils::mkdir_p File.join(new_src_dir, "traditional_mutants", "all")

    copy_original(full_path)

    File.open(full_path, "r") do |source|

        mutator = Mutant.new
        original_lines = []
        mutant_lines = []
        source.each do |line| #build the arrays
            original_lines << line
            mutant_lines << mutator.mutate_line(line)
        end
        
        puts "Original source was #{original_lines.length} lines long"
        puts "New lines is an array of #{mutant_lines.length} maps"
        (0...mutant_lines.length).each do |index|
            new_dirs = mutant_lines[index].keys
            mutant_lines[index].keys.each do |key|
                mutant_line = mutant_lines[index][key] #each key is a new mutant file
                #make a new directory for the mutant
                    #dir structur: result/class_thing/traditional_mutants/ALL_FUNCS/AORB_345..etc
                new_dir = File.join(new_src_dir, "traditional_mutants", "all", key)
                FileUtils::mkdir_p new_dir
                #make a new source file for the mutant
                new_source = File.open( File.join(new_dir, filename), "w")
                #write all the lines of the original source, replacing the mutated line
                new_source.print "//This is an automatically generated faulty java program \n//Author: Anders Lundgren\n"
                (0...original_lines.length).each do |o_index|
                    if o_index == index #this is the modified line
                        new_source.print "/*\nFault injected here\nOriginal line was: \n#{original_lines[o_index]}*/\n"
                        new_source.print mutant_lines[index][key]
                    else #write the original line
                        new_source.print original_lines[o_index]
                    end
                end
                new_source.close
                #compile the completed file
                compile_java( File.join(new_dir, filename) )
            end
        end
        #write the method_list and mutant_log files as required

    end
end


parse_dir(File.join(@here, "src"))
puts "Compiled #{@good} out of #{@total}"

