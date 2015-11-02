require 'optparse'
require 'fileutils'

require_relative 'mutant'

@here = File.dirname(__FILE__)
@results_dir = File.join( @here, "..", "result")
@package = "bbmap.current.align2"

#Reformat the source with eclipse
#Scan the source file
    #make new copies of each faulty version and keep them in subdirectories here, not in the original source location
#Make class files using -cp ORIGINAL_LOCATION
#Move all the new source and binary files to the appropriate directory in /results/ so muJava can work with them

OptionParser.new do |options|
    options.banner = "Usage: generate_faults.rb <file1> <file2> ... [options]"
end.parse!

p ARGV

#Uncomment for prod, file is set for now
#%x(cp ~/mu_bbmap/src/bbmap/current/align2/BBMap.java ~/mu_bbmap/mutator/src/)
#%x(~/eclipse/eclipse -application org.eclipse.jdt.core.JavaCodeFormatter -config ~/mu_bbmap/mutator/java_format.prefs ~/mu_bbmap/mutator/src/BBMap.java)

def parse_dir(dir)
    Dir.foreach(dir) do |file|
        parse_file File.join(dir, file) unless ((file == ".") or (file == ".."))
    end
end

def parse_file(full_path)

    filename = File.split(full_path)[-1]
    puts "Parsing #{filename}"

    #ensure the correct directory structure exists for the results
    new_src_dir = File.join( @results_dir, "#{@package}.#{filename[0..-6]}") #strip ".java" from file
    FileUtils::mkdir_p File.join(new_src_dir, "original")
    FileUtils::mkdir_p File.join(new_src_dir, "traditional_mutants")

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
        end

    end
end


parse_dir(File.join(@here, "src"))

