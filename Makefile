
.SUFFIXES: 

SHELL     := bash

.PHONY: default
default: test

DESTDIR   ?= build
LOGDIR    ?= log

include deps.mk

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

.PHONY: test $(TESTS:test/%.java=test-%)
test: $(TESTS:test/%.java=test-%)
$(TESTS:test/%.java=test-%): test-%: $(LOGDIR)/%.log
$(TESTS:test/%.java=$(LOGDIR)/%.log): $(LOGDIR)/%.log: Makefile deps.mk
$(TESTS:test/%.java=$(LOGDIR)/%.log): $(LOGDIR)/%.log: $(LOGDIR)/md5.log
$(TESTS:test/%.java=$(LOGDIR)/%.log): $(LOGDIR)/%.log: $(DESTDIR)/build.ok
$(TESTS:test/%.java=$(LOGDIR)/%.log): $(LOGDIR)/%.log:
	@mkdir -p $(dir $@)
	@cat /dev/null > $@.tmp
	@time bash -c "set -o pipefail ;$(MAKE) rawtest-$* 2>&1 | tee -a $@.tmp"
	@echo "date:   `date`"
	@echo "uptime: `uptime`"
	@mv $@.tmp $@

$(LOGDIR)/md5.log: Makefile deps.mk $(SRC) $(TEST_SRC) $(DEPS)
	@mkdir -p $(dir $@)
	@cat /dev/null > $@.tmp
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
