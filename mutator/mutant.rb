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

class Mutant

    attr_reader :mutants
    attr_reader :types

    def initialize()
        @mutants = {
            "AORB" => [' + ', ' - ', ' * ', ' / ', ' % '], #binary only
	    "AORU" => ['++', '--'], #unary only, not including -
            "AORS" => [' += ', ' -= ', ' *= ', ' /= ', ' %= ', ' >>= ', ' <<= ', ' &= ', ' ^= '],
            "AODU" => ['++', '--'], #unary only, not including -
            "AODS" => ['<<', '>>', '>>>'],
            "ROR"  => [' > ', ' >= ', ' < ', ' <= ', ' == ', ' != '],
            "COD"  => ['!'], #unary only
            "COR"  => [' && ', ' || ', ' & ', ' | ', ' ^ '], #binary only
            "SOR"  => [' << ', ' >> ', ' >>> '],
            "LOR"  => [' & ', ' | '], #binary only
            "LOD"  => ['~'] #unary only
        }

        @types = {
            "AORB" => "replacement",
	    "AORU" => "replacement",
            "AORS" => "replacement",
            "ROR"  => "replacement",
            "COR"  => "replacement",
            "SOR"  => "replacement",
            "LOR"  => "replacement",
            "LOD"  => "deletion",
            "AODU" => "deletion",
            "AODS" => "deletion",
            "COD"  => "deletion"
        }

        @key_counts = {}
        @mutants.keys.each do |key|
            @key_counts[key] = 1
        end

    end

    #produce a map of alternatives given a line of source code
    def mutate_line(line)
        new_lines = {}
        @mutants.keys.each do |key|
            mutation_type = @types[key]
            tokens = @mutants[key]
            tokens.each do |token|
                offset = 0
                index = line.index(token, offset)
                while (!index.nil?)
                    #this token should produce new mutants on this line, one per other token

                    if mutation_type == "replacement" #many possible mutants
                        alts = tokens.clone.keep_if { |t| t != token }
                        alts.each do |alt|
                            mutant_name = "#{key}_#{@key_counts[key]}"
                            @key_counts[key] += 1
                            new_line = "#{line[0...index]}#{alt}#{line[(index + token.length)..-1]}"
                            new_lines[mutant_name] = new_line
                        end
                    elsif mutation_type == "deletion" #one possible mutant
                        mutant_name = "#{key}_#{@key_counts[key]}"
                        @key_counts[key] += 1
                        new_line = "#{line[0...index]}#{line[(index + token.length)..-1]}"
                        new_lines[mutant_name] = new_line
                    else
                        print "Unkown mutation type: #{mutation_type}\n"
                    end
                    offset = index + 1
                    index = line.index(token, offset) #next occurance of the token
                end
            end
        end
        new_lines
    end

end
