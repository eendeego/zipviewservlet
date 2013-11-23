CPATH=$(CLASSPATH);c:\usr\local\java-lib\jsdk.jar;c:\usr\local\java-lib\xml.jar

%.class : %.java
	javac -O -g -deprecation -classpath "$(CPATH)" $<

CLASSES= \
	ResourceCreator.class \
	ResourcePool.class \
	ZipRecord.class \
	EntryPoint.class \
	ZipRepository.class \
	ZipViewServlet.class

SRC=$(patsubst %.class,%.java,$(CLASSES))

JAR=ZipView.jar

all: $(JAR) XmlTest.class res.zip

res.zip : $(wildcard res/images/*) $(wildcard res/styles/*) $(wildcard res/html/*)
	cd res;\
	jar cvfM ../res.zip images/* styles/* html/*;
	cd ..

$(JAR) : $(CLASSES)
	jar cvf $(JAR) *.class

test : XmlTest.class
	java XmlTest

#copy: all
#	cp ZipViewServlet.class "//e/program files/jrun/servlets/"
#	cp ZipInfo.class "//e/program files/jrun/servlets/"

