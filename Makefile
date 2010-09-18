
.SUFFIXES: 

SHELL     := bash

DESTDIR   ?= build
SRC       += $(wildcard src/*.java)

# Maintaining TESTS explicitly so I can control ordering.
#
#TESTS     += test/TestMem.java
TESTS     += test/TestScm.java

LIBS                 += junit-4.8.2.jar
HOME-junit-4.8.2.jar := http://github.com/downloads/KentBeck/junit
MD5-junit-4.8.2.jar  := 8a498c3d820db50cc7255d8c46c1ebd1

DEPS := $(LIBS:%=$(DESTDIR)/%)

.PHONY: test $(TESTS:%=test-%)
test: $(TESTS:%=test-%)
$(TESTS:%=test-%): test-%: $(DESTDIR)/%.tested
$(TESTS:%=build/%.tested): $(DESTDIR)/%.tested: $(DESTDIR)/build.ok
$(TESTS:%=build/%.tested): $(DESTDIR)/%.tested: 
	time java -cp $(DESTDIR):$(DEPS) `basename $* | sed 's/.java$$//g'`
	uptime

.PHONY: build
build: $(DESTDIR)/build.ok
$(DESTDIR)/build.ok: $(DEPS)
$(DESTDIR)/build.ok: $(SRC)
$(DESTDIR)/build.ok: $(TESTS)
$(DESTDIR)/build.ok:
	mkdir -p $(dir $@)
	time javac -cp $(DESTDIR):$(DEPS) -d $(DESTDIR) $(SRC) $(TESTS)
	touch $@

.PHONY: clean
clean:
	rm -rf $(DESTDIR)

.PHONY: getdeps
getdeps: $(DEPS)
$(DEPS): $(DESTDIR)/%: 
	mkdir -p $(dir $@)
	curl $(HOME-$*)/$* --location --output $@.tmp
	md5sum $@.tmp | cut -f 1 -d ' ' > $@.md5.actual
	echo $(MD5-$*) > $@.md5.expect
	diff $@.md5.expect $@.md5.actual
	mv $@.tmp $@

