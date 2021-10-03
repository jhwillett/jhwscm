#
# jhwscm/Makefile
#
# Copyright (C) 2010,2021 Jesse H. Willett
# MIT License (see jhwscm/LICENSE.txt)

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
MODULES += shell

SRC-base := $(wildcard base/src/*.java)
TST-base := $(wildcard base/test/*.java)
CPS-base := $(DESTDIR)/base/classes
CPT-base := $(CPS-base):$(DESTDIR)/base/test

SRC-firm := $(wildcard firm/src/*.java)
TST-firm := $(wildcard firm/test/*.java)
CPS-firm := $(CPS-base):$(DESTDIR)/firm/classes
CPT-firm := $(CPT-base):$(DESTDIR)/firm/classes:$(DESTDIR)/firm/test

SRC-shell := $(wildcard shell/src/*.java)
TST-shell := $(wildcard shell/test/*.java)
CPS-shell := $(CPS-firm):$(DESTDIR)/shell/classes
CPT-shell := $(CPT-firm):$(DESTDIR)/shell/classes:$(DESTDIR)/shell/test

.PHONY: build $(MODULES:%=build-%)
build: $(MODULES:%=build-%)
$(MODULES:%=build-%): build-%: $(DESTDIR)/build-%.ok

$(DESTDIR)/build-base.ok:  $(wildcard base/*/*.java)

$(DESTDIR)/build-firm.ok:  $(wildcard firm/*/*.java)
$(DESTDIR)/build-firm.ok:  $(DESTDIR)/build-base.ok

$(DESTDIR)/build-shell.ok: $(DESTDIR)/build-base.ok
$(DESTDIR)/build-shell.ok: $(DESTDIR)/build-firm.ok
$(DESTDIR)/build-shell.ok: $(wildcard shell/*/*.java)

$(MODULES:%=$(DESTDIR)/build-%.ok): $(DESTDIR)/build-%.ok:
	mkdir -p $(DESTDIR)/$*/classes
	javac -cp $(CPS-$*) -d $(DESTDIR)/$*/classes $(SRC-$*)
	mkdir -p  $(DESTDIR)/$*/test
	javac -cp $(CPT-$*) -d $(DESTDIR)/$*/test    $(TST-$*)
	touch $@

.PHONY: run
run: $(DESTDIR)/build-shell.ok
run:
	java -cp $(CPT-shell) Shell

.PHONY: rawtest $(MODULES:%=rawtest-%)
rawtest: $(MODULES:%=rawtest-%)
$(MODULES:%=rawtest-%): rawtest-%: $(DESTDIR)/build-%.ok
rawtest-base:
	time java -cp $(CPT-base) TestUtil
	time java -cp $(CPT-base) TestMem
	time java -cp $(CPT-base) TestIOBuffer
	time java -cp $(CPT-base) TestMachine
	time java -cp $(CPT-base) TestComputer
rawtest-firm:
	time java -cp $(CPT-firm) TestScm
rawtest-shell:
	time java -cp $(CPT-shell) TestShell

.PHONY: test $(MODULES:%=test-%)
test: $(MODULES:%=test-%)
$(MODULES:%=test-%): test-%: $(DESTDIR)/test-%.ok
$(MODULES:%=$(DESTDIR)/test-%.ok): $(DESTDIR)/test-%.ok: $(DESTDIR)/build-%.ok
$(DESTDIR)/test-base.ok:
	set -o pipefail ; java -cp $(CPT-base) TestUtil     2>&1 | tee $(LOGDIR)/TestUtil.log
	set -o pipefail ; java -cp $(CPT-base) TestMem      2>&1 | tee $(LOGDIR)/TestMem.log
	set -o pipefail ; java -cp $(CPT-base) TestIOBuffer 2>&1 | tee $(LOGDIR)/TestIOBuffer.log
	set -o pipefail ; java -cp $(CPT-base) TestMachine  2>&1 | tee $(LOGDIR)/TestMachine.log
	set -o pipefail ; java -cp $(CPT-base) TestComputer 2>&1 | tee $(LOGDIR)/TestComputer.log
	touch $@
$(DESTDIR)/test-firm.ok: 
	set -o pipefail ; java -cp $(CPT-firm) TestScm      2>&1 | tee $(LOGDIR)/TestScm.log
	touch $@
$(DESTDIR)/test-shell.ok: 
	set -o pipefail ; java -cp $(CPT-shell) TestShell   2>&1 | tee $(LOGDIR)/TestShell.log
	touch $@

test: $(LOGDIR)/md5.log
$(LOGDIR)/md5.log: Makefile deps.mk
$(LOGDIR)/md5.log: $(SRC-base)  $(TST-base)
$(LOGDIR)/md5.log: $(SRC-firm)  $(TST-firm)
$(LOGDIR)/md5.log: $(SRC-shell) $(TST-shell)
$(LOGDIR)/md5.log:
	@mkdir -p $(dir $@)
	@cat /dev/null > $@.tmp
	echo $^ | tr ' ' '\n' | sort | xargs md5sum > $@.tmp
	@mv $@.tmp $@

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
