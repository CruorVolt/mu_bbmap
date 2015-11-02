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

    def initialize()
        @mutants = {
            "AORB" => ['+', '-', '*', '/', '%'],
	    "AORU" => ['-', '++', '--'],
            "AORS" => ['+=', '-=', '*=', '/=', '%=', '>>=', '<<=', '&=', '^='],
            "AODU" => ['-', '++', '--'],
            "AODS" => ['<<', '>>', '>>>'],
            "ROR" => ['>', '>=', '<', '<=', '==', '!='],
            "COD" => ['!'],#unary only
            "COR" => ['&&', '||', '&', '|', '^'],#binary only
            "SOR" => ['<<', '>>', '>>>'],
            "LOR" => ['&', '|'],#binary only
            "LOD" => ['~']#unary only
        }
    end

end
