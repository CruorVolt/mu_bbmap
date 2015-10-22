#!/bin/bash
#dedupebymapping in=<infile> out=<outfile>

usage(){
echo "
Written by Brian Bushnell
Last modified February 25, 2015

Description:  Deduplicates mapped reads based on pair mapping coordinates.

Usage:   dedupebymapping.sh in=<file> out=<file>

Input may be stdin or a sam/bam file, compressed or uncompressed.


Parameters:
in=<file>           The 'in=' flag is needed if the input file is not the 
                    first parameter.  'in=stdin' will pipe from standard in.
out=<file>          The 'out=' flag is needed if the output file is not the 
                    second parameter.  'out=stdout' will pipe to standard out.
overwrite=t         (ow) Set to false to force the program to abort rather 
                    than overwrite an existing file.
ziplevel=2          (zl) Set to 1 (lowest) through 9 (max) to change 
                    compression level; lower compression is faster.
keepunmapped=t      (ku) Keep unmapped reads.  This refers to unmapped
                    single-ended reads or pairs with both unmapped.
monitor=f           Kill this process if it crashes.  monitor=600,0.01 
                    would kill after 600 seconds under 1% usage.

Java Parameters:
-Xmx                This will be passed to Java to set memory usage, overriding
                    the program's automatic memory detection.  -Xmx20g will 
                    specify 20 gigs of RAM, and -Xmx200m will specify 200 megs.
                    The max is typically 85% of physical memory.

Please contact Brian Bushnell at bbushnell@lbl.gov if you encounter any problems.
"
}

pushd . > /dev/null
DIR="${BASH_SOURCE[0]}"
while [ -h "$DIR" ]; do
  cd "$(dirname "$DIR")"
  DIR="$(readlink "$(basename "$DIR")")"
done
cd "$(dirname "$DIR")"
DIR="$(pwd)/"
popd > /dev/null

#DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )/"
CP="$DIR""current/"

z="-Xmx3g"
z2="-Xms3g"
EA="-ea"
set=0

if [ -z "$1" ] || [[ $1 == -h ]] || [[ $1 == --help ]]; then
	usage
	exit
fi

calcXmx () {
	source "$DIR""/calcmem.sh"
	parseXmx "$@"
	if [[ $set == 1 ]]; then
		return
	fi
	freeRam 3000m 84
	z="-Xmx${RAM}m"
	z2="-Xms${RAM}m"
}
calcXmx "$@"

dedupebymapping() {
	#module unload oracle-jdk
	#module load oracle-jdk/1.7_64bit
	#module load pigz
	#module load samtools
	local CMD="java $EA $z $z2 -cp $CP jgi.DedupeByMapping $@"
	echo $CMD >&2
	eval $CMD
}

dedupebymapping "$@"
