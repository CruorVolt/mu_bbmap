require 'optparse'

live = 0
killed = 0
timeout = 0

File.open(ARGV[0], "r") do |input|
    lines = input.readlines
    for index in [0..lines.length]
        if lines[index].include? "time_out"
            timeout += 1
        elsif lines[index].include? "test_addition_of_reads" and lines[index-1].include? "Fault executed"
            if lines[index].scan("pass").size() == 5
                live += 1
            else 
                killed += 1
            end
        else
            #useless line
        end
    end
end

puts "Live: #{live}"
puts "Killed: #{killed}"
puts "Incomplete: #{timeout}"
