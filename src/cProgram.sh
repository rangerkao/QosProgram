echo start...
pwd

#setting libs path 
libDir=lib/* 
temp=.:

append(){ 
                temp=$temp":"$1 
} 

for file in $libDir;    do 
    append $file 
done 

javac -classpath $temp main/QosProgram.java

echo finished