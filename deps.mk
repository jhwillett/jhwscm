#
# jhwscm/deps.mk
#
# Copyright (C) 2010,2021 Jesse H. Willett
# MIT License (see jhwscm/LICENSE.txt)

FILES := $(shell ls -1 deps/*.url | sed 's:^deps/\(.*\).url$$:\1:g' )
JARS  := $(filter %.jar,$(FILES))
TBZS  := $(filter %.tar.bz2,$(FILES))
TGZS  := $(filter %.tar.gz,$(FILES))
ZIPS  := $(filter %.zip,$(FILES))

EXTDIR := external

.PHONY: clean-deps
clean-deps:
	rm -rf $(EXTERNAL)

.PHONY: deps $(FILES:%=dep-%)
deps: $(FILES:%=dep-%)
$(FILES:%=dep-%): dep-%: $(EXTDIR)/%.crack.ok

# download the files
$(FILES:%=$(EXTDIR)/%): $(EXTDIR)/%: 
	mkdir -p $(dir $@)
	curl `cat deps/$*.url` --location --output $@.tmp
	mv $@.tmp $@

# confirm file signatures
$(FILES:%=$(EXTDIR)/%.md5.ok): $(EXTDIR)/%.md5.ok: $(EXTDIR)/%
	mkdir -p $(dir $@)
	md5sum $< | cut -f 1 -d ' ' > $@.md5.actual
	diff -wB deps/$(notdir $<).md5 $@.md5.actual
	touch $@

# open the files, varies by file type
$(JARS:%=$(EXTDIR)/%.crack.ok): $(EXTDIR)/%.crack.ok: $(EXTDIR)/%.md5.ok
	touch $@
$(TBZS:%=$(EXTDIR)/%.crack.ok): $(EXTDIR)/%.crack.ok: $(EXTDIR)/%.md5.ok
	cd $(EXTDIR) && tar xvfj $*
	touch $@
$(TGZS:%=$(EXTDIR)/%.crack.ok): $(EXTDIR)/%.crack.ok: $(EXTDIR)/%.md5.ok
	cd $(EXTDIR) && tar xvfz $*
	touch $@
$(ZIPS:%=$(EXTDIR)/%.crack.ok): $(EXTDIR)/%.crack.ok: $(EXTDIR)/%.md5.ok
	cd $(EXTDIR) && unzip $*
	touch $@
