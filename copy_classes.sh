#Copy class files from reference install to /classes
find src/bbmap/ -type f -name '*.class' -exec cp '{}' classes/ ';'
