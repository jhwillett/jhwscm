
.SUFFIXES: 

SHELL     := bash

DESTDIR   ?= build
SRC       := *.java
MAIN      := Test
MAIN_ARGS := 


LIBS                 += junit-4.8.2.jar
HOME-junit-4.8.2.jar := http://github.com/downloads/KentBeck/junit
MD5-junit-4.8.2.jar  := 8a498c3d820db50cc7255d8c46c1ebd1

DEPS := $(LIBS:%=$(DESTDIR)/%)

.PHONY: test build clean getdeps

test: $(DESTDIR)/build.ok
	time java -cp $(DESTDIR):$(DEPS) $(MAIN) $(MAIN_ARGS)

build: $(DESTDIR)/build.ok
$(DESTDIR)/build.ok: $(DEPS)
$(DESTDIR)/build.ok: $(SRC)
	mkdir -p $(dir $@)
	time javac -cp $(DESTDIR):$(DEPS) -d $(DESTDIR) $(SRC)
	touch $@

clean:
	rm -rf $(DESTDIR)

getdeps: $(DEPS)
$(DEPS): $(DESTDIR)/%: 
	mkdir -p $(dir $@)
	curl $(HOME-$*)/$* --location --output $@.tmp
	md5sum $@.tmp | cut -f 1 -d ' ' > $@.md5.actual
	echo $(MD5-$*) > $@.md5.expect
	diff $@.md5.expect $@.md5.actual
	mv $@.tmp $@

