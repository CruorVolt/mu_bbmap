require 'shellwords'
require 'optparse'

#scan the log and find the last mutant - this will be the first new mutant
#get an ordered list of the mutant's directory and list all the folders up to the mutant in question
#move these folders to some temporary directory where there can't mess with the swing - because that's a problem for some dumb reason
#restart the test and repeat after it runs out of memory
#when all the mutants are exhausted retarn the exiled files to their home in /result

if ARGV.length != 1
    puts "Incorrect number of arguments (#{ARGV.length}). Expected one arg of the form 'package.class'"
    exit
end

test_class = ARGV[0]

last = ""
File.open("/Users/anders/mu_bbmap/out.log", "r")do |file|
    file.each do |line|
        last = line
    end 
    last.strip!
end


#folders = Dir::entries("/Users/anders/mu_bbmap/result/bbmap.current.align2.BBMap/traditional_mutants/void_method()")
folders = Dir::entries("/Users/anders/mu_bbmap/result/bbmap.current.#{test_class}/traditional_mutants/void_method()")
last_place = folders.index(last)
move_these = folders[0...last_place].keep_if { |x| !(["..", "."].include? x) }

p(last)
puts
p(move_these)

move_these.each do |dir|
    #system "mv /Users/anders/mu_bbmap/result/bbmap.current.align2.BBMap/traditional_mutants/void_method\\(\\)/#{dir} /Users/anders/mu_bbmap/mutants_lockup/"
    system "mv /Users/anders/mu_bbmap/result/bbmap.current.#{test_class}/traditional_mutants/void_method\\(\\)/#{dir} /Users/anders/mu_bbmap/mutants_lockup/"
end

#copy existing log to master log file
system "cat /Users/anders/mu_bbmap/out.log >> /Users/anders/mu_bbmap/master.log"
