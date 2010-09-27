
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

MODULES += base
MODULES += firm

SRC-base := $(wildcard base/src/*.java)
TST-base := $(wildcard base/test/*.java)
CPS-base := 
CPT-base := -cp $(DESTDIR)/base/classes:$(DESTDIR)/base/test

SRC-firm := $(wildcard firm/src/*.java)
TST-firm := $(wildcard firm/test/*.java)
CPS-firm := -cp $(DESTDIR)/base/classes
CPT-firm := $(CPT-base):$(DESTDIR)/firm/classes:$(DESTDIR)/firm/test

.PHONY: build $(MODULES:%=build-%)
build: $(MODULES:%=build-%)
$(MODULES:%=build-%): build-%: $(DESTDIR)/build-%.ok
$(MODULES:%=$(DESTDIR)/build-%.ok): $(DESTDIR)/build-%.ok:
	@echo "pattern: $*"
	@echo "SRC:     $(SRC-$*)"
	@echo "TST:     $(TST-$*)"
	@echo "CPS:     $(CPS-$*)"
	@echo "CPT:     $(CPT-$*)"
	mkdir -p $(DESTDIR)/$*/classes
	javac $(CPS-$*) -d $(DESTDIR)/$*/classes $(SRC-$*)
	mkdir -p  $(DESTDIR)/$*/test
	javac $(CPT-$*) -d $(DESTDIR)/$*/test    $(TST-$*)
	touch $@

.PHONY: rawtest $(MODULES:%=rawtest-%)
rawtest: $(MODULES:%=rawtest-%)
$(MODULES:%=rawtest-%): rawtest=%: $(DESTDIR)/build-%.ok
rawtest-base:
	java $(CPT-base) TestUtil
	java $(CPT-base) TestMem
	java $(CPT-base) TestIOBuffer
	java $(CPT-base) TestMachine
	java $(CPT-base) TestComputer
rawtest-firm:
	java $(CPT-firm) TestScm

.PHONY: test $(MODULES:%=test-%)
test: $(MODULES:%=test-%)
$(MODULES:%=test-%): test-%: $(DESTDIR)/test-%.ok
$(MODULES:%=$(DESTDIR)/test-%.ok): $(DESTDIR)/test-%.ok: $(DESTDIR)/build-%.ok
$(DESTDIR)/test-base.ok:
	java $(CPT-base) TestUtil     2>&1 | tee $(LOGDIR)/TestUtil.log
	java $(CPT-base) TestMem      2>&1 | tee $(LOGDIR)/TestMem.log
	java $(CPT-base) TestIOBuffer 2>&1 | tee $(LOGDIR)/TestIOBuffer.log
	java $(CPT-base) TestMachine  2>&1 | tee $(LOGDIR)/TestMachine.log
	java $(CPT-base) TestComputer 2>&1 | tee $(LOGDIR)/TestComputer.log
	touch $@
$(DESTDIR)/test-firm.ok: 
	java $(CPT-firm) TestScm      2>&1 | tee $(LOGDIR)/TestScm.log
	touch $@

test: $(LOGDIR)/md5.log
$(LOGDIR)/md5.log: Makefile deps.mk
$(LOGDIR)/md5.log: $(SRC-base) $(TST-base)
$(LOGDIR)/md5.log: $(SRC-firm) $(TST-firm)
$(LOGDIR)/md5.log:
	@mkdir -p $(dir $@)
	@cat /dev/null > $@.tmp
	md5sum $^ > $@.tmp
	@mv $@.tmp $@


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

#.PHONY: test $(TESTS:test/%.java=test-%)
#test: $(TESTS:test/%.java=test-%)
#$(TESTS:test/%.java=test-%): test-%: $(LOGDIR)/%.log
#$(TESTS:test/%.java=$(LOGDIR)/%.log): $(LOGDIR)/%.log: Makefile deps.mk
#$(TESTS:test/%.java=$(LOGDIR)/%.log): $(LOGDIR)/%.log: $(LOGDIR)/md5.log
#$(TESTS:test/%.java=$(LOGDIR)/%.log): $(LOGDIR)/%.log: $(DESTDIR)/build-test.ok
#$(TESTS:test/%.java=$(LOGDIR)/%.log): $(LOGDIR)/%.log:
#	@mkdir -p $(dir $@)
#	@cat /dev/null > $@.tmp
#	@time bash -c "set -o pipefail ;$(MAKE) rawtest-$* 2>&1 | tee -a $@.tmp"
#	@echo "date:   `date`"
#	@echo "uptime: `uptime`"
#	@mv $@.tmp $@

#.PHONY: rawtest $(TESTS:test/%.java=rawtest-%)
#rawtest: $(TESTS:test/%.java=rawtest-%)
#$(TESTS:test/%.java=rawtest-%): rawtest-%:
#	@echo "test:   $*"
#	@echo "host:   `hostname`"
#	@java -cp $(DESTDIR)/classes:$(DESTDIR)/test $*

COBERTURA_HOME       := external/cobertura-1.9.4.1
COBERTURA_JAR        := $(COBERTURA_HOME)/cobertura.jar
COBERTURA_INSTRUMENT := $(COBERTURA_HOME)/cobertura-instrument.sh
COBERTURA_REPORT     := $(COBERTURA_HOME)/cobertura-report.sh

COBERTURA_RUN := java
COBERTURA_RUN += -cp $(COBERTURA_JAR):$(DESTDIR)/cobertura:$(DESTDIR)/classes:$(DESTDIR)/test
COBERTURA_RUN += -Dnet.sourceforge.cobertura.datafile=$(DESTDIR)/cobertura.ser

.PHONY: cobertura
cobertura: $(DESTDIR)/cobertura.ok
$(DESTDIR)/cobertura.ok: Makefile
$(DESTDIR)/cobertura.ok: $(DESTDIR)/cobertura-instrument.ok
$(DESTDIR)/cobertura.ok: $(DESTDIR)/build-test.ok
$(DESTDIR)/cobertura.ok:
	$(COBERTURA_RUN) TestUtil
	$(COBERTURA_RUN) TestIOBuffer
	$(COBERTURA_RUN) TestMem
	$(COBERTURA_RUN) TestMachine
	$(COBERTURA_RUN) TestComputer
	$(COBERTURA_REPORT) --format html --datafile $(DESTDIR)/cobertura.ser --destination $(DESTDIR)/report-base --source src/
	$(COBERTURA_RUN) TestScm
	$(COBERTURA_REPORT) --format html --datafile $(DESTDIR)/cobertura.ser --destination $(DESTDIR)/report-full --source src/
	touch $@
$(DESTDIR)/cobertura-instrument.ok: Makefile
$(DESTDIR)/cobertura-instrument.ok: deps 
$(DESTDIR)/cobertura-instrument.ok: $(DESTDIR)/build-src.ok
$(DESTDIR)/cobertura-instrument.ok:
	rm -rf $(DESTDIR)/cobertura $(DESTDIR)/cobertura.ser
	mkdir -p $(DESTDIR)/cobertura
	$(COBERTURA_INSTRUMENT) --destination $(DESTDIR)/cobertura --datafile $(DESTDIR)/cobertura.ser $(DESTDIR)/classes
	touch $@
