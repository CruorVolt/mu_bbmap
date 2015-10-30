require 'optparse'

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
    print dir
    Dir.foreach(dir) do |file|
        parse_file File.join(dir, file) unless ((file == ".") or (file == ".."))
    end
end

def parse_file(file)
end

class Mutator

    def initialize
    Mutator(token, alts)

end

#implmentable
#AORB - arithmetic operator replacement binary
#AORU - arithmetic operator replacement unary
#AORS - arithmetic operator replacement shortcut
#AODU - arithmetic operator deletion unary
#AODS - arithmetic operator deletion shortcut (++, -- only?)
#ROR - relational operator replacement
#COR - conditional operator replacement
#COD - conditional operator deletion (unary)
#SOR - shift operator replacement
#LOR - logical operator replacement (binary)
#LOD - logical operator delete (unary)
#ASRS - shortcut assignment operator replacement

#non-implementable
#AOIU - arithmetic operator insertion unary
#AOIS - arithmetic operator insertion shortcut
#COI - conditional operator insertion
#LOI - logical operator insertion
