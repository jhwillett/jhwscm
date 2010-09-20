
.SUFFIXES: 

SHELL     := bash

DESTDIR   ?= build
LOGDIR    ?= log
SRC       += $(wildcard src/*.java)

# Maintaining TESTS explicitly so I can control ordering.
#
TESTS     += test/TestUtil.java
TESTS     += test/TestMem.java
TESTS     += test/TestIOBuffer.java
TESTS     += test/TestMachine.java
TESTS     += test/TestComputer.java
TESTS     += test/TestScm.java

TEST_SRC  := $(wildcard test/*.java)

#LIBS                 += junit-4.8.2.jar
HOME-junit-4.8.2.jar := http://github.com/downloads/KentBeck/junit
MD5-junit-4.8.2.jar  := 8a498c3d820db50cc7255d8c46c1ebd1

DEPS := $(LIBS:%=$(DESTDIR)/%)

.PHONY: test $(TESTS:test/%.java=test-%)
test: $(TESTS:test/%.java=test-%)
$(TESTS:test/%.java=test-%): test-%: $(LOGDIR)/%.log
$(TESTS:test/%.java=$(LOGDIR)/%.log): $(LOGDIR)/%.log: Makefile
$(TESTS:test/%.java=$(LOGDIR)/%.log): $(LOGDIR)/%.log: $(LOGDIR)/md5.log
$(TESTS:test/%.java=$(LOGDIR)/%.log): $(LOGDIR)/%.log: $(DESTDIR)/build.ok
$(TESTS:test/%.java=$(LOGDIR)/%.log): $(LOGDIR)/%.log:
	@mkdir -p $(dir $@)
	@time bash -c "set -o pipefail ;$(MAKE) rawtest-$* 2>&1 | tee -a $@.tmp"
	@echo "date:   `date`"
	@echo "uptime: `uptime`"
	@mv $@.tmp $@

$(LOGDIR)/md5.log: Makefile $(SRC) $(TEST_SRC) $(DEPS)
	@mkdir -p $(dir $@)
	md5sum $^ > $@.tmp
	@mv $@.tmp $@

.PHONY: rawtest $(TESTS:test/%.java=rawtest-%)
rawtest: $(TESTS:test/%.java=rawtest-%)
$(TESTS:test/%.java=rawtest-%): rawtest-%:
	@echo "test:   $*"
	@echo "host:   `hostname`"
	@java -cp $(DESTDIR):$(DEPS) $*

.PHONY: build
build: $(DESTDIR)/build.ok
$(DESTDIR)/build.ok: $(DEPS)
$(DESTDIR)/build.ok: $(SRC)
$(DESTDIR)/build.ok: $(TEST_SRC)
$(DESTDIR)/build.ok:
	mkdir -p $(dir $@)
	time javac -cp $(DESTDIR):$(DEPS) -d $(DESTDIR) $(SRC) $(TEST_SRC)
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

