
#scan the log and find the last mutant - this will be the first new mutant
#get an ordered list of the mutant's directory and list all the folders up to the mutant in question
#move these folders to some temporary directory where there can't mess with the swing - because that's a problem for some dumb reason
#restart the test and repeat after it runs out of memory
#when all the mutants are exhausted retarn the exiled files to their home in /result

last = ""
File.open("/Users/anders/mu_bbmap/out.log", "r")do |file|
    file.each do |line|
        last = line
    end 
    last.strip!
end


folders = Dir::entries("/Users/anders/mu_bbmap/result/bbmap.current.align2.BBMap/traditional_mutants/void_method()")
last_place = folders.index(last)
move_these = folders[0...last_place].keep_if { |x| !(["..", "."].include? x) }
p(last)
puts
p(move_these)
