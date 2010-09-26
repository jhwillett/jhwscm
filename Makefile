
.SUFFIXES: 

SHELL     := bash

.PHONY: default
default: test

DESTDIR   ?= build
LOGDIR    ?= log

.PHONY: clean
clean:
	rm -rf $(DESTDIR)

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

CLASSES   := $(SRC:src/%.java=%)

.PHONY: test $(TESTS:test/%.java=test-%)
test: $(TESTS:test/%.java=test-%)
$(TESTS:test/%.java=test-%): test-%: $(LOGDIR)/%.log
$(TESTS:test/%.java=$(LOGDIR)/%.log): $(LOGDIR)/%.log: Makefile deps.mk
$(TESTS:test/%.java=$(LOGDIR)/%.log): $(LOGDIR)/%.log: $(LOGDIR)/md5.log
$(TESTS:test/%.java=$(LOGDIR)/%.log): $(LOGDIR)/%.log: $(DESTDIR)/build-test.ok
$(TESTS:test/%.java=$(LOGDIR)/%.log): $(LOGDIR)/%.log:
	@mkdir -p $(dir $@)
	@cat /dev/null > $@.tmp
	@time bash -c "set -o pipefail ;$(MAKE) rawtest-$* 2>&1 | tee -a $@.tmp"
	@echo "date:   `date`"
	@echo "uptime: `uptime`"
	@mv $@.tmp $@

$(LOGDIR)/md5.log: Makefile deps.mk $(SRC) $(TEST_SRC)
	@mkdir -p $(dir $@)
	@cat /dev/null > $@.tmp
	md5sum $^ > $@.tmp
	@mv $@.tmp $@

.PHONY: rawtest $(TESTS:test/%.java=rawtest-%)
rawtest: $(TESTS:test/%.java=rawtest-%)
$(TESTS:test/%.java=rawtest-%): rawtest-%:
	@echo "test:   $*"
	@echo "host:   `hostname`"
	@java -cp $(DESTDIR)/classes:$(DESTDIR)/test $*

.PHONY: build-src
build-src: $(DESTDIR)/build-src.ok
$(DESTDIR)/build-src.ok: $(SRC)
$(DESTDIR)/build-src.ok:
	mkdir -p $(dir $@)/classes
	time javac -d $(DESTDIR)/classes $(SRC)
	touch $@

.PHONY: build-test
build-test: $(DESTDIR)/build-test.ok
$(DESTDIR)/build-test.ok: $(DESTDIR)/build-src.ok
$(DESTDIR)/build-test.ok: $(TEST_SRC)
$(DESTDIR)/build-test.ok:
	mkdir -p $(dir $@)/test
	time javac -cp $(DESTDIR)/classes -d $(DESTDIR)/test $(TEST_SRC)
	touch $@

COBERTURA_HOME       := external/cobertura-1.9.4.1
COBERTURA_JAR        := $(COBERTURA_HOME)/cobertura.jar
COBERTURA_INSTRUMENT := $(COBERTURA_HOME)/cobertura-instrument.sh
COBERTURA_REPORT     := $(COBERTURA_HOME)/cobertura-report.sh
.PHONY: cobertura
cobertura: $(DESTDIR)/cobertura-instrument.ok
	java -cp $(COBERTURA_JAR):$(DESTDIR)/cobertura TestIOBuffer
$(DESTDIR)/cobertura-instrument.ok: deps 
$(DESTDIR)/cobertura-instrument.ok: $(DESTDIR)/build-src.ok
$(DESTDIR)/cobertura-instrument.ok: Makefile
$(DESTDIR)/cobertura-instrument.ok:
	rm -rf $(DESTDIR)/cobertura $(DESTDIR)/cobertura.ser
	mkdir -p $(DESTDIR)/cobertura
	$(COBERTURA_INSTRUMENT) --destination $(DESTDIR)/cobertura --datafile $(DESTDIR)/cobertura.ser $(CLASSES)

.PHONY: report
report: $(DESTDIR)/cobertura-instrument.ok 
report: Makefile
report: $(DESTDIR)/build-src.ok
report:
	$(COBERTURA_REPORT) --format html --datafile $(DESTDIR)/cobertura.ser --destination $(DESTDIR)/report src/
